package notsotiny.lang.compiler.types;

public interface TypedValue {
    
    /**
     * Convert the type of this value to a new type
     * 
     * @param newType
     * @return true if successful, false if unsuccessful
     */
    public boolean convertType(NSTLType newType);
    
    /**
     * @return type
     */
    public NSTLType getType();
    
    /**
     * @return value in bytes
     */
    public byte[] getBytes();
    
    public default long toLong() {
        if(this instanceof TypedRaw tr) return tr.getValue().value();
        
        long v = 0;
        byte[] bs = getBytes();
        int size = getType().getSize();
        
        for(int i = size - 1; i >= 0; i--) {
            if(i < bs.length) v |= (bs[i] & 0xFF) << (8 * i);
        }
        
        return v;
    }
    
    public default boolean equals(TypedValue v2) {
        if(this instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
            return tr1.getValue().value() == tr2.getValue().value();
        } else if(this instanceof TypedRaw tr) {
            byte[] bytes = v2.getBytes();
            long v = tr.getValue().value();
            
            // sign or zero extend
            if(((RawType) tr.getType()).isSigned()) {
                for(int i = 0; i < bytes.length; i++) {
                    if(bytes[i] != (v & 0xFF)) return false;
                    v >>>= 1;
                }
            } else {
                for(int i = 0; i < bytes.length; i++) {
                    if(bytes[i] != (v & 0xFF)) return false;
                    v >>= 1;
                }
            }
        } else if(v2 instanceof TypedRaw tr) {
            byte[] bytes = this.getBytes();
            long v = tr.getValue().value();
            
            // sign/zero extend
            if(((RawType) tr.getType()).isSigned()) {
                for(int i = 0; i < bytes.length; i++) {
                    if(bytes[i] != (v & 0xFF)) return false;
                    v >>>= 1;
                }
            } else {
                for(int i = 0; i < bytes.length; i++) {
                    if(bytes[i] != (v & 0xFF)) return false;
                    v >>= 1;
                }
            }
        } else {
            // compare bytes
            byte[] bytes1 = getBytes(),
                   bytes2 = v2.getBytes();
            
            if(bytes1.length != bytes2.length) return false;
            
            for(int i = 0; i < bytes1.length; i++) {
                if(bytes1[i] != bytes2[i]) return false;
            }
        }
        
        return true;
    }
}
