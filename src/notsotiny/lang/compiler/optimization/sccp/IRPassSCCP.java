package notsotiny.lang.compiler.optimization.sccp;

import notsotiny.lang.compiler.optimization.sccp.SCCPLatticeElement.Type;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import notsotiny.lang.compiler.optimization.IROptimizationLevel;
import notsotiny.lang.compiler.optimization.IROptimizationPass;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRBranchInstruction;
import notsotiny.lang.ir.parts.IRBranchOperation;
import notsotiny.lang.ir.parts.IRCondition;
import notsotiny.lang.ir.parts.IRConstant;
import notsotiny.lang.ir.parts.IRDefinition;
import notsotiny.lang.ir.parts.IRDefinitionType;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRGlobal;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.parts.IRLinearOperation;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lang.ir.util.IRUtil;
import notsotiny.lib.data.Triple;

/**
 * Performs Sparse Conditional Constant Propagation
 * https://www.cs.wustl.edu/~cytron/531Pages/f11/Resources/Papers/cprop.pdf
 */
public class IRPassSCCP implements IROptimizationPass {

    private static Logger LOG = Logger.getLogger(IRPassSCCP.class.getName());
    
    @Override
    public IROptimizationLevel level() {
        return IROptimizationLevel.ONE;
    }

    @Override
    public IRModule optimize(IRModule module) {
        LOG.finer("Performing SCCP on " + module.getName());
        
        // In each function
        for(IRFunction func : module.getInternalFunctions().values()) {
            // Throw it in the SCCP
            runSCCP(func);
        }
        
        return module;
    }
    
