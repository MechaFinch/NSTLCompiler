package notsotiny.lang.compiler.codegen.dag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import notsotiny.lang.ir.parts.IRArgumentList;
import notsotiny.lang.ir.parts.IRArgumentMapping;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRBranchInstruction;
import notsotiny.lang.ir.parts.IRBranchOperation;
import notsotiny.lang.ir.parts.IRConstant;
import notsotiny.lang.ir.parts.IRDefinition;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.parts.IRLinearOperation;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lang.util.Pair;

/**
 * Builds an instruction selection DAG
 */
public class ISelDAGBuilder {
    
    private static Logger LOG = Logger.getLogger(ISelDAGBuilder.class.getName());
    
    private static Set<IRLinearOperation> involvesSideEffects;
    
    static {
        involvesSideEffects = new HashSet<>();
        involvesSideEffects.add(IRLinearOperation.CALLN);
        involvesSideEffects.add(IRLinearOperation.CALLR);
        involvesSideEffects.add(IRLinearOperation.LOAD);
        involvesSideEffects.add(IRLinearOperation.STORE);
    }
    
    /**
     * Builds an instruction selection DAG for the given basic block
     * @param bb
     * @param typeMap
     * @param livenessSets
     * @return A map from bb ID to DAG. If the BB branches conditionally, up to two additional DAGs handling conditional argument mappings may be produced.
     */
    public static Map<IRIdentifier, ISelDAG> buildDAG(IRBasicBlock bb, Map<IRIdentifier, IRType> typeMap, Map<IRIdentifier, Pair<Set<IRIdentifier>, Set<IRIdentifier>>> livenessSets, Map<IRIdentifier, IRDefinition> definitionMap) {
        LOG.finer("Building DAG for " + bb.getID());
        
        Map<IRIdentifier, ISelDAG> dagMap = new HashMap<>();
        
        ISelDAG dag = new ISelDAG(bb);
        dagMap.put(bb.getID(), dag);
        
        /*
         * Traverse BB code backwards. If the instruction defines a live-out, has side effects, or
         * no dead code elimination is allowed, add the instruction to the DAG if it is not present.
         * For each argument of an added instruction, add its definition if not already present.
         * 
         * Traverse BB code forwards. Record chain information in side-effecting (STORE, CALL) and
         * side-effected (LOAD) instructions.
         */
        
        // Traverse backwards. Start with branch instruction
        IRBranchInstruction branch = bb.getExitInstruction();
        ISelDAGTerminatorNode branchNode;
        
        switch(branch.getOp()) {
            case JCC:
                // Conditional argument mappings are moved to their own basic blocks as a pretransformation.
                // Verify that this is the case to ensure things aren't silently missed
                if((branch.getTrueArgumentMapping().getMap().size() > 0) || (branch.getFalseArgumentMapping().getMap().size() > 0)) {
                    throw new IllegalArgumentException("Found conditional argument mappping: " + branch);
                }
                
                ISelDAGProducerNode compLeftNode = buildProducer(branch.getCompareLeft(), bb, dag, typeMap, livenessSets, definitionMap);
                ISelDAGProducerNode compRightNode = buildProducer(branch.getCompareRight(), bb, dag, typeMap, livenessSets, definitionMap);
                branchNode = new ISelDAGTerminatorNode(dag, ISelDAGTerminatorOperation.JCC, branch.getTrueTargetBlock(), branch.getFalseTargetBlock(), compLeftNode, compRightNode, branch.getCondition());
                break;
                
            case JMP:
                branchNode = new ISelDAGTerminatorNode(dag, ISelDAGTerminatorOperation.JMP, branch.getTrueTargetBlock());
                
                // OUT nodes for target arg mapping
                Map<IRIdentifier, ISelDAGProducerNode> mapNodes = buildArgumentMapping(branch.getTrueArgumentMapping(), bb, dag, typeMap, livenessSets, definitionMap);
                
                for(Entry<IRIdentifier, ISelDAGProducerNode> mapEntry : mapNodes.entrySet()) {
                    new ISelDAGTerminatorNode(dag, ISelDAGTerminatorOperation.OUT, mapEntry.getKey(), mapEntry.getValue());
                }
                break;
                
            case RET:
                ISelDAGProducerNode valNode = buildProducer(branch.getReturnValue(), bb, dag, typeMap, livenessSets, definitionMap);
                branchNode = new ISelDAGTerminatorNode(dag, ISelDAGTerminatorOperation.RET, valNode);
                break;
            
            default:
                throw new IllegalArgumentException("Unexpected branch operation: " + branch.getOp());
        }
        
        Set<IRIdentifier> liveOut = livenessSets.get(bb.getID()).b;
        
        // Traverse through LIs
        ISelDAGNode chainNode = branchNode;
        
        for(int i = bb.getInstructions().size() - 1; i >= 0; i--) {
            IRLinearInstruction li = bb.getInstructions().get(i);
            
            if(li.hasDestination()) {
                boolean makesLiveOut = liveOut.contains(li.getDestinationID()),
                        sideEffects = involvesSideEffects.contains(li.getOp());
                
                if(makesLiveOut || sideEffects) {
                    // Is this node is an input to another node
                    boolean hasLocalUser = dag.getProducers().containsKey(li.getDestinationID());
                    
                    // LI produces a live-out or involves side effects. Get node.
                    ISelDAGProducerNode node = buildProducer(li.getDestinationID(), bb, dag, typeMap, livenessSets, definitionMap);
                    
                    // Record chain if side effects involved
                    if(node.getOp() == ISelDAGProducerOperation.CALLR) {
                        // Calls produce a chain of PUSHs
                        chainNode.setChain(node);
                        
                        if(node.getInputNodes().size() > 1) {
                            // chain to last push if it exists
                            chainNode = node.getInputNodes().get(node.getInputNodes().size() - 1);
                        } else {
                            chainNode = node;
                        }
                    } else if(sideEffects) {
                        chainNode.setChain(node);
                        chainNode = node;
                    }
                    
                    if(makesLiveOut) {
                        // LI produces a live-out. Create OUT terminator
                        new ISelDAGTerminatorNode(dag, ISelDAGTerminatorOperation.OUT, li.getDestinationID(), node);
                    } else if(!hasLocalUser) {
                        // LI doesn't produce a live-out and isn't used locally, mark as terminator
                        dag.addTerminator(node);
                    }
                }
            } else if(involvesSideEffects.contains(li.getOp())) {
                // LI does not produce a value, but has side effects (STORE, CALLN)
                ISelDAGTerminatorNode node;
                
                switch(li.getOp()) {
                    case STORE:
                        ISelDAGProducerNode valueNode = buildProducer(li.getLeftSourceValue(), bb, dag, typeMap, livenessSets, definitionMap);
                        ISelDAGProducerNode targetNode = buildProducer(li.getRightSourceValue(), bb, dag, typeMap, livenessSets, definitionMap);
                        
                        node = new ISelDAGTerminatorNode(dag, ISelDAGTerminatorOperation.STORE, valueNode, targetNode);
                        break;
                    
                    case CALLN:
                        // call
                        List<ISelDAGProducerNode> argList = buildCallArguments(li, bb, dag, typeMap, livenessSets, definitionMap);
                        node = new ISelDAGTerminatorNode(dag, ISelDAGTerminatorOperation.CALLN, argList);
                        break;
                    
                    default:
                        throw new IllegalStateException("Unexpected definitionless side-effecting operation: " + li.getOp());
                }
                
                // Record chain
                if(node.getOp() == ISelDAGTerminatorOperation.CALLN) {
                    // Calls produce a chain of PUSHs
                    chainNode.setChain(node);
                    
                    if(node.getInputNodes().size() > 1) {
                        // chain to last push if it exists
                        chainNode = node.getInputNodes().get(node.getInputNodes().size() - 1);
                    } else {
                        chainNode = node;
                    }
                } else {
                    chainNode.setChain(node);
                    chainNode = node;
                }
            }
        }
        
        // Add chain end
        new ISelDAGTerminatorNode(dag, chainNode);
        
        return dagMap;
    }
    
