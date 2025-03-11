package notsotiny.lang.ir.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
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
import notsotiny.lang.ir.parts.IRCondition;
import notsotiny.lang.ir.parts.IRConstant;
import notsotiny.lang.ir.parts.IRDefinition;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.parts.IRLinearOperation;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lang.util.MapUtil;
import notsotiny.lang.util.Pair;
import notsotiny.lang.util.TreeNode;
import notsotiny.lang.util.UnionFindForest;

/**
 * Utility functions for the IR
 */
public class IRUtil {
    
    private static Logger LOG = Logger.getLogger(IRUtil.class.getName());
    
    /**
     * Where possible, replaces NONE with a concrete type 
     * @param bb
     * @param typeMap
     */
    public static void inferNoneTypes(IRBasicBlock bb, Map<IRIdentifier, IRType> typeMap) {
        boolean modifiedTypes;
        
        do {
            modifiedTypes = false;
            
            // Infer in body code
            for(IRLinearInstruction inst : bb.getInstructions()) {
                switch(inst.getOp()) {
                    case LOAD:
                        // pointers are expected to be I32 
                        if(inst.getLeftSourceValue() instanceof IRConstant irc && irc.getType() == IRType.NONE) {
                            inst.setLeftSourceValue(new IRConstant(irc.getValue(), IRType.I32));
                            modifiedTypes = true;
                        }
                        break;
                    
                    case STORE:
                        // pointers are expected to be I32 
                        if(inst.getRightSourceValue() instanceof IRConstant irc && irc.getType() == IRType.NONE) {
                            inst.setRightSourceValue(new IRConstant(irc.getValue(), IRType.I32));
                            modifiedTypes = true;
                        }
                        break;
                    
                    case SELECT:
                        // Infer compared typed, then fall through to two-argument
                        if(inst.getLeftComparisonValue() instanceof IRConstant irc && irc.getType() == IRType.NONE) {
                            IRType inferredType;
                            
                            if(inst.getRightComparisonValue() instanceof IRIdentifier id) {
                                inferredType = typeMap.get(id);
                            } else {
                                inferredType = ((IRConstant) inst.getRightComparisonValue()).getType();
                            }
                            
                            inst.setLeftComparisonValue(new IRConstant(irc.getValue(), inferredType));
                        }
                        
                        if(inst.getRightComparisonValue() instanceof IRConstant irc && irc.getType() == IRType.NONE) {
                            IRType inferredType;
                            
                            if(inst.getLeftComparisonValue() instanceof IRIdentifier id) {
                                inferredType = typeMap.get(id);
                            } else {
                                inferredType = ((IRConstant) inst.getLeftComparisonValue()).getType();
                            }
                            
                            inst.setRightComparisonValue(new IRConstant(irc.getValue(), inferredType));
                        }
                    
                    case ADD, SUB, MULU, MULS, DIVU, DIVS, REMU, REMS,
                         SHL, SHR, SAR, ROL, ROR, AND, OR, XOR:
                        // two-argument
                        if(inst.getLeftSourceValue() instanceof IRConstant irc && irc.getType() == IRType.NONE) {
                            inst.setLeftSourceValue(new IRConstant(irc.getValue(), inst.getDestinationType()));
                            modifiedTypes = true;
                        }
                    
                        if(inst.getRightSourceValue() instanceof IRConstant irc && irc.getType() == IRType.NONE) {
                            inst.setRightSourceValue(new IRConstant(irc.getValue(), inst.getDestinationType()));
                            modifiedTypes = true;
                        }
                        break;
                    
                    case NOT, NEG:
                        // 1-argument
                        if(inst.getLeftSourceValue() instanceof IRConstant irc && irc.getType() == IRType.NONE) {
                            inst.setLeftSourceValue(new IRConstant(irc.getValue(), inst.getDestinationType()));
                            modifiedTypes = true;
                        }
                        break;
                    
                    case CALLR, CALLN:
                        // function arguments have expected types
                        if(inst.getCallTarget() instanceof IRIdentifier id) {
                            IRArgumentList args = bb.getFunction().getModule().getFunctions().get(id).getArguments();
                            Map<IRIdentifier, IRValue> mapping = inst.getCallArgumentMapping().getMap();
                            
                            for(int i = 0; i < args.getArgumentCount(); i++) {
                                IRIdentifier argName = args.getName(i);
                                
                                if(mapping.get(argName) instanceof IRConstant irc && irc.getType() == IRType.NONE) {
                                    mapping.put(argName, new IRConstant(irc.getValue(), args.getType(i)));
                                    modifiedTypes = true;
                                }
                            }
                        }
                        break;
                    
                    // These operations aren't inferred
                    default:
                }
            }
            
            // Infer in exit code
            IRBranchInstruction exit = bb.getExitInstruction();
            switch(exit.getOp()) {
                case RET:
                    // infer from return type
                    if(exit.getReturnValue() instanceof IRConstant irc && irc.getType() == IRType.NONE) {
                        exit.setReturnValue(new IRConstant(irc.getValue(), bb.getFunction().getReturnType()));
                    }
                    break;
                
                case JCC:
                    // Infer one comparison type from the other
                    if(exit.getCompareLeft() instanceof IRConstant irc && irc.getType() == IRType.NONE) {
                        IRType inferredType;
                        
                        if(exit.getCompareRight() instanceof IRIdentifier id) {
                            inferredType = typeMap.get(id);
                        } else {
                            inferredType = ((IRConstant) exit.getCompareRight()).getType();
                        }
                        
                        exit.setCompareLeft(new IRConstant(irc.getValue(), inferredType));
                    }
                    
                    if(exit.getCompareRight() instanceof IRConstant irc && irc.getType() == IRType.NONE) {
                        IRType inferredType;
                        
                        if(exit.getCompareLeft() instanceof IRIdentifier id) {
                            inferredType = typeMap.get(id);
                        } else {
                            inferredType = ((IRConstant) exit.getCompareLeft()).getType();
                        }
                        
                        exit.setCompareRight(new IRConstant(irc.getValue(), inferredType));
                    }
                
                default:
            }
        } while(modifiedTypes);
    }
    
