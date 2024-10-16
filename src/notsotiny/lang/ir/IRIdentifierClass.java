package notsotiny.lang.ir;

/**
 * Visibility class of an identifier
 * 
 * @author Mechafinch
 */
public enum IRIdentifierClass {
    GLOBAL  ("@"),
    LOCAL   ("%"),
    BLOCK   ("$"),
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
