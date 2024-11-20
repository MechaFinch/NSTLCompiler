package notsotiny.lang.ir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    // Predecessors
    private List<IRIdentifier> predecessorBlocks;
    
    // Source function
    private IRFunction function;
    
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
    public IRBasicBlock(IRIdentifier id, IRArgumentList args, List<IRLinearInstruction> instructions, IRBranchInstruction exitInstruction, List<IRIdentifier> predecessors, boolean isSealed, IRModule sourceModule, IRFunction sourceFunction, int sourceLineNumber) {
        this.id = id;
        this.arguments = args;
        this.instructions = instructions;
        this.exitInstruction = exitInstruction;
        this.predecessorBlocks = predecessors;
        this.function = sourceFunction;
        this.module = sourceModule;
        this.sourceLineNumber = sourceLineNumber;
    }
    
    /**
     * Minimal constructor
     * @param id
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRBasicBlock(IRIdentifier id, IRModule sourceModule, IRFunction sourceFunction, int sourceLineNumber) {
        this(id, new IRArgumentList(), new ArrayList<>(), null, new ArrayList<>(), false, sourceModule, sourceFunction, sourceLineNumber);
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
     * Add a basic block as a predecessor
     * @param bb
     */
    public void addPredecessor(IRIdentifier bb) {
        this.predecessorBlocks.add(bb);
    }
    
    /**
     * Remove a predecessor
     * @param bb
     */
    public void removePredecessor(IRIdentifier bb) {
        this.predecessorBlocks.remove(bb);
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
    
    public IRModule getParentModule() { return this.module; }
    public IRFunction getParentFunction() { return this.function; }
    
    public IRIdentifier getID() { return this.id; }
    public IRArgumentList getArgumentList() { return this.arguments; }
    public List<IRLinearInstruction> getInstructions() { return this.instructions; }
    public IRBranchInstruction getExitInstruction() { return this.exitInstruction; }
    public List<IRIdentifier> getPredecessorBlocks() { return this.predecessorBlocks; }
    public IRFunction getFunction() { return this.function; }
    
}