    /**
     * Determines the type of each local
     * @param bb
     * @return
     */
    public static Map<IRIdentifier, IRType> getTypeMap(IRFunction function) {
        Map<IRIdentifier, IRType> typeMap = new HashMap<>();
        
        // Get function arguments
        addTypeInfo(function.getArguments(), typeMap);
        
        // Go through basic blocks
        for(IRBasicBlock bb : function.getBasicBlockList()) {
            // Add bb args
            addTypeInfo(bb.getArgumentList(), typeMap);
            
            // For each instruction, if it defines something, add its type
            for(IRLinearInstruction li : bb.getInstructions()) {
                if(li.getOp().hasDestination()) {
                    typeMap.put(li.getDestinationID(), li.getDestinationType());
                }
            }
        }
        
        return typeMap;
    }
    
    /**
     * Adds type information of an argument list to a type map
     * @param args
     * @param typeMap
     */
    private static void addTypeInfo(IRArgumentList args, Map<IRIdentifier, IRType> typeMap) {
        for(int i = 0; i < args.getArgumentCount(); i++) {
            typeMap.put(args.getName(i), args.getType(i));
        }
    }
    
    /**
     * Computes the liveness sets of each basic block in the function. 
     * @param function
     * @param argAssignmentsAreLiveOut If true, values assigned to the arguments of successors are considered live-out
     * @return Map from BB ID to Pair<Live-In set, Live-Out set>
     */
    public static Map<IRIdentifier, Pair<Set<IRIdentifier>, Set<IRIdentifier>>> getLivenessSets(IRFunction function, boolean argAssignmentsAreLiveOut) {
        Pair<List<IRIdentifier>, Map<Integer, Integer>> dfsInfoPair = getPreorderInfo(function);
        List<IRIdentifier> dfsOrderList = dfsInfoPair.a;
        Map<Integer, Integer> dfsAncestryMap = dfsInfoPair.b;
        
        return getLivenessSets(function, argAssignmentsAreLiveOut, dfsOrderList, dfsAncestryMap);
    }
    
    /**
     * Computes the liveness sets of each basic block in the function, given preorder DFS information
     * @param function
     * @param argAssignmentsAreLiveOut If true, values assigned to the arguments of successors are considered live-out
     * @param dfsOrderList
     * @param dfsAncestryMap
     * @return Map from BB ID to Pair<Live-In set, Live-Out set>
     */
    public static Map<IRIdentifier, Pair<Set<IRIdentifier>, Set<IRIdentifier>>> getLivenessSets(IRFunction function, boolean argAssignmentsAreLiveOut, List<IRIdentifier> dfsOrderList, Map<Integer, Integer> dfsAncestryMap) {
        return getLivenessSets(function, argAssignmentsAreLiveOut, getLoopNestingForest(function, dfsOrderList, dfsAncestryMap), dfsOrderList, dfsAncestryMap);
    }
    
