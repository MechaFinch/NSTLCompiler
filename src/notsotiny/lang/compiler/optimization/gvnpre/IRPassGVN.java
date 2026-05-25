package notsotiny.lang.compiler.optimization.gvnpre;

import java.util.ArrayList;
import java.util.Collections;
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
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lang.ir.util.IRUtil;
import notsotiny.lib.data.Pair;
import notsotiny.lib.data.TreeNode;
import notsotiny.lib.data.Triple;
import notsotiny.lib.util.MapUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import notsotiny.lang.ir.parts.IRArgumentMapping;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRBranchInstruction;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lib.data.Pair;
import notsotiny.lib.data.Triple;

/**
 * Global Value Numbering pass
 * Just GVN on its own
 */
public class IRPassGVN implements IROptimizationPass {

    private static Logger LOG = Logger.getLogger(IRPassGVN.class.getName());
    
    @Override
    public IROptimizationLevel level() {
        return IROptimizationLevel.ONE;
    }

    @Override
    public IRModule optimize(IRModule module) {
        LOG.finer("Performing GVN on " + module.getName());
        
        // In each function
        for(IRFunction func : module.getInternalFunctions().values()) {
            LOG.finest("Performing GVN on " + func.getID());
            
            // Get metadata
            List<IRIdentifier> reversePostorderList = IRUtil.getReversePostorderList(func);
            Map<IRIdentifier, Integer> reversePostorderMap = MapUtil.listToMap(reversePostorderList);
            Map<IRIdentifier, IRIdentifier> dominatorMap = IRUtil.getDominatorMap(func, reversePostorderList);
            Map<IRIdentifier, TreeNode<IRIdentifier>> dominatorTreeMap = MapUtil.mapToForest(dominatorMap);
            
            // Get function args in value table
            PGVNValueTable valueTable = new PGVNValueTable();
            
            for(IRIdentifier argID : func.getArguments().getNameList()) {
                int number = valueTable.addIRValue(argID).a;
                
                if(LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("Got number " + number + " for function arg " + argID);
                }
            }
            
            // Get GVNing
            doGVN(dominatorTreeMap.get(func.getEntryBlock().getID()), func, valueTable, new HashMap<>(), reversePostorderMap);
        }
        
        return module;
    }
    
    /**
     * Do GVN given a node in the dominator tree
     * @param domTreeNode
     * @param func
     * @param valueTable
     * @param functionPurityMap
     * @param reversePostorderIndexMap A map from BB ID to reverse postorder number
     */
    private void doGVN(TreeNode<IRIdentifier> domTreeNode, IRFunction func, PGVNValueTable valueTable, Map<IRIdentifier, Boolean> functionPurityMap, Map<IRIdentifier, Integer> reversePostorderIndexMap) {
        IRIdentifier bbID = domTreeNode.getElement();
        IRBasicBlock bb = func.getBasicBlock(bbID);
        
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.finest("Processing basic block " + bbID);
        }
        
        // Save a copy of the set of available values
        Set<Integer> valuesBefore = new HashSet<>(valueTable.getAvailableNumbers());
        
        // Handle arguments
        // Get a copy of the arg ID list so we can modify it
        List<IRIdentifier> allArgIDs = new ArrayList<>(bb.getArgumentList().getNameList()); 
        
        for(IRIdentifier argID : allArgIDs) {
            // Add argument to value table
            Pair<Integer, IRValue> pair = valueTable.addBBArgument(argID, bbID, bb.getPredecessorBlocks(), func);
            int number = pair.a;
            IRValue representative = pair.b;
            
            // If the arg is redundant or meaningless, it will have a different representative from the arg name
            if(!representative.equals(argID)) {
                if(LOG.isLoggable(Level.FINEST)) { 
                    LOG.finest("Got number " + number + " for bb arg " + argID + "; replacing with " + representative);
                }
                
                // Replace arg with representative
                IRUtil.replaceInFunction(func, argID, representative);
            } else {
                if(LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("Got number " + number + " for bb arg " + argID);
                }
            }
        }
        
        // Handle instructions
        // Copy so we can modify
        List<IRLinearInstruction> instructions = new ArrayList<>(bb.getInstructions());
        
