package notsotiny.lang.ir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A basic block
 * 
 * @author Mechafinch
 */
public class IRBasicBlock implements IRSourceInfo {
    
    // Identifier
    private IRIdentifier id;
    
    // Arguments
    private IRArgumentList arguments;
    
    // Contents
    private List<IRLinearInstruction> instructions;
    
    // Exit instruction
    private IRBranchInstruction exitInstruction;
    
    // Source module
    private IRModule module;
    
    // Line number of the function's header
    private int sourceLineNumber;
    
    /**
     * Full constructor
     * @param id
     * @param args
     * @param instructions
     * @param exitInstruction
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRBasicBlock(IRIdentifier id, IRArgumentList args, List<IRLinearInstruction> instructions, IRBranchInstruction exitInstruction, IRModule sourceModule, int sourceLineNumber) {
        this.id = id;
        this.arguments = args;
        this.instructions = instructions;
        this.exitInstruction = exitInstruction;
        this.module = sourceModule;
        this.sourceLineNumber = sourceLineNumber;
    }
    
    /**
     * No source info constructor
     * @param id
     * @param args
     * @param instructions
     * @param exitInstruction
     */
    public IRBasicBlock(IRIdentifier id, IRArgumentList args, List<IRLinearInstruction> instructions, IRBranchInstruction exitInstruction) {
        this(id, args, instructions, exitInstruction, null, 0);
    }
    
    /**
     * Minimal constructor w/ source info
     * @param id
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRBasicBlock(IRIdentifier id, IRModule sourceModule, int sourceLineNumber) {
        this(id, new IRArgumentList(), new ArrayList<>(), null, sourceModule, sourceLineNumber);
    }
    
    /**
     * Minimal constructor
     */
    public IRBasicBlock(IRIdentifier id) {
        this(id, new IRArgumentList(), new ArrayList<>(), null, null, 0);
    }
    
    /**
     * Add a linear instruction to the block
     * @param inst
     */
    public void addInstruction(IRLinearInstruction inst) { 
        this.instructions.add(inst);
    }
    
    /**
     * Set the exit instruction of the block
     * @param inst
     */
    public void setExitInstruction(IRBranchInstruction inst) {
        this.exitInstruction = inst;
    }
    
    /**
     * Add an argument to the block
     * @param name
     * @param type
     */
    public void addArgument(IRIdentifier name, IRType type) {
        this.arguments.addArgument(name, type);
    }

    @Override
    public Path getSourceFile() {
        return module.getSourceFile();
    }

    @Override
    public int getSourceLineNumber() {
        return this.sourceLineNumber;
    }
    
    public IRIdentifier getID() { return this.id; }
    public IRArgumentList getArgumentList() { return this.arguments; }
    public List<IRLinearInstruction> getInstructions() { return this.instructions; }
    public IRBranchInstruction getExitInstruction() { return this.exitInstruction; }
    
}
