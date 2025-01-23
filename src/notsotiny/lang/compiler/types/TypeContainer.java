package notsotiny.lang.compiler.types;

/**
 * Has a type but no value.
 */
public class TypeContainer implements TypedValue {
    
    private NSTLType type;
    
    /**
     * @param type
     */
    public TypeContainer(NSTLType type) {
        this.type = type;
    }

    @Override
    public boolean convertType(NSTLType newType) {
        newType = newType.getRealType();
        
        // Copied from TypedRaw, TypedArray, TypedStrucutre
        if(type instanceof RawType || type instanceof PointerType) {
            if(newType instanceof RawType rt) {
                if(rt.getSize() != 0) {
                    this.type = newType;
                    return true;
                }
                
                return true;
            } else if(newType instanceof PointerType) {
                this.type = newType;
                return true;
            }
        } else if(this.type instanceof ArrayType ato) {
            if(newType instanceof ArrayType atn) {
                return ato.getLength() == atn.getLength() && ato.getMemberType().equals(atn.getMemberType());
            }
        } else if(this.type instanceof StructureType) {
            return this.type.equals(newType);
        }
        
        return false;
    }
    
    @Override
    public TypedValue convertCopy(NSTLType newType) {
        TypedValue tv = new TypeContainer(this.type);
        tv.convertType(newType);
        return tv;
    }

    @Override
    public NSTLType getType() {
        return this.type;
    }

    @Override
    public byte[] getBytes() {
        return new byte[this.type.getSize()];
    }
    
    @Override
    public String toString() {
        return this.type.toString();
    }
    
}
