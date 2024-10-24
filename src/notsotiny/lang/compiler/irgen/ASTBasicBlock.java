package notsotiny.lang.compiler.irgen;

import java.util.ArrayList;
import java.util.List;

import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.ir.IRCondition;

/**
 * Contains the AST of a basic block
 */
public class ASTBasicBlock {
    
    // Function this is a part of
    private ASTFunction parentFunction;
    
    // Blocks for which this block is a successor
    private List<ASTBasicBlock> parents;
    
    private ASTBasicBlock trueSuccessor,    // Next block if unconditional or condition true
                          falseSuccessor;   // Next block if condition false
    
    // Condition
    private IRCondition condition;
    
    // Code contents
    private List<ASTNode> contents;
    
    /**
     * Empty constructor
     */
    public ASTBasicBlock(ASTFunction parent) {
        this.parentFunction = parent;
        this.parents = new ArrayList<>();
        this.trueSuccessor = null;
        this.falseSuccessor = null;
        this.condition = IRCondition.NONE;
        this.contents = new ArrayList<>();
    }
    
    public void addParent(ASTBasicBlock bb) { this.parents.add(bb); }
    public void addCode(ASTNode code) { this.contents.add(code); }
    public void addAllCode(List<ASTNode> code) { this.contents.addAll(code); }
    public void setCondition(IRCondition cond) { this.condition = cond; }
    public void setTrueSuccessor(ASTBasicBlock bb) { this.trueSuccessor = bb; }
    public void setFalseSuccessor(ASTBasicBlock bb) { this.falseSuccessor = bb; }
    
    public ASTFunction getParentFunction() { return this.parentFunction; }
    public List<ASTBasicBlock> getParents() { return this.parents; }
    public ASTBasicBlock getTrueSuccessor() { return this.trueSuccessor; }
    public ASTBasicBlock getFalseSuccessor() { return this.falseSuccessor; }
    public IRCondition getCondition() { return this.condition; }
    public List<ASTNode> getCode() { return this.contents; }
    
}
