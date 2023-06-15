package notsotiny.lang.compiler.types;

import java.util.List;

/**
 * Represents a function header
 * 
 * @author Mechafinch
 */
public class FunctionHeader {
    
    private String name;
    private List<String> argumentNames;
    private List<NSTLType> argumentTypes;
    
    private NSTLType returnType;
    
    /**
     * Named constructor
     * 
     * @param name
     * @param argumentNames
     * @param argumentTypes
     * @param returnType
     */
    public FunctionHeader(String name, List<String> argumentNames, List<NSTLType> argumentTypes, NSTLType returnType) {
        this.name = name;
        this.argumentNames = argumentNames;
        this.argumentTypes = argumentTypes;
        this.returnType = returnType;
    }
    
    public String getName() { return this.name; }
    public List<String> getArgumentNames() { return this.argumentNames; }
    public List<NSTLType> getArgumentTypes() { return this.argumentTypes; }
    public NSTLType getReturnType() { return this.returnType; }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("func ");
        sb.append(this.name);
        sb.append(" (");
        
        if(this.argumentNames.size() > 0) {
            for(int i = 0; i < this.argumentNames.size(); i++) {
                sb.append(this.argumentTypes.get(i));
                sb.append(" ");
                sb.append(this.argumentNames.get(i));
                sb.append(", ");
            }
            
            sb.delete(sb.length() - 2, sb.length());
        }
        
        sb.append(") -> ");
        sb.append(this.returnType);
        
        return sb.toString();
    }
}