    /**
     * Runs SCCP on a function
     * @param func
     */
    private void runSCCP(IRFunction func) {
        LOG.finest("Running SCCP on " + func.getID());
        /**
         * how SCCP works
         *  - lattice tracks whether a value is an unknown constant, a known constant, or variable
         *      initially unknown constant TOP
         *  - a set of executable flow graph edges is tracked -> triple<predbb, destbb, condition>  
         *  - FlowWorkList tracks flow graph edges to be handled
         *      Starts with <null, entry, true>
         *  - SSAWorkList tracks computations to be handled -> definitions to handle.
         *      Starts empty
         *  - Items from either worklist get processed, order doesn't matter
         *  - To handle a flow edge:
         *      - if in executable set, do nothing -> next work item
         *      - put edge in executable set
         *      - visit BB args in dest
         *      - if this is the first eval of dest (can be tracked separately or by counting executable edges)
         *          - visit each instruction
         *      - if there is only one successor, add it to the FlowWorkList
         *  - To handle an SSA edge (definition)
         *      - if its a BB arg, visit as bb arg
         *      - check if the parent bb is executable. if so, visit as instruction
         *  
         *  - To visit as a bb arg
         *      - for each predecessor of parent
         *          - if <predecessor, parent> is executable, update val
         *          - otherwise dont
         *      - if val changed, add uses of arg to SSAWorkList
         *  
         *  - To visit as an instruction
         *      - evaluate val from arg lattice vals
         *      - if val changed
         *          - if this defines a value, add uses to SSAWorkList
         *          - if this defines a branch
         *              - for val BOTTOM, add both true and false edges to FlowWorkList
         *              - for val VALUE, add corresponding edge to FlowWorkList
         */
        
        // 'SSA graph'
        List<IRDefinition> defList = IRUtil.getDefinitionList(func);
        Map<IRIdentifier, IRDefinition> defMap = IRUtil.getDefinitionMap(defList);
        Map<IRIdentifier, List<IRDefinition>> useMap = IRUtil.getUseMap(func, defList);
        
        // Lattice
        Map<IRIdentifier, SCCPLatticeElement> lattice = new HashMap<>();
        
        // Executable edges & BBs
        Set<IRIdentifier> executableBBs = new HashSet<>();
        Set<Triple<IRIdentifier, IRIdentifier, Boolean>> executableFlowEdges = new HashSet<>();
        
        // Worklists
        Deque<Triple<IRIdentifier, IRIdentifier, Boolean>> flowWorklist = new ArrayDeque<>();
        Deque<IRDefinition> ssaWorklist = new ArrayDeque<>();
        
        // Initialize lattice with function arguments = BOTTOM
        for(IRIdentifier argID : func.getArguments().getNameList()) {
            lattice.put(argID, new SCCPLatticeElement(Type.BOTTOM));
        }
        
        // Initial work
        flowWorklist.add(new Triple<>(new IRIdentifier("", IRIdentifierClass.BLOCK), func.getEntryBlock().getID(), true));
        
        // Do work
        while(flowWorklist.size() != 0 || ssaWorklist.size() != 0) {
            // There's work to do. What kind?
            if(flowWorklist.size() != 0) {
                // There's a flow edge.
                /*
                 *  - if in executable set, do nothing -> next work item
                 *  - put edge in executable set
                 *  - visit BB args in dest
                 *  - if this is the first eval of dest (can be tracked separately or by counting executable edges)
                 *      - visit each instruction
                 *  - if there is only one successor, add it to the FlowWorkList
                 */
                Triple<IRIdentifier, IRIdentifier, Boolean> flowEdge = flowWorklist.poll();
                //LOG.finest("Inspecting flow edge " + flowEdge);
                if(!executableFlowEdges.contains(flowEdge)) {
                    // If not yet executable, make it executable & visit destination
                    executableFlowEdges.add(flowEdge);
                    IRIdentifier destID = flowEdge.b;
                    
                    // Visit BB args
                    IRBasicBlock destBB = func.getBasicBlock(destID);
                    for(IRIdentifier argID : destBB.getArgumentList().getNameList()) {
                        visit(defMap.get(argID), flowWorklist, ssaWorklist, lattice, executableFlowEdges, useMap, func);
                    }
                    
                    if(!executableBBs.contains(destID)) {
                        // If this is the first eval of dest, visit each instruction
                        executableBBs.add(destID);
                        
                        for(IRLinearInstruction li : destBB.getInstructions()) {
                            if(li.hasDestination()) {
                                visit(defMap.get(li.getDestinationID()), flowWorklist, ssaWorklist, lattice, executableFlowEdges, useMap, func);
                            }
                        }
                        
                        if(destBB.getExitInstruction().getOp() == IRBranchOperation.JCC) {
                            // If conditional, visit it
                            visit(defMap.get(destID), flowWorklist, ssaWorklist, lattice, executableFlowEdges, useMap, func);
                        } else if(destBB.getExitInstruction().getOp() == IRBranchOperation.JMP) {
                            // If unconditional, add successor edge
                            flowWorklist.add(new Triple<>(destID, destBB.getExitInstruction().getTrueTargetBlock(), true));
                        }
                    }
                }
            } else {
                // There's a SSA edge
                /*
                 *  - if its a BB arg, visit as bb arg
                 *  - check if the parent bb is executable. if so, visit as instruction
                 */
                IRDefinition def = ssaWorklist.poll();
                //LOG.finest("Inspecing definition " + def);
                if(def.getType() == IRDefinitionType.BBARG) {
                    // Visit without checking as visiting a BBArg does its own checking
                    visit(def, flowWorklist, ssaWorklist, lattice, executableFlowEdges, useMap, func);
                } else if(def.getType() == IRDefinitionType.BRANCH) {
                    // Check that the def is executable, then visit
                    if(executableBBs.contains(def.getBranchInstruction().getBasicBlock().getID())) {
                        visit(def, flowWorklist, ssaWorklist, lattice, executableFlowEdges, useMap, func);
                    }
                } else if(def.getType() == IRDefinitionType.LINEAR) {
                    // Check that the def is executable, then visit
                    if(executableBBs.contains(def.getLinearInstruction().getBasicBlock().getID())) {
                        visit(def, flowWorklist, ssaWorklist, lattice, executableFlowEdges, useMap, func);
                    }
                } else {
                    // invalid
                    throw new IllegalStateException("invalid definition " + def);
                }
            }
        }
        
        // Use the results of SCCP to optimize
        // Substitute constants
        for(Entry<IRIdentifier, SCCPLatticeElement> latticeEntry : lattice.entrySet()) {
            if(latticeEntry.getValue().getType() != Type.BOTTOM && latticeEntry.getKey().getIDClass() == IRIdentifierClass.LOCAL) {
                if(LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("Replacing " + latticeEntry.getKey() + " with " + latticeEntry.getValue());
                }
                
                IRUtil.replaceInFunction(func, latticeEntry.getKey(), latticeEntry.getValue().getValue());
            }
        }
        
        // Eliminate not-taken JCC edges
        for(IRBasicBlock bb : func.getBasicBlockList()) {
            IRBranchInstruction bbExit = bb.getExitInstruction();
            
            if(bbExit.getOp() == IRBranchOperation.JCC) {
                IRIdentifier trueID = bbExit.getTrueTargetBlock();
                IRIdentifier falseID = bbExit.getFalseTargetBlock();
                Triple<IRIdentifier, IRIdentifier, Boolean> trueTriple = new Triple<>(bb.getID(), trueID, true);
                Triple<IRIdentifier, IRIdentifier, Boolean> falseTriple = new Triple<>(bb.getID(), falseID, false);
                boolean trueEdgeExecutable = executableFlowEdges.contains(trueTriple);
                boolean falseEdgeExecutable = executableFlowEdges.contains(falseTriple);
                boolean sameTarget = bbExit.getTrueTargetBlock().equals(bbExit.getFalseTargetBlock());
                
                // If neither edge executable, this will be handled in next step
                // If one edge executable, convert to JCC
                if(trueEdgeExecutable && !falseEdgeExecutable) {
                    if(LOG.isLoggable(Level.FINEST)) {
                        LOG.finest("Eliminating flow edge " + falseTriple);
                    }
                    
                    if(!sameTarget) {
                        // If the true and false targets are different, false target is no longer a successor
                        func.getBasicBlock(falseID).removePredecessor(bb.getID());
                    }
                    
                    IRBranchInstruction jmpInst = new IRBranchInstruction(IRBranchOperation.JMP, trueID, bbExit.getTrueArgumentMapping(), bb, bbExit.getSourceLineNumber());
                    bb.setExitInstruction(jmpInst);
                } else if(falseEdgeExecutable && !trueEdgeExecutable) {
                    if(LOG.isLoggable(Level.FINEST)) {
                        LOG.finest("Eliminating flow edge " + trueTriple);
                    }
                    
                    if(!sameTarget) {
                        // If the true and false targets are different, true target is no longer a successor
                        func.getBasicBlock(trueID).removePredecessor(bb.getID());
                    }
                    
                    IRBranchInstruction jmpInst = new IRBranchInstruction(IRBranchOperation.JMP, falseID, bbExit.getFalseArgumentMapping(), bb, bbExit.getSourceLineNumber());
                    bb.setExitInstruction(jmpInst);
                }
            }
        }
        
        // Remove dead BBs
        for(int i = 0; i < func.getBasicBlockList().size(); i++) {
            IRBasicBlock bb = func.getBasicBlockList().get(i);
            
            if(!executableBBs.contains(bb.getID())) {
                LOG.finest("Removing dead basic block " + bb.getID());
                
                // Remove from predecessors of successors
                IRBranchInstruction bbExit = bb.getExitInstruction();
                IRIdentifier trueSuccID = bbExit.getTrueTargetBlock();
                IRIdentifier falseSuccID = bbExit.getFalseTargetBlock();
                
                if(func.getBasicBlock(trueSuccID) != null) {
                    func.getBasicBlock(trueSuccID).removePredecessor(bb.getID());
                }
                
                if(func.getBasicBlock(falseSuccID) != null) {
                    func.getBasicBlock(falseSuccID).removePredecessor(bb.getID());
                }
                
                // Modify predecessor exit instructions
                for(IRIdentifier predID : bb.getPredecessorBlocks()) {
                    IRBasicBlock predBB = func.getBasicBlock(predID);
                    IRBranchInstruction predExit = predBB.getExitInstruction();
                    
                    // If we're one of two successors, convert to JMP.
                    // If we're all successors, no action (block will be eliminated later)
                    if(predExit.getOp() == IRBranchOperation.JCC) {
                        boolean isTrue = bb.getID().equals(predExit.getTrueTargetBlock());
                        boolean isFalse = bb.getID().equals(predExit.getFalseTargetBlock());
                        
                        if(isTrue && !isFalse) {
                            IRBranchInstruction jmpInst = new IRBranchInstruction(IRBranchOperation.JMP, predExit.getFalseTargetBlock(), predExit.getFalseArgumentMapping(), predBB, predExit.getSourceLineNumber());
                            predBB.setExitInstruction(jmpInst);
                        } else if(isFalse && !isTrue) {
                            IRBranchInstruction jmpInst = new IRBranchInstruction(IRBranchOperation.JMP, predExit.getTrueTargetBlock(), predExit.getTrueArgumentMapping(), predBB, predExit.getSourceLineNumber());
                            predBB.setExitInstruction(jmpInst);
                        }
                    }
                    
                }
                
                // Remove
                func.removeBasicBlock(bb.getID());
                i--;
            }
        }
        
        // TODO
    }
    
