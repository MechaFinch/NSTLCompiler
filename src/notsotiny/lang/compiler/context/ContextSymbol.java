package notsotiny.lang.compiler.context;

import notsotiny.asm.resolution.ResolvableLocationDescriptor;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.TypedValue;

/**
 * Symbol in the context stack
 * 
 * @author Mechafinch
 */
public class ContextSymbol implements ContextEntry {
    public String name;
    public NSTLType type;
    public TypedValue constantValue;
    public ResolvableLocationDescriptor variableDescriptor;
    public boolean isConstant;
    
    /**
     * variable constructor
     * 
     * @param name
     * @param type
     * @param descriptor
     */
    public ContextSymbol(String name, NSTLType type, ResolvableLocationDescriptor descriptor) {
        this.name = name;
        this.type = type;
        this.variableDescriptor = descriptor;
        this.constantValue = null;
        this.isConstant = false;
    }
    
    /**
     * Constant constructor
     * 
     * @param name
     * @param tv
     */
    public ContextSymbol(String name, TypedValue tv) {
        this.name = name;
        this.type = tv.getType();
        this.variableDescriptor = null;
        this.constantValue = tv;
        this.isConstant = true;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(this.name);
        
        if(this.isConstant) {
            sb.append(" = ");
            sb.append(this.constantValue);
        } else {
            sb.append(": ");
            sb.append(this.type);
            sb.append(" at ");
            sb.append(this.variableDescriptor);
        }
        
        return sb.toString();
    }
}