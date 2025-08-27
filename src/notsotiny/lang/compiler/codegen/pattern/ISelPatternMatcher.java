package notsotiny.lang.compiler.codegen.pattern;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import notsotiny.sim.Register;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.aasm.AASMAbstractRegister;
import notsotiny.lang.compiler.aasm.AASMCompileConstant;
import notsotiny.lang.compiler.aasm.AASMInstruction;
import notsotiny.lang.compiler.aasm.AASMLabel;
import notsotiny.lang.compiler.aasm.AASMLinkConstant;
import notsotiny.lang.compiler.aasm.AASMMachineRegister;
import notsotiny.lang.compiler.aasm.AASMMemory;
import notsotiny.lang.compiler.aasm.AASMOperation;
import notsotiny.lang.compiler.aasm.AASMPart;
import notsotiny.lang.compiler.aasm.AASMPatternIndex;
import notsotiny.lang.compiler.aasm.AASMPatternReference;
import notsotiny.lang.compiler.aasm.AASMPrinter;
import notsotiny.lang.compiler.aasm.AASMStackSlot;
import notsotiny.lang.compiler.codegen.CodeGenV1;
import notsotiny.lang.compiler.codegen.dag.ISelDAG;
import notsotiny.lang.compiler.codegen.dag.ISelDAGNode;
import notsotiny.lang.compiler.codegen.dag.ISelDAGOperation;
import notsotiny.lang.compiler.codegen.dag.ISelDAGProducerNode;
import notsotiny.lang.compiler.codegen.dag.ISelDAGProducerOperation;
import notsotiny.lang.compiler.codegen.dag.ISelDAGTerminatorNode;
import notsotiny.lang.compiler.codegen.dag.ISelDAGTerminatorOperation;
import notsotiny.lang.compiler.codegen.dag.ISelDAGTile;
import notsotiny.lang.ir.parts.IRArgumentList;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRCondition;
import notsotiny.lang.ir.parts.IRConstant;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lib.util.MapUtil;

/**
 * Performs pattern matching on ISelDAGs
 */
public class ISelPatternMatcher {
    
    private static Logger LOG = Logger.getLogger(ISelPatternMatcher.class.getName());
    
    // Patterns
    // Maps group name to group
    private static Map<String, List<ISelPattern>> patternGroupMap;
    
    // Maps root node operation to patterns
    private static Map<ISelDAGOperation, List<ISelPattern>> patternRootMap;
    
    // For (STORE (OP ...), maps OP to patterns
    // Stores extraneously check a large number of patterns without this
    private static Map<ISelDAGOperation, List<ISelPattern>> patternStoreMap;
    
