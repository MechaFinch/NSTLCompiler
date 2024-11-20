package notsotiny.lang.ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes a map from locals to arguments
 * 
 * @author Mechafinch
 */
public class IRArgumentMapping {
    
    // The contents of the list are mapped by index to the associated argument list
    private Map<IRIdentifier, IRValue> args;
    
    /**
     * @param args
     */
    public IRArgumentMapping(Map<IRIdentifier, IRValue> args) {
        this.args = args;
    }
    
    /**
     * Empty constructor
     */
    public IRArgumentMapping() {
        this.args = new HashMap<>();
    }
    
    /**
     * Get the mapping for an argument
     * @param index
     * @return
     */
    public IRValue getMapping(IRIdentifier id) {
        return this.args.get(id);
    }
    
    /**
     * Add a mapping to the list
     * @param arg
     */
    public void addMapping(IRIdentifier id, IRValue arg) {
        this.args.put(id, arg);
    }
    
    public Map<IRIdentifier, IRValue> getMap() { return this.args; }
    
}
