package notsotiny.lang.compiler.aasm;

import notsotiny.sim.Register;
import notsotiny.lang.ir.parts.IRCondition;
import notsotiny.lang.ir.parts.IRType;

/**
 * Data about an instruction
 */
public class AASMInstructionMeta {
    public AASMOperation op = null;                     // Opcode
    public boolean causesFlush = false;                 // Some instructions flush the peephole area
    
    public boolean destIsRegister = false,
                   destIsMemory = false,
                   destOffsIsCCon = false,              // True if dest offset is a compile constant
                   destIsBPSlot = false;                // True if dest is [BP - x]
    public Register destRegister = Register.NONE,
                    destBase = Register.NONE,
                    destIndex = Register.NONE;
    public int destScale = 0,
               destOffset = 0;
    public IRType sourceType = IRType.NONE;
    
    public boolean sourceIsRegister = false,
                   sourceIsMemory = false,
                   sourceIsCompileConstant = false,
                   sourceIsLinkConstant = false,
                   sourceOffsIsCCon = false,            // True if source offset is a compile constant
                   sourceIsBPSlot = false;              // True if source is [BP - x]
    public Register sourceRegister = Register.NONE,
                    sourceBase = Register.NONE,
                    sourceIndex = Register.NONE;
    public int sourceScale = 0,
               sourceOffset = 0,
               sourceValue = 0;
    public IRType destType = IRType.NONE;
    
    public IRCondition condition = IRCondition.NONE;    // condition
    
    /**
     * @param other
     * @return true if the source of this and other are the same register/slot
     */
    public boolean sourceEqualsSourceOf(AASMInstructionMeta other) {
        return (this.sourceIsRegister && other.sourceIsRegister && this.sourceRegister == other.sourceRegister) ||
               (this.sourceIsBPSlot && other.sourceIsBPSlot && this.sourceOffset == other.sourceOffset);
    }
    
    /**
     * @param other
     * @return true if the source of this and the dest of other are the same register/slot
     */
    public boolean sourceEqualsDestOf(AASMInstructionMeta other) {
        return (this.sourceIsRegister && other.destIsRegister && this.sourceRegister == other.destRegister) ||
               (this.sourceIsBPSlot && other.destIsBPSlot && this.sourceOffset == other.destOffset);
    }
    
    /**
     * @param other
     * @return true if the dest of this and other are the same register/slot
     */
    public boolean destEqualsDestOf(AASMInstructionMeta other) {
        return (this.destIsRegister && other.destIsRegister && this.destRegister == other.destRegister) ||
               (this.destIsBPSlot && other.destIsBPSlot && this.destOffset == other.destOffset);
    }
}
