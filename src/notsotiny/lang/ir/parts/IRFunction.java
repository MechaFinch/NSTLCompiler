package notsotiny.lang.ir.parts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    // Maps IDs to BBs for lookup
    Map<IRIdentifier, IRBasicBlock> blockNameMap;
    
    // Maps strings to variable types
    // TODO: not properly maintained
    Map<IRIdentifier, IRType> localTypeMap;
    
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
        this.isExternal = external;
        this.module = sourceModule;
        this.sourceLineNumber = sourceLineNumber;
        
        // Build basic block map
        this.blockNameMap = new HashMap<>();
        for(IRBasicBlock bb : this.basicBlocks) {
            this.blockNameMap.put(bb.getID(), bb);
        }
        
        // Build local type map
        this.localTypeMap = new HashMap<>();
        for(int i = 0; i < this.arguments.getArgumentCount(); i++) {
            this.localTypeMap.put(this.arguments.getName(i), this.arguments.getType(i));
        }
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
     * Empty header constructor
     * @param id
     * @param returnType
     * @param external
     * @param sourceModule
     * @param sourceLineNumber
     */
    public IRFunction(IRIdentifier id, IRType returnType, boolean external, IRModule sourceModule, int sourceLineNumber) {
        this(id, returnType, new IRArgumentList(), new ArrayList<>(), external, sourceModule, sourceLineNumber);
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
     * Add an argument
     * @param name
     * @param type
     */
    public void addArgument(IRIdentifier name, IRType type) {
        this.arguments.addArgument(name, type);
        this.localTypeMap.put(name, type);
    }
    
    /**
     * Add a basic block
     * @param bb
     */
    public void addBasicBlock(IRBasicBlock bb) {
        this.basicBlocks.add(bb);
        this.blockNameMap.put(bb.getID(), bb);
    }
    
    /**
     * Add the type of a local
     * @param local
     * @param type
     */
    public void addLocalType(IRIdentifier local, IRType type) {
        this.localTypeMap.put(local, type);
    }
    
    /**
     * Get the BB with the given ID
     * @param id
     * @return
     */
    public IRBasicBlock getBasicBlock(IRIdentifier id) {
        return this.blockNameMap.get(id);
    }
    
    /**
     * Get the entry BB
     * @return
     */
    public IRBasicBlock getEntryBlock() {
        return this.getBasicBlock(new IRIdentifier("entry", IRIdentifierClass.BLOCK));
    }
    
    /**
     * Remove the BB with the given ID
     * @param id
     * @return
     */
    public IRBasicBlock removeBasicBlock(IRIdentifier id) {
        if(this.blockNameMap.containsKey(id)) {
            // Remove from us
            IRBasicBlock bb = this.blockNameMap.remove(id);
            this.basicBlocks.remove(bb);            
            return bb;
        } else {
            return null;
        }
    }
    
    /**
     * Get the type of the local with the given ID
     * @param id
     * @return
     */
    public IRType getLocalType(IRIdentifier id) {
        return this.localTypeMap.get(id);
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
    public IRModule getModule() { return this.module; }
    
}
