package notsotiny.lang.compiler.types;

import java.util.List;

/**
 * A lovely pointer
 * 
 * @author Mechafinch
 */
public class PointerType implements NSTLType {
    
    private NSTLType pointedType;
    
    public PointerType(NSTLType pointedType) {
        this.pointedType = pointedType;
    }

    @Override
    public int getSize() {
        return 4;
    }
    
    public NSTLType getPointedType() { return this.pointedType; }
    
    @Override
    public String toString() {
        return "(" + this.pointedType.getName() + ") pointer";
    }

    @Override
    public String getName() {
        return "(" + this.pointedType.getName() + ") pointer";
    }

    @Override
    public boolean equals(NSTLType t) {
        if(t instanceof PointerType pt) {
            NSTLType t1 = this.pointedType,
                     t2 = pt.pointedType;
            
            // pointers to strings are equivalent to pointers to u8s
            if(t1 instanceof StringType st) t1 = RawType.U8;
            if(t2 instanceof StringType st) t2 = RawType.U8;
            
            // pointers to values and to arrays of those values are equivalent
            if(t1 instanceof ArrayType at) t1 = at.getMemberType();
            if(t2 instanceof ArrayType at) t2 = at.getMemberType();
            
            return t1.equals(t2);
        } else if(t instanceof RawType rt) {
            // raw pointers
            return rt.getSize() == 4;
        }
        
        return false;
    }

    @Override
    public boolean updateSize(List<String> updatedNames) {
        return this.pointedType.updateSize(updatedNames);
    }
}
