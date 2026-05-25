package notsotiny.lang.compiler.optimization.gvnpre;

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
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lang.ir.util.IRUtil;
import notsotiny.lib.data.Pair;
import notsotiny.lib.data.Triple;

/**
 * A table of GVN Values
 */
public class GVNValueTable {
    
    // Map canonical-form values to value numbers
    private Map<GVNElement, Integer> valueNumberMap;
    
    // Map value numbers to their types
    private Map<Integer, IRType> numberTypeMap;
    
    // Next number to be assigned
    private int currentNumber;
    
    /**
     * Creates an empty table
     */
    public GVNValueTable() {
        this.valueNumberMap = new HashMap<>();
        this.numberTypeMap = new HashMap<>();
        this.currentNumber = 0;
    }
    
    /**
     * Adds the element if not present
     * @param e
     * @return
     */
    public int addElement(GVNElement e) {
        // Do we have a number already
        if(!this.valueNumberMap.containsKey(e)) {
            // Not present, make new number
            int vn = this.currentNumber++;
            this.valueNumberMap.put(e, vn);
            this.numberTypeMap.put(vn, e.type());
            
            return vn;
        }
        
        // A number exists for this value
        return this.valueNumberMap.get(e);
    }
    
    /**
     * Adds the element if not present
     * @param e Element
     * @param representative IRValue containing e
     * @return The element's value number
     */
    public int addElement(GVNElement e, IRValue representative) {
        GVNValue repVal = new GVNValue(e.type(), representative);
        
        int vn = addElement(e);
        this.valueNumberMap.put(repVal, vn);
        return vn;
    }
    
    /**
     * Adds the element if not present, adding to EXP_GEN if not added
     * @param e Element
     * @param representative IRValue containing e
     * @param expGen EXP_GEN set
     * @param addedExps Value numbers already added to EXP_GEN
     * @return The element's value number
     */
    public int addElement(GVNElement e, IRValue representative, List<Pair<Integer, GVNElement>> expGen, Set<Integer> addedExps) {
        int vn = addElement(e, representative);
        
        if(!addedExps.contains(vn)) {
            addedExps.add(vn);
            expGen.add(new Pair<>(vn, e));
        }
        
        return vn;
    }
    
    /**
     * Add an IRValue if not present
     * @param v
     * @param type
     * @return
     */
    public int addIRValue(IRValue v, IRType type) {
        return addElement(new GVNValue(type, v), v);
    }
    
    /**
     * Add an IRValue if not present
     * @param v
     * @param type
     * @param expGen EXP_GEN set
     * @param addedExps values added to EXP_GEN
     * @return The IRValue's value number
     */
    public int addIRValue(IRValue v, IRType type, List<Pair<Integer, GVNElement>> expGen, Set<Integer> addedExps) {
        return addElement(new GVNValue(type, v), v, expGen, addedExps);
    }
    
    /**
     * Adds an IRValue if not present, unless it is a local
     * @param v
     * @param type
     * @return The IRValue's value number, or -1 if it is a local without a number
     */
    private int addIRValueNoLocals(IRValue v, IRType type) {
        if(v instanceof IRIdentifier id && id.getIDClass() == IRIdentifierClass.LOCAL) {
            // Local. Cannot be made a leader.
            GVNValue vVal = new GVNValue(type, v);
            
            if(this.valueNumberMap.containsKey(vVal)) {
                // Has a number
                return this.valueNumberMap.get(vVal);
            } else {
                // No number
                return -1;
            }
        } else {
            // Not a local. just add
            return addIRValue(v, type);
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
    public int addBBArgument(IRIdentifier arg, IRIdentifier bbID, List<IRIdentifier> preds, IRFunction func, Map<Integer, IRIdentifier> phiGen) {
        Map<IRIdentifier, Pair<Integer, Integer>> mappedValueNumbers = new HashMap<>();
        
        IRType type = func.getBasicBlock(bbID).getArgumentList().getType(arg);
        
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
                
                // We don't want to make a value number for the mapped value if it isn't present
                int mappedNumber = addIRValueNoLocals(mappedValue, type);
                //System.out.println(arg + " is " + mappedValue + " from " + predID + " = " + mappedNumber);
                
                if(mappedNumber == -1) {
                    // Found a mapping without a value number
                    //System.out.println(arg + " missing number from " + predID);
                    int vn = addIRValue(arg, type); 
                    phiGen.put(vn, arg);
                    return vn;
                }
                
                if(lastNumber != -1 && lastNumber != mappedNumber) {
                    // more than one value number is in the phi
                    //System.out.println(lastNumber + " != " + mappedNumber + " from " + predID + " for " + arg);
                    meaningful = true;
                }
                
                lastNumber = mappedNumber;
                trueNum = mappedNumber;
            }
            
            if(bbID.equals(predBranch.getFalseTargetBlock())) {
                // False target is the block in question
                IRValue mappedValue = predBranch.getFalseArgumentMapping().getMapping(arg);
                
                // We don't want to make a value number for the mapped value if it isn't present
                int mappedNumber = addIRValueNoLocals(mappedValue, type);
                //System.out.println(arg + " is " + mappedValue + " from " + predID + " = " + mappedNumber);
                
                if(mappedNumber == -1) {
                    // Found a mapping without a value number
                    //System.out.println(arg + " missing number from " + predID);
                    int vn = addIRValue(arg, type); 
                    phiGen.put(vn, arg);
                    return vn;
                }
                
                if(lastNumber != -1 && lastNumber != mappedNumber) {
                    // more than one value number is in the phi
                    //System.out.println(lastNumber + " != " + mappedNumber + " from " + predID + " for " + arg);
                    meaningful = true;
                }
                
                lastNumber = mappedNumber;
                falseNum = mappedNumber;
            }
            
            mappedValueNumbers.put(predID, new Pair<>(trueNum, falseNum));
        }
        
        if(meaningful) {
            // Put in phi & add 
            int vn = addElement(new GVNPhi(type, mappedValueNumbers), arg);
            phiGen.put(vn, arg);
            return vn;
        } else {
            // phi is meaningless. Return assigned number.
            this.valueNumberMap.put(new GVNValue(type, arg), lastNumber);
            return lastNumber;
        }
    }
    
