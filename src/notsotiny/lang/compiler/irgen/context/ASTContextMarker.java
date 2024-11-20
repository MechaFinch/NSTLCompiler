package notsotiny.lang.compiler.irgen.context;

/**
 * A marker in the stack. Used to pop contexts
 */
public class ASTContextMarker extends ASTContextEntry {
    
    public ASTContextMarker() {
        this.sourceName = "";
        this.uniqueName = "";
    }
    
    @Override
    public String toString() {
        return "Marker";
    }
    
}
