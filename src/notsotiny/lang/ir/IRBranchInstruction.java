package notsotiny.lang.ir;

import java.nio.file.Path;

/**
 * A branch instruction
 * 
 * @author Mechafinch
 */
public class IRBranchInstruction implements IRSourceInfo {
    
    // Operation
    private IRBranchOperation op;
    
    // Comparison for JCC
    private IRCondition cond;
    
    // Left side of comparison for JCC
    private IRValue compareA;
    
    // Right side of comparison for JCC
    private IRValue compareB;
    
    // True destination
    private IRIdentifier trueBlock;
    
    // True argument mapping
    private IRArgumentMapping trueMapping;
    
    // False destination
    private IRIdentifier falseBlock;
    
    // False argument mapping
    private IRArgumentMapping falseMapping;
    
    // Return value
    private IRValue returnValue;
    
    // Source module
    private IRModule module;
    
    // Line number of the function's header
    private int sourceLineNumber;
    
    /**
     * Full constructor
     * @param op
     * @param cond
     * @param compareA
     * @param compareB
     * @param trueBlock
     * @param trueMapping
     * @param falseBlock
     * @param falseMapping
     * @param returnValue
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRBranchInstruction(IRBranchOperation op, IRCondition cond, IRValue compareA, IRValue compareB, IRIdentifier trueBlock, IRArgumentMapping trueMapping, IRIdentifier falseBlock, IRArgumentMapping falseMapping, IRValue returnValue, IRModule sourceModule, int sourceLineNumber) {
        this.op = op;
        this.cond = cond;
        this.compareA = compareA;
        this.compareB = compareB;
        this.trueBlock = trueBlock;
        this.trueMapping = trueMapping;
        this.falseBlock = falseBlock;
        this.falseMapping = falseMapping;
        this.returnValue = returnValue;
        this.module = sourceModule;
        this.sourceLineNumber = sourceLineNumber;
    }
    
    /**
     * JCC constructor
     * @param op
     * @param cond
     * @param compareA
     * @param compareB
     * @param trueBlock
     * @param trueMapping
     * @param falseBlock
     * @param falseMapping
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRBranchInstruction(IRBranchOperation op, IRCondition cond, IRValue compareA, IRValue compareB, IRIdentifier trueBlock, IRArgumentMapping trueMapping, IRIdentifier falseBlock, IRArgumentMapping falseMapping, IRModule sourceModule, int sourceLineNumber) {
        this(op, cond, compareA, compareB, trueBlock, trueMapping, falseBlock, falseMapping, null, sourceModule, sourceLineNumber);
    }
    
    /**
     * JCC constructor, no source info
     * @param op
     * @param cond
     * @param compareA
     * @param compareB
     * @param trueBlock
     * @param trueMapping
     * @param falseBlock
     * @param falseMapping
     */
    public IRBranchInstruction(IRBranchOperation op, IRCondition cond, IRValue compareA, IRValue compareB, IRIdentifier trueBlock, IRArgumentMapping trueMapping, IRIdentifier falseBlock, IRArgumentMapping falseMapping) {
        this(op, cond, compareA, compareB, trueBlock, trueMapping, falseBlock, falseMapping, null, null, 0);
    }
    
    /**
     * JMP constructor
     * @param op
     * @param block
     * @param mapping
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRBranchInstruction(IRBranchOperation op, IRIdentifier block, IRArgumentMapping mapping, IRModule sourceModule, int sourceLineNumber) {
        this(op, IRCondition.NONE, null, null, block, mapping, null, null, null, sourceModule, sourceLineNumber);
    }
    
    /**
     * JMP constructor, no source info
     * @param op
     * @param block
     * @param mapping
     */
    public IRBranchInstruction(IRBranchOperation op, IRIdentifier block, IRArgumentMapping mapping) {
        this(op, IRCondition.NONE, null, null, block, mapping, null, null, null, null, 0);
    }
    
    /**
     * RET constructor
     * @param op
     * @param returnValue
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRBranchInstruction(IRBranchOperation op, IRValue returnValue, IRModule sourceModule, int sourceLineNumber) {
        this(op, IRCondition.NONE, null, null, null, null, null, null, returnValue, sourceModule, sourceLineNumber);
    }
    
    /**
     * RET constructor, no source info
     * @param op
     * @param returnValue
     */
    public IRBranchInstruction(IRBranchOperation op, IRValue returnValue) {
        this(op, IRCondition.NONE, null, null, null, null, null, null, returnValue, null, 0);
    }

    @Override
    public Path getSourceFile() {
        return module.getSourceFile();
    }

    @Override
    public int getSourceLineNumber() {
        return this.sourceLineNumber;
    }
    
    public IRBranchOperation getOp() { return this.op; }
    public IRCondition getCondition() { return this.cond; }
    public IRValue getCompareLeft() { return this.compareA; }
    public IRValue getCompareRight() { return this.compareB; }
    public IRValue getReturnValue() { return this.returnValue; }
    public IRIdentifier getTrueTargetBlock() { return this.trueBlock; }
    public IRIdentifier getFalseTargetBlock() { return this.falseBlock; }
    public IRArgumentMapping getTrueArgumentMapping() { return this.trueMapping; }
    public IRArgumentMapping getFalseArgumentMapping() { return this.falseMapping; }
    
}