    /**
     * Computes the liveness sets of each basic block in the function, given the loop nesting forest and preorder DFS information
     * @param function
     * @param argAssignmentsAreLiveOut If true, values assigned to the arguments of successors are considered live-out
     * @param loopNestingForest
     * @param dfsOrderList
     * @param dfsAncestryMap
     * @return Map from BB ID to Pair<Live-In set, Live-Out set>
     */
    public static Map<IRIdentifier, Pair<Set<IRIdentifier>, Set<IRIdentifier>>> getLivenessSets(IRFunction function, boolean argAssignmentsAreLiveOut, UnionFindForest<IRIdentifier> loopNestingForest, List<IRIdentifier> dfsOrderList, Map<Integer, Integer> dfsAncestryMap) {
        Map<IRIdentifier, Pair<Set<IRIdentifier>, Set<IRIdentifier>>> livenessSetMap = new HashMap<>();
        
        // Initialize map
        for(IRBasicBlock bb : function.getBasicBlockList()) {
            livenessSetMap.put(bb.getID(), new Pair<>(new HashSet<>(), new HashSet<>()));
        }
        
        // Postorder partial liveness
        glsPartialLivenessDFS(function.getEntryBlock().getID(), argAssignmentsAreLiveOut, livenessSetMap, new HashSet<>(), function, listToMap(dfsOrderList), dfsAncestryMap);
        
        // Propagate liveness through loops
        for(TreeNode<IRIdentifier> loopHeaderNode : loopNestingForest.getRoots()) {
            glsPropagateLoops(loopHeaderNode, livenessSetMap, function);
        }
        
        return livenessSetMap;
    }
    
    /**
     * Computes partial liveness information via a DFS
     * @param bbID
     * @param argAssignmentsAreLiveOut If true, values assigned to the arguments of successors are considered live-out
     * @param livenessSetMap
     * @param function
     * @param dfsOrderList
     * @param dfsAncestryMap
     */
    private static void glsPartialLivenessDFS(IRIdentifier bbID, boolean argAssignmentsAreLiveOut, Map<IRIdentifier, Pair<Set<IRIdentifier>, Set<IRIdentifier>>> livenessSetMap, Set<IRIdentifier> processed, IRFunction function, Map<IRIdentifier, Integer> dfsIndexMap, Map<Integer, Integer> dfsAncestryMap) {
        IRBasicBlock bb = function.getBasicBlock(bbID);
        
        Pair<Set<IRIdentifier>, Set<IRIdentifier>> livenessSetPair = livenessSetMap.get(bbID);
        Set<IRIdentifier> liveInSet = livenessSetPair.a,
                          liveOutSet = livenessSetPair.b;
        
        // Process each unprocessed successor that isn't a backedge
        for(IRIdentifier successorID : bb.getSuccessorBlocks()) {
            if(!isBackedge(bbID, successorID, dfsIndexMap, dfsAncestryMap) && !processed.contains(successorID)) {
                glsPartialLivenessDFS(successorID, argAssignmentsAreLiveOut, livenessSetMap, processed, function, dfsIndexMap, dfsAncestryMap);
            }
        }
        
        // Initialize 'currently live' with variables assigned to BB args if applicable
        Set<IRIdentifier> liveSet = new HashSet<>();
        
        IRBranchInstruction bbExit = bb.getExitInstruction();
        IRArgumentMapping trueMapping = bbExit.getTrueArgumentMapping(),
                          falseMapping = bbExit.getFalseArgumentMapping();
        
        if(argAssignmentsAreLiveOut) {
            if(trueMapping != null) {
                trueMapping.getMap().values().forEach(irv -> {
                    if(irv instanceof IRIdentifier id && id.getIDClass() == IRIdentifierClass.LOCAL) {
                        liveSet.add(id);
                    }
                });
            }
            
            if(falseMapping != null) {
                falseMapping.getMap().values().forEach(irv -> {
                    if(irv instanceof IRIdentifier id && id.getIDClass() == IRIdentifierClass.LOCAL) {
                        liveSet.add(id);
                    }
                });
            }
        }
        
        // Include non-bbarg live-ins of non-backedge successors in live set
        for(IRIdentifier successorID : bb.getSuccessorBlocks()) {
            if(!isBackedge(bbID, successorID, dfsIndexMap, dfsAncestryMap)) {
                IRBasicBlock successorBB = function.getBasicBlock(successorID);
                List<IRIdentifier> successorArgs = successorBB.getArgumentList().getNameList();
                
                for(IRIdentifier successorLiveIn : livenessSetMap.get(successorID).a) {
                    if(!successorArgs.contains(successorLiveIn)) {
                        liveSet.add(successorLiveIn);
                    }
                }
            }
        }
        
        // Record current live set as live-outs for this bb
        liveOutSet.addAll(liveSet);
        
        // If assignments are not considered live-out, add them to the live set here
        if(!argAssignmentsAreLiveOut) {
            if(trueMapping != null) {
                trueMapping.getMap().values().forEach(irv -> {
                    if(irv instanceof IRIdentifier id && id.getIDClass() == IRIdentifierClass.LOCAL) {
                        liveSet.add(id);
                    }
                });
            }
            
            if(falseMapping != null) {
                falseMapping.getMap().values().forEach(irv -> {
                    if(irv instanceof IRIdentifier id && id.getIDClass() == IRIdentifierClass.LOCAL) {
                        liveSet.add(id);
                    }
                });
            }
        }
        
        // Go through the BB's code backwards, updating liveness accordingly
        // For each instruction, remove variables that are defined and add variables being used to/from the live set
        switch(bbExit.getOp()) {
            case JCC:
                // Uses comparison arguments
                if(bbExit.getCompareLeft() instanceof IRIdentifier leftID && leftID.getIDClass() == IRIdentifierClass.LOCAL) {
                    liveSet.add(leftID);
                }
                
                if(bbExit.getCompareRight() instanceof IRIdentifier rightID && rightID.getIDClass() == IRIdentifierClass.LOCAL) {
                    liveSet.add(rightID);
                }
                break;
                
            case RET:
                // Uses the return value
                if(bbExit.getReturnValue() instanceof IRIdentifier retID && retID.getIDClass() == IRIdentifierClass.LOCAL) {
                    liveSet.add(retID);
                }
                break;
            
            default:
                // JMP, no use
        }
        
        for(int i = bb.getInstructions().size() - 1; i >= 0; i--) {
            IRLinearInstruction inst = bb.getInstructions().get(i);
            
            // Remove defined variable
            if(inst.hasDestination()) {
                liveSet.remove(inst.getDestinationID());
            }
            
            // Add used variables
            // Sources
            switch(inst.getSourceCount()) {
                // This switch uses fall-through to reduce code repetition
                case 4:
                    // All four
                    if(inst.getLeftComparisonValue() instanceof IRIdentifier lcid && lcid.getIDClass() == IRIdentifierClass.LOCAL) {
                        liveSet.add(lcid);
                    }
                    
                    if(inst.getRightComparisonValue() instanceof IRIdentifier rcid && rcid.getIDClass() == IRIdentifierClass.LOCAL) {
                        liveSet.add(rcid);
                    }
                
                case 2:
                    // left & right
                    if(inst.getRightSourceValue() instanceof IRIdentifier rsid && rsid.getIDClass() == IRIdentifierClass.LOCAL) {
                        liveSet.add(rsid);
                    }
                    
                case 1:
                    // left
                    if(inst.getLeftSourceValue() instanceof IRIdentifier lsid && lsid.getIDClass() == IRIdentifierClass.LOCAL) {
                        liveSet.add(lsid);
                    }
                    break;
                
                default:
                    throw new IllegalArgumentException("Linear op with 0 arguments: " + inst + " in " + bb.getID() + " in " + function.getID() + " in " + function.getModule().getName());
            }
            
            // Argument mappings
            if(inst.getOp() == IRLinearOperation.CALLN || inst.getOp() == IRLinearOperation.CALLR) {
                for(IRValue argMapVal : inst.getCallArgumentMapping().getMap().values()) {
                    if(argMapVal instanceof IRIdentifier argMapID && argMapID.getIDClass() == IRIdentifierClass.LOCAL) {
                        liveSet.add(argMapID);
                    }
                }
            }
        }
        
        // Live-in = live set + arguments
        liveInSet.addAll(liveSet);
        liveInSet.addAll(bb.getArgumentList().getNameList());
        processed.add(bbID);
    }
    
