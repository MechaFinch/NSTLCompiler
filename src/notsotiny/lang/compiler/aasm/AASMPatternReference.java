package notsotiny.lang.compiler.aasm;

import java.util.List;

import notsotiny.lang.ir.parts.IRType;

/**
 * A pattern reference directs the pattern substitution algorithm to
 * an identifier in the pattern
 */
public record AASMPatternReference(List<String> identifiers) implements AASMPart  {
    
    /**
     * @return True if this references a half of a value
     */
    public boolean isHalf() {
        String last = this.identifiers.get(this.identifiers.size() - 1);
        
        return last.equals("<high>") || last.equals("<low>");
    }
    
    /**
     * @return True if this references the high half of a value
     */
    public boolean isHigh() {
        return this.identifiers.get(this.identifiers.size() - 1).equals("<high>");
    }
    
    /**
     * @return True if this assigns a type to the reference
     */
    public boolean isTyped() {
        String last = this.identifiers.get(this.identifiers.size() - 1);
        
        return last.equals("<i8>") || last.equals("<i16>") || last.equals("<i32>");
    }
    
    /**
     * @return Type if typed
     */
    public IRType getType() {
        return switch(this.identifiers.get(this.identifiers.size() - 1)) {
            case "<i8>"     -> IRType.I8;
            case "<i16>"    -> IRType.I16;
            case "<i32>"    -> IRType.I32;
            default         -> IRType.NONE;
        };
    }
    
    /**
     * Gets the string associated with the series of identifiers
     * @param modifiers Number of modifier identifiers (<high>, <low>, <type>)
     * @return
     */
    public String toKey(int modifiers) {
        StringBuilder sb = new StringBuilder();
        
        for(int i = 0; i < this.identifiers.size() - modifiers; i++) {
            sb.append(this.identifiers.get(i));
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        for(String id : this.identifiers) {
            sb.append(id);
        }
        
        return sb.toString();
    }
    
}