    /**
     * Adds an instruction to the table. Assumes that:
     * - The instruction's arguments are already numbered
     * - Numbering the instruction is valid
     * @param li
     * @return value number
     */
    public int addInstruction(IRLinearInstruction li, List<Pair<Integer, GVNElement>> expGen, Set<Integer> addedExps, Map<IRIdentifier, IRType> typeMap) {
        GVNElement e;
        
        // Convert to element
        switch(li.getOp()) {
            case TRUNC, SX, ZX, NOT, NEG:
                // Single argument
                e = new GVNExpression(li.getDestinationType(), li.getOp(), List.of(
                    addIRValue(li.getLeftSourceValue(), IRUtil.getType(li.getLeftSourceValue(), typeMap), expGen, addedExps)
                ));
                break;
        
            case ADD, SUB, MULU, MULS, DIVU, DIVS, REMU, REMS,
                 SHL, SHR, SAR, ROL, ROR, AND, OR, XOR:
                // Two argument
                e = new GVNExpression(li.getDestinationType(), li.getOp(), List.of(
                    addIRValue(li.getLeftSourceValue(), IRUtil.getType(li.getLeftSourceValue(), typeMap), expGen, addedExps),
                    addIRValue(li.getRightSourceValue(), IRUtil.getType(li.getRightSourceValue(), typeMap), expGen, addedExps)
                ));
                break;
        
            case SELECT:
                // Select
                e = new GVNExpression(li.getDestinationType(), li.getOp(), li.getSelectCondition(), List.of(
                    addIRValue(li.getLeftComparisonValue(), IRUtil.getType(li.getLeftComparisonValue(), typeMap), expGen, addedExps),
                    addIRValue(li.getRightComparisonValue(), IRUtil.getType(li.getRightComparisonValue(), typeMap), expGen, addedExps),
                    addIRValue(li.getLeftSourceValue(), IRUtil.getType(li.getLeftSourceValue(), typeMap), expGen, addedExps),
                    addIRValue(li.getRightSourceValue(), IRUtil.getType(li.getRightSourceValue(), typeMap), expGen, addedExps)
                ));
                break;
            
            case CALLR:
                // CALLR to a presumably pure function
                List<Integer> argList = new ArrayList<>();
                argList.add(addIRValue(li.getCallTarget(), IRUtil.getType(li.getCallTarget(), typeMap), expGen, addedExps));
                
                // Arg list is in order target, first arg, second arg, etc
                IRArgumentMapping argMap = li.getCallArgumentMapping();
                
                for(IRIdentifier id : argMap.getOrdering()) {
                    argList.add(addIRValue(argMap.getMapping(id), typeMap.get(id), expGen, addedExps));
                }
                
                e = new GVNExpression(li.getDestinationType(), li.getOp(), argList);
                break;
            
            default:
                throw new IllegalArgumentException("Instruction " + li + " can't be numbered: " + (li.getOp().hasDestination() ? "invalid op" : "no destination."));
        }
        
        // Number element & associate destination with number
        return addElement(e, li.getDestinationID(), expGen, addedExps);
    }
    
    /**
     * Get the value number representing e
     * @param e
     * @return
     */
    public int getValueNumber(GVNElement e) {
        return this.valueNumberMap.getOrDefault(e, -1);
    }
    
    /**
     * Remove the value number representing e
     * @param e
     * @return
     */
    public int removeValueNumber(GVNElement e) {
        return this.valueNumberMap.remove(e);
    }
    
    /**
     * Get the type of a vn
     * @param vn
     * @return
     */
    public IRType getType(int vn) {
        return this.numberTypeMap.get(vn);
    }
    
}