    /**
     * Completes liveness information by propagating through loops
     * @param loopNode
     * @param livenessSetMap
     * @param function
     */
    private static void glsPropagateLoops(TreeNode<IRIdentifier> loopNode, Map<IRIdentifier, Pair<Set<IRIdentifier>, Set<IRIdentifier>>> livenessSetMap, IRFunction function) {
        // Is this a loop header
        if(loopNode.getChildren().size() != 0) {
            IRIdentifier headerID = loopNode.getElement();
            
            // Live in loop = live in(header) - header args
            Set<IRIdentifier> liveInLoop = new HashSet<>(livenessSetMap.get(headerID).a);
            liveInLoop.removeAll(function.getBasicBlock(headerID).getArgumentList().getNameList());
            
            // Add liveInLoop to live-in and live-out of all children
            for(TreeNode<IRIdentifier> child : loopNode.getChildren()) {
                IRIdentifier childID = child.getElement();
                Pair<Set<IRIdentifier>, Set<IRIdentifier>> livenessPair = livenessSetMap.get(childID);
                
                livenessPair.a.addAll(liveInLoop);
                livenessPair.b.addAll(liveInLoop);
                
                // and recurse
                glsPropagateLoops(child, livenessSetMap, function);
            }
        }
    }
    
    /**
     * Determines the loop-nesting forest of a function
     * Implements Tarjan's algorithm as described by G. Ramalingam - Identifying Loops In Almost Linear Time
     * NOTE - REQUIRES REDUCIBLE CFG
     * @param function
     * @return map from BB ID to corresponding loop header's BB ID
     */
    public static UnionFindForest<IRIdentifier> getLoopNestingForest(IRFunction function) {
        // Get DFS order and ancestry
        Pair<List<IRIdentifier>, Map<Integer, Integer>> dfsPair = getPreorderInfo(function);
        List<IRIdentifier> dfsOrderList = dfsPair.a;
        Map<Integer, Integer> dfsAncestryMap = dfsPair.b;
        
        // Defer
        return getLoopNestingForest(function, dfsOrderList, dfsAncestryMap);
    }
    
