package notsotiny.lang.compiler.types;

import java.util.List;

/**
 * A raw integer type
 * 
 * @author Mechafinch
 */
public class RawType implements NSTLType {
    public static final RawType U8 = new RawType("u8", 1, false),
                                I8 = new RawType("i8", 1, true),
                                BOOLEAN = new RawType("boolean", 1, false),
                                U16 = new RawType("u16", 2, false),
                                I16 = new RawType("i16", 2, true),
                                U32 = new RawType("u32", 4, false),
                                I32 = new RawType("i32", 4, true),
                                PTR = new RawType("ptr", 4, false),
                                NONE = new RawType("none", 0, true);
    
    private String identifier;
    private int size;
    private boolean signed;
    
    private RawType(String identifier, int size, boolean signed) {
        this.size = size;
        this.identifier = identifier;
        this.signed = signed;
    }
    
    /**
     * Get a raw type with a length the maximum of the two
     * 
     * @param t
     * @return
     */
    public RawType promote(NSTLType t) {
        if(t instanceof RawType rt && rt.size > this.size) return rt;
        else return this;
    }
    
    public String getIdentifier() { return this.identifier; }
    public int getSize() { return this.size; }
    public boolean isSigned() { return this.signed; }
    
    @Override
    public String toString() {
        return this.identifier;
    }

    @Override
    public String getName() {
        return this.identifier;
    }

    @Override
    public boolean equals(NSTLType t) {
        // u32/i32/ptr = pointer
        if(t instanceof PointerType pt) {
            return this.size == 4;
        }
        
        return t instanceof RawType rt && rt.size == this.size;
    }

    @Override
    public boolean updateSize(List<String> updatedNames) {
        return false;
    }
}
