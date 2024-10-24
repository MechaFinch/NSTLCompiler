package notsotiny.lang.ir;

/**
 * An identifier
 * 
 * @author Mechafinch
 */
public class IRIdentifier extends IRValue {
    
    // Name
    private String name;
    
    /*
     * Class
     * LOCAL    Identifies a virtual register. Evaluates to its value, may be I8, I16, I32
     * GLOBAL   Identifies a static memory location. Evaluates to a pointer/I32
     * BLOCK    Identifies a basic block
     */
    private IRIdentifierClass idclass;
    
    /**
     * Full constructor
     * @param name
     * @param idclass
     */
    public IRIdentifier(String name, IRIdentifierClass idclass) {
        this.name = name;
        this.idclass = idclass;
    }
    
    @Override
    public String toString() {
        return idclass.getPrefix() + name;
    }
}
