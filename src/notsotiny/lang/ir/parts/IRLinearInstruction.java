package notsotiny.lang.ir.parts;

import java.nio.file.Path;

/**
 * A linear instruction
 * 
 * @author Mechafinch
 */
public class IRLinearInstruction implements IRSourceInfo {
    
    // Operation
    private IRLinearOperation op;
    
    // CMOV comparison
    private IRCondition cond;
    
    // Destination, if present
    private IRIdentifier destination;
    
    // Type of the destination
    private IRType destType;
    
    // First normal source
    private IRValue sourceA;
    
    // Second normal source
    private IRValue sourceB;
    
    // Left side of comparison for CMOV
    private IRValue compareA;
    
    // Right side of comparison for CMOV
    private IRValue compareB;
    
    // Argument mapping for CALLR, CALLN
    private IRArgumentMapping callMapping;

    // Containing basic block
    private IRBasicBlock bb;
    
    // Line number of the function's header
    private int sourceLineNumber;
    
    /**
     * Full constructor
     * @param op
     * @param destination
     * @param destinationType
     * @param sourceA
     * @param sourceB
     * @param condition
     * @param compareA
     * @param compareB
     * @param sourceBB
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRValue sourceA, IRValue sourceB, IRCondition condition, IRValue compareA, IRValue compareB, IRArgumentMapping callMapping, IRBasicBlock sourceBB, int sourceLineNumber) {
        this.op = op;
        this.destination = destination;
        this.destType = destinationType;
        this.sourceA = sourceA;
        this.sourceB = sourceB;
        this.cond = condition;
        this.compareA = compareA;
        this.compareB = compareB;
        this.callMapping = callMapping;
        this.bb = sourceBB;
        this.sourceLineNumber = sourceLineNumber;
    }
    
    /**
     * SELECT constructor
     * @param op
     * @param destination
     * @param destinationType
     * @param sourceA
     * @param sourceB
     * @param condition
     * @param compareA
     * @param compareB
     * @param sourceBB
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRValue sourceA, IRValue sourceB, IRCondition condition, IRValue compareA, IRValue compareB, IRBasicBlock sourceBB, int sourceLineNumber) {
        this(op, destination, destinationType, sourceA, sourceB, condition, compareA, compareB, null, sourceBB, sourceLineNumber);
    }
    
    /**
     * 2-argument constructor
     * @param op
     * @param destination
     * @param destinationType
     * @param sourceA
     * @param sourceB
     * @param sourceBB
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRValue sourceA, IRValue sourceB, IRBasicBlock sourceBB, int sourceLineNumber) {
        this(op, destination, destinationType, sourceA, sourceB, IRCondition.NONE, null, null, null, sourceBB, sourceLineNumber);
    }
    
    /**
     * 1-argument constructor
     * @param op
     * @param destination
     * @param destinationType
     * @param sourceA
     * @param sourceBB
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRValue sourceA, IRBasicBlock sourceBB, int sourceLineNumber) {
        this(op, destination, destinationType, sourceA, null, IRCondition.NONE, null, null, null, sourceBB, sourceLineNumber);
    }
    
    /**
     * STORE constructor
     * @param op
     * @param destination
     * @param sourceA
     * @param sourceB
     * @param sourceBB
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRValue sourceA, IRValue sourceB, IRBasicBlock sourceBB, int sourceLineNumber) {
        this(op, null, IRType.NONE, sourceA, sourceB, IRCondition.NONE, null, null, null, sourceBB, sourceLineNumber);
    }
    
    /**
     * CALLR constructor
     * @param op
     * @param destination
     * @param destinationType
     * @param targetFunction
     * @param argMap
     * @param sourceBB
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRValue targetFunction, IRArgumentMapping argMap, IRBasicBlock sourceBB, int sourceLineNumber) {
        this(op, destination, destinationType, targetFunction, null, IRCondition.NONE, null, null, argMap, sourceBB, sourceLineNumber);
    }
    
    /**
     * CALLN constructor
     * @param op
     * @param targetFunction
     * @param argMap
     * @param sourceBB
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRValue targetFunction, IRArgumentMapping argMap, IRBasicBlock sourceBB, int sourceLineNumber) {
        this(op, null, IRType.NONE, targetFunction, null, IRCondition.NONE, null, null, argMap, sourceBB, sourceLineNumber);
    }
    
    /**
     * A hashCode for use by IRDefinition
     * Includes definition, excludes destination
     * @return
     */
    public int definitionHashCode() {
        return this.op.hashCode() ^ switch(this.op) { 
            case TRUNC, SX, ZX, LOAD, STACK, NOT, NEG
                        -> this.destType.hashCode() ^ this.sourceA.hashCode();
            
            case ADD, SUB, MULU, MULS, DIVU, DIVS, REMU, REMS,
                 SHL, SHR, SAR, ROL, ROR, AND, OR, XOR
                        -> this.destType.hashCode() ^ this.sourceA.hashCode() ^ this.sourceB.hashCode();
                    
            case SELECT -> this.destType.hashCode() ^ this.sourceA.hashCode() ^ this.sourceB.hashCode() ^
                           this.cond.hashCode() ^ this.compareA.hashCode() ^ this.compareB.hashCode();
                
            case STORE  -> this.sourceA.hashCode() ^ this.sourceB.hashCode();
            case CALLN  -> this.sourceA.hashCode() ^ this.callMapping.hashCode();
            case CALLR  -> this.destType.hashCode() ^ this.sourceA.hashCode() ^ this.callMapping.hashCode();
        };
    }
    
