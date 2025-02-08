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
        
        switch(branch.getOp()) {
            case JCC:
                // If a target has an argument mapping, direct it to a new DAG/bb which contains the mapping OUT nodes
                // If computing the value for a mapping involves no side effects, it may be beneficial to move it to
                // the new DAG
                if(branch.getTrueArgumentMapping().getMap().size() > 0) {
                    assignInNewDAG(branch, true, bb, dag, dagMap, typeMap, livenessSets, definitionMap);
                }
                
                if(branch.getFalseArgumentMapping().getMap().size() > 0) {
                    assignInNewDAG(branch, false, bb, dag, dagMap, typeMap, livenessSets, definitionMap);
                }
                
                ISelDAGProducerNode compLeftNode = buildProducer(branch.getCompareLeft(), bb, dag, typeMap, livenessSets, definitionMap);
                ISelDAGProducerNode compRightNode = buildProducer(branch.getCompareRight(), bb, dag, typeMap, livenessSets, definitionMap);
                new ISelDAGTerminatorNode(dag, ISelDAGTerminatorOperation.JCC, branch.getTrueTargetBlock(), branch.getFalseTargetBlock(), compLeftNode, compRightNode, branch.getCondition());
                break;
                
            case JMP:
                new ISelDAGTerminatorNode(dag, ISelDAGTerminatorOperation.JMP, branch.getTrueTargetBlock());
                
                // OUT nodes for target arg mapping
                Map<IRIdentifier, ISelDAGProducerNode> mapNodes = buildArgumentMapping(branch.getTrueArgumentMapping(), bb, dag, typeMap, livenessSets, definitionMap);
                
                for(Entry<IRIdentifier, ISelDAGProducerNode> mapEntry : mapNodes.entrySet()) {
                    new ISelDAGTerminatorNode(dag, ISelDAGTerminatorOperation.OUT, mapEntry.getKey(), mapEntry.getValue());
                }
                break;
                
            case RET:
                ISelDAGProducerNode valNode = buildProducer(branch.getReturnValue(), bb, dag, typeMap, livenessSets, definitionMap);
                new ISelDAGTerminatorNode(dag, ISelDAGTerminatorOperation.RET, valNode);
                break;
        }
        
        Set<IRIdentifier> liveOut = livenessSets.get(bb.getID()).b;
        
        // Traverse through LIs
        ISelDAGNode chainNode = null;
        
        for(int i = bb.getInstructions().size() - 1; i >= 0; i--) {
            IRLinearInstruction li = bb.getInstructions().get(i);
            
            if(li.hasDestination()) {
                boolean makesLiveOut = liveOut.contains(li.getDestinationID()),
                        sideEffects = involvesSideEffects.contains(li.getOp());
                
                if(makesLiveOut || sideEffects) {
                    // LI produces a live-out or involves side effects. Get node.
                    ISelDAGProducerNode node = buildProducer(li.getDestinationID(), bb, dag, typeMap, livenessSets, definitionMap);
                    
                    // Record chain if side effects involved
                    if(sideEffects) {
                        if(chainNode != null) {
                            chainNode.setChain(node);
                        }
                        
                        chainNode = node;
                    }
                    
                    if(makesLiveOut) {
                        // LI produces a live-out. Create OUT terminator
                        new ISelDAGTerminatorNode(dag, ISelDAGTerminatorOperation.OUT, li.getDestinationID(), node);
                    } else {
                        // LI doesn't produce a live-out. Mark node as a terminator
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
                        IRArgumentMapping mapping = li.getCallArgumentMapping();
                        Map<IRIdentifier, ISelDAGProducerNode> argMap = buildArgumentMapping(mapping, bb, dag, typeMap, livenessSets, definitionMap);
                        List<ISelDAGProducerNode> argList = new ArrayList<>();
                        argList.add(buildProducer(li.getCallTarget(), bb, dag, typeMap, livenessSets, definitionMap));
                        
                        // Get target function's argument list to determine ordering
                        for(IRIdentifier argName : mapping.getOrdering()) {
                            argList.add(argMap.get(argName));
                        }
                        
                        node = new ISelDAGTerminatorNode(dag, ISelDAGTerminatorOperation.CALLN, argList);
                        break;
                    
                    default:
                        throw new IllegalStateException("Unexpected definitionless side-effecting operation: " + li.getOp());
                }
                
                // Record chain
                if(chainNode != null) {
                    chainNode.setChain(node);
                    chainNode = node;
                }
            }
        }
        
        return dagMap;
    }
    
    /**
     * Creates a new IRBasicBlock and corresponding ISelDAG which performs the assignments of the specified branch target.
     * The IR is modified to redirect through the new basic block. 
     * @param branch
     * @param isTrue If true, applies to the true target. If false, applies to the false target
     * @param bb
     * @param mainDAG
     * @param dagMap
     * @return The ID of the new basic block
     */
    private static IRIdentifier assignInNewDAG(IRBranchInstruction branch, boolean isTrue, IRBasicBlock bb, ISelDAG mainDAG, Map<IRIdentifier, ISelDAG> dagMap, Map<IRIdentifier, IRType> typeMap, Map<IRIdentifier, Pair<Set<IRIdentifier>, Set<IRIdentifier>>> livenessSets, Map<IRIdentifier, IRDefinition> definitionMap) {
        IRIdentifier target = isTrue ? branch.getTrueTargetBlock() : branch.getFalseTargetBlock();
        IRArgumentMapping mapping = isTrue ? branch.getTrueArgumentMapping() : branch.getFalseArgumentMapping();
        
        // Create new BB
        IRIdentifier newID = new IRIdentifier(bb.getID().getName() + "%" + (isTrue ? "true" : "false") + "%" + bb.getFunction().getFUID(), IRIdentifierClass.BLOCK);
        IRBasicBlock newBB = new IRBasicBlock(newID, bb.getModule(), bb.getFunction(), branch.getSourceLineNumber());
        
        bb.getFunction().addBasicBlock(newBB);
        
        // Move target to new BB IR argument mapping
        newBB.setExitInstruction(new IRBranchInstruction(IRBranchOperation.JMP, target, mapping, newBB, branch.getSourceLineNumber()));
        
        // Retarget branch
        if(isTrue) {
            branch.setTrueTargetBlock(newID);
            branch.setTrueArgumentMapping(new IRArgumentMapping());
        } else {
            branch.setFalseTargetBlock(newID);
            branch.setFalseArgumentMapping(new IRArgumentMapping());
        }
        
        // Create new DAG
        ISelDAG newDAG = new ISelDAG(newBB);
        dagMap.put(newID, newDAG);
        
        // Fill new DAG
        // Exit instruction
        new ISelDAGTerminatorNode(newDAG, ISelDAGTerminatorOperation.JMP, target);
        
        // Create OUT nodes in each DAG
        // Doing manually instead of buildArgumentMapping to avoid constructing maps
        for(Entry<IRIdentifier, IRValue> entry : mapping.getMap().entrySet()) {
            // Argument assignment OUT nodes
            ISelDAGProducerNode prod;
            
            // Each producer is either VALUE or IN
            if(entry.getValue() instanceof IRConstant c) {
                prod = new ISelDAGProducerNode(newDAG, c, c.getType(), ISelDAGProducerOperation.VALUE); 
            } else {
                IRIdentifier id = (IRIdentifier) entry.getValue();
                prod = new ISelDAGProducerNode(newDAG, id, typeMap.get(id), ISelDAGProducerOperation.IN);
                
                // Live-out in main DAG
                new ISelDAGTerminatorNode(mainDAG, ISelDAGTerminatorOperation.OUT, id, buildProducer(id, bb, mainDAG, typeMap, livenessSets, definitionMap));
            }
            
            // Live-out in new DAG
            new ISelDAGTerminatorNode(newDAG, ISelDAGTerminatorOperation.OUT, entry.getKey(), prod);
        }
        
        return newID;
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
            return new ISelDAGProducerNode(dag, c, c.getType(), ISelDAGProducerOperation.VALUE);
        }
        
        // Must be ID
        IRIdentifier id = (IRIdentifier) value;
        
        if(id.getIDClass() != IRIdentifierClass.LOCAL) { 
            // Resolves to some address
            return new ISelDAGProducerNode(dag, id, IRType.I32, ISelDAGProducerOperation.VALUE);
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
        Set<IRIdentifier> liveIn = livenessSets.get(bb.getID()).a;
        
        if(liveIn.contains(id)) {
            // It's a live-in. Make an IN node
            return new ISelDAGProducerNode(dag, id, type, ISelDAGProducerOperation.IN);
        }
        
        //LOG.finest("Making node: " + value);
        
        // Not live-in. Should have a definition from a linear instruction in the block
        IRLinearInstruction inst = definitionMap.get(id).getLinearInstruction();
        ISelDAGProducerOperation op = ISelDAGProducerOperation.get(inst.getOp());
        
        switch(op) {
            case TRUNC, SX, ZX, LOAD, STACK, NOT, NEG:
                // 1-argument
                ISelDAGProducerNode argNode = buildProducer(inst.getLeftSourceValue(), bb, dag, typeMap, livenessSets, definitionMap);
                return new ISelDAGProducerNode(dag, id, type, op, argNode);
            
            case ADD, SUB, MULU, MULS, DIVU, DIVS, REMU, REMS,
                 SHL, SHR, SAR, ROL, ROR, AND, OR, XOR:
                // two-argument
                ISelDAGProducerNode leftNode = buildProducer(inst.getLeftSourceValue(), bb, dag, typeMap, livenessSets, definitionMap);
                ISelDAGProducerNode rightNode = buildProducer(inst.getRightSourceValue(), bb, dag, typeMap, livenessSets, definitionMap);
                
                return new ISelDAGProducerNode(dag, id, type, op, leftNode, rightNode);
                
            case SELECT:
                // select
                ISelDAGProducerNode compLeftNode = buildProducer(inst.getLeftComparisonValue(), bb, dag, typeMap, livenessSets, definitionMap);
                ISelDAGProducerNode compRightNode = buildProducer(inst.getRightComparisonValue(), bb, dag, typeMap, livenessSets, definitionMap);
                ISelDAGProducerNode valLeftNode = buildProducer(inst.getLeftSourceValue(), bb, dag, typeMap, livenessSets, definitionMap);
                ISelDAGProducerNode valRightNode = buildProducer(inst.getRightSourceValue(), bb, dag, typeMap, livenessSets, definitionMap);
                
                return new ISelDAGProducerNode(dag, id, type, op, compLeftNode, compRightNode, valLeftNode, valRightNode, inst.getSelectCondition());
            
            case CALLR:
                // call
                IRArgumentMapping mapping = inst.getCallArgumentMapping();
                Map<IRIdentifier, ISelDAGProducerNode> argMap = buildArgumentMapping(mapping, bb, dag, typeMap, livenessSets, definitionMap);
                List<ISelDAGProducerNode> argList = new ArrayList<>();
                argList.add(buildProducer(inst.getCallTarget(), bb, dag, typeMap, livenessSets, definitionMap));
                
                // Get target function's argument list to determine ordering
                for(IRIdentifier argName : mapping.getOrdering()) {
                    argList.add(argMap.get(argName));
                }
                
                return new ISelDAGProducerNode(dag, id, type, op, argList);
            
            default:
                LOG.warning("Unimplemented: " + inst);
                return null;
        }
    }
    
}