        for(IRLinearInstruction li : instructions) {
            // Number the instruction if appropriate
            switch(li.getOp()) {
                case CALLR:
                    // Numberable if the target is pure
                    if(func.getModule().getInternalFunctions().containsKey(li.getCallTarget())) {
                        if(!IRUtil.isFunctionPure(func.getModule().getInternalFunctions().get(li.getCallTarget()), false, functionPurityMap)) {
                            // impure
                            int number = valueTable.addIRValue(li.getDestinationID()).a;
                            
                            if(LOG.isLoggable(Level.FINEST)) {
                                LOG.finest("Got number " + number + " for impure function call for " + li.getDestinationID());
                            }
                            break;
                        }
                    } else {
                        // Non-internal target, impure
                        int number = valueTable.addIRValue(li.getDestinationID()).a;
                        
                        if(LOG.isLoggable(Level.FINEST)) {
                            LOG.finest("Got number " + number + " for impure function call for " + li.getDestinationID());
                        }
                        break;
                    }
                    
                    // pure, fallthrough
                
                case TRUNC, SX, ZX, SELECT,
                     ADD, SUB, MULU, MULS, DIVU, DIVS, REMU, REMS,
                     SHL, SHR, SAR, ROL, ROR, AND, OR, XOR, NOT, NEG:
                    // Numberable
                    Pair<Integer, IRValue> pair = valueTable.addInstruction(li);
                    int number = pair.a;
                    IRValue representative = pair.b;
                    
                    // If the representative is different from the destination, the instruction is eliminated
                    if(!representative.equals(li.getDestinationID())) {
                        if(LOG.isLoggable(Level.FINEST)) {
                            LOG.finest("Got number " + number + " for " + li.getDestinationID() + "; replacing with " + representative);
                        }
                        
                        IRUtil.replaceInFunction(func, li.getDestinationID(), representative);
                        continue;
                    } else {
                        if(LOG.isLoggable(Level.FINEST)) {
                            LOG.finest("Got number " + number + " for " + li.getDestinationID());
                        }
                    }
                    break;
                
                default:
                    // Not numberable
                    if(li.hasDestination()) {
                        // destination still needs a number
                        number = valueTable.addIRValue(li.getDestinationID()).a;
                        
                        if(LOG.isLoggable(Level.FINEST)) {
                            LOG.finest("Got number " + number + " for black box " + li.getDestinationID());
                        }
                    }
            }
            
            // At this point, the instruction has not been replaced
            // Thanks to replaceInFunction, propagation of numbered values has been handled already 
        }
        
        // Recurse on dominator tree children, in reverse postorder
        // Doing so in this order ensures no numberable bb args are missed
        List<TreeNode<IRIdentifier>> domChildren = new ArrayList<>(domTreeNode.getChildren());
        Collections.sort(domChildren, (a, b) -> reversePostorderIndexMap.get(a.getElement()) - reversePostorderIndexMap.get(b.getElement()));
        
        for(TreeNode<IRIdentifier> domChild : domChildren) {
            doGVN(domChild, func, valueTable, functionPurityMap, reversePostorderIndexMap);
        }
        
        // Remove any values made available in this BB
        Set<Integer> newValues = new HashSet<>(valueTable.getAvailableNumbers());
        newValues.removeAll(valuesBefore);
        
        for(int i : newValues) {
            valueTable.removeValue(i);
        }
    }
    
}

/**
 * A table of GVN Values
 * (GVNValueTable before it was modified to suit GVN-PRE)
 */
class PGVNValueTable {
    
    // Map canonical-form values to value numbers
    private Map<GVNElement, Integer> valueNumberMap;
    
    // Map value number to earliest-computed IRValue containing the value
    private Map<Integer, IRValue> numberLeaderMap;
    
    // Next number to be assigned
    private int currentNumber;
    
    /**
     * Creates an empty table
     */
    public PGVNValueTable() {
        this.valueNumberMap = new HashMap<>();
        this.numberLeaderMap = new HashMap<>();
        this.currentNumber = 0;
    }
    