    /**
     * Gets an element from the lattice
     * @param lattice
     * @param id
     * @return
     */
    private SCCPLatticeElement getLatticeElement(IRValue val, Map<IRIdentifier, SCCPLatticeElement> lattice, IRFunction func) {
        if(val instanceof IRIdentifier id) {
            // Get element corresponding to ID
            if(lattice.containsKey(id)) {
                return lattice.get(id);
            } else {
                SCCPLatticeElement e;
                
                // Different kinds of IDs get initialized in different ways
                switch(id.getIDClass()) {
                    case GLOBAL:
                        // Global. If it's a constant, use its value. Otherwise, BOTTOM
                        IRGlobal g = func.getModule().getGlobals().get(id);
                        if(g != null && g.isConstant() && g.getContents().size() == 1) {
                            e = new SCCPLatticeElement((IRConstant) g.getContents().get(0));
                        } else {
                            e = new SCCPLatticeElement(Type.BOTTOM);
                        }
                        break;
                        
                    case LOCAL:
                    default:
                        // Local. Start as TOP
                        e = new SCCPLatticeElement(Type.TOP);
                }
                
                lattice.put(id, e);
                return e;
            }
        } else {
            // Construct element for value
            return new SCCPLatticeElement((IRConstant) val);
        }
    }
    
