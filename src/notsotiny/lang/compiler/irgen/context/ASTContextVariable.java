package notsotiny.lang.compiler.irgen.context;

import notsotiny.lang.compiler.types.NSTLType;

/**
 * A variable in the context stack.
 */
public class ASTContextVariable extends ASTContextEntry {
    
    private NSTLType type;
    
    /**
     * @param name
     * @param type
     */
    public ASTContextVariable(String sourceName, String uniqueName, NSTLType type) {
        this.sourceName = sourceName;
        this.uniqueName = uniqueName;
        this.type = type;
    }
    
    public NSTLType getType() { return this.type; }
    
    @Override
    public String toString() {
        return "Variable " + this.sourceName + " (" + this.type + ")";
    }
}