    /**
     * Adds the element if not present
     * @param e Element
     * @param representative IRValue containing e
     * @param expGen EXP_GEN set
     * @param addedExps Value numbers already added to EXP_GEN
     * @param true if a phi destination
     * @return The element's value number
     */
    private Pair<Integer, IRValue> addElement(GVNElement e, IRValue representative) {
        GVNValue repVal = new GVNValue(IRType.NONE, representative); 
        
        if(!this.valueNumberMap.containsKey(e)) {
            // Not present, make new number
            int vn = this.currentNumber++;
            this.valueNumberMap.put(e, vn);
            this.valueNumberMap.put(repVal, vn);
            this.numberLeaderMap.put(vn, representative);
            
            return new Pair<>(vn, representative);
        }
        
        // A number exists for this value
        int vn = this.valueNumberMap.get(e);
        this.valueNumberMap.put(repVal, vn);
        
        // If it doesn't have a representative, add it
        if(!this.numberLeaderMap.containsKey(vn)) {
            this.numberLeaderMap.put(vn, representative);
            
            return new Pair<>(vn, representative);
        } else {
            return new Pair<>(vn, this.numberLeaderMap.get(vn));
        }
    }
    
    /**
     * Add an IRValue if not present
     * @param v
     * @param expGen EXP_GEN set
     * @param addedExps values added to EXP_GEN
     * @param phi true if a phi destination
     * @return The IRValue's value number
     */
    public Pair<Integer, IRValue> addIRValue(IRValue v) {
        return addElement(new GVNValue(IRType.NONE, v), v);
    }
    
    /**
     * Adds an IRValue if not present, unless it is a local
     * @param v
     * @param expGen EXP_GEN set
     * @param addedExps values added to EXP_GEN
     * @param phi true if a phi destination
     * @return The IRValue's value number, or -1 if it is a local without a number
     */
    private Pair<Integer, IRValue> addIRValueNoLocals(IRValue v) {
        if(v instanceof IRIdentifier id && id.getIDClass() == IRIdentifierClass.LOCAL) {
            // Local. Cannot be made a leader.
            GVNValue vVal = new GVNValue(IRType.NONE, v);
            
            if(this.valueNumberMap.containsKey(vVal)) {
                // Has a number
                return addIRValue(v);
            } else {
                // No number
                return new Pair<>(-1, v);
            }
        } else {
            // Not a local. just add
            return addIRValue(v);
        }
    }
    
    /**
     * Adds a basic block argument to the table
     * @param arg
     * @param bbID
     * @param preds
     * @param func
     * @return value number
     */
    public Pair<Integer, IRValue> addBBArgument(IRIdentifier arg, IRIdentifier bbID, List<IRIdentifier> preds, IRFunction func) {
        Map<IRIdentifier, Pair<Integer, Integer>> mappedValueNumbers = new HashMap<>();
        
        int lastNumber = -1;
        boolean meaningful = false;
        
        // Gather argument mapping(s) from each predecessor
        for(IRIdentifier predID : preds) {
            IRBasicBlock predBB = func.getBasicBlock(predID);
            IRBranchInstruction predBranch = predBB.getExitInstruction();
            
            int trueNum = -1,
                falseNum = -1;
            
            if(bbID.equals(predBranch.getTrueTargetBlock())) {
                // True target is the block in question
                IRValue mappedValue = predBranch.getTrueArgumentMapping().getMapping(arg);
                
                // We don't want to make a value number for the mapped value
                int mappedNumber = addIRValueNoLocals(mappedValue).a;
                //System.out.println(arg + " is " + mappedValue + " from " + predID + " = " + mappedNumber);
                
                if(mappedNumber == -1) {
                    // Found a mapping without a value number
                    return addIRValue(arg);
                }
                
                if(lastNumber != -1 && lastNumber != mappedNumber) {
                    // more than one value number is in the phi
                    meaningful = true;
                }
                
                lastNumber = mappedNumber;
                trueNum = mappedNumber;
            }
            
            if(bbID.equals(predBranch.getFalseTargetBlock())) {
                // False target is the block in question
                IRValue mappedValue = predBranch.getFalseArgumentMapping().getMapping(arg);
                
                // We don't want to make a value number for the mapped value
                int mappedNumber = addIRValueNoLocals(mappedValue).a;
                //System.out.println(arg + " is " + mappedValue + " from " + predID + " = " + mappedNumber);
                
                if(mappedNumber == -1) {
                    // Found a mapping without a value number
                    return addIRValue(arg);
                }
                
                if(lastNumber != -1 && lastNumber != mappedNumber) {
                    // more than one value number is in the phi
                    meaningful = true;
                }
                
                lastNumber = mappedNumber;
                falseNum = mappedNumber;
            }
            
            mappedValueNumbers.put(predID, new Pair<>(trueNum, falseNum));
        }
        
        if(meaningful) {
            // Put in phi & add 
            return addElement(new GVNPhi(IRType.NONE, mappedValueNumbers), arg);
        } else {
            // phi is meaningless. Return assigned number.
            return new Pair<>(lastNumber, this.numberLeaderMap.get(lastNumber));
        }
    }
    
