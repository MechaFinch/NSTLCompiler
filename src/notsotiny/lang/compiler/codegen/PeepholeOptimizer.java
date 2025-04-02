package notsotiny.lang.compiler.codegen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Logger;

import notsotiny.asm.Register;
import notsotiny.lang.compiler.aasm.AASMCompileConstant;
import notsotiny.lang.compiler.aasm.AASMInstruction;
import notsotiny.lang.compiler.aasm.AASMLabel;
import notsotiny.lang.compiler.aasm.AASMLinkConstant;
import notsotiny.lang.compiler.aasm.AASMMachineRegister;
import notsotiny.lang.compiler.aasm.AASMMemory;
import notsotiny.lang.compiler.aasm.AASMOperation;
import notsotiny.lang.compiler.aasm.AASMPart;
import notsotiny.lang.ir.parts.IRCondition;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRType;

/**
 * Performs peephole optimizations
 */
public class PeepholeOptimizer {
    
    private static Logger LOG = Logger.getLogger(PeepholeOptimizer.class.getName());
    
    /**
     * Data about an instruction
     */
    private static class InstructionMeta {
        AASMOperation op = null;                    // Opcode
        boolean causesFlush = false;                // Some instructions flush the peephole area
        
        boolean destIsRegister = false,
                destIsMemory = false,
                destOffsIsCCon = false;             // True if dest offset is a compile constant
        Register destRegister = Register.NONE,
                 destBase = Register.NONE,
                 destIndex = Register.NONE;
        int destScale = 0,
            destOffset = 0;
        IRType sourceType = IRType.NONE;
        
        boolean sourceIsRegister = false,
                sourceIsMemory = false,
                sourceIsCompileConstant = false,
                sourceIsLinkConstant = false,
                sourceOffsIsCCon = false;           // True if source offset is a compile constant
        Register sourceRegister = Register.NONE,
                 sourceBase = Register.NONE,
                 sourceIndex = Register.NONE;
        int sourceScale = 0,
            sourceOffset = 0,
            sourceValue = 0;
        IRType destType = IRType.NONE;
        
        IRCondition condition = IRCondition.NONE;   // condition
    }
    
    private static final int PEEP_SIZE = 3;
    
    /**
     * Performs peephole optimizations.
     * @param code
     * @return
     */
    public static List<AASMPart> optimize(List<AASMPart> code, IRFunction sourceFunction) {
        LOG.finer("Performing peephole optimizations on " + sourceFunction.getID());
        
        List<AASMPart> optimized = new ArrayList<>();
        
        ArrayList<AASMInstruction> peepInst = new ArrayList<>();
        ArrayList<InstructionMeta> peepMeta = new ArrayList<>();
        
        // Go through each instruction
        for(AASMPart part : code) {
            boolean flush = true;
            
            if(part instanceof AASMInstruction inst) {
                InstructionMeta meta = analyzeInstruction(inst);
                
                // Add to peephole if not flushed
                if(!meta.causesFlush) {
                    peepInst.add(inst);
                    peepMeta.add(meta);
                    flush = meta.causesFlush;
                }
            }
            
            // Move oldest out of peephole if applicable
            if(peepInst.size() > PEEP_SIZE) {
                AASMInstruction i = peepInst.removeFirst();
                peepMeta.removeFirst();
                
                LOG.finest("\t" + i);
                optimized.add(i);
            }
            
            if(flush) {
                // Part flushes the peephole
                while(!peepInst.isEmpty()) {
                    optimizeStep(peepInst, peepMeta);
                    
                    if(!peepInst.isEmpty()) {
                        AASMInstruction i = peepInst.removeFirst();
                        peepMeta.removeFirst();
                        
                        LOG.finest("\t" + i);
                        optimized.add(i);
                    }
                }
                
                if(part instanceof AASMLabel lbl) {
                    LOG.finest(lbl + "");
                } else {
                    LOG.finest("\t" + part);
                }
                
                optimized.add(part);
            } else {
                // Part was added to the peephole. Optimize!
                optimizeStep(peepInst, peepMeta);
            }
        }
        
        return optimized;
    }
    