    /**
     * Construct the DAG nodes required to produce the values assigned to arguments
     * Does not produce OUT terminators.
     * @param mapping
     * @param bb
     * @param dag
     * @param typeMap
     * @param livenessSets
     * @param definitionMap
     */
    private static Map<IRIdentifier, ISelDAGProducerNode> buildArgumentMapping(IRArgumentMapping mapping, IRBasicBlock bb, ISelDAG dag, Map<IRIdentifier, IRType> typeMap, Map<IRIdentifier, Pair<Set<IRIdentifier>, Set<IRIdentifier>>> livenessSets, Map<IRIdentifier, IRDefinition> definitionMap) {
        Map<IRIdentifier, ISelDAGProducerNode> nodes = new HashMap<>();
        
        for(Entry<IRIdentifier, IRValue> entry : mapping.getMap().entrySet()) {
            nodes.put(entry.getKey(), buildProducer(entry.getValue(), bb, dag, typeMap, livenessSets, definitionMap));
        }
        
        return nodes;
    }
    
    /**
     * Constructs the DAG nodes for the arguments of a CALLR or CALLN instruction.
     * @param li
     * @param bb
     * @param dag
     * @param typeMap
     * @param livenessSets
     * @param definitionMap
     * @return
     */
    private static List<ISelDAGProducerNode> buildCallArguments(IRLinearInstruction li, IRBasicBlock bb, ISelDAG dag, Map<IRIdentifier, IRType> typeMap, Map<IRIdentifier, Pair<Set<IRIdentifier>, Set<IRIdentifier>>> livenessSets, Map<IRIdentifier, IRDefinition> definitionMap) {
        Map<IRIdentifier, ISelDAGProducerNode> argMap = buildArgumentMapping(li.getCallArgumentMapping(), bb, dag, typeMap, livenessSets, definitionMap);
        List<ISelDAGProducerNode> argList = new ArrayList<>();
        argList.add(buildProducer(li.getCallTarget(), bb, dag, typeMap, livenessSets, definitionMap));
        
        /*
         * Calls are built so that arguments are given PUSH nodes rather than being direct inputs to the CALL to allow argument computation
         * to be interlaced with argument pushes, reducing register pressure. Chains ensure that the pushes happen in the correct order.
         * 
         * The original idea saw sets of PUSHs have their own chains independent of the main side-effector chain, but this introduces a
         * scheduling problem about how to represent the fact that PUSHs from separate CALLs can be interlaced but only if all PUSHs from
         * one call happen between the same pair of PUSHs from the other. 
         * Including PUSHs in the main side-effector chain reduces the amount of possible interlacing, but causes no such representation
         * problem.
         */
        
        // Connect arguments via PUSH nodes
        ISelDAGProducerNode previousPush = null;
        
        // Get target function's argument list to determine ordering
        for(IRIdentifier argName : li.getCallArgumentMapping().getOrdering()) {
            // arg -> PUSH -> CALL
            ISelDAGProducerNode argNode = argMap.get(argName);
            ISelDAGProducerNode pushNode = new ISelDAGProducerNode(dag, new IRIdentifier("push%" + bb.getFunction().getFUID(), IRIdentifierClass.LOCAL), argNode.getProducedType(), ISelDAGProducerOperation.PUSH, argNode);
            
            // chain PUSHs for correct ordering
            if(previousPush != null) {
                previousPush.setChain(pushNode);
            }
                        
            argList.add(pushNode);
            previousPush = pushNode;
        }
        
        return argList;
    }
    
