package notsotiny.lang.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a map from locals to arguments
 * 
 * @author Mechafinch
 */
public class IRArgumentMapping {
    
    // The contents of the list are mapped by index to the associated argument list
    private List<IRValue> args;
    
    /**
     * @param args
     */
    public IRArgumentMapping(List<IRValue> args) {
        this.args = args;
    }
    
    /**
     * Empty constructor
     */
    public IRArgumentMapping() {
        this.args = new ArrayList<>();
    }
    
    /**
     * Get the mapping for an argument
     * @param index
     * @return
     */
    public IRValue getMapping(int index) {
        return this.args.get(index);
    }
    
    /**
     * Add a mapping to the list
     * @param arg
     */
    public void addMapping(IRValue arg) {
        this.args.add(arg);
    }
    
    public List<IRValue> getMappingList() { return this.args; }
    
}