    /**
     * Determines the loop-nesting forest of a function given preorder DFS information
     * Implements Tarjan's algorithm as described by G. Ramalingam - Identifying Loops In Almost Linear Time
     * NOTE - REQUIRES REDUCIBLE CFG
     * @param function
     * @param dfsOrderList
     * @param dfsAncestryMap
     * @return
     */
    public static UnionFindForest<IRIdentifier> getLoopNestingForest(IRFunction function, List<IRIdentifier> dfsOrderList, Map<Integer, Integer> dfsAncestryMap) {
        UnionFindForest<IRIdentifier> forest= new UnionFindForest<>();
        
        // Initialize forest with BBs
        for(IRBasicBlock bb : function.getBasicBlockList()) {
            IRIdentifier bbID = bb.getID();
            forest.add(bbID);
        }
        
        // Construct map from ID to list index
        Map<IRIdentifier, Integer> dfsIndexMap = listToMap(dfsOrderList);
        
        // Go in reverse order
        List<IRIdentifier> dfsReverseList = new ArrayList<>(dfsOrderList);
        Collections.reverse(dfsReverseList);
        for(IRIdentifier bbID : dfsReverseList) {
            // findLoop
            getLNFFindLoop(bbID, forest, dfsIndexMap, dfsAncestryMap, function);
        }
        
        // 
        
        return forest;
    }
    
    /**
     * findLoop for getLoopNestingForest
     * @param potentialHeader
     * @param dfsIndexMap
     * @param dfsAncestryMap
     */
    private static void getLNFFindLoop(IRIdentifier potentialHeader, UnionFindForest<IRIdentifier> forest, Map<IRIdentifier, Integer> dfsIndexMap, Map<Integer, Integer> dfsAncestryMap, IRFunction function) {
        Set<IRIdentifier> loopBody = new HashSet<>();
        Deque<IRIdentifier> worklist = new ArrayDeque<>();
        
        //LOG.info("potential header " + potentialHeader);
        
        // Initialize worklist with backedges pointing to potentialHeader
        for(IRIdentifier from : function.getBasicBlock(potentialHeader).getPredecessorBlocks()) {
            if(!from.equals(potentialHeader) && isBackedge(from, potentialHeader, dfsIndexMap, dfsAncestryMap)) {
                //LOG.info("backedge " + from + " -> " + potentialHeader);
                worklist.add(forest.findElement(from));
            }
        }
        
        // Work through the worklist
        while(!worklist.isEmpty()) {
            // Get work, add to body
            IRIdentifier bbID = worklist.poll();
            loopBody.add(bbID);
            //LOG.info("processing worklist item " + bbID);
            
            // For each predecessor that isn't a backedge
            for(IRIdentifier from : function.getBasicBlock(bbID).getPredecessorBlocks()) {
                if(!isBackedge(from, bbID, dfsIndexMap, dfsAncestryMap)) {
                    IRIdentifier fromHeader = forest.findElement(from);
                    
                    if(!(fromHeader.equals(potentialHeader) || loopBody.contains(fromHeader) || worklist.contains(fromHeader))) {
                        //LOG.info("adding to worklist " + fromHeader);
                        worklist.offer(fromHeader);
                    }
                }
            }
        }
        
        // Collapse
        for(IRIdentifier bbID : loopBody) {
            forest.union(potentialHeader, bbID);
        }
    }
    
    /**
     * Given DFS information, determines if from -> to is a backedge
     * @param from
     * @param to
     * @param dfsIndexMap
     * @param dfsAncestryMap
     * @return
     */
    public static boolean isBackedge(IRIdentifier from, IRIdentifier to, Map<IRIdentifier, Integer> dfsIndexMap, Map<Integer, Integer> dfsAncestryMap) {
        // x -> y is a backedge if x is a descendant of y in the DFS search tree
        // y is an ancestor of x if index(y) <= index(x) and index(x) <= ancestry(index(y))
        int xIndex = dfsIndexMap.get(from);
        int yIndex = dfsIndexMap.get(to);
        
        return yIndex <= xIndex && xIndex <= dfsAncestryMap.get(yIndex);
    }
    
    /**
     * Gets a list of BB identifiers in reverse postorder order
     * @param function
     * @return
     */
    public static List<IRIdentifier> getReversePostorderList(IRFunction function) {
        // get postorder
        List<IRIdentifier> list = getPostorderList(function);
        
        // reverse it
        Collections.reverse(list);
        
        return list;
    }
    
    /**
     * Gets a list of BB identifiers in postorder order
     * @param function
     * @return
     */
    public static List<IRIdentifier> getPostorderList(IRFunction function) {
        List<IRIdentifier> list = new ArrayList<>();
        
        // Traverse in postorder
        postorderDFS(function.getBasicBlockList().get(0).getID(), list, new HashSet<>(), function);
        
        return list;
    }
    
