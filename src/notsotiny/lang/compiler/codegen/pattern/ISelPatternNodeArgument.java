package notsotiny.lang.compiler.codegen.pattern;

import notsotiny.lang.ir.parts.IRType;

/**
 * An IselPatternNode that represents a function argument
 */
public class ISelPatternNodeArgument extends ISelPatternNode {
    
    private String identifier;
    
    private IRType type;
    
    /**
     * @param identifier
     * @param type
     */
    public ISelPatternNodeArgument(String identifier, IRType type) {
        this.identifier = identifier;
        this.type = type;
    }
    
    public String getIdentifier() { return this.identifier; }
    public IRType getType() { return this.type; }
    
    @Override
    public String toString() {
        return "(" + this.type + " ARG " + this.identifier + ")";
    }
    
}
