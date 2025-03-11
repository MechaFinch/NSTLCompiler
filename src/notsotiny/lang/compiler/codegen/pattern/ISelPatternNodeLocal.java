package notsotiny.lang.compiler.codegen.pattern;

import notsotiny.lang.ir.parts.IRType;

/**
 * An ISelPatternNode that represents an 'input' to the tile
 */
public class ISelPatternNodeLocal extends ISelPatternNode {
    
    private String identifier;
    
    private IRType type;
    
    /**
     * @param identifier
     * @param type
     */
    public ISelPatternNodeLocal(String identifier, IRType type) {
        this.identifier = identifier;
        this.type = type;
    }
    
    public String getIdentifier() { return this.identifier; }
    public IRType getType() { return this.type; }
    
    @Override
    public String toString() {
        return "(" + type + " LOCAL " + identifier + ")";
    }
    
}
