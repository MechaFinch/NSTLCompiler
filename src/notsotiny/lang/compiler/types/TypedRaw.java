package notsotiny.lang.compiler.types;

import notsotiny.asm.resolution.ResolvableValue;
import notsotiny.lang.compiler.CompilationException;

/**
 * A typed integer
 * 
 * @author Mechafinch
 */
public class TypedRaw implements TypedValue {
    
    public static final String CAUSE_UNRESOLVED = "unresolved";
    
    private NSTLType t;
    private ResolvableValue v;
    
    public TypedRaw(ResolvableValue v, RawType t) {
        this.v = v;
        this.t = t;
    }
    
    /**
     * Gets the resolved value of this TypedRaw. If not resolved, throws an exception.
     * @return
     * @throws CompilationException
     */
    public long getResolvedValue() throws CompilationException {
        if(this.v.isResolved()) {
            return this.v.value();
        } else {
            throw new CompilationException(CAUSE_UNRESOLVED);
        }
    }

    @Override
    public NSTLType getType() {
        return t;
    }
    
    public RawType getRawType() {
        if(this.t instanceof RawType rt) {
            return rt;
        } else {
            return RawType.PTR;
        }
    }
    
    @Override
    public byte[] getBytes() {
        byte[] b = new byte[t.getSize()];
        
        long v2 = v.value();
        for(int i = 0; i < b.length; i++) {
            b[i] = (byte)(v2 & 0xFF);
            v2 >>= 8;
        }
        
        return b;
    }
    
    public ResolvableValue getValue() { return this.v; }
    
    @Override
    public String toString() {
        return t + " " + v;
    }

    @Override
    public boolean convertType(NSTLType newType) {
        newType = newType.getRealType();
        
        if(newType instanceof RawType tr) {
            if(newType.getSize() != 0) this.t = tr;
            return true;
        } else if(newType instanceof PointerType pt) {
            this.t = pt;
            return true;
        }
        
        return false;
    }
    
    @Override
    public TypedValue convertCopy(NSTLType newType) {
        TypedValue tv = new TypedRaw(this.v, this.t instanceof RawType rt ? rt : RawType.I32);
        tv.convertType(newType);
        return tv;
    }
    
}
