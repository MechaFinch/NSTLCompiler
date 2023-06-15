package notsotiny.lang.compiler.context;

import java.util.HashMap;
import java.util.Map;

import notsotiny.asm.Register;

/**
 * Marks and describes a context in the context stack
 * This marker is for 
 * 
 * @author Mechafinch
 */
public class AllocatedContextMarker implements ContextMarker {
    public Map<Register, String> registerAllocations;
    public int stackAllocationSize;
    
    public AllocatedContextMarker(Map<Register, String> registerAllocations, int stackAllocationSize) {
        this.registerAllocations = registerAllocations;
        this.stackAllocationSize = stackAllocationSize;
    }
    
    /**
     * Copy Constructor
     */
    public AllocatedContextMarker(AllocatedContextMarker cm) {
        this.registerAllocations = new HashMap<>(cm.registerAllocations);
        this.stackAllocationSize = cm.stackAllocationSize;
    }
    
    /**
     * Gets the next available register of the given size. If none is available, NONE is returned
     * 
     * @param size
     * @return
     */
    public Register getNextUnallocatedRegister(int size) {
        // bad size = no
        if(size == 3 || size > 4) return Register.NONE;
        
        if(size == 4) {
            // JI LK BC
            if(registerAvailable(Register.LK)) return Register.LK;
            if(registerAvailable(Register.BC)) return Register.BC;
            if(registerAvailable(Register.JI)) return Register.JI;
        } else if(size == 2) {
            // B, C, I, J, K, L
            if(registerAvailable(Register.K)) return Register.K;
            if(registerAvailable(Register.L)) return Register.L;
            if(registerAvailable(Register.B)) return Register.B;
            if(registerAvailable(Register.C)) return Register.C;
            if(registerAvailable(Register.I)) return Register.I;
            if(registerAvailable(Register.J)) return Register.J;
        } else {
            // BL, BH, CL, CH
            if(registerAvailable(Register.BL)) return Register.BL;
            if(registerAvailable(Register.BH)) return Register.BH;
            if(registerAvailable(Register.CL)) return Register.CL;
            if(registerAvailable(Register.CH)) return Register.CH;
        }
        
        return Register.NONE;
    }
    
    /**
     * Returns true if this register is unallocated
     * 
     * @param r
     * @return
     */
    public boolean registerAvailable(Register r) {
        switch(r) {
            case B:
                return !(registerAllocations.containsKey(Register.BC) || registerAllocations.containsKey(Register.B) || registerAllocations.containsKey(Register.BH) || registerAllocations.containsKey(Register.BL));
                
            case BC:
                return registerAvailable(Register.B) && registerAvailable(Register.C); 
                
            case BH:
                return !registerAllocations.containsKey(Register.BH) && registerAvailable(Register.B);
                
            case BL:
                return !registerAllocations.containsKey(Register.BL) && registerAvailable(Register.B);
                
            case C:
                return !(registerAllocations.containsKey(Register.BC) || registerAllocations.containsKey(Register.C) || registerAllocations.containsKey(Register.CH) || registerAllocations.containsKey(Register.CL));
                
            case CD:
                return registerAvailable(Register.C) && registerAvailable(Register.D); 
                
            case CH:
                return !registerAllocations.containsKey(Register.CH) && registerAvailable(Register.C); 
                
            case CL:
                return !registerAllocations.containsKey(Register.CL) && registerAvailable(Register.C);
                
            case I:
                return !(registerAllocations.containsKey(Register.JI) || registerAllocations.containsKey(Register.I));
                
            case J:
                return !(registerAllocations.containsKey(Register.JI) || registerAllocations.containsKey(Register.J));
                
            case JI:
                return registerAvailable(Register.J) && registerAvailable(Register.I); 
                
            case K:
                return !(registerAllocations.containsKey(Register.LK) || registerAllocations.containsKey(Register.K));
                
            case L:
                return !(registerAllocations.containsKey(Register.LK) || registerAllocations.containsKey(Register.L));
                
            case LK:
                return registerAvailable(Register.L) && registerAvailable(Register.K);
                
            default:
                return false;
        }
    }

    @Override
    public ContextMarker duplicate() {
        return new AllocatedContextMarker(this);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Allocated Registers: ");
        sb.append(this.registerAllocations.toString());
        sb.append(" Stack Allocation Size: ");
        sb.append(this.stackAllocationSize);
        
        return sb.toString();
    }
}