    /**
     * An equals for use by IRDefinition
     * Includes definition, excludes destination
     * @return
     */
    public boolean definitionEquals(IRLinearInstruction other) {
        return this.op.equals(other.op) && switch(this.op) {
            // 1-argument
            case TRUNC, SX, ZX, NOT, NEG
                        -> this.destType.equals(other.destType) && this.sourceA.equals(other.sourceA);
            
            // commutative 2-argument
            case ADD, MULU, MULS, AND, OR, XOR
                        -> this.destType.equals(other.destType) && (
                                (this.sourceA.equals(other.sourceA) && this.sourceB.equals(other.sourceB)) ||
                                (this.sourceA.equals(other.sourceB) && this.sourceB.equals(other.sourceA))
                           );
                        
            // non-commutative 2-argument
            case SUB, DIVU, DIVS, REMU, REMS,
                 SHL, SHR, SAR, ROL, ROR
                        -> this.destType.equals(other.destType) && this.sourceA.equals(other.sourceA) && this.sourceB.equals(other.sourceB);
            
            case SELECT -> this.destType.equals(other.destType) && this.sourceA.equals(other.sourceA) && this.sourceB.equals(other.sourceB) &&
                           this.cond.equals(other.cond) && this.compareA.equals(other.compareA) && this.compareB.equals(other.compareB);
            
            /*
             * STACK returns a unique value for each instruction
             * LOAD may be non-deterministic
             * STORE doesn't define a virtual register
             * CALLN and CALLR may not call pure functions
             */
            case STACK, LOAD, STORE, CALLR, CALLN -> this == other;
        };
    }
    
    @Override
    public Path getSourceFile() {
        return this.bb.getSourceFile();
    }

    @Override
    public int getSourceLineNumber() {
        return this.sourceLineNumber;
    }
    
    public int getSourceCount() { return this.op.getSourceCount(); }
    public boolean hasDestination() { return this.op.hasDestination(); }
    
    public void setLeftSourceValue(IRValue v) { this.sourceA = v; }
    public void setRightSourceValue(IRValue v) { this.sourceB = v; }
    public void setLeftComparisonValue(IRValue v) { this.compareA = v; }
    public void setRightComparisonValue(IRValue v) { this.compareB = v; }
    
    public IRLinearOperation getOp() { return this.op; }
    public IRIdentifier getDestinationID() { return this.destination; }
    public IRType getDestinationType() { return this.destType; }
    public IRValue getCallTarget() { return this.sourceA; }
    public IRValue getLeftSourceValue() { return this.sourceA; }
    public IRValue getRightSourceValue() { return this.sourceB; }
    public IRCondition getSelectCondition() { return this.cond; }
    public IRValue getLeftComparisonValue() { return this.compareA; }
    public IRValue getRightComparisonValue() { return this.compareB; }
    public IRArgumentMapping getCallArgumentMapping() { return this.callMapping; }
    public IRBasicBlock getBasicBlock() { return this.bb; }
        
}
