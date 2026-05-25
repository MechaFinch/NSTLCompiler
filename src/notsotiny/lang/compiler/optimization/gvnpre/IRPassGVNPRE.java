package notsotiny.lang.compiler.optimization.gvnpre;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import notsotiny.lang.compiler.optimization.IROptimizationLevel;
import notsotiny.lang.compiler.optimization.IROptimizationPass;
import notsotiny.lang.compiler.optimization.transforms.IRTransformCriticalEdgeRemoval;
import notsotiny.lang.ir.parts.IRArgumentMapping;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRBranchInstruction;
import notsotiny.lang.ir.parts.IRConstant;
import notsotiny.lang.ir.parts.IRDefinition;
import notsotiny.lang.ir.parts.IRDefinitionType;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.parts.IRLinearOperation;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lang.ir.util.IRPrinter;
import notsotiny.lang.ir.util.IRUtil;
import notsotiny.lib.data.ComputedMap;
import notsotiny.lib.data.Pair;
import notsotiny.lib.data.TreeNode;
import notsotiny.lib.printing.LogPrinter;
import notsotiny.lib.util.MapUtil;

/**
 * Global Value Numbering based Partial Redundancy Elimination
 * as described in VanDrunen & Hosking Value-Based Partial Redundancy Elimination
 * and based on the implementation by I-mikan-I in https://github.com/I-mikan-I/ssa-compiler/blob/main/src/ssa.rs
 */
public class IRPassGVNPRE implements IROptimizationPass {
    
    private static Logger LOG = Logger.getLogger(IRPassGVNPRE.class.getName());
    
    // Linear ops which can be numbered expressions
    private static final Set<IRLinearOperation> NUMBERABLE_OPS = EnumSet.of(
        IRLinearOperation.TRUNC, IRLinearOperation.SX, IRLinearOperation.ZX, IRLinearOperation.SELECT,
        IRLinearOperation.ADD, IRLinearOperation.SUB, IRLinearOperation.MULU, IRLinearOperation.MULS,
        IRLinearOperation.DIVU, IRLinearOperation.DIVS, IRLinearOperation.REMU, IRLinearOperation.REMS,
        IRLinearOperation.SHL, IRLinearOperation.SHR, IRLinearOperation.SAR, IRLinearOperation.ROL,
        IRLinearOperation.ROR, IRLinearOperation.AND, IRLinearOperation.OR, IRLinearOperation.XOR,
        IRLinearOperation.NOT, IRLinearOperation.NEG
    );

    @Override
    public IROptimizationLevel level() {
        return IROptimizationLevel.TWO;
    }

    @Override
    public IRModule optimize(IRModule module) {
        LOG.finer("Performing GVN-PRE on " + module.getName());
        
        // In each function
        for(IRFunction func : module.getInternalFunctions().values()) {
            LOG.finest("Performing GVN-PRE on " + func.getID());
            
            // Do necessary transformations
            IRUtil.inferNoneTypes(func);
            
            /*
             * TODO: Loop rotation
             */
            
            IRTransformCriticalEdgeRemoval.removeCriticalEdges(func);
            
            // Get necessary information
            IRIdentifier exitID = func.getFUID("exit", IRIdentifierClass.BLOCK);
             
            List<IRIdentifier> reversePostorderList = IRUtil.getReversePostorderList(func);
            Map<IRIdentifier, IRIdentifier> dominatorMap = IRUtil.getDominatorMap(func, reversePostorderList);
            Map<IRIdentifier, IRIdentifier> postdominatorMap = IRUtil.getPostdominatorMap(func, exitID);
            
            Map<IRIdentifier, TreeNode<IRIdentifier>> dominatorTreeMap = MapUtil.mapToForest(dominatorMap);
            Map<IRIdentifier, TreeNode<IRIdentifier>> postdominatorTreeMap = MapUtil.mapToForest(postdominatorMap);
            
            // Local type map + treat GLOBAL and BLOCk ids as pointers
            Map<IRIdentifier, IRType> typeMap = new ComputedMap<>(IRUtil.getTypeMap(func), id -> id.getIDClass() == IRIdentifierClass.LOCAL ? IRType.NONE : IRType.I32); 
            
            // Do GVN-PRE
            // Generate initial value table
            Map<IRIdentifier, Boolean> functionPurityMap = new HashMap<>();
            GVNValueTable valueTable = generateInitialTable(func, reversePostorderList, functionPurityMap, typeMap);
            
            // Produced by BuildSets
            Map<IRIdentifier, Map<Integer, IRValue>> leaderSets = new HashMap<>();                  // Map bbID -> map value# -> leader (earliest IRValue containing value#)
            Map<IRIdentifier, List<Pair<Integer, GVNElement>>> antileaderSets = new HashMap<>();    // Map bbID -> topological sorted set of value# with their antileader (anticipated local or expression for value#)
            Map<IRIdentifier, Map<Integer, IRIdentifier>> phiGenSets = new HashMap<>();             // Map bbID -> map value# -> phi destination id
            
            buildSets(phiGenSets, leaderSets, antileaderSets, valueTable, func, functionPurityMap, dominatorTreeMap.get(func.getEntryBlock().getID()), postdominatorTreeMap.get(exitID), typeMap);
            insert(leaderSets, antileaderSets, phiGenSets, valueTable, func, dominatorTreeMap.get(func.getEntryBlock().getID()), typeMap);
            eliminate(leaderSets, valueTable, func, dominatorMap);
        }
        
        return module;
    }
    
