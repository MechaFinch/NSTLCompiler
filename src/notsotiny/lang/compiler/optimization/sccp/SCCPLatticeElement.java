package notsotiny.lang.compiler.optimization.sccp;

import notsotiny.lang.ir.IRConstant;

/**
 * Represents a lattice element in SCCP
 */
public class SCCPLatticeElement {
    
    enum Type {
        TOP, VALUE, BOTTOM
    }
    
    private Type type;
    
    private IRConstant value;
    
    /**
     * Full constructor
     * @param type
     * @param value
     */
    private SCCPLatticeElement(Type type, IRConstant value) {
        this.type = type;
        this.value = value;
    }
    
    /**
     * known-constant constructor
     * @param value
     */
    public SCCPLatticeElement(IRConstant value) {
        this(Type.VALUE, value);
    }
    
    /**
     * Non known-constant constructor
     * @param type
     */
    public SCCPLatticeElement(Type type) {
        this(type, null);
    }
    
    /**
     * Lower with another lattice element
     * @param e
     * @return true if this element changed
     */
    public boolean lower(SCCPLatticeElement e) {
        switch(e.getType()) {    
            case TOP:
                return false;
            
            case VALUE:
                return lower(e.getValue());
                
            case BOTTOM:
            default:
                return lower();
        }
    }
    
    /**
     * Lower to a BOTTOM element
     * @return true if this element changed
     */
    public boolean lower() {
        boolean changed = this.type != Type.BOTTOM;
        
        this.type = Type.BOTTOM;
        this.value = null;
        
        return changed;
    }
    
    /**
     * Lower to a VALUE or BOTTOM element
     * @param v
     * @return true if this element changed
     */
    public boolean lower(IRConstant v) {
        if(this.type == Type.TOP) {
            this.type = Type.VALUE;
            this.value = v;
            return true;
        } else if(this.type == Type.VALUE) {
            if(this.value.equals(v)) {
                return false;
            } else {
                this.type = Type.BOTTOM;
                this.value = null;
                return true;
            }
        } else {
            return false;
        }
    }
    
    public Type getType() { return this.type; }
    public IRConstant getValue() { return this.value; }
    
    @Override
    public String toString() { 
        if(this.type == Type.VALUE) {
            return this.value.toString();
        } else {
            return this.type.toString();
        }
    }
    
}
