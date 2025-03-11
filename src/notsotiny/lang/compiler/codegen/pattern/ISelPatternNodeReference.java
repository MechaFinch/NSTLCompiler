package notsotiny.lang.compiler.codegen.pattern;

/**
 * An ISelPatternNode that matches any DAG subtree equal to the subtree which matched the referenced subpattern
 */
public class ISelPatternNodeReference extends ISelPatternNode {
    
    private String identifier;
    
    /**
     * @param identifier Identifier of the sub-pattern being referenced
     */
    public ISelPatternNodeReference(String identifier) {
        this.identifier = identifier;
    }
    
    public String getReferencedIdentifier() { return this.identifier; }
    
    @Override
    public String toString() {
        return "(" + this.identifier + ")";
    }
    
}