    /**
     * Perform the Elminiate phase of the algorithm
     * For each instruction, if the leader for the value number of the instruction's target is not the target, replace it with the leader
     * @param leaderSets
     * @param valueTable
     * @param func
     * @param dominatorMap
     */
    private void eliminate(Map<IRIdentifier, Map<Integer, IRValue>> leaderSets, GVNValueTable valueTable, IRFunction func, Map<IRIdentifier, IRIdentifier> dominatorMap) {
        for(IRBasicBlock bb : func.getBasicBlockList()) {
            IRIdentifier bbID = bb.getID();
            
            if(LOG.isLoggable(Level.FINEST)) { 
                LOG.finest("Running Eliminate for " + bbID);
            }
            
            
            Map<Integer, IRValue> leaders = leaderSets.getOrDefault(bbID, new HashMap<>());
            Map<Integer, IRValue> leadersIn = new HashMap<>(leaderSets.getOrDefault(dominatorMap.get(bbID), new HashMap<>()));
            
            // Handle args
            for(IRIdentifier argID : new ArrayList<>(bb.getArgumentList().getNameList())) {
                int argVN = valueTable.addIRValue(argID, bb.getArgumentList().getType(argID));
                IRValue leader = leaders.getOrDefault(argVN, argID);
                
                if(!leader.equals(argID)) {
                    // Leader /= target
                    if(LOG.isLoggable(Level.FINEST)) {
                        LOG.finest("Replacing " + argID + " with " + leader);
                    }
                    
                    IRUtil.replaceInFunction(func, argID, leader);
                }
                
                
                leadersIn.put(argVN, leader);
            }
            
            // Handle instructions
            for(IRLinearInstruction li : new ArrayList<>(bb.getInstructions())) {
                if(li.hasDestination()) {
                    IRIdentifier dest = li.getDestinationID();
                    int destVN = valueTable.getValueNumber(new GVNValue(li.getDestinationType(), dest));
                    IRValue leader = leadersIn.getOrDefault(destVN, dest);
                    
                    if(!leader.equals(dest)) {
                        // Leader /= target
                        if(LOG.isLoggable(Level.FINEST)) {
                            LOG.finest("Replacing " + dest + " with " + leader);
                        }
                        
                        IRUtil.replaceInFunction(func, dest, leader);
                    }
                    
                    leadersIn.put(destVN, leader);
                }
            }
        }
    }
    
    /**
     * Perform the Insert phase of the algorithm
     * This phase inserts anticipated expressions such that additional expressions can be eliminated in the subsequent phase
     * This phase does the PRE part of GVN-PRE, and yields a GVN algorithm when omitted
     * @param leaderSets
     * @param antileaderSets
     * @param phiGenSets
     * @param valueTable
     * @param func
     * @param dominatorTreeRoot
     * @param typeMap
     */
    private void insert(Map<IRIdentifier, Map<Integer, IRValue>> leaderSets, Map<IRIdentifier, List<Pair<Integer, GVNElement>>> antileaderSets, Map<IRIdentifier, Map<Integer, IRIdentifier>> phiGenSets, GVNValueTable valueTable, IRFunction func, TreeNode<IRIdentifier> dominatorTreeRoot, Map<IRIdentifier, IRType> typeMap) {
        // Because of the way our IR does constants, they won't show up in the leader set on their own
        for(Entry<IRIdentifier, List<Pair<Integer, GVNElement>>> anticEntry : antileaderSets.entrySet()) {
            IRIdentifier bbID = anticEntry.getKey();
            List<Pair<Integer, GVNElement>> antic = anticEntry.getValue();
            Map<Integer, IRValue> leaders = leaderSets.get(bbID);
            
            for(Pair<Integer, GVNElement> pair : antic) {
                int vn = pair.a;
                GVNElement elem = pair.b;
                
                if(elem instanceof GVNValue v && v.val() instanceof IRConstant c) {
                    leaders.putIfAbsent(vn, c);
                }
            }
        }
        
        // According to paper, not more than 3 iterations
        Map<IRIdentifier, Set<Integer>> newExprs = new HashMap<>();
        while(insertRecursive(dominatorTreeRoot, newExprs, leaderSets, antileaderSets, phiGenSets, valueTable, func, typeMap));
    }
    
