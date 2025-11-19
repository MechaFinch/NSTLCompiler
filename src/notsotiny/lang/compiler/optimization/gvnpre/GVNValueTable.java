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
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lib.data.Pair;
import notsotiny.lib.data.Triple;

/**
 * A table of GVN Values
 */
public class GVNValueTable {
    
    // Map canonical-form values to value numbers
    private Map<GVNElement, Integer> valueNumberMap;
    
    // Map value number to earliest-computed IRValue containing the value
    private Map<Integer, IRValue> numberLeaderMap;
    
    // Next number to be assigned
    private int currentNumber;
    
    /**
     * Creates an empty table
     */
    public GVNValueTable() {
        this.valueNumberMap = new HashMap<>();
        this.numberLeaderMap = new HashMap<>();
        this.currentNumber = 0;
    }
    
    /**
     * Adds the element if not present
     * @param e Element
     * @param representative IRValue containing e
     * @return The element's value number and leader value
     */
    private Pair<Integer, IRValue> addElement(GVNElement e, IRValue representative) {
        if(!this.valueNumberMap.containsKey(e)) {
            // Not present, make new number
            int vn = this.currentNumber++;
            this.valueNumberMap.put(e, vn);
            this.numberLeaderMap.put(vn, representative);
            
            return new Pair<>(vn, representative);
        }
        
        // A number exists for this value
        int vn = this.valueNumberMap.get(e);
        
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
     * @return The IRValue's value number and leader value
     */
    public Pair<Integer, IRValue> addIRValue(IRValue v) {
        return addElement(new GVNValue(v), v);
    }
    
    /**
     * Adds an IRValue if not present, unless it is a local
     * @param v
     * @return The IRValue's value number, or -1 if it is a local without a number
     */
    private Pair<Integer, IRValue> addIRValueNoLocals(IRValue v) {
        if(this.valueNumberMap.containsKey(new GVNValue(v))) {
            // Has a number
            return addIRValue(v);
        } else {
            if(v instanceof IRIdentifier id && id.getIDClass() == IRIdentifierClass.LOCAL) {
                // Local without a number, don't add
                return new Pair<>(-1, v);
            } else {
                // Not a local
                return addIRValue(v);
            }
        }
    }
    
    /**
     * Adds a basic block argument to the table
     * @param arg
     * @param bbID
     * @param preds
     * @param func
     * @return Pair<Value number, representative>
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
            return addElement(new GVNPhi(mappedValueNumbers), arg);
        } else {
            // phi is meaningless, return assigned number instead
            return new Pair<>(lastNumber, this.numberLeaderMap.get(lastNumber));
        }
    }
    
    /**
     * Adds an instruction to the table. Assumes that:
     * - The instruction's arguments are already numbered
     * - Numbering the instruction is valid
     * @param li
     * @return
     */
    public Pair<Integer, IRValue> addInstruction(IRLinearInstruction li) {
        GVNElement e;
        
        // Convert to element
        switch(li.getOp()) {
            case TRUNC, SX, ZX, NOT, NEG:
                // Single argument
                e = new GVNExpression(li.getOp(), List.of(
                    addIRValue(li.getLeftSourceValue()).a
                ));
                break;
        
            case ADD, SUB, MULU, MULS, DIVU, DIVS, REMU, REMS,
                 SHL, SHR, SAR, ROL, ROR, AND, OR, XOR:
                // Two argument
                e = new GVNExpression(li.getOp(), List.of(
                    addIRValue(li.getLeftSourceValue()).a,
                    addIRValue(li.getRightSourceValue()).a
                ));
                break;
        
            case SELECT:
                // Select
                e = new GVNExpression(li.getOp(), li.getSelectCondition(), List.of(
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
                
                e = new GVNExpression(li.getOp(), argList);
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
    
    /**
     * Returns the set of available value numbers
     * @return
     */
    public Set<Integer> getAvailableNumbers() {
        return this.numberLeaderMap.keySet();
    }
    
}