    static {
        // Load selection patterns
        try {
            patternGroupMap = new HashMap<>();
            
            List<String> patternFiles = List.of(
                    "patterns basic.txt",
                    "memory patterns.txt",
                    "constant patterns.txt",
                    //"special patterns.txt",
                    "" // placeholder to allow trailing comma
            );
            
            for(String fileName : patternFiles) {
                if(!fileName.equals("")) {
                    patternGroupMap.putAll(ISelPatternCompiler.compilePatterns(ISelPatternMatcher.class.getResourceAsStream("resources/" + fileName)));
                }
            }
        } catch(IOException | NullPointerException | CompilationException e) {
            LOG.severe(e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            throw new MissingResourceException(e.getMessage(), CodeGenV1.class.getName(), "instruction selection patterns file");
        }
        
        // Build pattern root maps
        patternRootMap = new HashMap<>();
        patternStoreMap = new HashMap<>();
        
        for(List<ISelPattern> group : patternGroupMap.values()) {
            for(ISelPattern pattern : group) {
                if(pattern.getRoot() instanceof ISelPatternNodeNode node) {
                    ISelDAGOperation op = node.getOperation();
                    
                    if(op == ISelDAGTerminatorOperation.STORE) {
                        // Store has its own map
                        if(node.getArgumentNodes().get(0) instanceof ISelPatternNodeNode storedNode) {
                            ISelDAGOperation storedOp = storedNode.getOperation();
                            
                            MapUtil.getOrCreateList(patternStoreMap, storedOp).add(pattern);
                        } else {
                            // With non nodes going in the general map
                            MapUtil.getOrCreateList(patternRootMap, op).add(pattern);
                        }
                    } else {
                        // General map
                        MapUtil.getOrCreateList(patternRootMap, op).add(pattern);
                    }
                }
            }
        }
    }
    
    /**
     * Matches dag nodes to patterns
     * @param dag
     * @param typeMap
     * @return
     * @throws CompilationException 
     */
    public static Map<ISelDAGNode, Set<ISelDAGTile>> matchPatterns(ISelDAG dag, Map<IRIdentifier, IRType> typeMap) throws CompilationException {
        LOG.finer("Matching patterns for " + dag.getBasicBlock().getID());
        
        Map<ISelDAGNode, Set<ISelDAGTile>> tileMap = new HashMap<>();
        
        IRArgumentList functionArgs = dag.getBasicBlock().getFunction().getArguments();
        boolean hasUnmatchedNodes = false;
        
        // Dependency graph - maps each DAG node to all DAG nodes that must come before it
        Map<ISelDAGNode, Set<ISelDAGNode>> dependencyMap = new HashMap<>();
        
        for(ISelDAGNode node : dag.getReverseTopologicalSort(true)) {
            Set<ISelDAGNode> dependencies = new HashSet<>();
            
            for(ISelDAGNode inputNode : node.getInputNodes()) {
                dependencies.addAll(dependencyMap.get(inputNode));
                dependencies.add(inputNode);
            }
            
            if(node.getChain() != null) {
                dependencies.addAll(dependencyMap.get(node.getChain()));
                dependencies.add(node.getChain());
            }
            
            dependencyMap.put(node, dependencies);
        }
        
        // Process each node
        for(ISelDAGNode node : dag.getAllNodes()) {
            if(LOG.isLoggable(Level.FINEST)) { 
                LOG.finest("Matching patterns for node " + node.getDescription());
            }
            
            List<ISelDAGTile> matchingTiles = new ArrayList<>();
            
            // Handle special cases
            switch(node.getOp()) {
                case ISelDAGProducerOperation po:
                    ISelDAGProducerNode pn = (ISelDAGProducerNode) node;
                    
                    switch(po) {
                        case IN: {
                            LOG.finest("Found special case for IN");
                            // IN assigns the funcation-local name to the dag-local name
                            List<AASMPart> aasm = List.of(
                                new AASMInstruction(
                                    AASMOperation.MOV,
                                    new AASMAbstractRegister(pn.getProducedName(), pn.getProducedType()),
                                    new AASMAbstractRegister((IRIdentifier) pn.getProducedValue(), pn.getProducedType())
                                )
                            );
                            matchingTiles.add(new ISelDAGTile(node, Set.of(node), Set.of(), aasm));
                            break;
                        }
                        
                        case ARG: {
                            LOG.finest("Found special case for ARG");
                            // ARG nodes produce MOV <arg name>, [BP - <arg position>]
                            // Whether the <arg name> local is a real register is up to
                            // the register allocator and peephole optimizer
                            List<AASMPart> aasm = List.of(new AASMInstruction(
                                AASMOperation.MOV,
                                new AASMAbstractRegister(pn.getProducedName(), pn.getProducedType()),
                                new AASMMemory(
                                    new AASMMachineRegister(Register.BP),
                                    null, null,
                                    new AASMCompileConstant(functionArgs.getBPOffset(pn.getProducedName())),
                                    pn.getProducedType()
                                )
                            ));
                            
                            matchingTiles.add(new ISelDAGTile(node, Set.of(node), Set.of(), aasm));
                            break;
                        }
                        
                        case VALUE: {
                            LOG.finest("Found special case for VALUE");
                            List<AASMPart> aasm;
                            
                            if(pn.getProducedType() == IRType.NONE) { 
                                // NONE -> nothing
                                aasm = List.of();
                            } else {
                                // Other -> MOV <out>, value
                                AASMPart src;
                                
                                if(pn.getProducedValue() instanceof IRConstant irc) {
                                    src = new AASMCompileConstant(irc.getValue(), irc.getType());
                                } else {
                                    src = new AASMLinkConstant((IRIdentifier) pn.getProducedValue());
                                }
                                
                                aasm = List.of(new AASMInstruction(
                                    AASMOperation.MOV,
                                    new AASMAbstractRegister(pn.getProducedName(), pn.getProducedType()),
                                    src
                                ));
                            }
                            
                            matchingTiles.add(new ISelDAGTile(node, Set.of(node), Set.of(), aasm));
                            break;
                        }
                        
                        case SELECT: {
                            LOG.finest("Found special case for SELECT");
                            List<AASMPart> aasm = null;
                            
                            // Depends on compared types
                            ISelDAGProducerNode leftNode = pn.getInputNodes().get(0),
                                                rightNode = pn.getInputNodes().get(1),
                                                trueNode = pn.getInputNodes().get(2),
                                                falseNode = pn.getInputNodes().get(3);
                            
                            IRType compareType = leftNode.getProducedType(),
                                   valueType = trueNode.getProducedType();
                            
                            IRCondition cond = pn.getCondition();
                            
                            if(leftNode.getProducedType() != rightNode.getProducedType() ||
                               trueNode.getProducedType() != falseNode.getProducedType()) {
                                throw new IllegalArgumentException("Invalid SELECT: " + pn.getDescription());
                            }
                            
                            if(rightNode.getOperation() == ISelDAGProducerOperation.VALUE) {
                                // Right-side-constant comparison
                                aasm = List.of(
                                    new AASMInstruction(
                                        AASMOperation.CMP,
                                        new AASMAbstractRegister(leftNode.getProducedName(), compareType),
                                        new AASMCompileConstant(((IRConstant) rightNode.getProducedValue()).getValue(), compareType)
                                    ),
                                    new AASMInstruction(
                                        AASMOperation.MOV,
                                        new AASMAbstractRegister(pn.getProducedName(), valueType),
                                        new AASMAbstractRegister(falseNode.getProducedName(), valueType)
                                    ),
                                    new AASMInstruction(
                                        AASMOperation.CMOV,
                                        new AASMAbstractRegister(pn.getProducedName(), valueType),
                                        new AASMAbstractRegister(trueNode.getProducedName(), valueType),
                                        cond
                                    )
                                );
                                
                                matchingTiles.add(new ISelDAGTile(node, Set.of(node, rightNode), Set.of(leftNode, trueNode, falseNode), aasm));
                            } else if(leftNode.getOperation() == ISelDAGProducerOperation.VALUE) {
                                // Left-side-constant comparison
                                aasm = List.of(
                                    new AASMInstruction(
                                        AASMOperation.CMP,
                                        new AASMAbstractRegister(rightNode.getProducedName(), compareType),
                                        new AASMCompileConstant(((IRConstant) leftNode.getProducedValue()).getValue(), compareType)
                                    ),
                                    new AASMInstruction(
                                        AASMOperation.MOV,
                                        new AASMAbstractRegister(pn.getProducedName(), valueType),
                                        new AASMAbstractRegister(falseNode.getProducedName(), valueType)
                                    ),
                                    new AASMInstruction(
                                        AASMOperation.CMOV,
                                        new AASMAbstractRegister(pn.getProducedName(), valueType),
                                        new AASMAbstractRegister(trueNode.getProducedName(), valueType),
                                        cond.swapped()
                                    )
                                );
                                
                                matchingTiles.add(new ISelDAGTile(node, Set.of(node, leftNode), Set.of(rightNode, trueNode, falseNode), aasm));
                            } else {
                                // Normal comparison
                                // CMP, MOV, CMOV
                                aasm = List.of(
                                    new AASMInstruction(
                                        AASMOperation.CMP,
                                        new AASMAbstractRegister(leftNode.getProducedName(), compareType),
                                        new AASMAbstractRegister(rightNode.getProducedName(), compareType)
                                    ),
                                    new AASMInstruction(
                                        AASMOperation.MOV,
                                        new AASMAbstractRegister(pn.getProducedName(), valueType),
                                        new AASMAbstractRegister(falseNode.getProducedName(), valueType)
                                    ),
                                    new AASMInstruction(
                                        AASMOperation.CMOV,
                                        new AASMAbstractRegister(pn.getProducedName(), valueType),
                                        new AASMAbstractRegister(trueNode.getProducedName(), valueType),
                                        cond
                                    )
                                );
                            }
                            
                            matchingTiles.add(new ISelDAGTile(node, Set.of(node), new HashSet<>(node.getInputNodes()), aasm));
                            break;
                        }
                        
                        case STACK: {
                            LOG.finest("Found special case for STACK");
                            // Create an LEA from a StackSlot
                            // Stack slots have their own namespace, so don't need to add to type map
                            // We need a unique ID for the slot, though, to prevent a conflict in the
                            // case where the STACK's local gets spilled
                            IRIdentifier slotID = new IRIdentifier(pn.getProducedName().getName() + "%slot%" + dag.getBasicBlock().getFunction().getFUID(), IRIdentifierClass.LOCAL);
                            
                            List<AASMPart> aasm = List.of(new AASMInstruction(
                                    AASMOperation.LEA,
                                    new AASMAbstractRegister(pn.getProducedName(), IRType.I32),
                                    new AASMStackSlot(slotID, ((IRConstant) pn.getInputNodes().get(0).getProducedValue()).getValue())
                            ));
                            
                            matchingTiles.add(new ISelDAGTile(node, Set.of(node), Set.of(node.getInputNodes().get(0)), aasm));
                            break;
                        }
                        
                        case CALLR: {
                            LOG.finest("Found special case for CALLR");
                            // Call with no return
                            // Arguments have been taken care of by PUSH, so we just need to call, add sp, and move the result to the output
                            List<AASMPart> aasm;
                            Set<ISelDAGNode> inputs = new HashSet<>();
                            int argsSize = 0;
                            
                            // Determine size in bytes of the args, put them in inputs set
                            for(int i = 1; i < pn.getInputNodes().size(); i++) {
                                ISelDAGProducerNode input = pn.getInputNodes().get(i);
                                argsSize += input.getProducedType().getSize();
                                inputs.add(input);
                            }
                            
                            AASMInstruction callInst, moveInst;
                            
                            // What kind of call
                            ISelDAGProducerNode targetNode = pn.getInputNodes().get(0);
                            if(targetNode.getProducedValue() instanceof IRIdentifier ctid && ctid.getIDClass() != IRIdentifierClass.LOCAL) {
                                // Calling a link time constant (global, function or bb name), relative call
                                callInst = new AASMInstruction(
                                    AASMOperation.CALL,
                                    new AASMLinkConstant(ctid)
                                );
                            } else {
                                // Calling a local or number, absolute call
                                // TODO: call compile constants directly
                                callInst = new AASMInstruction(
                                    AASMOperation.CALLA,
                                    new AASMAbstractRegister(targetNode.getProducedName(), targetNode.getProducedType(), false, false)
                                );
                                
                                inputs.add(targetNode);
                            }
                            
                            // Move result to output
                            moveInst = new AASMInstruction(
                                AASMOperation.MOV,
                                new AASMAbstractRegister(pn.getProducedName(), pn.getProducedType(), false, false),
                                new AASMMachineRegister(
                                    switch(pn.getProducedType()) {
                                        case I8     -> Register.AL;
                                        case I16    -> Register.A;
                                        default     -> Register.DA;
                                    }
                                )
                            );
                            
                            if(argsSize != 0) {
                                // Has args, ADD SP
                                aasm = List.of(
                                    callInst,
                                    new AASMInstruction(
                                        AASMOperation.ADD,
                                        new AASMMachineRegister(Register.SP),
                                        new AASMCompileConstant(argsSize)
                                    ),
                                    moveInst
                                );
                            } else {
                                // No args, no ADD SP
                                aasm = List.of(callInst, moveInst);
                            }
                            
                            matchingTiles.add(new ISelDAGTile(node, Set.of(node), inputs, aasm));
                            break;
                        }
                        
                        default:
                            // not a special case
                    }
                    break;
                
                case ISelDAGTerminatorOperation to:
                    ISelDAGTerminatorNode tn = (ISelDAGTerminatorNode) node;
                    
                    switch(to) {
                        case JMP: {
                            // JMP just jumps
                            LOG.finest("Found special case for JMP");
                            List<AASMPart> aasm = List.of(new AASMInstruction(AASMOperation.JMP, new AASMLinkConstant(tn.getTrueTargetBlock())));
                            matchingTiles.add(new ISelDAGTile(node, Set.of(node), Set.of(), aasm));
                            break;
                        }
                        
                        case RET: {
                            // RET NONE is a special case
                            ISelDAGProducerNode input = node.getInputNodes().get(0); 
                            if(input.getProducedType() == IRType.NONE) {
                                LOG.finest("Found special case for RET NONE");
                                List<AASMPart> aasm = List.of(new AASMInstruction(AASMOperation.RET));
                                matchingTiles.add(new ISelDAGTile(node, Set.of(node), Set.of(input), aasm));
                            }
                            break;
                        }
                        
                        case JCC: {
                            LOG.finest("Found special case for JCC");
                            List<AASMPart> aasm;
                            
                            // Depends on compared types
                            ISelDAGProducerNode leftNode = tn.getInputNodes().get(0),
                                                rightNode = tn.getInputNodes().get(1);
                            
                            IRType compareType = leftNode.getProducedType();
                            
                            IRCondition cond = tn.getCondition();
                            
                            if(leftNode.getProducedType() != rightNode.getProducedType()) {
                                throw new IllegalArgumentException("Invalid JCC in block " + dag.getBasicBlock().getID() + " of function " + dag.getBasicBlock().getFunction().getID() + " in module " + dag.getBasicBlock().getModule().getName());
                            }
                            
                            if(rightNode.getOperation() == ISelDAGProducerOperation.VALUE) {
                                // Right-side-constant
                                // left-constant comparisons could also be done but are
                                // uncommon and would weaken some assumptions about true/false paths
                                aasm = List.of(
                                    new AASMInstruction(
                                        AASMOperation.CMP,
                                        new AASMAbstractRegister(leftNode.getProducedName(), compareType),
                                        new AASMCompileConstant(((IRConstant) rightNode.getProducedValue()).getValue(), compareType)
                                    ),
                                    new AASMInstruction(
                                        AASMOperation.JCC,
                                        new AASMLinkConstant(tn.getTrueTargetBlock()),
                                        cond
                                    ),
                                    new AASMInstruction(
                                        AASMOperation.JMP,
                                        new AASMLinkConstant(tn.getFalseTargetBlock())
                                    )
                                );
                                
                                matchingTiles.add(new ISelDAGTile(node, Set.of(node, rightNode), Set.of(leftNode), aasm));
                            } else {
                                // Normal type. Compare and branch
                                aasm = List.of(
                                    new AASMInstruction(
                                        AASMOperation.CMP,
                                        new AASMAbstractRegister(leftNode.getProducedName(), compareType),
                                        new AASMAbstractRegister(rightNode.getProducedName(), compareType)
                                    ),
                                    new AASMInstruction(
                                        AASMOperation.JCC,
                                        new AASMLinkConstant(tn.getTrueTargetBlock()),
                                        cond
                                    ),
                                    new AASMInstruction(
                                        AASMOperation.JMP,
                                        new AASMLinkConstant(tn.getFalseTargetBlock())
                                    )
                                );
                            }
                            
                            matchingTiles.add(new ISelDAGTile(node, Set.of(node), Set.of(leftNode, rightNode), aasm));
                            break;
                        }
                        
                        case CALLN: {
                            LOG.finest("Found special case for CALLN");
                            // Call with no return
                            // Arguments have been taken care of by PUSH, so we just need to call and add sp
                            List<AASMPart> aasm;
                            Set<ISelDAGNode> inputNodes = new HashSet<>();
                            int argsSize = 0;
                            
                            // Determine size in bytes of the args
                            for(int i = 1; i < tn.getInputNodes().size(); i++) {
                                ISelDAGProducerNode input = tn.getInputNodes().get(i); 
                                argsSize += input.getProducedType().getSize();
                                inputNodes.add(input);
                            }
                            
                            AASMInstruction callInst;
                            
                            // What kind of call
                            ISelDAGProducerNode targetNode = tn.getInputNodes().get(0);
                            if(targetNode.getProducedValue() instanceof IRIdentifier ctid && ctid.getIDClass() != IRIdentifierClass.LOCAL) {
                                // Calling a link time constant (global, function or bb name), relative call
                                callInst = new AASMInstruction(
                                    AASMOperation.CALL,
                                    new AASMLinkConstant(ctid)
                                );
                            } else {
                                // Calling a local or number, absolute call
                                // TODO: call compile constants directly
                                callInst = new AASMInstruction(
                                    AASMOperation.CALLA,
                                    new AASMAbstractRegister(targetNode.getProducedName(), targetNode.getProducedType(), false, false)
                                );
                                
                                inputNodes.add(targetNode);
                            }
                            
                            if(argsSize != 0) {
                                // Has args, ADD SP
                                aasm = List.of(
                                    callInst,
                                    new AASMInstruction(
                                        AASMOperation.ADD,
                                        new AASMMachineRegister(Register.SP),
                                        new AASMCompileConstant(argsSize)
                                    )
                                );
                            } else {
                                // No args, no ADD SP
                                aasm = List.of(callInst);
                            }
                            
                            matchingTiles.add(new ISelDAGTile(node, Set.of(node), inputNodes, aasm));
                            break;
                        }
                        
                        case ENTRY: {
                            LOG.finest("Found special case for ENTRY");
                            // ENTRY produces no AASM
                            List<AASMPart> aasm = List.of();
                            matchingTiles.add(new ISelDAGTile(node, Set.of(node), Set.of(), aasm));
                            break;
                        }
                        
                        default:
                            // not a special case
                    }
                    break;
                
                default:
                    // Not a special case
            }
            
            // Log special case results
            if(matchingTiles.size() != 0) {
                if(LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("Got " + AASMPrinter.getAASMString(matchingTiles.get(0).aasm()));
                }
            }
            
            // Check each pattern with the same root as the node
            List<ISelPattern> potentialPatterns = patternRootMap.getOrDefault(node.getOp(), new ArrayList<>());
            
            if(node.getOp() == ISelDAGTerminatorOperation.STORE) {
                // Store has its own map according to stored node
                potentialPatterns.addAll(patternStoreMap.getOrDefault(node.getInputNodes().get(0).getOp(), new ArrayList<>()));
            }
            
            for(ISelPattern pattern : potentialPatterns) {
                if(!pattern.producesInstructions()) {
                    continue;
                }
                
                // Try to match the pattern with the node
                List<ISelDAGTile> tiles = matchPattern(node, pattern, typeMap, dependencyMap);
                
                if(tiles != null) {
                    // Found a match!
                    matchingTiles.addAll(tiles);
                }
            }
            
            // Verify that at least one tile matched
            if(matchingTiles.size() == 0) {
                // log some debug info
                IRBasicBlock sourceBB = dag.getBasicBlock();
                IRFunction sourceFunction = sourceBB.getFunction();
                
                StringBuilder message = new StringBuilder();
                message.append("No matching pattern found for node ");
                message.append(node.getDescription());                
                message.append(" in block ");
                message.append(sourceBB.getID());
                message.append(" in function ");
                message.append(sourceFunction.getID());
                message.append(" of module ");
                message.append(sourceFunction.getModule().getName());
                
                LOG.severe(message.toString());
                
                hasUnmatchedNodes = true;
            }
            
            LOG.finest("Got " + matchingTiles.size() + " tiles");
            tileMap.put(node, new HashSet<>(matchingTiles));
        }
        
        if(hasUnmatchedNodes) {
            throw new CompilationException();
        }
        
        return tileMap;
    }
    
    /**
     * Matches a pattern to a node
     * @param node
     * @param pattern
     * @param typeMap
     * @return Resulting ISelDAGTile, or null if the pattern does not match
     */
    private static List<ISelDAGTile> matchPattern(ISelDAGNode node, ISelPattern pattern, Map<IRIdentifier, IRType> typeMap, Map<ISelDAGNode, Set<ISelDAGNode>> dependencyMap) {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.finest("Trying to match against " + pattern.getDescription());
        }
        
        // Local information
        ISelMatchData matchData = new ISelMatchData(pattern, node, new HashMap<>(), new HashMap<>(), new ArrayList<>(), new ArrayList<>());
        
        // Try to match the pattern. This will populate the above maps.
        boolean matches = tryMatch(node, pattern.getRoot(), matchData);
        
        if(matches) {
            // Matched!
            LOG.finest("Pattern match succeeded");
            
            List<ISelDAGTile> rawTiles = doConversions(matchData, new HashMap<>(), typeMap);
            
            // Verification
            List<ISelDAGTile> validTiles = new ArrayList<>();
            
            checking:
            for(ISelDAGTile tile : rawTiles) {
                // Tiles should be instructions and labels only at the top level
                for(AASMPart part : tile.aasm()) {
                    if(!(part instanceof AASMInstruction || part instanceof AASMLabel)) {
                        if(LOG.isLoggable(Level.FINEST)) {
                            LOG.finest("Not a top level pattern: " + AASMPrinter.getAASMString(tile.aasm()));
                        }
                        
                        // Invalid top level AASM, reject tile
                        continue checking;
                    }
                }
                
                // Tiles should not violate chains
                Set<ISelDAGNode> coveredChained = new HashSet<>();
                
                // Collect covered nodes that have chains
                for(ISelDAGNode coveredNode : tile.coveredNodes()) {
                    if(coveredNode.getChain() != null) {
                        coveredChained.add(coveredNode);
                    }
                }
                
                // Verify that only one node has a chain that is outside the tile
                if(coveredChained.size() > 1) {
                    boolean hasOutside = false;
                    
                    for(ISelDAGNode chainedNode : coveredChained) {
                        if(!coveredChained.contains(chainedNode.getChain())) {
                            if(hasOutside) {
                                if(LOG.isLoggable(Level.FINEST)) {
                                    LOG.finest("Violates internal chain: " + AASMPrinter.getAASMString(tile.aasm()));
                                }
                                
                                // Multiple outside chains, reject tile
                                continue checking;
                            }
                            
                            hasOutside = true;
                        }
                    }
                }
                
                // Verify that no input of the tile eventually chains into the tile
                for(ISelDAGNode inputNode : tile.inputNodes()) {
                    // Check directly
                    if(tile.coveredNodes().contains(inputNode.getChain())) {
                        if(LOG.isLoggable(Level.FINEST)) {
                            LOG.finest("Violates input chain: " + AASMPrinter.getAASMString(tile.aasm()));
                        }
                        
                        // An input chains into the tile, reject tile
                        continue checking;
                    }
                    
                    // Check dependencies
                    for(ISelDAGNode inputDependency : dependencyMap.get(inputNode)) {
                        if(tile.coveredNodes().contains(inputDependency.getChain())) {
                            if(LOG.isLoggable(Level.FINEST)) {
                                LOG.finest("Violates dependency chain: " + AASMPrinter.getAASMString(tile.aasm()));
                            }
                            
                            // A dependency of an input chain into the tile, reject tile
                            continue checking;
                        }
                    }
                }
                
                // No check rejected the tile
                validTiles.add(tile);
            }
            
            return validTiles;
        } else {
            // Didn't match
            LOG.finest("Pattern match failed");
            return null;
        }
    }
    