    /**
     * Recursive part of the Insert phase
     * Performs insert for the given node, then recurses on its dominated nodes
     * @param domTreeNode
     * @param newExprs
     * @param leaderSets
     * @param antileaderSets
     * @param phiGenSets
     * @param valueTable
     * @param func
     * @param typeMap
     * @return
     */
    private boolean insertRecursive(TreeNode<IRIdentifier> domTreeNode, Map<IRIdentifier, Set<Integer>> newExprs, Map<IRIdentifier, Map<Integer, IRValue>> leaderSets, Map<IRIdentifier, List<Pair<Integer, GVNElement>>> antileaderSets, Map<IRIdentifier, Map<Integer, IRIdentifier>> phiGenSets, GVNValueTable valueTable, IRFunction func, Map<IRIdentifier, IRType> typeMap) {
        boolean changed = false;
        
        // Get bb info
        IRIdentifier bbID = domTreeNode.getElement();
        IRBasicBlock bb = func.getBasicBlock(bbID);
        
        Set<Integer> bbNew = MapUtil.getOrCreateSet(newExprs, bbID);        
        Map<Integer, IRValue> bbLeaders = leaderSets.get(bbID);
        
        if(bb.getPredecessorBlocks().size() > 1) {
            if(LOG.isLoggable(Level.FINEST)) {
                LOG.finest("Running Insert for " + bbID);
            }
            
            /*
             * Block is a merge - For each anticipated expression, if it is available in at least
             * one predecessor, insert it into predecessors where it is not available, and create a
             * bb arg to merge the predecessors' leaders
             * When adding a new computation or phi, update the leader set and new set with it.
             * Additionally, expressions in a block's dominator's new set are added to that block's
             * leader and new sets
             */
            List<Pair<Integer, GVNElement>> bbAntic = antileaderSets.get(bbID);
            Map<Integer, IRIdentifier> bbPhiGen = phiGenSets.get(bbID);
            
            // Collect phi translated antic for each predecessor
            Map<IRIdentifier, List<Pair<Integer, GVNElement>>> translatedAntics = new HashMap<>();
            
            for(IRIdentifier predID : bb.getPredecessorBlocks()) {
                translatedAntics.put(predID, phiTranslate(predID, bbID, bbPhiGen, bbAntic, valueTable, func));
            }
            
            /*
             * Collect expressions to hoist into predecessors
             * Value numbers & expressions are translated by phiTranslate, but we can identify
             * equivalent expressions by their index in the ordered set.
             * 
             * If all predecessors have the same leader for a value, we don't want to create an
             * extra phi for it, so track the leader until a difference is found
             * Removing these extra phis has a negative effect on performance. My guess is that it
             * produces long lifetimes which interact poorly with the current register allocator
             */
            //Map<Integer, IRValue> hoistCandidates = new HashMap<>();
            Set<Integer> indicesToHoist = new HashSet<>();
            
            for(Entry<IRIdentifier, List<Pair<Integer, GVNElement>>> entry : translatedAntics.entrySet()) {
                // Get info
                IRIdentifier predID = entry.getKey();
                List<Pair<Integer, GVNElement>> predAnticOut = entry.getValue();
                Map<Integer, IRValue> predLeaders = leaderSets.get(predID);
                
                // For each anticipated expression
                for(int idx = 0; idx < bbAntic.size(); idx++) {
                    // Unwrap info
                    Pair<Integer, GVNElement> bbPair = bbAntic.get(idx);
                    Pair<Integer, GVNElement> predPair = predAnticOut.get(idx);
                    
                    int bbVN = bbPair.a;
                    int translatedVN = predPair.a;
                    GVNElement translatedElement = predPair.b;
                    
                    // If the expression is available in this predecessor, isn't simple, and hasn't already been handled,
                    if(predLeaders.containsKey(translatedVN) && !(translatedElement instanceof GVNValue) && !bbNew.contains(bbVN)) {
                        /*
                        // Is this a candidate for real hoisting?
                        if(hoistCandidates.containsKey(idx)) {
                            // Is its leader different from existing ones?
                            if(!hoistCandidates.get(idx).equals(predLeaders.get(translatedVN))) {*/
                                // Mark it for hoisting
                                boolean added = indicesToHoist.add(idx);
                                
                                if(added && LOG.isLoggable(Level.FINEST)) {
                                    LOG.finest("Marked " + bbVN + " for hoisting");
                                }
                            /*}
                        } else {
                            // No, make it one
                            hoistCandidates.put(idx, predLeaders.get(translatedVN));
                            
                            if(LOG.isLoggable(Level.FINEST)) {
                                LOG.finest("Found hoist candidate " + bbVN);
                            }
                        }*/
                    } /*else if(!predLeaders.containsKey(translatedVN) && hoistCandidates.containsKey(idx)) {
                        // Index is a candidate and doesn't have a leader here. Must be hoisted.
                        indicesToHoist.add(idx);
                        
                        if(LOG.isLoggable(Level.FINEST)) {
                            LOG.finest("Marked " + bbVN + " for hoisting");
                        }
                    }*/
                }
            }
            
            // Create bb args for hoisted expressions
            Map<Integer, IRIdentifier> indexIDMap = new HashMap<>();
            
            for(int idx : indicesToHoist) {
                Pair<Integer, GVNElement> expr = bbAntic.get(idx);
                int vn = expr.a;
                GVNElement elem = expr.b;
                
                // Make a new arg for the hoisted expression, inheriting its name if possible
                String inheritedName = switch(elem) {
                    case GVNValue gv        -> (gv.val() instanceof IRIdentifier id) ? id.getName() : "";
                    case GVNExpression ge   -> {
                        int gn = valueTable.getValueNumber(ge);
                        if(bbLeaders.containsKey(gn)) {
                            yield (bbLeaders.get(gn) instanceof IRIdentifier id) ? id.getName() : "";
                        } else {
                            yield "";
                        }
                    }
                    default -> "";
                };
                
                IRIdentifier argID = func.getFUID(inheritedName);
                bb.addArgument(argID, elem.type());
                indexIDMap.put(idx, argID);
                typeMap.put(argID, elem.type());
                
                if(LOG.isLoggable(Level.FINEST)) { 
                    LOG.finest("Creating arg for " + vn + " = " + argID);
                }
            }
            
            // Place hoisted expressions
            for(Entry<IRIdentifier, List<Pair<Integer, GVNElement>>> entry : translatedAntics.entrySet()) {
                // Get info
                IRIdentifier predID = entry.getKey();
                List<Pair<Integer, GVNElement>> predAnticOut = entry.getValue();
                Map<Integer, IRValue> predLeaders = leaderSets.get(predID);
                Set<Integer> predNew = MapUtil.getOrCreateSet(newExprs, predID);
                
                IRBasicBlock predBB = func.getBasicBlock(predID);
                IRBranchInstruction predExit = predBB.getExitInstruction();
                IRArgumentMapping predMapping = bbID.equals(predExit.getTrueTargetBlock()) ? predExit.getTrueArgumentMapping() : predExit.getFalseArgumentMapping();
                
                // For each anticipated expression
                for(int idx = 0; idx < bbAntic.size(); idx++) {
                    // Must be marked for it to hoist
                    if(!indicesToHoist.contains(idx)) {
                        continue;
                    }
                    
                    // Get expression info
                    Pair<Integer, GVNElement> predPair = predAnticOut.get(idx); 
                    int predVN = predPair.a;
                    GVNElement predExpr = predPair.b;
                    
                    // Must not have a leader in the pred already
                    if(!predLeaders.containsKey(predVN)) {
                        IRIdentifier phidID = indexIDMap.get(idx); // ID post-phi
                        
                        if(LOG.isLoggable(Level.FINEST)) {
                            LOG.finest("Hoisting " + phidID + " to " + predID + " as " + predVN);
                        }
                        
                        // Expression is anticipated, nonsimple, present in at least one other predecessor,
                        // and not present here.
                        // Put it here
                        if(predExpr instanceof GVNExpression exp) {
                            // New ID as destination
                            IRIdentifier dest = func.getFUID(phidID.getName());
                            
                            // Make instruction
                            IRLinearInstruction li = switch(exp.op()) {
                                case CALLR, CALLN -> {
                                    // Build arg map
                                    IRValue target = predLeaders.get(exp.argValues().get(0));
                                    IRArgumentMapping argMap = new IRArgumentMapping(); // an ordering & names could be gotten if present, but it isn't /necessary/
                                    
                                    for(int i = 1; i < exp.argValues().size(); i++) {
                                        argMap.addMapping(new IRIdentifier("arg" + (i - 1), IRIdentifierClass.LOCAL), predLeaders.get(exp.argValues().get(i)));
                                    }
                                    
                                    // And make instruction
                                    yield new IRLinearInstruction(exp.op(), dest, exp.type(), target, argMap, predBB, predBB.getSourceLineNumber());
                                }
                                
                                case SELECT -> {
                                    // Get args
                                    IRValue leftCompare = predLeaders.get(exp.argValues().get(0)),
                                            rightCompare = predLeaders.get(exp.argValues().get(1)),
                                            leftSource = predLeaders.get(exp.argValues().get(2)),
                                            rightSource = predLeaders.get(exp.argValues().get(3));
                                    
                                    // & make instruction
                                    yield new IRLinearInstruction(exp.op(), dest, exp.type(), leftSource, rightSource, exp.cond(), leftCompare, rightCompare, predBB, predBB.getSourceLineNumber());
                                }
                                
                                // normal instructions
                                default -> switch(exp.op().getSourceCount()) {
                                    case 1  -> new IRLinearInstruction(exp.op(), dest, exp.type(), predLeaders.get(exp.argValues().get(0)), predBB, predBB.getSourceLineNumber());
                                    case 2  -> new IRLinearInstruction(exp.op(), dest, exp.type(), predLeaders.get(exp.argValues().get(0)), predLeaders.get(exp.argValues().get(1)), predBB, predBB.getSourceLineNumber());
                                    default -> throw new IllegalStateException("Invalid source count " + exp.op().getSourceCount());
                                };
                            };
                            
                            if(LOG.isLoggable(Level.FINEST)) {
                                LOG.finest("Added instruction " + dest + " = " + li.getOp() + " to " + predID);
                            }
                            
                            // Add to BB
                            predBB.addInstruction(li);
                            typeMap.put(dest, exp.type());
                            
                            // Add to sets
                            valueTable.addInstruction(li, new ArrayList<>(), new HashSet<>(), typeMap);
                            predLeaders.put(predVN, dest);
                            predNew.add(predVN);
                            changed = true;
                        } else {
                            // This should not happen
                            throw new IllegalStateException("Not an expression: " + predExpr);
                        }
                    }
                    
                    // Add to argument mapping
                    predMapping.addMapping(indexIDMap.get(idx), predLeaders.get(predVN));
                }
            }
            
            // Put bb args into leader set & value table
            // This happening here rather than when they're created is based on the code being
            // referenced, but I'm pretty sure it's necessary so that a block with itself as a
            // predecessor works correctly.
            for(int idx : indicesToHoist) {
                // Get hoisted info
                Pair<Integer, GVNElement> pair = bbAntic.get(idx);
                int vn = pair.a;
                IRIdentifier id = indexIDMap.get(idx);
                
                // Put in leader set & value table
                int newVN = valueTable.addBBArgument(id, bbID, bb.getPredecessorBlocks(), func, new HashMap<>());
                bbLeaders.put(vn, id);
                bbLeaders.put(newVN, id);
                bbNew.add(vn);
                bbNew.add(newVN);
            }
        }
        
        // recurse on dominated nodes
        for(TreeNode<IRIdentifier> domChild : domTreeNode.getChildren()) {
            // Update child leaders & new sets 
            IRIdentifier domID = domChild.getElement();
            
            Map<Integer, IRValue> childLeaders = leaderSets.get(domID);
            
            for(int newVN : bbNew) {
                childLeaders.put(newVN, bbLeaders.get(newVN));
            }
            
            MapUtil.getOrCreateSet(newExprs, domID).addAll(bbNew);
            
            // Then recurse on child
            changed |= insertRecursive(domChild, newExprs, leaderSets, antileaderSets, phiGenSets, valueTable, func, typeMap);
        }
        
        return changed;
    }
    
