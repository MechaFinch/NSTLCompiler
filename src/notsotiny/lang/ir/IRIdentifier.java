package notsotiny.lang.ir;

/**
 * An identifier
 * 
 * @author Mechafinch
 */
public class IRIdentifier extends IRValue {
    
    private String name;
    
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
