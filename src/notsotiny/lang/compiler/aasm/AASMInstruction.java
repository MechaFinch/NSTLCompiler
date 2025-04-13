package notsotiny.lang.compiler.aasm;

import notsotiny.asm.Register;
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
    
    public AASMInstructionMeta getMeta() {
        AASMInstructionMeta meta = new AASMInstructionMeta();
        
        meta.op = this.op;
        meta.condition = this.condition;
        
        meta.causesFlush = switch(this.op) {
            case CALL, CALLA, RET, JMP, JMPA, JCC
                    -> true;
            default -> false;
        };
        
        if(this.destination != null) {
            switch(this.destination) {
                case AASMMachineRegister reg: {
                    meta.destIsRegister = true;
                    meta.destRegister = reg.reg();
                    meta.destType = reg.getType();
                    break;
                }
                
                case AASMMemory mem: {
                    meta.destIsMemory = true;
                    meta.destBase = asReg(mem.getBase(), true);
                    meta.destIndex = asReg(mem.getIndex(), true);
                    meta.destScale = asInt(mem.getScale(), 1);
                    meta.destType = mem.getType();
                    
                    if(mem.getOffset() instanceof AASMCompileConstant ccon) {
                        meta.destOffsIsCCon = true;
                        meta.destOffset = ccon.value();
                    }
                    break;
                }
                
                default:
                    throw new IllegalArgumentException("Unexpected destination type: " + this);
            }
        }
        
        if(this.source != null) {
            switch(this.source) {
                case AASMMachineRegister reg: {
                    meta.sourceIsRegister = true;
                    meta.sourceRegister = reg.reg();
                    meta.sourceType = reg.getType();
                    break;
                }
                
                case AASMMemory mem: {
                    meta.sourceIsMemory = true;
                    meta.sourceBase = asReg(mem.getBase(), true);
                    meta.sourceIndex = asReg(mem.getIndex(), true);
                    meta.sourceScale = asInt(mem.getScale(), 1);
                    meta.sourceType = mem.getType();
                    
                    if(mem.getOffset() instanceof AASMCompileConstant ccon) {
                        meta.sourceOffsIsCCon = true;
                        meta.sourceOffset = ccon.value();
                    }
                    break;
                }
                
                case AASMCompileConstant cc: {
                    meta.sourceIsCompileConstant = true;
                    meta.sourceValue = asInt(cc, 0);
                    meta.sourceType = cc.getType();
                    break;
                }
                
                case AASMLinkConstant lc: {
                    meta.sourceIsLinkConstant = true;
                    meta.sourceType = lc.getType();
                    break;
                }
                
                default:
                    throw new IllegalArgumentException("Unexpected destination type: " + this);
            }
        }
        
        meta.sourceIsBPSlot = meta.sourceIsMemory && meta.sourceBase == Register.BP && meta.sourceIndex == Register.NONE && meta.sourceOffsIsCCon;
        meta.destIsBPSlot = meta.destIsMemory && meta.destBase == Register.BP && meta.destIndex == Register.NONE && meta.destOffsIsCCon;
        
        return meta;
    }
    
    /**
     * Returns part as a Register
     * @param part
     * @return
     */
    private static Register asReg(AASMPart part, boolean allowCC0) {
        if(part == null) {
            return Register.NONE;
        }
        
        return switch(part) {
            case AASMMachineRegister reg    -> reg.reg();
            case AASMCompileConstant ccon   -> {
                if(allowCC0 && ccon.value() == 0) {
                    yield Register.NONE;
                } else {
                    throw new IllegalArgumentException("Unexpected part as register: " + part);
                }
            }
            default                         -> throw new IllegalArgumentException("Unexpected part as register: " + part);
        };
    }
    
    /**
     * Returns part as an integer
     * @param part
     * @param def Default on-null value
     * @return
     */
    private static int asInt(AASMPart part, int def) {
        if(part == null) {
            return def;
        }
        
        return switch(part) {
            case AASMCompileConstant cc -> cc.value();
            default                     -> throw new IllegalArgumentException("Unexpected part as integer: " + part);
        };
    }
    
}
