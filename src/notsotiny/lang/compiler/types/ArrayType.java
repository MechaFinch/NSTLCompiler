package notsotiny.lang.compiler.types;

import java.util.List;

import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.ir.parts.IRType;

/**
 * Constant-size arrays
 * 
 * @author Mechafinch
 */
public class ArrayType implements NSTLType {
    
    private NSTLType memberType;
    private int length;
    
    public ArrayType(NSTLType memberType, int length) {
        this.memberType = memberType.getRealType();
        this.length = length;
    }
    
    @Override
    public IRType getIRType() throws CompilationException {
        return IRType.I32;
    }
    
    @Override
    public int getSize() {
        return this.memberType.getSize() * this.length;
    }
    
    @Override
    public boolean isSigned() {
        return false;
    }
    
    public int getLength() { return this.length; }
    public NSTLType getMemberType() { return this.memberType.getRealType(); }
    
    @Override
    public String toString() {
        return "array of " + this.length + " (" + this.memberType.getName() + ")"; 
    }

    @Override
    public String getName() {
        return this.memberType.getName() + "%a" + this.length;
    }
    
    @Override
    public boolean equals(NSTLType t) {
        t = t.getRealType();
        
        if(t instanceof ArrayType at) {
            return this.memberType.equals(at.memberType) && this.length == at.length;
        }
        
        return false;
    }

    @Override
    public boolean updateSize(List<String> updatedNames) {
        return this.memberType.updateSize(updatedNames);
    }
}
