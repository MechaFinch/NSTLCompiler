package notsotiny.lang.ir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A function.
 * 
 * @author Mechafinch
 */
public class IRFunction implements IRSourceInfo {
    
    // Identifier
    private IRIdentifier id;
    
    // Return type
    private IRType returnType;
    
    // Argument list
    private IRArgumentList arguments;
    
    // Actual code
    // index zero is the entry block
    private List<IRBasicBlock> basicBlocks;
    
    // True if the function object represents only a function header 
    private boolean isExternal;
    
    // Source module
    private IRModule module;
    
    // Line number of the function's header
    private int sourceLineNumber;
    
    /**
     * Full constructor w/ external possibility
     * @param id
     * @param returnType
     * @param arguments
     * @param basicBlocks
     * @param external
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRFunction(IRIdentifier id, IRType returnType, IRArgumentList arguments, List<IRBasicBlock> basicBlocks, boolean external, IRModule sourceModule, int sourceLineNumber) {
        this.id = id;
        this.returnType = returnType;
        this.arguments = arguments;
        this.basicBlocks = basicBlocks;
        this.module = sourceModule;
        this.sourceLineNumber = sourceLineNumber;
    }
    
    /**
     * Full internal constructor
     * @param id
     * @param returnType
     * @param arguments
     * @param basicBlocks
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRFunction(IRIdentifier id, IRType returnType, IRArgumentList arguments, List<IRBasicBlock> basicBlocks, IRModule sourceModule, int sourceLineNumber) {
        this(id, returnType, arguments, basicBlocks, false, sourceModule, sourceLineNumber);
    }
    
    /**
     * Full internal constructor, no source info
     * @param id
     * @param returnType
     * @param arguments
     * @param basicBlocks
     */
    public IRFunction(IRIdentifier id, IRType returnType, IRArgumentList arguments, List<IRBasicBlock> basicBlocks) {
        this(id, returnType, arguments, basicBlocks, false, null, 0);
    }
    
    /**
     * Full header constructor
     * @param id
     * @param returnType
     * @param arguments
     * @param external
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRFunction(IRIdentifier id, IRType returnType, IRArgumentList arguments, boolean external, IRModule sourceModule, int sourceLineNumber) {
        this(id, returnType, arguments, new ArrayList<>(), external, sourceModule, sourceLineNumber);
    }
    
    /**
     * Full header constructor, no source info
     * @param id
     * @param returnType
     * @param arguments
     * @param external
     */
    public IRFunction(IRIdentifier id, IRType returnType, IRArgumentList arguments, boolean external) {
        this(id, returnType, arguments, new ArrayList<>(), external, null, 0);
    }
    
    /**
     * Empty header constructor
     * @param id
     * @param returnType
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRFunction(IRIdentifier id, IRType returnType, boolean external, IRModule sourceModule, int sourceLineNumber) {
        this(id, returnType, new IRArgumentList(), new ArrayList<>(), external, sourceModule, sourceLineNumber);
    }
    
    /**
     * Empty header constructor, no source info
     * @param id
     * @param returnType
     * @param external
     */
    public IRFunction(IRIdentifier id, IRType returnType, boolean external) {
        this(id, returnType, new IRArgumentList(), new ArrayList<>(), external, null, 0);
    }
    
    /**
     * Empty internal constructor
     * @param id
     * @param returnType
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRFunction(IRIdentifier id, IRType returnType, IRModule sourceModule, int sourceLineNumber) {
        this(id, returnType, new IRArgumentList(), new ArrayList<>(), false, sourceModule, sourceLineNumber);
    }
    
    /**
     * Empty internal constructor, no source info
     * @param id
     * @param returnType
     */
    public IRFunction(IRIdentifier id, IRType returnType) {
        this(id, returnType, new IRArgumentList(), new ArrayList<>(), null, 0);
    }
    
    /**
     * Add an argument
     * @param name
     * @param type
     */
    public void addArgument(IRIdentifier name, IRType type) {
        this.arguments.addArgument(name, type);
    }
    
    /**
     * Add a basic block
     * @param bb
     */
    public void addBasicBlock(IRBasicBlock bb) {
        this.basicBlocks.add(bb);
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
    public IRType getReturnType() { return this.returnType; }
    public IRArgumentList getArguments() { return this.arguments; }
    public List<IRBasicBlock> getBasicBlockList() { return this.basicBlocks; }
    public boolean isExternal() { return this.isExternal; }
    
}
