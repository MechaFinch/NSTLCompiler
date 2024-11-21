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
    
    // Containing basic block
    private IRBasicBlock bb;
    
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
     * @param sourceBB
     * @param sourceLineNumber
     */
    public IRBranchInstruction(IRBranchOperation op, IRCondition cond, IRValue compareA, IRValue compareB, IRIdentifier trueBlock, IRArgumentMapping trueMapping, IRIdentifier falseBlock, IRArgumentMapping falseMapping, IRValue returnValue, IRBasicBlock sourceBB, int sourceLineNumber) {
        this.op = op;
        this.cond = cond;
        this.compareA = compareA;
        this.compareB = compareB;
        this.trueBlock = trueBlock;
        this.trueMapping = trueMapping;
        this.falseBlock = falseBlock;
        this.falseMapping = falseMapping;
        this.returnValue = returnValue;
        this.bb = sourceBB;
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
     * @param sourceBB
     * @param sourceLineNumber
     */
    public IRBranchInstruction(IRBranchOperation op, IRCondition cond, IRValue compareA, IRValue compareB, IRIdentifier trueBlock, IRArgumentMapping trueMapping, IRIdentifier falseBlock, IRArgumentMapping falseMapping, IRBasicBlock sourceBB, int sourceLineNumber) {
        this(op, cond, compareA, compareB, trueBlock, trueMapping, falseBlock, falseMapping, null, sourceBB, sourceLineNumber);
    }
    
    /**
     * JMP constructor
     * @param op
     * @param block
     * @param mapping
     * @param sourceBB
     * @param sourceLineNumber
     */
    public IRBranchInstruction(IRBranchOperation op, IRIdentifier block, IRArgumentMapping mapping, IRBasicBlock sourceBB, int sourceLineNumber) {
        this(op, IRCondition.NONE, null, null, block, mapping, null, null, null, sourceBB, sourceLineNumber);
    }
    
    /**
     * RET constructor
     * @param op
     * @param returnValue
     * @param sourceBB
     * @param sourceLineNumber
     */
    public IRBranchInstruction(IRBranchOperation op, IRValue returnValue, IRBasicBlock sourceBB, int sourceLineNumber) {
        this(op, IRCondition.NONE, null, null, null, null, null, null, returnValue, sourceBB, sourceLineNumber);
    }

    @Override
    public Path getSourceFile() {
        return this.bb.getSourceFile();
    }

    @Override
    public int getSourceLineNumber() {
        return this.sourceLineNumber;
    }
    
    public void setCompareLeft(IRValue v) { this.compareA = v; }
    public void setCompareRight(IRValue v) { this.compareB = v; }
    public void setReturnValue(IRValue v) { this.returnValue = v; }
    public void setTrueTargetBlock(IRIdentifier id) { this.trueBlock = id; }
    public void setFalseTargetBlock(IRIdentifier id) { this.falseBlock = id; }
    public void setTrueArgumentMapping(IRArgumentMapping map) { this.trueMapping = map; }
    public void setFalseArgumentMapping(IRArgumentMapping map) { this.falseMapping = map; }
    
    public IRBranchOperation getOp() { return this.op; }
    public IRCondition getCondition() { return this.cond; }
    public IRValue getCompareLeft() { return this.compareA; }
    public IRValue getCompareRight() { return this.compareB; }
    public IRValue getReturnValue() { return this.returnValue; }
    public IRIdentifier getTrueTargetBlock() { return this.trueBlock; }
    public IRIdentifier getFalseTargetBlock() { return this.falseBlock; }
    public IRArgumentMapping getTrueArgumentMapping() { return this.trueMapping; }
    public IRArgumentMapping getFalseArgumentMapping() { return this.falseMapping; }
    public IRBasicBlock getBasicBlock() { return this.bb; }
    
}
