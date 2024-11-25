package notsotiny.lang.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of arguments (to a basic block or function)
 * 
 * @author Mechafinch
 */
public class IRArgumentList {
    
    // Name of each argument
    private List<IRIdentifier> names;
    
    // Type of each argument
    private List<IRType> types;
    
    /**
     * @param names
     * @param types
     */
    public IRArgumentList(List<IRIdentifier> names, List<IRType> types) {
        this.names = names;
        this.types = types;
    }
    
    /**
     * Empty constructor
     */
    public IRArgumentList() {
        this.names = new ArrayList<>();
        this.types = new ArrayList<>();
    }
    
    /**
     * Gets the name of an argument
     * @param index
     * @return
     */
    public IRIdentifier getName(int index) {
        return this.names.get(index);
    }
    
    /**
     * Gets the type of an argument
     * @param index
     * @return
     */
    public IRType getType(int index) {
        return this.types.get(index);
    }
    
    /**
     * Gets the number of arguments
     * @return
     */
    public int getArgumentCount() {
        return this.names.size();
    }
    
    /**
     * Add an argument to the list
     * @param name
     * @param type
     */
    public void addArgument(IRIdentifier name, IRType type) {
        this.names.add(name);
        this.types.add(type);
    }
    
    /**
     * Move arguments from other to this
     * @param other
     */
    public void addAll(IRArgumentList other) {
        List<IRIdentifier> otherNames = other.getNameList();
        List<IRType> otherTypes = other.getTypeList();
        
        for(int i = 0; i < otherNames.size(); i++) {
            addArgument(otherNames.get(i), otherTypes.get(i));
        }
    }
    
    public List<IRIdentifier> getNameList() { return this.names; }
    public List<IRType> getTypeList() { return this.types; }
    
}