    /**
     * Adds an instruction to the table. Assumes that:
     * - The instruction's arguments are already numbered
     * - Numbering the instruction is valid
     * @param li
     * @return value number
     */
    public Pair<Integer, IRValue> addInstruction(IRLinearInstruction li) {
        GVNElement e;
        
        // Convert to element
        switch(li.getOp()) {
            case TRUNC, SX, ZX, NOT, NEG:
                // Single argument
                e = new GVNExpression(IRType.NONE, li.getOp(), List.of(
                    addIRValue(li.getLeftSourceValue()).a
                ));
                break;
        
            case ADD, SUB, MULU, MULS, DIVU, DIVS, REMU, REMS,
                 SHL, SHR, SAR, ROL, ROR, AND, OR, XOR:
                // Two argument
                e = new GVNExpression(IRType.NONE, li.getOp(), List.of(
                    addIRValue(li.getLeftSourceValue()).a,
                    addIRValue(li.getRightSourceValue()).a
                ));
                break;
        
            case SELECT:
                // Select
                e = new GVNExpression(IRType.NONE, li.getOp(), li.getSelectCondition(), List.of(
                    addIRValue(li.getLeftComparisonValue()).a,
                    addIRValue(li.getRightComparisonValue()).a,
                    addIRValue(li.getLeftSourceValue()).a,
                    addIRValue(li.getRightSourceValue()).a
                ));
                break;
            
            case CALLR:
                // CALLR to a presumably pure function
                List<Integer> argList = new ArrayList<>();
                argList.add(addIRValue(li.getCallTarget()).a);
                
                // Arg list is in order target, first arg, second arg, etc
                IRArgumentMapping argMap = li.getCallArgumentMapping();
                
                for(IRIdentifier id : argMap.getOrdering()) {
                    argList.add(addIRValue(argMap.getMapping(id)).a);
                }
                
                e = new GVNExpression(IRType.NONE, li.getOp(), argList);
                break;
            
            default:
                throw new IllegalArgumentException("Instruction " + li + " can't be numbered: " + (li.getOp().hasDestination() ? "invalid op" : "no destination."));
        }
        
        // Number element & associate destination with number
        return addElement(e, li.getDestinationID());
    }
    
    /**
     * Get the representative IRValue of a value number
     * @param valueNumber
     * @return
     */
    public IRValue getNumberValue(int valueNumber) {
        return this.numberLeaderMap.get(valueNumber);
    }
    
    /**
     * Remove the value with the given number
     * @param valueNumber
     * @return The representative IRValue of the value number
     */
    public IRValue removeValue(int valueNumber) {
        // Remove from leaders
        IRValue v = this.numberLeaderMap.remove(valueNumber);
        
        // Mappings to the value are not removed since it would be O(n) and doing
        // so doesn't affect anything
        
        return v;
    }
    
    public Set<Integer> getAvailableNumbers() {
        return this.numberLeaderMap.keySet();
    }
    
    /**
     * Get the value number representing v
     * @param v
     * @return
     */
    public int getValueNumber(IRValue v) {
        return this.valueNumberMap.get(new GVNValue(IRType.NONE, v));
    }
    
}