    /**
     * Perform an optimization step
     * @param peepInst
     * @param peepMeta
     */
    private static void optimizeStep(ArrayList<AASMInstruction> peepInst, ArrayList<InstructionMeta> peepMeta) {
        // Checks are separate as functions can remove instructions
        if(peepInst.size() >= 3) {
            optimizeThree(peepInst, peepMeta);
        }
        
        if(peepInst.size() >= 2) {
            optimizeTwo(peepInst, peepMeta);
        }
        
        if(peepInst.size() >= 1) {
            optimizeOne(peepInst, peepMeta);
        }
    }
    
    /**
     * Optimizes using 1 instruction
     * @param peepInst
     * @param peepMeta
     */
    private static void optimizeOne(ArrayList<AASMInstruction> peepInst, ArrayList<InstructionMeta> peepMeta) {
        AASMInstruction inst = peepInst.get(0);
        InstructionMeta meta = peepMeta.get(0);
        
        //LOG.finest("O1: " + inst);
        
        if(meta.op == AASMOperation.MOV) { 
            // MOV x, x is a no-op
            if(meta.sourceIsRegister && meta.destIsRegister &&
               meta.sourceRegister == meta.destRegister) {
                //LOG.finest("Eliminated no-op move " + inst);
                peepInst.remove(0);
                peepMeta.remove(0);
                return;
            }
        }
        
        // TODO
    }
    
    /**
     * Optimizes using 2 instructions
     * @param peepInst
     * @param peepMeta
     */
    private static void optimizeTwo(ArrayList<AASMInstruction> peepInst, ArrayList<InstructionMeta> peepMeta) {
        AASMInstruction i0 = peepInst.get(0);
        AASMInstruction i1 = peepInst.get(1);
        InstructionMeta m0 = peepMeta.get(0);
        InstructionMeta m1 = peepMeta.get(1);
        
        //LOG.finest("O2: " + i0 + "; " + i1);
        
        if(m0.op == AASMOperation.MOV && m1.op == AASMOperation.MOV) { 
            // MOV ?, ?
            // MOV ?, ?
            
            if(m0.destIsMemory && m1.sourceIsMemory) {
                // MOV [?], ?
                // MOV ?, [?]
                
                // MOV [BP - x], y
                // MOV z, [BP - x]
                // Is equivalent to
                // MOV [BP - x], y
                // MOV z, y
                if(m0.destBase == Register.BP && m1.sourceBase == Register.BP &&
                   m0.destIndex == Register.NONE && m1.sourceIndex == Register.NONE &&
                   m0.destOffsIsCCon && m1.sourceOffsIsCCon &&
                   m0.destOffset == m1.sourceOffset) {
                    AASMInstruction new1 = new AASMInstruction(
                        i1.getOp(),
                        i1.getDestination(),
                        i0.getSource(),
                        i1.getCondition()
                    );
                    
                    peepInst.set(1, new1);
                    peepMeta.set(1, analyzeInstruction(new1));
                    return;
                }
            }
        }
        
        // TODO
    }
    
    /**
     * Optimizes using 3 instructions
     * @param peepInst
     * @param peepMeta
     */
    private static void optimizeThree(ArrayList<AASMInstruction> peepInst, ArrayList<InstructionMeta> peepMeta) {
        // TODO
    }    
    
    /**
     * Analyzes an instruction
     * @param inst
     * @return
     */
    private static InstructionMeta analyzeInstruction(AASMInstruction inst) {
        InstructionMeta meta = new InstructionMeta();
        
        meta.op = inst.getOp();
        meta.condition = inst.getCondition();
        
        meta.causesFlush = switch(inst.getOp()) {
            case PUSH, POP, CALL, CALLA, RET, JMP, JMPA, JCC
                    -> true;
            default -> false;
        };
        
        try {
            if(inst.getDestination() != null) {
                switch(inst.getDestination()) {
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
                        throw new IllegalArgumentException("Unexpected destination type: " + inst);
                }
            }
            
            if(inst.getSource() != null) {
                switch(inst.getSource()) {
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
                        throw new IllegalArgumentException("Unexpected destination type: " + inst);
                }
            }
        } catch(ClassCastException e) {
            LOG.severe("Unexpected type during instruction analysis");
            throw e;
        }
        
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
