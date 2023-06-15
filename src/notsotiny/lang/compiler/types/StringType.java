package notsotiny.lang.compiler.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * strings are kinda their own thing
 * 
 * @author Mechafinch
 */
public class StringType implements NSTLType, TypedValue {
    
    private String str;
    
    private static final Map<String, String> escapes = new HashMap<>();
    
    static {
        escapes.put("\\0", "\0");
        escapes.put("\\n", "\n");
        escapes.put("\\t", "\t");
        escapes.put("\\\"", "\"");
        escapes.put("\\b", "\b");
        escapes.put("\\f", "\f");
        escapes.put("\\\\", "\\");
    }
    
    public StringType(String constantString) {
        for(Entry<String, String> e : escapes.entrySet()) {
            constantString = constantString.replace(e.getKey(), e.getValue());
        }
        
        this.str = constantString;
    }
    
    public String getValue() { return this.str; }

    @Override
    public int getSize() {
        return this.str.length();
    }

    @Override
    public NSTLType getType() {
        return this;
    }
    
    @Override
    public byte[] getBytes() {
        return this.str.getBytes();
    }
    
    @Override
    public String toString() {
        return "\"" + str + "\"";
    }

    @Override
    public String getName() {
        return "%string" + str.length();
    }

    @Override
    public boolean convertType(NSTLType newType) {
        // no conversions allowed
        if(newType instanceof StringType st) {
            return st.getSize() == this.str.length() || st.str.equals("");
        }
        
        return false;
    }

    @Override
    public boolean equals(NSTLType t) {
        if(t instanceof StringType st) {
            return st.getSize() == this.str.length();
        }
        
        return false;
    }

    @Override
    public boolean updateSize(List<String> updatedNames) {
        return false;
    }
    
}
