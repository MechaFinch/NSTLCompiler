package notsotiny.lang.ir;

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

    // Source module
    private IRModule module;
    
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
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRValue sourceA, IRValue sourceB, IRCondition condition, IRValue compareA, IRValue compareB, IRArgumentMapping callMapping, IRModule sourceModule, int sourceLineNumber) {
        this.op = op;
        this.destination = destination;
        this.destType = destinationType;
        this.sourceA = sourceA;
        this.sourceB = sourceB;
        this.cond = condition;
        this.compareA = compareA;
        this.compareB = compareB;
        this.callMapping = callMapping;
        this.module = sourceModule;
        this.sourceLineNumber = sourceLineNumber;
    }
    
    /**
     * Full constructor, no source info
     * @param op
     * @param destination
     * @param destinationType
     * @param sourceA
     * @param sourceB
     * @param condition
     * @param compareA
     * @param compareB
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRValue sourceA, IRValue sourceB, IRCondition condition, IRValue compareA, IRValue compareB, IRArgumentMapping callMapping) {
        this(op, destination, destinationType, sourceA, sourceB, condition, compareA, compareB, callMapping, null, 0);
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
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRValue sourceA, IRValue sourceB, IRCondition condition, IRValue compareA, IRValue compareB, IRModule sourceModule, int sourceLineNumber) {
        this(op, destination, destinationType, sourceA, sourceB, condition, compareA, compareB, null, sourceModule, sourceLineNumber);
    }
    
    /**
     * SELECT constructor, no source info
     * @param op
     * @param destination
     * @param destinationType
     * @param sourceA
     * @param sourceB
     * @param condition
     * @param compareA
     * @param compareB
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRValue sourceA, IRValue sourceB, IRCondition condition, IRValue compareA, IRValue compareB) {
        this(op, destination, destinationType, sourceA, sourceB, condition, compareA, compareB, null, null, 0);
    }
    
    /**
     * 2-argument constructor
     * @param op
     * @param destination
     * @param destinationType
     * @param sourceA
     * @param sourceB
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRValue sourceA, IRValue sourceB, IRModule sourceModule, int sourceLineNumber) {
        this(op, destination, destinationType, sourceA, sourceB, IRCondition.NONE, null, null, null, sourceModule, sourceLineNumber);
    }
    
    /**
     * 2-argument constructor, no source info
     * @param op
     * @param destination
     * @param destinationType
     * @param sourceA
     * @param sourceB
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRValue sourceA, IRValue sourceB) {
        this(op, destination, destinationType, sourceA, sourceB, IRCondition.NONE, null, null, null, null, 0);
    }
    
    /**
     * 1-argument constructor
     * @param op
     * @param destination
     * @param destinationType
     * @param sourceA
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRValue sourceA, IRModule sourceModule, int sourceLineNumber) {
        this(op, destination, destinationType, sourceA, null, IRCondition.NONE, null, null, null, sourceModule, sourceLineNumber);
    }
    
    /**
     * 1-argument constructor, no source info
     * @param op
     * @param destination
     * @param destinationType
     * @param sourceA
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRValue sourceA) {
        this(op, destination, destinationType, sourceA, null, IRCondition.NONE, null, null, null, null, 0);
    }
    
    /**
     * STORE constructor
     * @param op
     * @param destination
     * @param sourceA
     * @param sourceB
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRValue sourceA, IRValue sourceB, IRModule sourceModule, int sourceLineNumber) {
        this(op, null, IRType.NONE, sourceA, sourceB, IRCondition.NONE, null, null, null, sourceModule, sourceLineNumber);
    }
    
    /**
     * STORE constructor, no source info
     * @param op
     * @param destination
     * @param sourceA
     * @param sourceB
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRValue sourceA, IRValue sourceB) {
        this(op, null, IRType.NONE, sourceA, sourceB, IRCondition.NONE, null, null, null, null, 0);
    }
    
    /**
     * CALLR constructor
     * @param op
     * @param destination
     * @param destinationType
     * @param targetFunction
     * @param argMap
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRIdentifier targetFunction, IRArgumentMapping argMap, IRModule sourceModule, int sourceLineNumber) {
        this(op, destination, destinationType, targetFunction, null, IRCondition.NONE, null, null, argMap, sourceModule, sourceLineNumber);
    }
    
    /**
     * CALLR constructor, no source info
     * @param op
     * @param destination
     * @param destinationType
     * @param targetFunction
     * @param argMap
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier destination, IRType destinationType, IRIdentifier targetFunction, IRArgumentMapping argMap) {
        this(op, destination, destinationType, targetFunction, null, IRCondition.NONE, null, null, argMap, null, 0);
    }
    
    /**
     * CALLN constructor
     * @param op
     * @param targetFunction
     * @param argMap
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier targetFunction, IRArgumentMapping argMap, IRModule sourceModule, int sourceLineNumber) {
        this(op, null, IRType.NONE, targetFunction, null, IRCondition.NONE, null, null, argMap, sourceModule, sourceLineNumber);
    }
    
    /**
     * CALLN constructor, no source info
     * @param op
     * @param targetFunction
     * @param argMap
     */
    public IRLinearInstruction(IRLinearOperation op, IRIdentifier targetFunction, IRArgumentMapping argMap) {
        this(op, null, IRType.NONE, targetFunction, null, IRCondition.NONE, null, null, argMap, null, 0);
    }
    
    @Override
    public Path getSourceFile() {
        return module.getSourceFile();
    }

    @Override
    public int getSourceLineNumber() {
        return this.sourceLineNumber;
    }
    
    public IRLinearOperation getOp() { return this.op; }
    public IRIdentifier getDestinationID() { return this.destination; }
    public IRType getDestinationType() { return this.destType; }
    public IRValue getLeftSourceValue() { return this.sourceA; }
    public IRValue getRightSourceValue() { return this.sourceB; }
    public IRCondition getSelectCondition() { return this.cond; }
    public IRValue getLeftComparisonValue() { return this.compareA; }
    public IRValue getRightComparisonValue() { return this.compareB; }
    public IRArgumentMapping getCallArgumentMapping() { return this.callMapping; }
        
}