    /**
     * Construct the DAG nodes required to produce the given value
     * @param value
     * @param bb
     * @param dag
     * @param typeMap
     * @param livenessSets
     * @param definitionMap
     * @return
     */
    private static ISelDAGProducerNode buildProducer(IRValue value, IRBasicBlock bb, ISelDAG dag, Map<IRIdentifier, IRType> typeMap, Map<IRIdentifier, Pair<Set<IRIdentifier>, Set<IRIdentifier>>> livenessSets, Map<IRIdentifier, IRDefinition> definitionMap) {
        // What are we dealing with
        if(value instanceof IRConstant c) {
            // Currently available constant
            IRIdentifier constID = new IRIdentifier("const%" + bb.getFunction().getFUID(), IRIdentifierClass.LOCAL);
            typeMap.put(constID, c.getType());
            
            return new ISelDAGProducerNode(dag, constID, c, c.getType(), ISelDAGProducerOperation.VALUE);
        }
        
        // Must be ID
        IRIdentifier id = (IRIdentifier) value;
        
        if(id.getIDClass() != IRIdentifierClass.LOCAL) { 
            // Resolves to some address
            IRIdentifier localID = new IRIdentifier(id.getName() + "%" + bb.getFunction().getFUID(), IRIdentifierClass.LOCAL);
            typeMap.put(localID, IRType.I32);
            
            return new ISelDAGProducerNode(dag, localID, id, IRType.I32, ISelDAGProducerOperation.VALUE);
        }
        
        // Local. Have we produced it yet?
        ISelDAGProducerNode node = dag.getProducer(id);
        
        if(node != null) {
            //LOG.finest("Found in DAG: " + value);
            
            // Yes. return it.
            return node;
        }
        
        // No. Make it.
        IRType type = typeMap.get(id);
        
        // Function arguments and live-ins have their own nodes
        if(bb.getFunction().getArguments().getNameList().contains(id)) {
            return new ISelDAGProducerNode(dag, id, type, ISelDAGProducerOperation.ARG);
        }
        
        Set<IRIdentifier> liveIn = livenessSets.get(bb.getID()).a;
        
        if(liveIn.contains(id)) {
            // It's a live-in. Make an IN node
            // Live-ins are given a local ID to avoid read-after-write hazards
            IRIdentifier localID = new IRIdentifier(id.getName() + "%" + bb.getFunction().getFUID(), IRIdentifierClass.LOCAL);
            typeMap.put(localID, typeMap.get(id));
            
            return new ISelDAGProducerNode(dag, localID, id, type, ISelDAGProducerOperation.IN);
        }
        
        //LOG.finest("Making node: " + value);
        
        // Not live-in. Should have a definition from a linear instruction in the block
        IRLinearInstruction inst = definitionMap.get(id).getLinearInstruction();
        ISelDAGProducerOperation op = ISelDAGProducerOperation.get(inst.getOp());
        
        if(op == ISelDAGProducerOperation.CALLR) {
            // call
            List<ISelDAGProducerNode> argList = buildCallArguments(inst, bb, dag, typeMap, livenessSets, definitionMap);
            return new ISelDAGProducerNode(dag, id, type, op, argList);
        } else if(op == ISelDAGProducerOperation.SELECT) {
            // select
            ISelDAGProducerNode compLeftNode = buildProducer(inst.getLeftComparisonValue(), bb, dag, typeMap, livenessSets, definitionMap);
            ISelDAGProducerNode compRightNode = buildProducer(inst.getRightComparisonValue(), bb, dag, typeMap, livenessSets, definitionMap);
            ISelDAGProducerNode valLeftNode = buildProducer(inst.getLeftSourceValue(), bb, dag, typeMap, livenessSets, definitionMap);
            ISelDAGProducerNode valRightNode = buildProducer(inst.getRightSourceValue(), bb, dag, typeMap, livenessSets, definitionMap);
            
            return new ISelDAGProducerNode(dag, id, type, op, compLeftNode, compRightNode, valLeftNode, valRightNode, inst.getSelectCondition());
        } else switch(op.getArgCount()) {
            case 1:
                // 1-argument
                ISelDAGProducerNode argNode = buildProducer(inst.getLeftSourceValue(), bb, dag, typeMap, livenessSets, definitionMap);
                return new ISelDAGProducerNode(dag, id, type, op, argNode);
                
            case 2:
                // two-argument
                ISelDAGProducerNode leftNode = buildProducer(inst.getLeftSourceValue(), bb, dag, typeMap, livenessSets, definitionMap);
                ISelDAGProducerNode rightNode = buildProducer(inst.getRightSourceValue(), bb, dag, typeMap, livenessSets, definitionMap);
                
                return new ISelDAGProducerNode(dag, id, type, op, leftNode, rightNode);
            
            default:
                LOG.warning("Unimplemented: " + inst);
                return null;
        }
    }
    
}
