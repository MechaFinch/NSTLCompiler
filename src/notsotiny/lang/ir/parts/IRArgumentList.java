package notsotiny.lang.ir.parts;

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
     * Gets the type of an argument given its index
     * @param index
     * @return
     */
    public IRType getType(int index) {
        return this.types.get(index);
    }
    
    /**
     * Gets the type of an argument given its name
     * @param name
     * @return
     */
    public IRType getType(IRIdentifier name) {
        return this.types.get(this.names.indexOf(name));
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
     * Remove an argument by name
     * @param name
     * @return True if found & removed
     */
    public boolean removeArgument(IRIdentifier name) {
        for(int i = 0; i < this.names.size(); i++) {
            if(this.names.get(i).equals(name)) {
                this.names.remove(i);
                this.types.remove(i);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Replace an argument by name
     * @param replacedName
     * @param newName
     * @param newType
     * @return True if found and replaced
     */
    public boolean replaceArgument(IRIdentifier prevName, IRIdentifier newName, IRType newType) {
        for(int i = 0; i < this.names.size(); i++) {
            if(this.names.get(i).equals(prevName)) {
                this.names.set(i, newName);
                this.types.set(i, newType);
                return true;
            }
        }
        
        return false;
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
    
    /**
     * Gets the [BP + x] offset used to access this argument following the
     * calling convention
     * @param argName
     * @return
     */
    public int getBPOffset(IRIdentifier argName) {
        int offset = 8;
        
        for(int i = 0; i < this.names.size(); i++) {
            if(this.names.get(i).equals(argName)) {
                return offset;
            }
            
            offset += this.types.get(i).getSize();
        }
        
        throw new IllegalArgumentException(argName + " is not in " + this);
    }
    
    public List<IRIdentifier> getNameList() { return this.names; }
    public List<IRType> getTypeList() { return this.types; }
    
}
