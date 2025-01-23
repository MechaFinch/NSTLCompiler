package notsotiny.lang.compiler.irgen;

import java.util.ArrayList;
import java.util.List;

import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.compiler.ASTUtil;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.irgen.context.ASTContextTree;
import notsotiny.lang.compiler.irgen.context.ASTContextVariable;
import notsotiny.lang.compiler.types.FunctionHeader;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.PointerType;
import notsotiny.lang.compiler.types.RawType;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.parts.IRType;

/**
 * Contains the AST of a function + header information
 */
public class ASTFunction {
    
    // Module this is a part of
    private ASTModule parentModule;
    
    // Context
    private ASTContextTree context;
    
    // Header info
    private FunctionHeader header;
    
    // Code 
    private List<ASTNode> contents;
    private List<ASTBasicBlock> basicBlocks;    // Note: the order of this list must match program source order
    
    // Other
    private boolean external;
    
    private int fuid;
    
    /**
     * Internal constructor
     * @param header
     * @param contents
     */
    public ASTFunction(ASTModule parent, FunctionHeader header, List<ASTNode> contents) {
        this.parentModule = parent;
        this.header = header;
        this.contents = contents;
        
        this.basicBlocks = new ArrayList<>();
        this.external = false;
        this.fuid = 0;
        
        this.context = new ASTContextTree(parent.getContext());
    }
    
    /**
     * External constructor
     * @param parent
     * @param header
     * @param external
     */
    public ASTFunction(ASTModule parent, FunctionHeader header) {
        this.parentModule = parent;
        this.header = header;
        this.contents = null;
        this.basicBlocks = null;
        this.external = true;
        this.fuid = 0;
    }
    
    /**
     * Placeholder constructor
     */
    public ASTFunction(ASTModule parent, boolean external) {
        this.parentModule = parent;
        this.header = null;
        this.contents = null;
        this.external = external;
    }
    
    /**
     * Gets a unique identifier with the given prefix
     * @param prefix
     * @return
     */
    public String getUnique(String prefix) {
        return prefix + "%" + this.fuid++;
    }
    
    /**
     * Gets IR from the header
     * @return
     */
    public IRFunction getHeaderIR(IRModule sourceModule) throws CompilationException {
        // Make object
        IRFunction func = new IRFunction(new IRIdentifier(header.getName(), IRIdentifierClass.GLOBAL), this.header.getReturnType().getIRType(), this.external, sourceModule, ASTUtil.getPosition(header.getSource()).getLine());
        
        // Add arguments
        List<String> argNames = this.header.getArgumentNames();
        List<NSTLType> argTypes = this.header.getArgumentTypes();
        
        for(int i = 0; i < argNames.size(); i++) {
            IRIdentifier argID = new IRIdentifier(argNames.get(i), IRIdentifierClass.LOCAL);
            IRType argType = argTypes.get(i).getIRType();
            func.addArgument(argID, argType);
        }
        
        return func;
    }
    
    /**
     * Add arguments to local context
     * Must have a header
     */
    public void addArgsToContext() {
        List<String> argNames = this.header.getArgumentNames();
        List<NSTLType> argTypes = this.header.getArgumentTypes();
        
        for(int i = 0; i < argNames.size(); i++) {
            this.context.addEntry(new ASTContextVariable(argNames.get(i), argNames.get(i), argTypes.get(i)));
        }
    }
    
    public void setHeader(FunctionHeader header) { this.header = header; }
    public void setContents(List<ASTNode> contents) { this.contents = contents; }

    public ASTModule getParentModule() { return this.parentModule; }
    public ASTContextTree getContext() { return this.context; }
    public FunctionHeader getHeader() { return this.header; }
    public List<ASTNode> getContents() { return this.contents; }
    public List<ASTBasicBlock> getBasicBlocks() { return this.basicBlocks; }
    public boolean isExternal() { return this.external; }
}
