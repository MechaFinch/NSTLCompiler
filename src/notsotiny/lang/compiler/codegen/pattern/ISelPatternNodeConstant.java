package notsotiny.lang.compiler.codegen.pattern;

import notsotiny.lang.ir.parts.IRType;

/**
 * An ISelPatternNode that matches a constant. May be a specific value or a wildcard.
 * Wildcard constants which allow I32 can match the VALUE nodes of globals
 */
public class ISelPatternNodeConstant extends ISelPatternNode {
    
    private String identifier;
    
    private int value;
    
    private IRType type;
    
    private boolean wildcard;
    
    /**
     * @param identifier
     * @param value
     * @param type
     * @param wildcard
     */
    public ISelPatternNodeConstant(String identifier, int value, IRType type, boolean wildcard) {
        this.identifier = identifier;
        this.value = value;
        this.type = type;
        this.wildcard = wildcard;
    }
    
    public String getIdentifier() { return this.identifier; }
    public int getValue() { return this.value; }
    public IRType getType() { return this.type; }
    public boolean isWildcard() { return this.wildcard; }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("(");
        sb.append(this.type);
        sb.append(" CONSTANT ");
        
        if(this.wildcard) {
            sb.append(this.identifier);
        } else {
            sb.append(this.value);
        }
        
        sb.append(")");
        
        return sb.toString();
    }
    
}
