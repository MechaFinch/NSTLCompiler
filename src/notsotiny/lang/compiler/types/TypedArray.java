package notsotiny.lang.compiler.types;

import java.util.List;

public class TypedArray implements TypedValue {
    
    private ArrayType t;
    private List<TypedValue> v;
    
    public TypedArray(List<TypedValue> v, ArrayType t) {
        this.t = t;
        this.v = v;
    }

    @Override
    public NSTLType getType() {
        return this.t;
    }
    
    @Override
    public byte[] getBytes() {
        byte[] arr = new byte[t.getSize()];
        
        for(int i = 0; i < v.size(); i++) {
            System.arraycopy(v.get(i).getBytes(), 0, arr, i, t.getMemberType().getSize());
        }
        
        return arr;
    }
    
    public TypedValue getValue(int index) { return this.v.get(index); }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("[");
        
        for(TypedValue tv : v) {
            sb.append(tv);
            sb.append(", ");
        }
        
        sb.delete(sb.length() - 2, sb.length());
        sb.append("]");
        
        return sb.toString();
    }

    @Override
    public boolean convertType(NSTLType newType) {
        // no conversions
        if(newType instanceof ArrayType at) {
            return this.t.getLength() == at.getLength() && this.t.getMemberType().equals(at.getMemberType());
        }
        
        return false;
    }
}