    /**
     * Lower a lattice element with an IRValue
     * @param e
     * @param value
     * @param lattice
     * @param func
     * @return
     */
    private boolean lower(SCCPLatticeElement e, IRValue value, Map<IRIdentifier, SCCPLatticeElement> lattice, IRFunction func) {
        if(value instanceof IRConstant c) {
            // This predecessor maps us to a constant
            return e.lower(c);
        } else {
            // This predecessor maps us to an ID
            return e.lower(getLatticeElement((IRIdentifier) value, lattice, func));
        }
    }
    
    /**
     * LOG the lowering of an element
     * @param id
     * @param e
     */
    private void logLowering(IRIdentifier id, SCCPLatticeElement e) {
        //LOG.finest("Lowered " + id + " to " + e);
    }
    
    /**
     * Visit a definition
     * @param def
     * @param flowWorklist
     * @param ssaWorklist
     * @param lattice
     * @param executableFlowEdges
     * @param useMap
     * @param func
     */
    private void visit(IRDefinition def, Deque<Triple<IRIdentifier, IRIdentifier, Boolean>> flowWorklist, Deque<IRDefinition> ssaWorklist, Map<IRIdentifier, SCCPLatticeElement> lattice, Set<Triple<IRIdentifier, IRIdentifier, Boolean>> executableFlowEdges, Map<IRIdentifier, List<IRDefinition>> useMap, IRFunction func) {
        //LOG.finest("Visiting " + def);
        // TODO - we can get more information if we track nonzero-ness of BOTTOM values. Conditional branches based on == 0 or != 0 are common. 
        
        // What are we dealing with
        if(def.getType() == IRDefinitionType.BBARG) {
            /*
             *  - for each predecessor of parent
             *      - if <predecessor, parent> is executable, update val
             *      - otherwise dont
             *  - if val changed, add uses of arg to SSAWorkList
             */
            IRBasicBlock parentBB = def.getBB();
            IRIdentifier parentID = parentBB.getID();
            
            // element being defined
            SCCPLatticeElement e = getLatticeElement(def.getID(), lattice, func);
            boolean changed = false;
            
            // is it possible for this to be lowered
            if(e.getType() == Type.BOTTOM) {
                // no
                return;
            }
            
            for(IRIdentifier predID : parentBB.getPredecessorBlocks()) {
                //LOG.finest(predID.toString());
                IRBranchInstruction predBranch = func.getBasicBlock(predID).getExitInstruction();
                
                // Check true edge
                if(executableFlowEdges.contains(new Triple<>(predID, parentID, true))) {
                    changed |= lower(e, predBranch.getTrueArgumentMapping().getMapping(def.getID()), lattice, func);
                }
                
                // Check false edge
                if(executableFlowEdges.contains(new Triple<>(predID, parentID, false))) {
                    changed |= lower(e, predBranch.getFalseArgumentMapping().getMapping(def.getID()), lattice, func);
                }
            }
            
            // If val changed, add uses to SSAWorkList
            if(changed) {
                logLowering(def.getID(), e);
                List<IRDefinition> uses = useMap.get(def.getID());
                
                if(uses != null) {
                    ssaWorklist.addAll(uses);
                }
            }
        } else if(def.getType() == IRDefinitionType.BRANCH) {
            /*
             *  - evaluate conditional val
             *  - if val changed
             *      - for val BOTTOM, add both true and false edges to FlowWorkList
             *      - for val VALUE, add corresponding edge to FlowWorkList
             */
            IRBranchInstruction inst = def.getBranchInstruction();
            SCCPLatticeElement e = getLatticeElement(def.getID(), lattice, func);
            boolean changed = false;
            
            if(e.getType() == Type.BOTTOM) {
                // Not possible to change
                return;
            }
            
            SCCPLatticeElement left = getLatticeElement(inst.getCompareLeft(), lattice, func);
            SCCPLatticeElement right = getLatticeElement(inst.getCompareRight(), lattice, func);
            
            ensureDefined(left, inst.getCompareLeft(), "as left comparison value");
            ensureDefined(right, inst.getCompareRight(), "as right comparison value");
            
            if(inst.getCondition() == IRCondition.NONE) {
                // unconditional -> always left
                changed |= e.lower(left);
            } else if(left.getType() == Type.BOTTOM || right.getType() == Type.BOTTOM) {
                // conditional & either bottom -> bottom
                changed |= e.lower();
            } else {
                // conditional & neither bottom -> value
                if(inst.getCondition().conditionTrue(left.getValue(), right.getValue())) {
                    // Lower as TRUE
                    changed |= e.lower(new IRConstant(1, IRType.NONE));
                } else {
                    // Lower as FALSE
                    changed |= e.lower(new IRConstant(0, IRType.NONE));
                }
            }
            
            if(changed) {
                if(e.getType() == Type.BOTTOM) {
                    // Both
                    flowWorklist.add(new Triple<>(inst.getBasicBlock().getID(), inst.getTrueTargetBlock(), true));
                    flowWorklist.add(new Triple<>(inst.getBasicBlock().getID(), inst.getFalseTargetBlock(), false));
                } else if(e.getValue().getValue() == 1) {
                    // TRUE
                    flowWorklist.add(new Triple<>(inst.getBasicBlock().getID(), inst.getTrueTargetBlock(), true));
                } else {
                    // FALSE
                    flowWorklist.add(new Triple<>(inst.getBasicBlock().getID(), inst.getFalseTargetBlock(), false));
                }
            }
        } else if(def.getType() == IRDefinitionType.LINEAR) {
            /*
             *  - evaluate val from arg lattice vals
             *  - if val changed, add uses to SSAWorkList
             */
            IRLinearInstruction inst = def.getLinearInstruction();
            SCCPLatticeElement e = getLatticeElement(def.getID(), lattice, func);
            boolean changed = false;
            
            // Is it possible to lower
            if(e.getType() == Type.BOTTOM) {
                // no
                return;
            }
            
            // All defining LIs have a left arg
            SCCPLatticeElement left = getLatticeElement(inst.getLeftSourceValue(), lattice, func);
            ensureDefined(left, inst.getLeftSourceValue(), "as left value of " + inst.getOp());
            
            if(inst.getOp().getSourceCount() == 1) {
                // TODO - loading from a constant global is constant. Account for that
                if(left.getType() == Type.BOTTOM ||
                   inst.getOp() == IRLinearOperation.LOAD ||
                   inst.getOp() == IRLinearOperation.CALLR ||
                   inst.getOp() == IRLinearOperation.STACK) {
                    // Always BOTTOM
                    changed |= e.lower();
                } else {
                    // If we have a value, apply the operation
                    changed |= e.lower(inst.getOp().applyUnary(inst.getDestinationType(), left.getValue()));
                }
            } else if(inst.getOp().getSourceCount() == 2) {
                // 2-argument operations
                SCCPLatticeElement right = getLatticeElement(inst.getRightSourceValue(), lattice, func);
                ensureDefined(right, inst.getRightSourceValue(), "as right value of " + inst.getOp());
                
                if(((left.getType() == Type.VALUE && left.getValue().getValue() == 0) ||
                    (right.getType() == Type.VALUE && right.getValue().getValue() == 0)) &&
                   (inst.getOp() == IRLinearOperation.MULS || inst.getOp() == IRLinearOperation.MULU || inst.getOp() == IRLinearOperation.AND)) {
                    // Multiplication and AND by zero produce zero
                    changed |= e.lower(new IRConstant(0, inst.getDestinationType()));
                } else if(left.getType() == Type.BOTTOM || right.getType() == Type.BOTTOM) {
                    // Either BOTTOM -> BOTTOM
                    changed |= e.lower();
                } else {
                    // Neither BOTTOM -> apply
                    changed |= e.lower(inst.getOp().applyBinary(inst.getDestinationType(), left.getValue(), right.getValue()));
                }
            } else {
                // SELECT
                SCCPLatticeElement right = getLatticeElement(inst.getRightSourceValue(), lattice, func),
                                   compLeft = getLatticeElement(inst.getLeftComparisonValue(), lattice, func),
                                   compRight = getLatticeElement(inst.getRightComparisonValue(), lattice, func);
                
                ensureDefined(right, inst.getRightSourceValue(), "as right select value of " + inst.getOp());
                ensureDefined(compLeft, inst.getLeftComparisonValue(), "as left comparison value of " + inst.getOp());
                ensureDefined(compRight, inst.getRightComparisonValue(), "as right comparison value of " + inst.getOp());
                
                if(left.getType() == Type.BOTTOM && right.getType() == Type.BOTTOM) {
                    // If both left and right are BOTTOM, the comparison doesn't matter
                    changed |= e.lower();
                } else if(inst.getSelectCondition() == IRCondition.NONE) {
                    // If unconditional, the comparison doesn't matter
                    changed |= e.lower(left);
                } else if(left.getType() == Type.VALUE && right.getType() == Type.VALUE && left.getValue().equals(right.getValue())) {
                    // If left = right, comparison doesn't matter
                    changed |= e.lower(left);
                } else if(compLeft.getType() == Type.BOTTOM || compRight.getType() == Type.BOTTOM) {
                    // If either comparison value is BOTTOM, BOTTOM
                    changed |= e.lower();
                } else {
                    // One or both of left and right is VALUE.
                    // The condition is not unconditional
                    // Both condition values are VALUE
                    // -> use based on conditional
                    if(inst.getSelectCondition().conditionTrue(compLeft.getValue(), compRight.getValue())) {
                        changed |= e.lower(left);
                    } else {
                        changed |= e.lower(right);
                    }
                }
            }
            
            // If val changed, add uses to SSAWorkList
            if(changed) {
                logLowering(def.getID(), e);
                List<IRDefinition> uses = useMap.get(def.getID());
                
                if(uses != null) {
                    ssaWorklist.addAll(uses);
                }
            }
        } else {
            // Invalid
            throw new IllegalStateException("unreachable");
        }
    }
    
    /**
     * Make sure an element is defined when it is used
     * @param e
     * @param source
     */
    private void ensureDefined(SCCPLatticeElement e, IRValue source, String context) {
        if(e.getType() == Type.TOP) {
            // Left is undefined, which is invalid
            LOG.severe(source + " is undefined " + context);
            throw new IllegalStateException(source + " is undefined " + context);
        }
    }
    
}