    /**
     * DFS for getPostorderList
     * @param list
     * @param visited
     * @param function
     */
    private static void postorderDFS(IRIdentifier id, List<IRIdentifier> list, Set<IRIdentifier> visited, IRFunction function) {
        // mark visited
        visited.add(id);
        
        // postorder depth-first
        IRBranchInstruction exit = function.getBasicBlock(id).getExitInstruction();
        IRIdentifier trueID = exit.getTrueTargetBlock();
        IRIdentifier falseID = exit.getFalseTargetBlock();
        
        if(trueID != null && !visited.contains(trueID)) {
            postorderDFS(trueID, list, visited, function);
        }
        
        if(falseID != null && !visited.contains(falseID)) {
            postorderDFS(falseID, list, visited, function);
        }
        
        // add to list
        list.add(id);
    }
    
    /**
     * Gets a list of BB identifiers in preorder order of the depth-first spanning tree, and a map from list indices to the list index of that BB's last descendant
     * @param function
     * @return
     */
    public static Pair<List<IRIdentifier>, Map<Integer, Integer>> getPreorderInfo(IRFunction function) {
        List<IRIdentifier> preorderList = new ArrayList<>();
        Map<Integer, Integer> preorderAncestryMap = new HashMap<>();
        
        // dfs
        preorderDFS(function.getBasicBlockList().get(0).getID(), preorderList, preorderAncestryMap, new HashSet<>(), function);
        
        return new Pair<>(preorderList, preorderAncestryMap);
    }
    
    /**
     * DFS for getPreorderInfo
     * @param id
     * @param preorderList
     * @param preorderAncestryMap
     * @param visited
     * @param function
     */
    private static void preorderDFS(IRIdentifier id, List<IRIdentifier> preorderList, Map<Integer, Integer> preorderAncestryMap, Set<IRIdentifier> visited, IRFunction function) {
        // Mark ID as visited
        visited.add(id);
        
        // Record preorder
        int preorderIndex = preorderList.size();
        preorderList.add(id);
        
        // Search successors
        IRBranchInstruction exit = function.getBasicBlock(id).getExitInstruction();
        IRIdentifier trueID = exit.getTrueTargetBlock();
        IRIdentifier falseID = exit.getFalseTargetBlock();
        
        if(trueID != null && !visited.contains(trueID)) {
            preorderDFS(trueID, preorderList, preorderAncestryMap, visited, function);
        }
        
        if(falseID != null && !visited.contains(falseID)) {
            preorderDFS(falseID, preorderList, preorderAncestryMap, visited, function);
        }
        
        // Record last descendant's preorder number
        preorderAncestryMap.put(preorderIndex, preorderList.size());
    }
    
    /**
     * Performs depth-first search, recording a variety of information
     * Not sure if it'll be used but w/e
     * @param id
     * @param postorderList List of BB IDs in postorder order
     * @param preorderList List of BB IDs in preorder order
     * @param preorderAncestryMap Maps the index of a BB ID in the preorder list to the index of its last descendant 
     * @param visited
     * @param function
     */
    private static void generalDFS(IRIdentifier id, List<IRIdentifier> postorderList, List<IRIdentifier> preorderList, Map<Integer, Integer> preorderAncestryMap, Set<IRIdentifier> visited, IRFunction function) {
        // Mark ID as visited
        visited.add(id);
        
        // Record preorder
        int preorderIndex = preorderList.size();
        preorderList.add(id);
        
        // Search successors
        IRBranchInstruction exit = function.getBasicBlock(id).getExitInstruction();
        IRIdentifier trueID = exit.getTrueTargetBlock();
        IRIdentifier falseID = exit.getFalseTargetBlock();
        
        if(trueID != null && !visited.contains(trueID)) {
            generalDFS(trueID, postorderList, preorderList, preorderAncestryMap, visited, function);
        }
        
        if(falseID != null && !visited.contains(falseID)) {
            generalDFS(falseID, postorderList, preorderList, preorderAncestryMap, visited, function);
        }
        
        // Record postorder
        postorderList.add(id);
        
        // Record last descendant's preorder number
        preorderAncestryMap.put(preorderIndex, preorderList.size());
    }
    
    /**
     * Constructs a list of IRDefinitions found in the function
     * @param function
     * @return
     */
    public static List<IRDefinition> getDefinitionList(IRFunction function) {
        List<IRDefinition> defList = new ArrayList<>();
        
        // Get global constants/variables
        IRModule module = function.getModule();
        
        for(IRIdentifier id : module.getGlobals().keySet()) {
            defList.add(new IRDefinition(id));
        }
        
        for(IRIdentifier id : module.getFunctions().keySet()) {
            defList.add(new IRDefinition(id));
        }
        
        // Get function args
        for(IRIdentifier id : function.getArguments().getNameList()) {
            defList.add(new IRDefinition(id, function));
        }
        
        // For each BB
        for(IRBasicBlock bb : function.getBasicBlockList()) {
            // Get bb args
            for(IRIdentifier id : bb.getArgumentList().getNameList()) {
                defList.add(new IRDefinition(id, bb));
            }
            
            // Get instruction defines
            for(IRLinearInstruction li : bb.getInstructions()) {
                IRIdentifier id = li.getDestinationID();
                if(id != null) {
                    defList.add(new IRDefinition(id, li));
                }
            }
            
            defList.add(new IRDefinition(bb.getID(), bb.getExitInstruction()));
        }
        
        return defList;
    }
    
