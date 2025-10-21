package notsotiny.lang.compiler.aasm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import notsotiny.sim.Register;
import notsotiny.lang.compiler.codegen.alloc.AllocationResult;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.nstasm.asmparts.ASMArgument;
import notsotiny.nstasm.asmparts.ASMComponent;
import notsotiny.nstasm.asmparts.ASMConstant;
import notsotiny.nstasm.asmparts.ASMInstruction;
import notsotiny.nstasm.asmparts.ASMLabel;
import notsotiny.nstasm.asmparts.ASMObject;
import notsotiny.nstasm.asmparts.ASMReference;
import notsotiny.nstasm.asmparts.ASMReference.ReferenceType;
import notsotiny.nstasm.asmparts.ASMMemory;
import notsotiny.sim.ops.Opcode;

/**
 * Translates AbstractAssembly to AssemblyObject components
 */
public class AASMTranslator {
    
    /**
     * Translates aasm, placing results in assemblyComponents and assemblyLabelIndexMap
     * @param aasm
     * @param allocRes
     * @param assemblyComponents
     * @param assemblyLabelIndexMap
     */
    public static void translate(AllocationResult allocRes, ASMObject asmObj, IRFunction sourceFunction) {
        // Create prologue
        asmObj.addComponent(new ASMLabel(sourceFunction.getID().getName()));
        
        // Base pointer
        asmObj.addComponent(new ASMInstruction(Opcode.PUSHW_BP));
        asmObj.addComponent(new ASMInstruction(
            Opcode.MOVW_RIM,
            ASMArgument.REG_BP,
            ASMArgument.REG_SP
        ));
        
        // Stack slots
        if(allocRes.stackAllocationSize() > 0) {
            if(allocRes.stackAllocationSize() < 0x100) {
                // Fits in I8 -> shortcut
                asmObj.addComponent(new ASMInstruction(
                    Opcode.SUBW_SP_I8,
                    new ASMArgument(new ASMConstant(allocRes.stackAllocationSize()), 1)
                ));
            } else {
                // Doesn't fit -> no shortcut
                asmObj.addComponent(new ASMInstruction(
                    Opcode.SUBW_RIM,
                    ASMArgument.REG_SP,
                    new ASMArgument(new ASMConstant(allocRes.stackAllocationSize()), 0)
                ));
            }
        }
        
        // Push callee-saved registers  if needed
        Set<Register> toSave = allocRes.usedCalleeSavedRegisters(); 
        if(toSave.contains(Register.JI)) {
            asmObj.addComponent(new ASMInstruction(Opcode.PUSHW_JI));
        } else {
            if(toSave.contains(Register.I)) {
                asmObj.addComponent(new ASMInstruction(Opcode.PUSH_I));
            }
            
            if(toSave.contains(Register.J)) {
                asmObj.addComponent(new ASMInstruction(Opcode.PUSH_J));
            }
        }
        
        if(toSave.contains(Register.LK)) {
            asmObj.addComponent(new ASMInstruction(Opcode.PUSHW_LK));
        } else {
            if(toSave.contains(Register.K)) {
                asmObj.addComponent(new ASMInstruction(Opcode.PUSH_K));
            }
            
            if(toSave.contains(Register.L)) {
                asmObj.addComponent(new ASMInstruction(Opcode.PUSH_L));
            }
        }
        
        if(toSave.contains(Register.XP)) {
            asmObj.addComponent(new ASMInstruction(Opcode.PUSHW_XP));
        }
        
        if(toSave.contains(Register.YP)) {
            asmObj.addComponent(new ASMInstruction(Opcode.PUSHW_YP));
        }
        
        // Create epilogue, which replaces RET
        List<ASMComponent> epilogue = new ArrayList<>();
        
        // Pop callee-saved registers if needed
        if(toSave.contains(Register.YP)) {
            epilogue.add(new ASMInstruction(Opcode.POPW_YP));
        }
        
        if(toSave.contains(Register.XP)) {
            epilogue.add(new ASMInstruction(Opcode.POPW_XP));
        }
        
        if(toSave.contains(Register.LK)) {
            epilogue.add(new ASMInstruction(Opcode.POPW_LK));
        } else {
            if(toSave.contains(Register.L)) {
                epilogue.add(new ASMInstruction(Opcode.POP_L));
            }
            
            if(toSave.contains(Register.K)) {
                epilogue.add(new ASMInstruction(Opcode.POP_K));
            }
        }
        
        if(toSave.contains(Register.JI)) {
            epilogue.add(new ASMInstruction(Opcode.POPW_JI));
        } else {
            if(toSave.contains(Register.J)) {
                epilogue.add(new ASMInstruction(Opcode.POP_J));
            }
            
            if(toSave.contains(Register.I)) {
                epilogue.add(new ASMInstruction(Opcode.POP_I));
            }
        }
        
        // Stack slots
        if(allocRes.stackAllocationSize() > 0) {
            if(allocRes.stackAllocationSize() < 0x80) {
                // Fits in I8 -> shortcut
                epilogue.add(new ASMInstruction(
                    Opcode.ADDW_SP_I8,
                    new ASMArgument(new ASMConstant(allocRes.stackAllocationSize()), 1)
                ));
            } else {
                // Doesn't fit -> no shortcut
                epilogue.add(new ASMInstruction(
                    Opcode.ADDW_RIM,
                    new ASMArgument(new ASMConstant(allocRes.stackAllocationSize()), 0)
                ));
            }
        }
        
        // Base pointer
        epilogue.add(new ASMInstruction(Opcode.POPW_BP));
        epilogue.add(new ASMInstruction(Opcode.RET));
        
        // Translate each part
        for(AASMPart part : allocRes.allocatedCode()) {
            switch(part) {
                case AASMInstruction inst: {
                    AASMInstructionMeta meta = inst.getMeta();
                    Opcode op = translateOpcode(meta);
                    
                    switch(meta.op) {
                        // Special cases
                        case RET:
                            asmObj.addComponents(epilogue);
                            break;
                        
                        case CMOV:
                            // CMOVCC needs its condition as an EI8
                            asmObj.addComponent(new ASMInstruction(
                                (meta.sourceType == IRType.I32 || meta.destType == IRType.I32) ? Opcode.CMOVWCC_RIM : Opcode.CMOVCC_RIM,
                                translateArg(inst.getDestination(), true, false, false, sourceFunction),
                                translateArg(inst.getSource(), true, false, false, sourceFunction),
                                new ASMConstant(meta.condition.toJCCOpcode().getOp() & 0x0F)
                            ));
                            break;
                        
                        case CALL, JMP, JCC:
                            // Inferred link sizes
                            asmObj.addComponent(new ASMInstruction(
                                op,
                                translateArg(inst.getSource(), true, true, false, sourceFunction)
                            ));
                            break;
                        
                        // One argument (destination)
                        case INC, ICC, DEC, DCC, NOT, NEG:
                            asmObj.addComponent(new ASMInstruction(
                                op,
                                translateArg(inst.getDestination(), true, false, false, sourceFunction)
                            ));
                            break;
                            
                        // One argument (source)
                        case CALLA, JMPA:
                            asmObj.addComponent(new ASMInstruction(
                                op,
                                translateArg(inst.getSource(), true, false, false, sourceFunction)
                            ));
                            break;
                            
                        case PUSH:
                            // Push becomes DST SP, x & needs its size specified
                            asmObj.addComponent(new ASMInstruction(
                                op,
                                ASMArgument.REG_SP,
                                translateArg(inst.getSource(), true, false, true, sourceFunction)
                            ));
                            break;
                            
                        case POP:
                            // POP becomes LDI x, SP
                            asmObj.addComponent(new ASMInstruction(
                                op,
                                translateArg(inst.getDestination(), true, false, false, sourceFunction),
                                ASMArgument.REG_SP
                            ));
                            break;
                        
                        // Two argument
                        default:
                            asmObj.addComponent(new ASMInstruction(
                                op,
                                translateArg(inst.getDestination(), true, false, false, sourceFunction),
                                translateArg(inst.getSource(), true, false, false, sourceFunction)
                            ));
                            break;
                    }
                    break;
                }
                
                case AASMLabel lbl: {
                    // Label
                    asmObj.addComponent(new ASMLabel(lbl.acName(sourceFunction.getID().getName())));
                    break;
                }
                
                default:
                    throw new IllegalArgumentException("Cannot translate " + part + " to assembly component");
            }
        }
    }
    
