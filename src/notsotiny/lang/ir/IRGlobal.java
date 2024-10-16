package notsotiny.lang.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains one or more IRConstants
 * Constants are arranged linearly in memory
 * 
 * @author Mechafinch
 */
public class IRGlobal {
    
    // Identifier
    IRIdentifier id;
    
    // Content constants
    private List<IRValue> contents;
    
    /**
     * Full constructor
     * @param id
     * @param constants
     */
    public IRGlobal(IRIdentifier id, List<IRValue> contents) {
        this.id = id;
        this.contents = contents;
    }
    
    /**
     * String constructor
     * @param id
     * @param charType
     * @param value
     */
    public IRGlobal(IRIdentifier id, IRType charType, String value) {
        this(id, new ArrayList<>());
        
        for(int i = 0; i < value.length(); i++) {
            this.contents.add(new IRConstant(value.charAt(i), charType));
        }
    }
    
    /**
     * Single-value constructor
     * @param id
     * @param value
     */
    public IRGlobal(IRIdentifier id, IRValue value) {
        this(id, new ArrayList<>());
        this.contents.add(value);
    }
    
    /**
     * Empty constructor
     * @param id
     */
    public IRGlobal(IRIdentifier id) {
        this(id, new ArrayList<>());
    }
    
    /**
     * Add a constant to the value of this global
     * @param con
     */
    public void addConstant(IRConstant con) {
        this.contents.add(con);
    }
    
    public IRIdentifier getID() { return this.id; }
    public List<IRValue> getContents() { return this.contents; }
    
}
