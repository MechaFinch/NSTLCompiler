package notsotiny.lang.compiler.irgen;

import java.util.ArrayList;
import java.util.List;

import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.compiler.ParseUtils;
import notsotiny.lang.compiler.irgen.context.ASTContextTree;
import notsotiny.lang.ir.parts.IRCondition;
import notsotiny.lang.parser.NstlgrammarLexer;
import notsotiny.lang.parser.NstlgrammarParser;
import notsotiny.lib.util.ASTUtil;

/**
 * Contains the AST of a basic block
 */
public class ASTBasicBlock {
    
    public enum ExitType {
        CONDITIONAL,    // exitCode = variable_expression. trueSuccessor if exitCode evaluates nonzero, falseSuccessor otherwise
        UNCONDITIONAL,  // exitCode = null. trueSuccessor always
        RETURN          // exitCode = return. No successors
    }
    
    // Function this is a part of
    private ASTFunction parentFunction;
    
    // Context
    private ASTContextTree context;
    
    private String name;
    
    // Blocks for which this block is a successor
    private List<ASTBasicBlock> predecessors;
    
    private ASTBasicBlock trueSuccessor,    // Next block if unconditional or condition true
                          falseSuccessor;   // Next block if condition false
    
    // Code contents
    private List<ASTNode> contents;
    
    private ExitType exitType; // grammar ID
    private ASTNode exitCode;
    
    private int sourceLineNumber;
    
    /**
     * Empty constructor
     */
    public ASTBasicBlock(ASTFunction parentFunction, ASTContextTree context, String name) {
        this.parentFunction = parentFunction;
        this.context = context;
        this.name = name;
        this.predecessors = new ArrayList<>();
        this.trueSuccessor = null;
        this.falseSuccessor = null;
        this.contents = new ArrayList<>();
        this.exitCode = null;
        this.exitType = ExitType.UNCONDITIONAL;
        this.sourceLineNumber = parentFunction.getHeader().getSource().getPosition().getLine();
    }
    
    /**
     * Sets the true-case successor for this basic block
     * @param bb
     */
    public void setTrueSuccessor(ASTBasicBlock bb) {
        this.trueSuccessor = bb;
        
        if(bb != null) {
            bb.addPredecessor(this);
        }
    }
    
    /**
     * Sets the false-case successor for this basic block
     * @param bb
     */
    public void setFalseSuccessor(ASTBasicBlock bb) {
        this.falseSuccessor = bb;
        
        if(bb != null) {
            bb.addPredecessor(this);
        }
    }
    
    /**
     * Sets the true successor if it is currently null
     * @param bb
     */
    public void setTrueSuccessorIfAbsent(ASTBasicBlock bb) {
        if(this.trueSuccessor == null && this.exitType != ExitType.RETURN) {
            setTrueSuccessor(bb);
        }
    }
    
    /**
     * Sets the false successor if it is currently null
     * @param bb
     */
    public void setFalseSuccessorIfAbsent(ASTBasicBlock bb) {
        if(this.falseSuccessor == null && this.exitType != ExitType.RETURN) {
            setFalseSuccessor(bb);
        }
    }
    
    /**
     * Sets the type and contents of the exit code
     * @param code
     * @param type
     */
    public void setExitCode(ASTNode code, ExitType type) {
        this.exitCode = code;
        this.exitType = type;
    }
    
    /**
     * Add code to the BB's contents
     * @param code
     */
    public void addCode(ASTNode code) {
        if(this.contents.size() == 0) {
            this.sourceLineNumber = ASTUtil.getPosition(code).getLine();
        }
        
        this.contents.add(code);
    }
    
    public void addPredecessor(ASTBasicBlock bb) { this.predecessors.add(bb); }
    public void removePredecessor(ASTBasicBlock bb) { this.predecessors.remove(bb); }
    public void setName(String name) { this.name = name; }
    public void addAllCode(List<ASTNode> code) { this.contents.addAll(code); }
    
    public ASTFunction getParentFunction() { return this.parentFunction; }
    public ASTContextTree getContext() { return this.context; }
    public String getName() { return this.name; }
    public List<ASTBasicBlock> getPredecessors() { return this.predecessors; }
    public ASTBasicBlock getTrueSuccessor() { return this.trueSuccessor; }
    public ASTBasicBlock getFalseSuccessor() { return this.falseSuccessor; }
    public List<ASTNode> getCode() { return this.contents; }
    public ASTNode getExitCode() { return this.exitCode; }
    public ExitType getExitType() { return this.exitType; }
    public int getSourceLine() { return this.sourceLineNumber; }
    
}
