package notsotiny.lang.compiler.irgen.context;

import notsotiny.lang.compiler.types.TypedValue;

/**
 * A constant in the context stack
 */
public class ASTContextConstant extends ASTContextEntry {
    
    private TypedValue value;
    
    /**
     * @param name
     * @param value
     */
    public ASTContextConstant(String sourceName, String uniqueName, TypedValue value) {
        this.sourceName = sourceName;
        this.uniqueName = uniqueName;
        this.value = value;
    }
    
    public TypedValue getValue() { return this.value; }
    
    @Override
    public String toString() {
        return "Constant " + this.sourceName + " (" + this.value + ")";
    }
    
}
