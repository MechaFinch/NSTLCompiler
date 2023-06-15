package notsotiny.lang.compiler.types;

import java.util.List;

/**
 * Constant-size arrays
 * 
 * @author Mechafinch
 */
public class ArrayType implements NSTLType {
    
    private NSTLType memberType;
    private int length;
    
    public ArrayType(NSTLType memberType, int length) {
        this.memberType = memberType;
        this.length = length;
    }
    
    @Override
    public int getSize() {
        return this.memberType.getSize() * this.length;
    }
    
    public int getLength() { return this.length; }
    public NSTLType getMemberType() { return this.memberType; }
    
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
