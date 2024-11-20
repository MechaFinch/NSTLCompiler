package notsotiny.lang.compiler.irgen.context;

import notsotiny.lang.compiler.irgen.ASTBasicBlock;

/**
 * A code label in the context stack
 */
public class ASTContextLabel extends ASTContextEntry {

    private ASTBasicBlock continueBB;
    private ASTBasicBlock breakBB;
    
    public ASTContextLabel(String sourceName, String uniqueName, ASTBasicBlock continueBB, ASTBasicBlock breakBB) {
        this.sourceName = sourceName;
        this.uniqueName = uniqueName;
        this.continueBB = continueBB;
        this.breakBB = breakBB;
    }
    
    @Override
    public String toString() {
        return "Label " + this.sourceName;
    }
    
    public ASTBasicBlock getContinueBlock() { return this.continueBB; }
    public ASTBasicBlock getBreakBlock() { return this.breakBB; }
    
}
