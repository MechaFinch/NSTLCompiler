package notsotiny.lang.compiler.aasm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import notsotiny.sim.Register;
import notsotiny.asm.components.Component;
import notsotiny.asm.components.Instruction;
import notsotiny.asm.resolution.ResolvableConstant;
import notsotiny.asm.resolution.ResolvableLocationDescriptor;
import notsotiny.asm.resolution.ResolvableMemory;
import notsotiny.asm.resolution.ResolvableLocationDescriptor.LocationType;
import notsotiny.lang.compiler.codegen.alloc.AllocationResult;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRType;
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
    public static void translate(AllocationResult allocRes, List<Component> assemblyComponents, HashMap<String, Integer> assemblyLabelIndexMap, IRFunction sourceFunction) {
        // Create prologue
        assemblyLabelIndexMap.put(sourceFunction.getID().getName(), assemblyComponents.size());
        
        // Base pointer
        assemblyComponents.add(new Instruction(
            Opcode.PUSHW_BP,
            true
        ));
        assemblyComponents.add(new Instruction(
            Opcode.MOVW_RIM,
            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.BP),
            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.SP),
            true
        ));
        
        // Stack slots
        if(allocRes.stackAllocationSize() > 0) {
            if(allocRes.stackAllocationSize() < 0x80) {
                // Fits in I8 -> shortcut
                assemblyComponents.add(new Instruction(
                    Opcode.SUBW_SP_I8,
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 1, new ResolvableConstant(allocRes.stackAllocationSize())),
                    false, true
                ));
            } else {
                // Doesn't fit -> no shortcut
                assemblyComponents.add(new Instruction(
                    Opcode.SUBW_RIM,
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.SP),
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(allocRes.stackAllocationSize())),
                    false
                ));
            }
        }
        
        // Push callee-saved registers  if needed
        Set<Register> toSave = allocRes.usedCalleeSavedRegisters(); 
        if(toSave.contains(Register.JI)) {
            assemblyComponents.add(new Instruction(Opcode.PUSHW_JI, true));
        } else {
            if(toSave.contains(Register.I)) {
                assemblyComponents.add(new Instruction(Opcode.PUSH_I, true));
            }
            
            if(toSave.contains(Register.J)) {
                assemblyComponents.add(new Instruction(Opcode.PUSH_J, true));
            }
        }
        
        if(toSave.contains(Register.LK)) {
            assemblyComponents.add(new Instruction(Opcode.PUSHW_LK, true));
        } else {
            if(toSave.contains(Register.K)) {
                assemblyComponents.add(new Instruction(Opcode.PUSH_K, true));
            }
            
            if(toSave.contains(Register.L)) {
                assemblyComponents.add(new Instruction(Opcode.PUSH_L, true));
            }
        }
        
        if(toSave.contains(Register.XP)) {
            assemblyComponents.add(new Instruction(Opcode.PUSHW_XP, true));
        }
        
        if(toSave.contains(Register.YP)) {
            assemblyComponents.add(new Instruction(Opcode.PUSHW_YP, true));
        }
        
        // Create epilogue, which replaces RET
        List<Component> epilogue = new ArrayList<>();
        
        // Pop callee-saved registers if needed
        if(toSave.contains(Register.YP)) {
            epilogue.add(new Instruction(Opcode.POPW_YP, true));
        }
        
        if(toSave.contains(Register.XP)) {
            epilogue.add(new Instruction(Opcode.POPW_XP, true));
        }
        
        if(toSave.contains(Register.LK)) {
            epilogue.add(new Instruction(Opcode.POPW_LK, true));
        } else {
            if(toSave.contains(Register.L)) {
                epilogue.add(new Instruction(Opcode.POP_L, true));
            }
            
            if(toSave.contains(Register.K)) {
                epilogue.add(new Instruction(Opcode.POP_K, true));
            }
        }
        
        if(toSave.contains(Register.JI)) {
            epilogue.add(new Instruction(Opcode.POPW_JI, true));
        } else {
            if(toSave.contains(Register.J)) {
                epilogue.add(new Instruction(Opcode.POP_J, true));
            }
            
            if(toSave.contains(Register.I)) {
                epilogue.add(new Instruction(Opcode.POP_I, true));
            }
        }
        
        // Stack slots
        if(allocRes.stackAllocationSize() > 0) {
            if(allocRes.stackAllocationSize() < 0x80) {
                // Fits in I8 -> shortcut
                epilogue.add(new Instruction(
                    Opcode.ADDW_SP_I8,
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 1, new ResolvableConstant(allocRes.stackAllocationSize())),
                    false, true
                ));
            } else {
                // Doesn't fit -> no shortcut
                epilogue.add(new Instruction(
                    Opcode.ADDW_RIM,
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.SP),
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(allocRes.stackAllocationSize())),
                    false
                ));
            }
        }
        
        // Base pointer
        epilogue.add(new Instruction(Opcode.POPW_BP, true));
        epilogue.add(new Instruction(Opcode.RET, true));
        
        // Translate each part
        for(AASMPart part : allocRes.allocatedCode()) {
            switch(part) {
                case AASMInstruction inst: {
                    AASMInstructionMeta meta = inst.getMeta();
                    Opcode op = translateOpcode(meta);
                    
                    switch(meta.op) {
                        // Special cases
                        case RET:
                            assemblyComponents.addAll(epilogue);
                            break;
                        
                        case CMOV:
                            // CMOVCC needs its condition as an EI8
                            assemblyComponents.add(new Instruction(
                                (meta.sourceType == IRType.I32 || meta.destType == IRType.I32) ? Opcode.CMOVWCC_RIM : Opcode.CMOVCC_RIM,
                                translateArg(inst.getDestination(), true, false, sourceFunction),
                                translateArg(inst.getSource(), true, false, sourceFunction),
                                meta.condition.toJCCOpcode().getOp(),
                                false
                            ));
                            break;
                        
                        case CALL, JMP, JCC:
                            // Inferred link sizes
                            assemblyComponents.add(new Instruction(
                                op,
                                translateArg(inst.getSource(), true, true, sourceFunction),
                                false, false
                            ));
                            break;
                        
                        // One argument (destination)
                        case POP, INC, ICC, DEC, DCC, NOT, NEG:
                            assemblyComponents.add(new Instruction(
                                op,
                                translateArg(inst.getDestination(), true, false, sourceFunction),
                                true, false
                            ));
                            break;
                            
                        // One argument (source)
                        case PUSH, CALLA, JMPA:
                            assemblyComponents.add(new Instruction(
                                op,
                                translateArg(inst.getSource(), true, false, sourceFunction),
                                false, false
                            ));
                            break;
                        
                        // Two argument
                        default:
                            assemblyComponents.add(new Instruction(
                                op,
                                translateArg(inst.getDestination(), true, false, sourceFunction),
                                translateArg(inst.getSource(), true, false, sourceFunction),
                                false
                            ));
                            break;
                    }
                    break;
                }
                
                case AASMLabel lbl: {
                    // Label
                    assemblyLabelIndexMap.put(lbl.acName(sourceFunction.getID().getName()), assemblyComponents.size());
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
    private static ResolvableLocationDescriptor translateArg(AASMPart arg, boolean signed, boolean inferLink, IRFunction sourceFunction) {
        switch(arg) {
            case AASMCompileConstant cc:
                return new ResolvableLocationDescriptor(
                    LocationType.IMMEDIATE,
                    cc.type().getSize(),
                    new ResolvableConstant(signed ? cc.signedLongValue() : cc.unsignedLongValue())
                );
            
            case AASMLinkConstant lc:
                return new ResolvableLocationDescriptor(
                    LocationType.IMMEDIATE,
                    inferLink ? -1 : 4,
                    new ResolvableConstant(lc.acName(sourceFunction.getID().getName()))
                );
            
            case AASMMachineRegister mr:
                return new ResolvableLocationDescriptor(
                    LocationType.REGISTER,
                    mr.reg()
                );
            
            case AASMMemory mem: {
                Register base = translateMemReg(mem.getBase()),
                         index = translateMemReg(mem.getIndex());
                int scale = 1;
                
                if(mem.getScale() instanceof AASMCompileConstant cc) {
                    scale = cc.value();
                }
                
                // Compile constant and link constant handled differently
                if(mem.getOffset() instanceof AASMCompileConstant cc) {
                    return new ResolvableLocationDescriptor(
                        LocationType.MEMORY,
                        mem.getType().getSize(),
                        new ResolvableMemory(
                            base,
                            index,
                            scale,
                            cc.signedValue()
                        )
                    );
                } else {
                    return new ResolvableLocationDescriptor(
                        LocationType.MEMORY,
                        mem.getType().getSize(),
                        new ResolvableMemory(
                            base,
                            index,
                            scale,
                            new ResolvableConstant(((AASMLinkConstant) mem.getOffset()).acName(sourceFunction.getID().getName()))
                        )
                    );
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
            case PUSH   -> (meta.sourceType == IRType.I32) ? Opcode.PUSHW_RIM : Opcode.PUSH_RIM;
            case POP    -> (meta.sourceType == IRType.I32) ? Opcode.POPW_RIM : Opcode.POP_RIM;
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
