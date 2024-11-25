package notsotiny.lang.compiler.types;

import java.util.List;

import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.ir.IRType;

/**
 * A type alias
 * 
 * @author Mechafinch
 */
public class AliasType implements NSTLType {
    
    private String name;
    private NSTLType realType;
    
    /**
     * full constructor
     * 
     * @param name
     * @param realType
     */
    public AliasType(String name, NSTLType realType) {
        this.name = name;
        this.realType = realType.getRealType();
    }
    
    /**
     * placeholder constructor
     * 
     * @param name
     */
    public AliasType(String name) { 
        this.name = name;
        this.realType = null;
    }
    
    /**
     * @param t
     */
    public void setRealType(NSTLType t) {
        this.realType = t.getRealType();
    }
    
    @Override
    public IRType getIRType() throws CompilationException {
        return this.realType.getIRType();
    }
    
    @Override
    public NSTLType getRealType() {
        if(this.realType == null) return this;
        return this.realType.getRealType();
    }
    
    @Override
    public String toString() {
        return this.name + ": " + (this.realType == null ? "(incomplete alias)" : ("alias of " + this.realType.getName()));
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public int getSize() {
        if(this.realType == null) return 0;
        return this.realType.getSize();
    }
    
    @Override
    public boolean isSigned() { 
        if(this.realType == null) return false;
        return this.realType.isSigned();
    }

    @Override
    public boolean updateSize(List<String> updatedNames) {
        if(updatedNames.contains(this.name)) return false;
        updatedNames.add(this.name);
        
        return this.realType.updateSize(updatedNames);
    }

    @Override
    public boolean equals(NSTLType t) {
        return this.getRealType().equals(t.getRealType());
    }
    
}