    /**
     * Constructs a map from identifiers to their definitions
     * @param defList
     * @return
     */
    public static Map<IRIdentifier, IRDefinition> getDefinitionMap(List<IRDefinition> defList) {
        Map<IRIdentifier, IRDefinition> defMap = new HashMap<>();
        
        for(IRDefinition def : defList) {
            defMap.put(def.getID(), def);
        }
        
        return defMap;
    }
    
    /**
     * Constructs a map from identifiers to their definitions
     * Calls getDefinitionList
     * @param function
     * @return
     */
    public static Map<IRIdentifier, IRDefinition> getDefinitionMap(IRFunction function) {
        return getDefinitionMap(getDefinitionList(function));
    }
    
    /**
     * Constructs a map from identifiers to defines that use them
     * @param function
     * @param defineMap
     * @return
     */
    public static Map<IRIdentifier, List<IRDefinition>> getUseMap(IRFunction function, List<IRDefinition> defList) {
        Map<IRIdentifier, List<IRDefinition>> useMap = new HashMap<>();
        
        for(IRDefinition def : defList) {
            IRIdentifier definedID = def.getID();
            
            switch(def.getType()) {
                case BBARG: {
                    // This def is a use for any value mapped to this argument for the BB
                    IRBasicBlock bb = def.getBB();
                    
                    for(IRIdentifier predID : bb.getPredecessorBlocks()) {
                        IRBranchInstruction predExit = function.getBasicBlock(predID).getExitInstruction();
                        
                        if(bb.getID().equals(predExit.getTrueTargetBlock())) {
                            if(predExit.getTrueArgumentMapping().getMapping(definedID) instanceof IRIdentifier mappedID) {
                                MapUtil.getOrCreateList(useMap, mappedID).add(def);
                            }
                        }
                        
                        if(bb.getID().equals(predExit.getFalseTargetBlock())) {
                            if(predExit.getFalseArgumentMapping().getMapping(definedID) instanceof IRIdentifier mappedID) {
                                MapUtil.getOrCreateList(useMap, mappedID).add(def);
                            }
                        }
                    }
                    break;
                }
                    
                case LINEAR: {
                    // This def is a use for any value in the arguments of the LI
                    IRLinearInstruction li = def.getLinearInstruction();
                    
                    if(li.getLeftSourceValue() instanceof IRIdentifier argID) {
                        MapUtil.getOrCreateList(useMap, argID).add(def);
                    }
                    
                    if(li.getRightSourceValue() instanceof IRIdentifier argID) {
                        MapUtil.getOrCreateList(useMap, argID).add(def);
                    }
                    
                    if(li.getLeftComparisonValue() instanceof IRIdentifier argID) {
                        MapUtil.getOrCreateList(useMap, argID).add(def);
                    }
                    
                    if(li.getRightComparisonValue() instanceof IRIdentifier argID) {
                        MapUtil.getOrCreateList(useMap, argID).add(def);
                    }
                    
                    if(li.getOp() == IRLinearOperation.CALLR) {
                        for(IRValue mappedVal : li.getCallArgumentMapping().getMap().values()) {
                            if(mappedVal instanceof IRIdentifier mappedID) {
                                MapUtil.getOrCreateList(useMap, mappedID).add(def);
                            }
                        }
                    }
                    break;
                }
                
                case BRANCH: {
                    // This def is a use for any value in the arguments of the BI. Mappings are handled by BBArg defs
                    IRBranchInstruction bi = def.getBranchInstruction();
                    
                    if(bi.getCondition() != IRCondition.NONE) {
                        if(bi.getCompareLeft() instanceof IRIdentifier argID) {
                            MapUtil.getOrCreateList(useMap, argID).add(def);
                        }
                        
                        if(bi.getCompareRight() instanceof IRIdentifier argID) {
                            MapUtil.getOrCreateList(useMap, argID).add(def);
                        }
                    }
                    break;
                }
                    
                default:
                    // GLOBAL and FUNARG don't have any use's within the function
                    break;
            }
        }
        
        return useMap;
    }
    
    /**
     * Constructs a map from identifiers to defines that use them
     * Calls getDefinitionMap
     * @param function
     * @return
     */
    public static Map<IRIdentifier, List<IRDefinition>> getUseMap(IRFunction function) {
        return getUseMap(function, getDefinitionList(function));
    }
    
