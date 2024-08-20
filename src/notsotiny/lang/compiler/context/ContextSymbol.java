package notsotiny.lang.compiler.context;

import notsotiny.asm.resolution.ResolvableConstant;
import notsotiny.asm.resolution.ResolvableLocationDescriptor;
import notsotiny.asm.resolution.ResolvableLocationDescriptor.LocationType;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.TypedValue;

/**
 * Symbol in the context stack
 * 
 * @author Mechafinch
 */
public class ContextSymbol implements ContextEntry {
    private String name;
    private NSTLType type;
    private TypedValue constantValue;
    private ResolvableLocationDescriptor variableDescriptor;
    private boolean isConstant;
    
    /**
     * variable constructor
     * 
     * @param name
     * @param type
     * @param descriptor
     */
    public ContextSymbol(String name, NSTLType type, ResolvableLocationDescriptor descriptor) {
        this.name = name;
        this.type = type.getRealType();
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
        this.type = tv.getType().getRealType();
        this.variableDescriptor = new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(tv.toLong()));
        this.constantValue = tv;
        this.isConstant = true;
    }
    
    public String getName() { return this.name; }
    public NSTLType getType() { return this.type.getRealType(); }
    public TypedValue getConstantValue() { return this.constantValue; }
    public ResolvableLocationDescriptor getVariableDescriptor() { return this.variableDescriptor; }
    public boolean getIsConstant() { return this.isConstant; }
    
    public void setName(String n) { this.name = n; }
    public void setType(NSTLType t) { this.type = t.getRealType(); }
    public void setConstantValue(TypedValue v) { this.constantValue = v; }
    public void setVariableDescriptor(ResolvableLocationDescriptor d) { this.variableDescriptor = d; }
    public void setIsConstant(boolean c) { this.isConstant = c; }
    
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