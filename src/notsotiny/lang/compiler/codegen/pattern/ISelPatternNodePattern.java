package notsotiny.lang.compiler.codegen.pattern;

/**
 * An ISelPatternNode that matches any pattern from a named group
 */
public class ISelPatternNodePattern extends ISelPatternNode {
    
    private String nodeIdentifier,  // Identifier of this node
                   patternName;     // Name of the named group of patterns
    
    /**
     * @param nodeIdentifier Identifier of this node
     * @param patternIdentifier Identifier of the named group of patterns
     */
    public ISelPatternNodePattern(String nodeIdentifier, String patternName) {
        this.nodeIdentifier = nodeIdentifier;
        this.patternName = patternName;
    }
    
    public String getNodeIdentifier() { return this.nodeIdentifier; }
    public String getPatternName() { return this.patternName; }
    
    @Override
    public String toString() {
        return "(" + nodeIdentifier + " " + patternName + ")";
    }
    
}
