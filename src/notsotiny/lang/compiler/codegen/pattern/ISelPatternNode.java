package notsotiny.lang.compiler.codegen.pattern;

/**
 * A pattern matching node in an ISelPattern
 */
public abstract class ISelPatternNode {
    
    protected int size = 1;
    
    private ISelPattern parent;
    
    private boolean isRoot;
    
    /**
     * @param parent
     * @param isRoot
     */
    protected ISelPatternNode() {
        this.parent = null;
        this.isRoot = false;
    }
    
    /**
     * Marks this node as the root of the given pattern
     * @param pattern
     */
    public void setAsRoot(ISelPattern pattern) {
        this.isRoot = true;
        this.parent = pattern;
    }

    public ISelPattern getParent() { return this.parent; }
    public boolean isRoot() { return this.isRoot; }
    public int getSize() { return this.size; }
    
}