    /**
     * Perform the BuildSets phase of the algorithm
     * This phase populates the leader set, antileader set, and others for each BB
     * @param phiGenSets
     * @param leaderSets
     * @param valueTable
     * @param func
     * @param functionPurityMap
     * @param dominatorTreeRoot
     * @param postdominatorTreeRoot
     * @param typeMap
     */
    private void buildSets(Map<IRIdentifier, Map<Integer, IRIdentifier>> phiGenSets, Map<IRIdentifier, Map<Integer, IRValue>> leaderSets, Map<IRIdentifier, List<Pair<Integer, GVNElement>>> antileaderSets, GVNValueTable valueTable, IRFunction func, Map<IRIdentifier, Boolean> functionPurityMap, TreeNode<IRIdentifier> dominatorTreeRoot, TreeNode<IRIdentifier> postdominatorTreeRoot, Map<IRIdentifier, IRType> typeMap) {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.finest("Running BuildSets for " + func.getID());
        }
        
        // Prepare phase 1
        Map<IRIdentifier, List<Pair<Integer, GVNElement>>> expGenSets = new HashMap<>(); 
        Map<IRIdentifier, Set<IRIdentifier>> tmpGenSets = new HashMap<>();
        
        // Initialize leaders for entry with function args
        IRIdentifier domRootID = dominatorTreeRoot.getElement();
        Map<Integer, IRValue> initialLeaders = new HashMap<>();
        