    /**
     * Attempts to match a DAG node to a pattern node
     * @param dagNode
     * @param patNode
     * @param matchData
     * @return
     */
    private static boolean tryMatch(ISelDAGNode dagNode, ISelPatternNode patNode, ISelMatchData matchData) {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.finest("Trying to match " + dagNode.getDescription() + " with " + patNode);
        }
        
        // What are we trying to match against
        switch(patNode) {
            case ISelPatternNodeNode nodeNode: {
                // Node
                // Does the operation match?
                if(nodeNode.getOperation() != dagNode.getOp()) {
                    LOG.finest("Match failed: Wrong operation");
                    return false;
                }
                
                // If applicable, does the type match?
                if(nodeNode.getProducedType() != IRType.NONE && nodeNode.getProducedType() != ((ISelDAGProducerNode) dagNode).getProducedType()) {
                    LOG.finest("Match failed: Wrong type");
                    return false;
                }
                
                // How many arguments are we dealing with?
                int args;
                boolean ordered;
                
                if(nodeNode.getOperation() instanceof ISelDAGProducerOperation prodOp) {
                    args = prodOp.getArgCount();
                    ordered = prodOp.isOrdered();
                } else if(nodeNode.getOperation() == ISelDAGTerminatorOperation.STORE) {
                    args = 2;
                    ordered = true;
                } else {
                    args = 1;
                    ordered = true;
                }
                
                if(args == 2) {
                    // Two-argument
                    ISelDAGNode dagArg1 = dagNode.getInputNodes().get(0),
                                dagArg2 = dagNode.getInputNodes().get(1);
                    
                    // TODO: It can be advantageous to emit both orderings. However, the current MatchData structure doesn't support it
                    
                    // Try in given order
                    boolean matched = try2ArgMatch(dagNode, nodeNode, dagArg1, dagArg2, matchData);
                    
                    if(!matched) {
                        // Didn't match. Can we try another order?
                        if(ordered) {
                            // No. Failed.
                            return false;
                        }
                        
                        // Yes. Try it
                        matched = try2ArgMatch(dagNode, nodeNode, dagArg2, dagArg1, matchData);
                        
                        if(!matched) {
                            // Both failed.
                            return false;
                        }
                    }
                    
                    // Matched!
                    // Updates performed by successful try2ArgMatch
                    return true;
                } else {
                    // One-argument
                    boolean argMatches = tryMatch(dagNode.getInputNodes().get(0), nodeNode.getArgumentNodes().get(0), matchData);
                    
                    if(argMatches) {
                        // Match!
                        // Add to covered nodes
                        matchData.coveredNodes().add(dagNode);
                        
                        // Update match map if applicable
                        if(nodeNode.getIdentifier() != null) {
                            matchData.matchMap().put(nodeNode.getIdentifier(), dagNode);
                        }
                        
                        return true;
                    } else {
                        return false;
                    }
                }
            }
            
            case ISelPatternNodeLocal localNode: {
                // Local. Can be any producer.
                if(dagNode instanceof ISelDAGProducerNode prod) {
                    // Check that types match
                    if(localNode.getType() == IRType.NONE || localNode.getType() == prod.getProducedType()) {
                        // Match!
                        matchData.matchMap().put(localNode.getIdentifier(), prod);
                        matchData.inputNodes().add(prod);
                        return true;
                    } else {
                        LOG.finest("Match failed: Wrong type");
                        return false;
                    }
                } else {
                    LOG.finest("Match failed: Not a producer");
                    return false;
                }
            }
            
            case ISelPatternNodeConstant constNode: {
                // Constant. Must be a VALUE node
                if(dagNode instanceof ISelDAGProducerNode prod && prod.getOperation() == ISelDAGProducerOperation.VALUE) {
                    // Check types
                    if(constNode.getType() == IRType.NONE || constNode.getType() == prod.getProducedType()) {
                        // Wildcard constants match at this point, but concrete ones need to check value
                        if(!constNode.isWildcard()) {
                            if(!(prod.getProducedValue() instanceof IRConstant irc && irc.getValue() == constNode.getValue())) {
                                LOG.finest("Match failed: Wrong value");
                                return false;
                            }
                        }
                        
                        // Match!
                        if(constNode.isWildcard()) {
                            matchData.matchMap().put(constNode.getIdentifier(), prod);
                        }
                        
                        matchData.coveredNodes().add(dagNode);
                        return true;
                    } else {
                        LOG.finest("Match failed: Wrong type");
                        return false;
                    }
                } else {
                    LOG.finest("Match failed: Not a constant");
                    return false;
                }
            }
            
            case ISelPatternNodePattern subpatNode: {
                // Subpattern. Match the given subpattern group.
                List<ISelPattern> patternGroup = patternGroupMap.get(subpatNode.getPatternName());
                List<ISelMatchData> matchGroup = new ArrayList<>();
                
                for(ISelPattern subpat : patternGroup) {
                    ISelMatchData subpatData = new ISelMatchData(subpat, dagNode, new HashMap<>(), new HashMap<>(), new ArrayList<>(), new ArrayList<>());
                    boolean matched = tryMatch(dagNode, subpatData.pattern().getRoot(), subpatData);
                    
                    if(matched) {
                        LOG.finest("Subpattern match succeeded");
                        matchGroup.add(subpatData);
                    }
                }
                
                if(matchGroup.size() > 0) {
                    // At least one subpattern matched
                    matchData.matchMap().put(subpatNode.getNodeIdentifier(), dagNode);
                    matchData.subpatternMap().put(subpatNode.getNodeIdentifier(), matchGroup);
                    return true;
                } else {
                    // No subpatterns matched
                    LOG.finest("Match failed: No matching subpatterns");
                    return false;
                }
            }
            
            case ISelPatternNodeReference refNode: {
                // Subpattern reference. 
                // The node must be the root of the matched subpattern. Subpatterns are defined once, so the check does not need to be per-match
                // In theory, a deep check that the same value is produced may find more matches. However, local CSE is expected to perform this task.
                if(dagNode == matchData.subpatternMap().get(refNode.getReferencedIdentifier()).get(0).matchRoot()) {
                    return true;
                } else {
                    LOG.finest("Match failed: Subpattern reference does not match");
                    return false;
                }
            }
            
            default:
                throw new IllegalArgumentException("Unexpected pattern node type " + patNode);
        }
    }
    
    /**
     * Attempts a two-argument match. If the match fails, matchData is unaffected. Otherwise, it is updated
     * @param dagNode
     * @param nodeNode
     * @param firstDAGArg
     * @param secondDAGArg
     * @param matchData
     * @return
     */
    private static boolean try2ArgMatch(ISelDAGNode dagNode, ISelPatternNodeNode nodeNode, ISelDAGNode firstDAGArg, ISelDAGNode secondDAGArg, ISelMatchData matchData) {
        // Use a copy of the state information in case second argument fails
        // subpatternMap is a shallow copy wrt the lists as values shouldn't be overwritten
        ISelMatchData tmpMatchData = new ISelMatchData(matchData.pattern(), matchData.matchRoot(), new HashMap<>(matchData.matchMap()), new HashMap<>(matchData.subpatternMap()), new ArrayList<>(), new ArrayList<>());
        
        // Try to match first argument
        boolean firstMatches = tryMatch(firstDAGArg, nodeNode.getArgumentNodes().get(0), tmpMatchData);
        
        if(!firstMatches) {
            return false;
        }
        
        // First matched, try to match second argument
        boolean secondMatches = tryMatch(secondDAGArg, nodeNode.getArgumentNodes().get(1), tmpMatchData);
        
        if(secondMatches) {
            // Match!
            // Merge information from temporary
            matchData.matchMap().putAll(tmpMatchData.matchMap());
            matchData.subpatternMap().putAll(tmpMatchData.subpatternMap());
            matchData.coveredNodes().addAll(tmpMatchData.coveredNodes());
            matchData.coveredNodes().add(dagNode);
            matchData.inputNodes().addAll(tmpMatchData.inputNodes());
            
            if(nodeNode.getIdentifier() != null) {
                matchData.matchMap().put(nodeNode.getIdentifier(), dagNode);
            }
            
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Performs all possible pattern substitutions for a match result
     * @param match
     * @param tmpMap
     * @param typeMap
     * @return
     */
    private static List<ISelDAGTile> doConversions(ISelMatchData match, Map<String, IRIdentifier> tmpMap, Map<IRIdentifier, IRType> typeMap) {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.finest("Performing conversions for " + match.pattern().getDescription());
        }
        
        /*
         * For a given pattern match, its subpatterns can each have multiple matches.
         * In order for conversions to be self-consistent, matches, or equivalently match converions,
         * for each subpattern should be chosen before conversion.
         */
        
        // Perform converions for each subpattern
        // Do doConverions for each subpattern in the subpattern map
        Map<String, List<List<ISelDAGTile>>> subpatternConversions = new HashMap<>();
        List<String> subpatternOrdering = new ArrayList<>(); // arbitrary ordering to permute according to
        
        for(Entry<String, List<ISelMatchData>> subpat : match.subpatternMap().entrySet()) {
            List<List<ISelDAGTile>> subpatConversionList = new ArrayList<>(subpat.getValue().size());
            
            for(ISelMatchData subpatMatch : subpat.getValue()) {
                subpatConversionList.add(doConversions(subpatMatch, tmpMap, typeMap));
            }
            
            subpatternConversions.put(subpat.getKey(), subpatConversionList);
            subpatternOrdering.add(subpat.getKey());
        }
        
        // Assemble a list of permutations of subpattern results
        List<Map<String, ISelDAGTile>> subpatternPermutations = permuteSubpatterns(subpatternConversions, subpatternOrdering);
        
        // Do the conversion for each combination of subpattern results
        List<ISelDAGTile> conversions = new ArrayList<>();
        
        for(Map<String, ISelDAGTile> subpatternResults : subpatternPermutations) {
            ISelDAGTile tile = doConversion(match, subpatternResults, tmpMap, typeMap);
            
            if(tile != null) {
                conversions.add(tile);
            }
        }
        
        //LOG.finest("Got " + conversions);
        
        if(LOG.isLoggable(Level.FINEST)) {
            for(ISelDAGTile conv : conversions) {
                LOG.finest("Got " + AASMPrinter.getAASMString(conv.aasm()));
            }
        }
        
        return conversions;
    }
    
    /**
     * Performs a pattern substitution for a match result given chosen results from each subpattern
     * @param match
     * @param subpatternResults
     * @param tmpMap
     * @param typeMap
     * @return
     */
    private static ISelDAGTile doConversion(ISelMatchData match, Map<String, ISelDAGTile> subpatternResults, Map<String, IRIdentifier> tmpMap, Map<IRIdentifier, IRType> typeMap) {
        List<AASMPart> parts = new ArrayList<>();
        Set<ISelDAGNode> coveredNodes = new HashSet<>(match.coveredNodes());
        Set<ISelDAGNode> inputNodes = new HashSet<>(match.inputNodes());
        
        // Merge covered & input sets of chosen matches
        for(ISelDAGTile subTile : subpatternResults.values()) {
            coveredNodes.addAll(subTile.coveredNodes());
            inputNodes.addAll(subTile.inputNodes());
        }
        
        try {
            // Convert AASM parts
            for(AASMPart patternPart : match.pattern().getTemplate()) {
                parts.add(convertPart(patternPart, match, subpatternResults, tmpMap, typeMap));
            }
            
            return new ISelDAGTile(match.matchRoot(), coveredNodes, inputNodes, parts, match);
        } catch(CompilationException e) {
            // Indicates a non-error conversion failure
            return null;
        }
    }
    
    /**
     * Performs pattern substitution on a single AASMPart
     * @param part
     * @param match
     * @param coveredNodes Set of nodes covered by relevant subpatterns
     * @param subpatternResults
     * @param tmpMap Information about temporaries
     * @param typeMap Types of temporaries are added to the map
     * @return
     */
    private static AASMPart convertPart(AASMPart part, ISelMatchData match, Map<String, ISelDAGTile> subpatternResults, Map<String, IRIdentifier> tmpMap, Map<IRIdentifier, IRType> typeMap) throws CompilationException {
        //LOG.finest("Converting " + part);
        
        switch(part) {
            case AASMInstruction patInst: {
                // Instruction
                // Convert arguments and build output copy
                AASMPart patDest = patInst.getDestination(),
                         patSource = patInst.getSource(),
                         resDest = null,
                         resSource = null;
                
                // TODO: verify that source/dest match the operation>
                
                // If source/dest exist, convert them
                if(patDest != null) {
                    resDest = convertPart(patDest, match, subpatternResults, tmpMap, typeMap);
                }
                
                if(patSource != null) {
                    resSource = convertPart(patSource, match, subpatternResults, tmpMap, typeMap);
                }
                
                // Build output instruction
                return new AASMInstruction(patInst.getOp(), resDest, resSource, patInst.getCondition());
            }
            
            case AASMPatternReference patRef: {
                // Reference
                // Strip high/low if present
                boolean half = patRef.isHalf(),
                        high = patRef.isHigh(),
                        typed = patRef.isTyped();
                
                int modifiers = (half || typed) ? 1 : 0;
                
                String key = patRef.toKey(modifiers);
                IRType type = patRef.getType();
                
                // Handle special cases
                if(key.equals("<out>")) {
                    // Output of this pattern
                    if(match.matchRoot() instanceof ISelDAGTerminatorNode tn) {
                        // OUT has an output despite being a terminator
                        return new AASMAbstractRegister(tn.getTargetRegister(), typeMap.get(tn.getTargetRegister()), half, high);
                    } else {
                        // out = produced name
                        ISelDAGProducerNode prodRoot = (ISelDAGProducerNode) match.matchRoot();
                        return new AASMAbstractRegister(prodRoot.getProducedName(), prodRoot.getProducedType(), half, high);
                    }
                } else if(key.startsWith("<tmp")) {
                    // Temporaries
                    // Create if absent
                    if(!tmpMap.containsKey(key)) {
                        IRIdentifier id = new IRIdentifier(key + "%" + match.matchRoot().getDAG().getBasicBlock().getFunction().getFUID(), IRIdentifierClass.LOCAL);
                        tmpMap.put(key, id);
                        typeMap.put(id, IRType.valueOf("I" + key.substring(4, key.indexOf("_"))));
                    }
                    
                    // Return register
                    IRIdentifier tmpID = tmpMap.get(key);
                    return new AASMAbstractRegister(tmpID, typeMap.get(tmpID), half, high);
                } else {
                    // References to matched values
                    // Figure out what's being referenced
                    ISelMatchData referencedMatch = match;
                    
                    // One level of indirection is possible
                    int index = 0;
                    String firstKey = patRef.identifiers().get(0);
                    
                    if(subpatternResults.containsKey(firstKey)) {
                        index = 1;
                        referencedMatch = subpatternResults.get(firstKey).sourceMatch();
                    }
                    
                    int numIDs = patRef.identifiers().size() - (modifiers + index);
                    
                    if(numIDs > 1) {
                        // If there're more identifiers than allowed, error
                        throw new IllegalArgumentException("Too many identifiers: " + patRef);
                    } else if(numIDs == 1) {
                        // 1 remaining identifier = references something in the pattern
                        // Get referenced pattern part
                        String refID = patRef.identifiers().get(index);
                        ISelPatternNode referencedPatternPart = referencedMatch.pattern().getSubpatternMap().get(refID);
                        
                        if(referencedPatternPart == null) {
                            throw new IllegalArgumentException("Unknown identifier " + patRef);
                        }
                        
                        switch(referencedPatternPart) {
                            case ISelPatternNodeLocal _: {
                                // Local. Grab info from producer.
                                ISelDAGProducerNode prodRef = (ISelDAGProducerNode) referencedMatch.matchMap().get(refID);
                                return new AASMAbstractRegister(prodRef.getProducedName(), prodRef.getProducedType(), half, high);
                            }
                            
                            case ISelPatternNodeConstant _: {
                                // Wildcard constant. Determine from producer
                                ISelDAGProducerNode prodRef = (ISelDAGProducerNode) referencedMatch.matchMap().get(refID);
                                
                                if(prodRef.getProducedValue() instanceof IRIdentifier id) {
                                    // Can't take halves of link constants
                                    if(half) {
                                        throw new CompilationException("Can't take halves of link constants");
                                    }
                                    
                                    // Link time constant
                                    return new AASMLinkConstant(id);
                                } else {
                                    int conValue = ((IRConstant) prodRef.getProducedValue()).getValue();
                                    IRType conType;
                                    
                                    if(half) {
                                        switch(prodRef.getProducedType()) {
                                            case I16:
                                                conValue = (high ? (conValue >> 8) : conValue) & 0xFF;
                                                conType = IRType.I8;
                                                break;
                                                
                                            case I32:
                                                conValue = (high ? (conValue >> 16) : conValue) & 0xFFFF;
                                                conType = IRType.I16;
                                                break;
                                                
                                            default:
                                                throw new IllegalArgumentException("Constant produced none type");
                                        }
                                    } else {
                                        conType = prodRef.getProducedType();
                                    }
                                    
                                    // Compile time constant
                                    return new AASMCompileConstant(conValue, conType);
                                }
                            }
                            
                            default:
                                throw new IllegalArgumentException("Unexpected pattern node type as reference in template: " + referencedPatternPart + " in " + referencedMatch.pattern().getDescription());
                        }
                    } else {
                        // 0 remaining identifiers = references the pattern itself
                        List<AASMPart> referencedAASM = subpatternResults.get(firstKey).aasm();
                        
                        if(half) {
                            throw new CompilationException("Can't take halves of subpatterns: " + subpatternResults);
                        }
                        
                        if(referencedAASM.size() == 1) {
                            AASMPart refPart = referencedAASM.get(0);
                            
                            // Convert the type if applicable
                            if(refPart instanceof AASMMemory refMem && typed) {
                                return new AASMMemory(refMem.getBase(), refMem.getIndex(), refMem.getScale(), refMem.getOffset(), type);
                            }
                            
                            return referencedAASM.get(0);
                        } else {
                            // Multiple parts in referenced subpattern
                            throw new IllegalArgumentException("Multiple parts in referenced subpattern " + patRef + " in " + match.pattern().getDescription());
                        }
                    }
                }
            }
            
            case AASMMemory patMem: {
                // Memory
                AASMPart base = AASMCompileConstant.ZERO,
                         index = AASMCompileConstant.ZERO,
                         scale = AASMCompileConstant.ONE,
                         offset = AASMCompileConstant.ZERO;
                
                // What do we have
                // We'll either have base with index,scale,offset = null
                // -> base or index depending on what the part is
                // or all parts
                
                if(patMem.getIndex() == null) {
                    base = convertPart(patMem.getBase(), match, subpatternResults, tmpMap, typeMap);
                    
                    if(base instanceof AASMCompileConstant || base instanceof AASMLinkConstant) {
                        offset = base;
                        base = AASMCompileConstant.ZERO;
                    }
                } else {
                    // All availabe
                    base = convertPart(patMem.getBase(), match, subpatternResults, tmpMap, typeMap);
                    index = convertPart(patMem.getIndex(), match, subpatternResults, tmpMap, typeMap);
                    scale = convertPart(patMem.getScale(), match, subpatternResults, tmpMap, typeMap);
                    offset = convertPart(patMem.getOffset(), match, subpatternResults, tmpMap, typeMap);
                }
                
                // If index includes scale, unpack it
                if(index instanceof AASMPatternIndex idx) {
                    index = idx.index();
                    scale = idx.ccScale();
                }
                
                // Type can't be gotten with available information
                // Can be inferred or overwritten with <ref><type>
                return new AASMMemory(base, index, scale, offset, IRType.NONE);
            }
            
            case AASMPatternIndex idx: {
                // Index. Convert index, copy scale
                AASMPart index = convertPart(idx.index(), match, subpatternResults, tmpMap, typeMap);
                return new AASMPatternIndex(index, idx.scale());
            }
            
            case AASMMachineRegister mReg: {
                // just a register innit
                return new AASMMachineRegister(mReg.reg());
            }
            
            case AASMCompileConstant cCon: {
                // just a constant innit
                return new AASMCompileConstant(cCon.value(), cCon.type());
            }
            
            default:
                throw new IllegalArgumentException("Unexpected pattern AASM part: " + part);
        }
    }
    
    /**
     * Assembles a list of permutations of subpattern results
     * @param subpatternConverions
     * @param subpatternOrdering
     * @return
     */
    private static List<Map<String, ISelDAGTile>> permuteSubpatterns(Map<String, List<List<ISelDAGTile>>> subpatternConversions, List<String> subpatternOrdering) {
        // Choose ISelMatchResult indices for each subpattern
        List<Map<String, Integer>> resultIndices = new ArrayList<>();
        permuteResultIndices(resultIndices, new ArrayList<>(), subpatternConversions, subpatternOrdering);
        
        // For each permutation, choose conversion indices
        List<Map<String, ISelDAGTile>> conversionPermutations = new ArrayList<>();
        
        for(Map<String, Integer> resultIndexMap : resultIndices) {
            permuteConversionIndices(conversionPermutations, new ArrayList<>(), resultIndexMap, subpatternConversions, subpatternOrdering);
        }
        
        return conversionPermutations;
    }
    
    /**
     * Permutes conversion indices
     * @param resultPermutations
     * @param indices
     * @param resultIndexMap
     * @param subpatternConversions
     */
    private static void permuteConversionIndices(List<Map<String, ISelDAGTile>> conversionPermutations, List<Integer> indices, Map<String, Integer> resultIndexMap, Map<String, List<List<ISelDAGTile>>> subpatternConversions, List<String> subpatternOrdering) {
        // If we've chosen all indices, add choices to list
        if(indices.size() == subpatternOrdering.size()) {
            Map<String, ISelDAGTile> map = new HashMap<>();
            
            for(int i = 0; i < indices.size(); i++) {
                String subpatName = subpatternOrdering.get(i);
                List<List<ISelDAGTile>> matchResultList = subpatternConversions.get(subpatName);
                List<ISelDAGTile> matchConversions = matchResultList.get(resultIndexMap.get(subpatName));
                ISelDAGTile conversion = matchConversions.get(indices.get(i));
                map.put(subpatName, conversion);
            }
            
            conversionPermutations.add(map);
        } else {
            // Otherwise, choose the next index
            String subpatName = subpatternOrdering.get(indices.size());
            int maxIndex = subpatternConversions.get(subpatName).get(resultIndexMap.get(subpatName)).size();
            
            for(int i = 0; i < maxIndex; i++) {
                indices.add(i);
                permuteConversionIndices(conversionPermutations, indices, resultIndexMap, subpatternConversions, subpatternOrdering);
                indices.removeLast();
            }
        }
    }
    
    /**
     * Permutes result indices
     * @param resultIndices
     * @param indices
     * @param subpatternConversions
     * @param subpatternOrdering
     */
    private static void permuteResultIndices(List<Map<String, Integer>> resultIndices, List<Integer> indices, Map<String, List<List<ISelDAGTile>>> subpatternConversions, List<String> subpatternOrdering) {
        // If we've chosen all indices, assemble them and add to list
        if(indices.size() == subpatternOrdering.size()) {
            Map<String, Integer> map = new HashMap<>();
            
            for(int i = 0; i < indices.size(); i++) {
                map.put(subpatternOrdering.get(i), indices.get(i));
            }
            
            resultIndices.add(map);
        } else {
            // Otherwise, choose the next index
            int maxIndex = subpatternConversions.get(subpatternOrdering.get(indices.size())).size();
            
            for(int i = 0; i < maxIndex; i++) {
                indices.add(i);
                permuteResultIndices(resultIndices, indices, subpatternConversions, subpatternOrdering);
                indices.removeLast();
            }
        }
    }
    
}
