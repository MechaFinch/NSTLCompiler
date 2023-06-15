package notsotiny.lang.compiler.context;

/**
 * A marker on the context stack
 * 
 * @author Mechafinch
 */
public interface ContextMarker extends ContextEntry {
    
    public ContextMarker duplicate();
}
