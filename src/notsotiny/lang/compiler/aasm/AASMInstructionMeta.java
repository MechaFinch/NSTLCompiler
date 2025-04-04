package notsotiny.lang.compiler.aasm;

import notsotiny.asm.Register;
import notsotiny.lang.ir.parts.IRCondition;
import notsotiny.lang.ir.parts.IRType;

/**
 * Data about an instruction
 */
public class AASMInstructionMeta {
    public AASMOperation op = null;                    // Opcode
    public boolean causesFlush = false;                // Some instructions flush the peephole area
    
    public boolean destIsRegister = false,
                   destIsMemory = false,
                   destOffsIsCCon = false;             // True if dest offset is a compile constant
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
                   sourceOffsIsCCon = false;           // True if source offset is a compile constant
    public Register sourceRegister = Register.NONE,
                    sourceBase = Register.NONE,
                    sourceIndex = Register.NONE;
    public int sourceScale = 0,
               sourceOffset = 0,
               sourceValue = 0;
    public IRType destType = IRType.NONE;
    
    public IRCondition condition = IRCondition.NONE;   // condition
}
