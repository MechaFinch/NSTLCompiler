package notsotiny.lang.ir.parts;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains one or more IRConstants
 * Constants are arranged linearly in memory
 * 
 * Constant global -> yields its value
 * Variable global -> yields pointer i32
 * 
 * IRValue is IRConstant    -> is type of IRConstant
 * IRValue is IRIdentifier  -> is pointer i32
 * 
 * @author Mechafinch
 */
public class IRGlobal {
    
    // Identifier
    IRIdentifier id;
    
    // Content constants
    private List<IRValue> contents;
    
    // Are the contents of the global constant?
    private boolean constant;
    
    /**
     * Full constructor
     * @param id
     * @param contents
     * @param constant
     */
    public IRGlobal(IRIdentifier id, List<IRValue> contents, boolean constant) {
        this.id = id;
        this.contents = contents;
        this.constant = constant;
    }
    
    /**
     * String constructor
     * @param id
     * @param charType
     * @param value
     * @param constant
     */
    public IRGlobal(IRIdentifier id, IRType charType, String value, boolean constant) {
        this(id, new ArrayList<>(), constant);
        
        for(int i = 0; i < value.length(); i++) {
            this.contents.add(new IRConstant(value.charAt(i), charType));
        }
    }
    
    /**
     * Single-value constructor
     * @param id
     * @param value
     * @param constant
     */
    public IRGlobal(IRIdentifier id, IRValue value, boolean constant) {
        this(id, new ArrayList<>(), constant);
        this.contents.add(value);
    }
    
    /**
     * Empty constructor
     * @param id
     */
    public IRGlobal(IRIdentifier id, boolean constant) {
        this(id, new ArrayList<>(), constant);
    }
    
    /**
     * Add a value to the contents of this global
     * @param val
     */
    public void addValue(IRValue val) {
        this.contents.add(val);
    }
    
    public IRIdentifier getID() { return this.id; }
    public List<IRValue> getContents() { return this.contents; }
    public boolean isConstant() { return this.constant; }
    
}