    /**
     * Replaces uses of from with to in module. Removes statement which defines from
     * @param module
     * @param from
     * @param to
     */
    public static void replaceInModule(IRModule module, IRValue from, IRValue to) {
        // Replace in functions
        for(IRFunction function : module.getFunctions().values()) {
            replaceInFunction(function, from, to);
        }
        
        // Find defining global if it exists
        // TODO
    }
    
    /**
     * Replaces uses of from with to in function. Removes statement which defines from if found
     * @param function
     * @param from
     * @param to
     */
    public static void replaceInFunction(IRFunction function, IRValue from, IRValue to) {
        // Replace in basic blocks
        for(IRBasicBlock block : function.getBasicBlockList()) {
            replaceInBlock(block, from, to);
        }
    }
    
    /**
     * Replaces uses of from with to in bb. Removes statement which defines from if found
     * @param bb
     * @param from
     * @param to
     */
    public static void replaceInBlock(IRBasicBlock bb, IRValue from, IRValue to) {
        
        // Replace in body code, find defining instruction if it exists\
        List<IRLinearInstruction> lis = bb.getInstructions();
        for(int i = 0; i < lis.size(); i++) {
            IRLinearInstruction li = lis.get(i);
            
            if(from.equals(li.getDestinationID())) {
                // LI defines from. Remove it.
                lis.remove(i--);
            } else {
                if(from.equals(li.getLeftSourceValue())) {
                    // Left source is from. replace.
                    li.setLeftSourceValue(to);
                }
                
                if(from.equals(li.getRightSourceValue())) {
                    // Right source is from. replace.
                    li.setRightSourceValue(to);
                }
                
                if(from.equals(li.getLeftComparisonValue())) {
                    // Left comparison value is from. replace.
                    li.setLeftComparisonValue(to);
                }
                
                if(from.equals(li.getRightComparisonValue())) {
                    // Right comparison value is from. replace.
                    li.setRightComparisonValue(to);
                }
                
                if(li.getCallArgumentMapping() != null) {
                    for(Entry<IRIdentifier, IRValue> mapping : li.getCallArgumentMapping().getMap().entrySet()) {
                        if(from.equals(mapping.getValue())) {
                            mapping.setValue(to);
                        }
                    }
                }
            }
        }
        
        // Replace in exit code
        IRBranchInstruction branch = bb.getExitInstruction();
        if(branch != null) {
            if(from.equals(branch.getCompareLeft())) {
                branch.setCompareLeft(to);
            }
            
            if(from.equals(branch.getCompareRight())) {
                branch.setCompareRight(to);
            }
            
            if(from.equals(branch.getReturnValue())) {
                branch.setReturnValue(to);
            }
            
            IRArgumentMapping trueMap = branch.getTrueArgumentMapping();
            IRArgumentMapping falseMap = branch.getFalseArgumentMapping();
            
            if(trueMap != null) {
                for(Entry<IRIdentifier, IRValue> entry : trueMap.getMap().entrySet()) {
                    if(entry.getValue().equals(from)) {
                        entry.setValue(to);
                    }
                }
            }
            
            if(falseMap != null) {
                for(Entry<IRIdentifier, IRValue> entry : falseMap.getMap().entrySet()) {
                    if(entry.getValue().equals(from)) {
                        entry.setValue(to);
                    }
                }
            }
        }
        
        // Find defining argument if it exists
        IRArgumentList args = bb.getArgumentList();
        List<IRIdentifier> argNames = args.getNameList();
        List<IRType> argTypes = args.getTypeList();
        
        for(int i = 0; i < argNames.size(); i++) {
            if(argNames.get(i).equals(from)) {
                // Defined by an argument. Remove it
                IRIdentifier argName = argNames.remove(i);
                argTypes.remove(i);
                i--;
                
                // Remove from predecessors' mappings
                for(IRIdentifier predID : bb.getPredecessorBlocks()) {
                    IRBasicBlock predBB = bb.getFunction().getBasicBlock(predID);
                    IRBranchInstruction predExit = predBB.getExitInstruction();
                    IRIdentifier predTrueSuccessor = predExit.getTrueTargetBlock();
                    IRIdentifier predFalseSuccessor = predExit.getFalseTargetBlock();
                    
                    if(bb.getID().equals(predTrueSuccessor)) {
                        // We're the true successor
                        predExit.getTrueArgumentMapping().removeArgument(argName);
                    }
                    
                    if(bb.getID().equals(predFalseSuccessor)) {
                        // We're the false successor
                        predExit.getFalseArgumentMapping().removeArgument(argName);
                    }
                }
            }
        }
    }
    
    /**
     * Converts a list to a map from element to index
     * @param <K>
     * @param list
     * @return
     */
    public static <K> Map<K, Integer> listToMap(List<K> list) {
        Map<K, Integer> map = new HashMap<>();
        
        for(int i = 0; i < list.size(); i++) {
            map.put(list.get(i), i);
        }
        
        return map;
    }
}