        for(IRIdentifier argID : func.getArguments().getNameList()) {
            initialLeaders.put(valueTable.addIRValue(argID, typeMap.get(argID)), argID);
        }
        
        leaderSets.put(domRootID, initialLeaders);
        
        // Do phase 1
        buildSetsPhase1(dominatorTreeRoot, expGenSets, phiGenSets, tmpGenSets, leaderSets, valueTable, func, functionPurityMap, typeMap);
        
        // Phase 2
        Map<IRIdentifier, List<Pair<Integer, GVNElement>>> anticOutSets = new HashMap<>();
        
        for(IRBasicBlock bb : func.getBasicBlockList()) {
            antileaderSets.put(bb.getID(), new LinkedList<>());
            anticOutSets.put(bb.getID(), new LinkedList<>());
        }
        
        boolean changed = true;
        int iters = 0;
        
        while(changed) {
            changed = false;
            iters++;
            
            // The root of the postdominator tree is an identifier without a real block
            for(TreeNode<IRIdentifier> postdomChild : postdominatorTreeRoot.getChildren()) {
                changed |= buildSetsPhase2(postdomChild, antileaderSets, anticOutSets, expGenSets, phiGenSets, tmpGenSets, valueTable, func);
            }
        }
        
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.finest("BuildSets phase 2 took " + iters + " iterations");
            for(IRBasicBlock bb : func.getBasicBlockList()) {
                IRIdentifier id = bb.getID();
                
                LOG.finest("BuildSets results for " + id);
                LOG.finest("PHI_GEN: " + phiGenSets.get(id));
                LOG.finest("EXP_GEN: " + expGenSets.get(id));
                LOG.finest("TMP_GEN: " + tmpGenSets.get(id));
                LOG.finest("ANTIC_IN: " + antileaderSets.get(id));
                LOG.finest("ANTIC_OUT: " + anticOutSets.get(id));
                LOG.finest("Leaders: " + leaderSets.get(id));
            }
        }
    }
    
    /**
     * Performs phase 2 of BuildSets
     * Phase 2 performs top-down traversals of the postdominator tree with the goal of computing the final sets and iterates until convergence
     * @param postdomTreeNode
     * @param anticInSets
     * @param anticOutSets
     * @param expGenSets
     * @param phiGenSets
     * @param tmpGenSets
     * @param valueTable
     * @param func
     * @return true if changed
     */
    private boolean buildSetsPhase2(TreeNode<IRIdentifier> postdomTreeNode, Map<IRIdentifier, List<Pair<Integer, GVNElement>>> anticInSets, Map<IRIdentifier, List<Pair<Integer, GVNElement>>> anticOutSets, Map<IRIdentifier, List<Pair<Integer, GVNElement>>> expGenSets, Map<IRIdentifier, Map<Integer, IRIdentifier>> phiGenSets, Map<IRIdentifier, Set<IRIdentifier>> tmpGenSets, GVNValueTable valueTable, IRFunction func) {
        boolean changed = false;
        IRIdentifier bbID = postdomTreeNode.getElement();
        IRBasicBlock bb = func.getBasicBlock(bbID);
        
        if(LOG.isLoggable(Level.FINEST)) {
            //LOG.finest("Running BuildSets Phase 2 for " + bbID);
        }
        
        // Determine ANTIC_OUT
        List<Pair<Integer, GVNElement>> newAnticOut = new LinkedList<>();
        
        // phi translate, no translate, or no ANTIC_OUT?
        if(bb.getSuccessorBlocks().size() > 1) {
            // no phi translate
            // Perfom a value-wise union of successor ANTIC_INs
            // Take first succesor as a base
            List<IRIdentifier> succIDs = bb.getSuccessorBlocks();
            List<Pair<Integer, GVNElement>> potentialOut = anticInSets.get(succIDs.get(0));
            
            // Collect other ANTIC_IN sets' value numbers
            List<Set<Integer>> otherAnticInVNs = new ArrayList<>(succIDs.size());
            
            for(int i = 1; i < succIDs.size(); i++) {
                Set<Integer> vns = new HashSet<>();
                
                for(Pair<Integer, GVNElement> pair : anticInSets.get(succIDs.get(i))) {
                    vns.add(pair.a);
                }
                
                otherAnticInVNs.add(vns);
            }
            
            // For each potential member of ANTIC_OUT
            potentials:
            for(Pair<Integer, GVNElement> potential : potentialOut) {
                int vn = potential.a;
                
                // For each other successor
                for(Set<Integer> anticInVNs : otherAnticInVNs) {
                    if(!anticInVNs.contains(vn)) {
                        continue potentials;
                    }
                }
                
                // If we reach here, all successors contain potential's vn
                newAnticOut.add(potential);
            }
        } else if(bb.getSuccessorBlocks().size() == 1) {
            // yes phi translate
            IRIdentifier succID = bb.getSuccessorBlocks().get(0);
            newAnticOut = phiTranslate(bbID, succID, phiGenSets.get(succID), anticInSets.get(succID), valueTable, func);
        } // ANTIC_OUT is empty otherwise
        
        // Did anything change
        List<Pair<Integer, GVNElement>> anticOut = anticOutSets.get(bbID);
        
        if(!newAnticOut.equals(anticOut)) {
            changed = true;
            anticOut = newAnticOut;
        }
        
        anticOutSets.put(bbID, newAnticOut);
        
        // Determine ANTIC_IN
        // Determine cleaned gen set
        Set<Integer> killed = new HashSet<>();
        List<Pair<Integer, GVNElement>> cleanedGens = new LinkedList<>();
        Set<IRIdentifier> tmpGen = tmpGenSets.get(bbID);
        
        // I get an error when I try to put this inline. alright.
        Iterable<Pair<Integer, GVNElement>> iter = Stream.of(expGenSets.get(bbID), anticOut).flatMap(s -> s.stream())::iterator;
        
        pairs:
        for(Pair<Integer, GVNElement> pair : iter) {
            int vn = pair.a;
            GVNElement elem = pair.b;
            
            // Does this depend on a killed value or is it one?
            switch(elem) {
                case GVNExpression expr:
                    // Check arguments
                    for(int argVN : expr.argValues()) {
                        if(killed.contains(argVN)) {
                            // Value is killed
                            killed.add(vn);
                            continue pairs;
                        }
                    }
                    break;
                
                case GVNPhi phi:
                    // Check mapped values
                    for(Pair<Integer, Integer> mapped : phi.mappedValueNumbers().values()) {
                        if(killed.contains(mapped.a) || killed.contains(mapped.b)) {
                            // Value is killed
                            killed.add(vn);
                            continue pairs;
                        }
                    }
                    break;
                    
                case GVNValue val:
                    // Check TMP_GEN
                    if(tmpGen.contains(val.val())) {
                        killed.add(vn);
                        continue pairs;
                    }
                
                default:
                    // no
            }
            
            // Not killed
            cleanedGens.add(pair);
        }
        
        // Perform value-wise union
        Set<Integer> added = new HashSet<>();
        List<Pair<Integer, GVNElement>> newAnticIn = new LinkedList<>();
        
        for(Pair<Integer, GVNElement> pair : cleanedGens) {
            // add if value number not present
            if(!added.contains(pair.a)) {
                added.add(pair.a);
                newAnticIn.add(pair);
            }
        }
        
        // Did anything change
        if(!newAnticIn.equals(anticInSets.get(bbID))) {
            changed = true;
        }
        
        anticInSets.put(bbID, newAnticIn);
        
        if(LOG.isLoggable(Level.FINEST)) {
            /*
            LOG.finest("ANTIC_IN: " + newAnticIn);
            LOG.finest("ANTIC_OUT: " + newAnticOut);
            LOG.finest("changed: " + changed);
            */
        }
        
        // Run on predominated BBs
        for(TreeNode<IRIdentifier> postdomChild : postdomTreeNode.getChildren()) {
            changed |= buildSetsPhase2(postdomChild, anticInSets, anticOutSets, expGenSets, phiGenSets, tmpGenSets, valueTable, func);
        }
        
        return changed;
    }
    
    /**
     * Given a block pred with single successor succ, construct ANTIC_OUT for pred by using ANTIC_IN of succ and for each ID defined by a phi, use the value mapped to that ID by pred
     * @param predID
     * @param succID
     * @param succPhiGen
     * @param succAnticIn
     * @param valueTable
     * @param func
     * @return
     */
    private List<Pair<Integer, GVNElement>> phiTranslate(IRIdentifier predID, IRIdentifier succID, Map<Integer, IRIdentifier> succPhiGen, List<Pair<Integer, GVNElement>> succAnticIn, GVNValueTable valueTable, IRFunction func) {
        IRBasicBlock predBB = func.getBasicBlock(predID);
        
        List<Pair<Integer, GVNElement>> predAnticOut = new LinkedList<>();
        Map<Integer, Integer> translationMap = new HashMap<>();
        
        // For each member of succ ANTIC_IN
        for(Pair<Integer, GVNElement> pair : succAnticIn) {
            // Is the value defined by a phi
            int succVN = pair.a;
            GVNElement element = pair.b;
            
            if(succPhiGen.containsKey(succVN)) {
                // Value is defined by a phi
                // Get name of arg being mapped to
                IRIdentifier argName = succPhiGen.get(succVN);
                
                // Figure out which mapping we come from
                IRBranchInstruction predExit = predBB.getExitInstruction();
                boolean isTrueSucc = succID.equals(predExit.getTrueTargetBlock());
                IRArgumentMapping predMapping = isTrueSucc ? predExit.getTrueArgumentMapping() : predExit.getFalseArgumentMapping();
                
                // Get GVNElement & value number for mapped IRValue
                GVNElement translatedElement = new GVNValue(element.type(), predMapping.getMapping(argName));
                int translatedVN = valueTable.addElement(translatedElement);
                
                // And put in ANTIC_OUT
                predAnticOut.add(new Pair<>(translatedVN, translatedElement));
                translationMap.put(succVN, translatedVN);
            } else {
                // Value is not defined by a phi (and no more will be as ANTIC_IN is iterated in topological sort order)
                // Update translated value numbers when used as arguments
                GVNElement translatedElement = element;
                int translatedVN = succVN;
                
                if(element instanceof GVNExpression exp) {
                    // Expression to translate
                    List<Integer> translatedArgs = new ArrayList<>();
                    
                    // Translate each arg
                    for(int argNum : exp.argValues()) {
                        translatedArgs.add(translationMap.getOrDefault(argNum, argNum));
                    }
                    
                    // And collect into new expression
                    translatedElement = new GVNExpression(exp.type(), exp.op(), exp.cond(), translatedArgs);
                    translatedVN = valueTable.addElement(translatedElement);
                }
                
                // Put in pred ANTIC_OUT
                predAnticOut.add(new Pair<>(translatedVN, translatedElement));
                translationMap.put(succVN, translatedVN);
            }
        }
        
        return predAnticOut;
    }
    
    /**
     * Perform phase 1 of BuildSets.
     * Phase 1 is a top-down traversal of the dominator tree where the various sets are first built for each block
     * @param domTreeNode
     * @param expGenSets
     * @param phiGenSets phi_translate uses the value number so both it and the id matter for PHI_GEN
     * @param tmpGenSets Only the presence of an id matters for TMP_GEN
     * @param leaderSets
     * @param valueTable
     * @param func
     * @param functionPurityMap
     * @param typeMap
     */
    private void buildSetsPhase1(TreeNode<IRIdentifier> domTreeNode, Map<IRIdentifier, List<Pair<Integer, GVNElement>>> expGenSets, Map<IRIdentifier, Map<Integer, IRIdentifier>> phiGenSets, Map<IRIdentifier, Set<IRIdentifier>> tmpGenSets, Map<IRIdentifier, Map<Integer, IRValue>> leaderSets, GVNValueTable valueTable, IRFunction func, Map<IRIdentifier, Boolean> functionPurityMap, Map<IRIdentifier, IRType> typeMap) {
        IRIdentifier bbID = domTreeNode.getElement();
        IRBasicBlock bb = func.getBasicBlock(bbID);
        
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.finest("Running BuildSets Phase 1 for " + bbID);
        }
        
        // Sets to populate
        List<Pair<Integer, GVNElement>> expGen = MapUtil.getOrCreateList(expGenSets, bbID);
        Map<Integer, IRIdentifier> phiGen = MapUtil.getOrCreateMap(phiGenSets, bbID);
        Set<IRIdentifier> tmpGen = MapUtil.getOrCreateSet(tmpGenSets, bbID);
        Map<Integer, IRValue> leaders = MapUtil.getOrCreateMap(leaderSets, bbID);
        Set<Integer> addedExpressions = new HashSet<>();
        
        // Handle arguments
        for(IRIdentifier argID : bb.getArgumentList().getNameList()) {
            int vn = valueTable.addBBArgument(argID, bbID, bb.getPredecessorBlocks(), func, phiGen);
            leaders.put(vn, argID);
        }
        
        // Handle LIs
        for(IRLinearInstruction li : bb.getInstructions()) {
            if((li.getOp() == IRLinearOperation.CALLR && functionPurityMap.getOrDefault(li.getCallTarget(), false)) || NUMBERABLE_OPS.contains(li.getOp())) {
                // Numberable expression
                int vn = valueTable.addInstruction(li, expGen, addedExpressions, typeMap);
                leaders.putIfAbsent(vn, li.getDestinationID());
            } else if(li.hasDestination()) {
                // Not numberable
                tmpGen.add(li.getDestinationID());
                
                // But the temporary is
                leaders.put(valueTable.addIRValue(li.getDestinationID(), li.getDestinationType()), li.getDestinationID());
            }
        }
        
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.finest("PHI_GEN: " + phiGen);
            LOG.finest("EXP_GEN: " + expGen);
            LOG.finest("TMP_GEN: " + tmpGen);
            LOG.finest("Leaders: " + leaders);
        }
        
        // Run on dominated BBs
        for(TreeNode<IRIdentifier> domChild : domTreeNode.getChildren()) {
            leaderSets.put(domChild.getElement(), new HashMap<>(leaders));
            buildSetsPhase1(domChild, expGenSets, phiGenSets, tmpGenSets, leaderSets, valueTable, func, functionPurityMap, typeMap);
        }
    }
    
    /**
     * Generate the initial state of the value table
     * @param func
     * @param reversePostorderList
     * @param functionPurityMap
     * @param typeMap
     * @return
     */
    private GVNValueTable generateInitialTable(IRFunction func, List<IRIdentifier> reversePostorderList, Map<IRIdentifier, Boolean> functionPurityMap, Map<IRIdentifier, IRType> typeMap) {
        LOG.finest("--Initializing value table--");
        
        GVNValueTable valueTable = new GVNValueTable();
        
        List<Pair<Integer, GVNElement>> dummyExpGen = new ArrayList<>();
        Set<Integer> dummyAdded = new HashSet<>();
        Map<Integer, IRIdentifier> dummyPhiGen = new HashMap<>();
        
        // Add arguments
        for(IRIdentifier argID : func.getArguments().getNameList()) {
            int num = valueTable.addIRValue(argID, typeMap.get(argID));
            
            if(LOG.isLoggable(Level.FINEST)) { 
                LOG.finest("Got " + num + " for function arg " + argID);
            }
        }
        
        // Add contents
        // reverse postorder -> values in expressions already numbered when processed
        for(IRIdentifier bbID : reversePostorderList) {
            IRBasicBlock bb = func.getBasicBlock(bbID);
            
            if(LOG.isLoggable(Level.FINEST)) {
                LOG.finest("Processing basic block " + bbID);
            }
            
            // Handle arguments
            for(IRIdentifier argID : bb.getArgumentList().getNameList()) {
                // Add to value table
                int num = valueTable.addBBArgument(argID, bbID, bb.getPredecessorBlocks(), func, dummyPhiGen);
                
                if(LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("Got " + num + " for bb arg " + argID);
                }
            }
            
            // Handle instructions
            for(IRLinearInstruction li : bb.getInstructions()) {
                int num = -1;
                
                if(li.getOp() == IRLinearOperation.CALLR) {
                    // Special case to check purity
                    IRValue target = li.getCallTarget();
                    
                    if(functionPurityMap.containsKey(target)) {
                        if(functionPurityMap.get(target)) {
                            // known pure
                            num = valueTable.addInstruction(li, dummyExpGen, dummyAdded, typeMap);
                        }
                    } else if(func.getModule().getInternalFunctions().containsKey(target) &&
                              IRUtil.isFunctionPure(func.getModule().getInternalFunctions().get(target), false, functionPurityMap)) {
                        // newly pure
                        num = valueTable.addInstruction(li, dummyExpGen, dummyAdded, typeMap);
                    }
                    
                    if(num == -1) {
                        // not pure
                        num = valueTable.addIRValue(li.getDestinationID(), li.getDestinationType());
                    }
                } else if(NUMBERABLE_OPS.contains(li.getOp())) {
                    // Numberable expression
                    num = valueTable.addInstruction(li, dummyExpGen, dummyAdded, typeMap);
                }
                
                if(num != -1 && LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("Got " + num + " for " + li.getDestinationID());
                }
            }
        }
        
        return valueTable;
    }
    
}