    /**
     * Translates an argument
     * @param arg
     * @return
     */
    private static ASMArgument translateArg(AASMPart arg, boolean signed, boolean inferLink, boolean includeSize, IRFunction sourceFunction) {
        switch(arg) {
            case AASMCompileConstant cc:
                return new ASMArgument(new ASMConstant(signed ? cc.signedLongValue() : cc.unsignedLongValue()), includeSize ? cc.type().getSize() : 0);
            
            case AASMLinkConstant lc:
                return new ASMArgument(new ASMReference(lc.acName(sourceFunction.getID().getName()), inferLink ? ReferenceType.RELATIVE_CURRENT : ReferenceType.NORMAL), inferLink ? 0 : 4);
            
            case AASMMachineRegister mr:
                return ASMArgument.fromReg(mr.reg());
            
            case AASMMemory mem: {
                Register base = translateMemReg(mem.getBase()),
                         index = translateMemReg(mem.getIndex());
                int scale = 1;
                
                if(mem.getScale() instanceof AASMCompileConstant cc) {
                    scale = cc.value();
                }
                
                // Compile constant and link constant handled differently
                if(mem.getOffset() instanceof AASMCompileConstant cc) {
                    return new ASMArgument(new ASMMemory(
                        base, index, new ASMConstant(scale), new ASMConstant(cc.signedLongValue())
                    ), mem.getType().getSize());
                } else {
                    return new ASMArgument(new ASMMemory(
                        base, index, new ASMConstant(scale),
                        new ASMReference(((AASMLinkConstant) mem.getOffset()).acName(sourceFunction.getID().getName()), ReferenceType.NORMAL)
                    ), mem.getType().getSize());
                }
            }
            
            default:
                throw new IllegalArgumentException("Unexpected part: " + arg);
        }
    }
    
