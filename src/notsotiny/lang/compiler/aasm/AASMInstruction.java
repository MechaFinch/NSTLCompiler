package notsotiny.lang.compiler.aasm;

import notsotiny.lang.ir.parts.IRCondition;

/**
 * An instruction in AbstractAssembly
 */
public class AASMInstruction implements AASMPart {
    
    private AASMOperation op;
    
    // Location or PatternReference 
    AASMPart source, destination;
    
    IRCondition condition;
    
    /**
     * Full constructor
     * @param op
     * @param destination
     * @param source
     * @param condition
     */
    public AASMInstruction(AASMOperation op, AASMPart destination, AASMPart source, IRCondition condition) {
        this.op = op;
        this.source = source;
        this.destination = destination;
        this.condition = condition;
    }
    
    /**
     * No condition constructor
     * @param op
     * @param destination
     * @param source
     */
    public AASMInstruction(AASMOperation op, AASMPart destination, AASMPart source) {
        this(op, destination, source, IRCondition.NONE);
    }
    
    /**
     * Single argument w/ condition constructor
     * @param op
     * @param arg
     */
    public AASMInstruction(AASMOperation op, AASMPart arg, IRCondition condition) {
        this(op, null, null, condition);
        
        // Place arg according to op
        switch(op) {
            case PUSH, CALL, CALLA, JMP, JMPA, JCC:
                this.source = arg;
                break;
            
            case POP, INC, ICC, DEC, DCC, NOT, NEG:
                this.destination = arg;
                break;
            
            default:
                throw new IllegalArgumentException(op + " is not single argument");
        }
    }
    
    /**
     * Single argument constructor
     * @param op
     * @param arg
     */
    public AASMInstruction(AASMOperation op, AASMPart arg) {
        this(op, arg, IRCondition.NONE);
    }

    /**
     * No arguments constructor
     * @param op
     */
    public AASMInstruction(AASMOperation op) {
        this(op, null, null, IRCondition.NONE);
    }
    
    public AASMOperation getOp() { return this.op; }
    public AASMPart getSource() { return this.source; }
    public AASMPart getDestination() { return this.destination; }
    public IRCondition getCondition() { return this.condition; }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        if(this.op == AASMOperation.JCC) {
            sb.append("J");
            sb.append(this.condition);
        } else if(this.op == AASMOperation.CMOV) {
            sb.append("CMOV");
            sb.append(this.condition);
        } else {
            sb.append(this.op);
        }
        
        if(this.destination != null) {
            sb.append(" ");
            sb.append(this.destination);
            
            if(this.source != null) {
                sb.append(", ");
                sb.append(this.source);
            }
        } else if(this.source != null) {
            sb.append(" ");
            sb.append(this.source);
        }
        
        return sb.toString();
    }
    
}
