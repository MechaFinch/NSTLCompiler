package notsotiny.lang.compiler.types;

import java.util.Map;

/**
 * Structure instance
 * 
 * @author Mechafinch
 */
public class TypedStructure implements TypedValue {
    
    private StructureType t;
    private Map<String, TypedValue> m;
    
    public TypedStructure(Map<String, TypedValue> m, StructureType t) {
        this.t = t;
        this.m = m;
    }

    @Override
    public NSTLType getType() {
        return this.t;
    }
    
    @Override
    public byte[] getBytes() {
        byte[] b = new byte[t.getSize()];
        
        int i = 0;
        for(String s : t.getMemberNames()) {
            byte[] b2 = m.get(s).getBytes();
            
            System.arraycopy(b2, 0, b, i, b2.length);
            i += b2.length;
        }
        
        return b;
    }
    
    public TypedValue getMember(String member) { return this.m.get(member); }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(this.t.getName());
        sb.append(" (");
        
        for(String mem : this.t.getMemberNames()) {
            sb.append(this.m.get(mem));
            sb.append(", ");
        }
        
        sb.delete(sb.length() - 2, sb.length());
        sb.append(")");
        
        return sb.toString();
    }

    @Override
    public boolean convertType(NSTLType newType) {
        // no conversion
        return this.t.equals(newType); 
    }
    
}
