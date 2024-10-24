package notsotiny.lang.compiler.irgen;

import java.util.ArrayList;
import java.util.List;

import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.compiler.types.FunctionHeader;

/**
 * Contains the AST of a function + header information
 */
public class ASTFunction {
    
    // Module this is a part of
    private ASTModule parentModule;
    
    // Header info
    private FunctionHeader header;
    
    // Code
    private List<ASTNode> contents;
    
    private boolean external;
    
    /**
     * Internal constructor
     * @param header
     * @param contents
     */
    public ASTFunction(ASTModule parent, FunctionHeader header, List<ASTNode> contents) {
        this.parentModule = parent;
        this.header = header;
        this.contents = contents;
        this.external = false;
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
        this.external = true;
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
    
    public void setHeader(FunctionHeader header) { this.header = header; }
    public void setContents(List<ASTNode> contents) { this.contents = contents; }

    public ASTModule getParentModule() { return this.parentModule; }
    public FunctionHeader getHeader() { return this.header; }
    public List<ASTNode> getContents() { return this.contents; }
    public boolean isExternal() { return this.external; }
}
