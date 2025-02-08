package notsotiny.lang.ir.parts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Describes a map from locals to arguments
 * 
 * @author Mechafinch
 */
public class IRArgumentMapping {
    
    private List<IRIdentifier> ordering;
    
    private Map<IRIdentifier, IRValue> args;
    
    /**
     * @param args
     */
    public IRArgumentMapping(Map<IRIdentifier, IRValue> args, List<IRIdentifier> ordering) {
        this.ordering = ordering;
        this.args = args;
    }
    
    public IRArgumentMapping(List<IRIdentifier> ordering) {
        this(new HashMap<>(), ordering);
    }
    
    /**
     * Empty constructor
     */
    public IRArgumentMapping() {
        this(new HashMap<>(), new ArrayList<>());
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
        if(!this.ordering.contains(id)) {
            this.ordering.add(id);
        }
        
        this.args.put(id, arg);
    }
    
    /**
     * Remove an argument from the mapping
     * @param id
     */
    public void removeArgument(IRIdentifier id) {
        this.ordering.remove(id);
        this.args.remove(id);
    }
    
    /**
     * Add all mappings in map to this
     * @param map
     */
    public void putAll(IRArgumentMapping map) {
        for(IRIdentifier id : map.getOrdering()) {
            this.addMapping(id, map.getMapping(id));
        }
    }
    
    public List<IRIdentifier> getOrdering() { return this.ordering; }
    public Map<IRIdentifier, IRValue> getMap() { return this.args; }
    
}