    /**
     * Translates a register found in a memory access
     * @param part
     * @return
     */
    private static Register translateMemReg(AASMPart part) {
        if(part instanceof AASMMachineRegister mr) {
            return mr.reg();
        } else {
            return Register.NONE;
        }
    }
    
    /**
     * Translate an opcode
     * @return
     */
    private static Opcode translateOpcode(AASMInstructionMeta meta) {
        return switch(meta.op) {
            case MOV    -> (meta.sourceType == IRType.I32 || meta.destType == IRType.I32) ? Opcode.MOVW_RIM : Opcode.MOV_RIM;
            case CMOV   -> (meta.sourceType == IRType.I32 || meta.destType == IRType.I32) ? Opcode.CMOVWCC_RIM : Opcode.CMOVCC_RIM;
            case XCHG   -> (meta.sourceType == IRType.I32 || meta.destType == IRType.I32) ? Opcode.XCHGW_RIM : Opcode.XCHG_RIM;
            case PUSH   -> (meta.sourceType == IRType.I32) ? Opcode.DSTW_RIM : Opcode.DST_RIM;
            case POP    -> (meta.sourceType == IRType.I32) ? Opcode.LDIW_RIM : Opcode.LDI_RIM;
            case ADD    -> (meta.sourceType == IRType.I32 || meta.destType == IRType.I32) ? Opcode.ADDW_RIM : Opcode.ADD_RIM;
            case SUB    -> (meta.sourceType == IRType.I32 || meta.destType == IRType.I32) ? Opcode.SUBW_RIM : Opcode.SUB_RIM;
            case CMP    -> (meta.sourceType == IRType.I32 || meta.destType == IRType.I32) ? Opcode.CMPW_RIM : Opcode.CMP_RIM;
            case RET    -> Opcode.RET;
            case JCC    -> meta.condition.toJCCOpcode();
            
            case CALLA  -> Opcode.CALLA_RIM32;
            case JMPA   -> Opcode.JMPA_RIM32;
            
            default -> Opcode.valueOf(meta.op + "_RIM");
        };
    }
    
}
