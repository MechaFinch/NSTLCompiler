package notsotiny.lang.compiler.irgen.context;

/**
 * An entry in the ASTContextStack
 */
public abstract class ASTContextEntry {
    
    protected String sourceName, uniqueName;
    
    public String getSourceName() { return this.sourceName; }
    public String getUniqueName() { return this.uniqueName; }
    
    /**
     * Returns true if either name for this entry matches the given name
     * @param name
     * @return
     */
    public boolean nameMatches(String name) {
        return this.sourceName.equals(name) || this.uniqueName.equals(name);
    }
    
}
