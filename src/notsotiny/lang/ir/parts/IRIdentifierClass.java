package notsotiny.lang.ir.parts;

/**
 * Visibility class of an identifier
 * 
 * @author Mechafinch
 */
public enum IRIdentifierClass {
    GLOBAL  ("@"),  // Top-level module member, resolves to an address
    LOCAL   ("%"),  // Local value. Visible to its function only.
    BLOCK   ("$"),  // Block label. Visible to its function only.
    SPECIAL ("&"),  // Special. Illegal in code.
    ;
    
    private String prefix;
    
    private IRIdentifierClass(String prefix) {
        this.prefix = prefix;
    }
    
    /**
     * @return Prefix character
     */
    public String getPrefix() {
        return this.prefix;
    }
}
