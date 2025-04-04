package notsotiny.lang.compiler.codegen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import notsotiny.asm.Register;
import notsotiny.lang.compiler.aasm.AASMCompileConstant;
import notsotiny.lang.compiler.aasm.AASMInstruction;
import notsotiny.lang.compiler.aasm.AASMInstructionMeta;
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
    
    private static final int PEEP_SIZE = 3;
    
    /**
     * Performs peephole optimizations.
     * @param code
     * @return
     */
    public static List<AASMPart> optimize(List<AASMPart> code, IRFunction sourceFunction) {
        LOG.finer("Performing peephole optimizations on " + sourceFunction.getID());
        
        List<AASMPart> optimizedCode = new ArrayList<>();
        
        ArrayList<AASMInstruction> peepInst = new ArrayList<>();
        ArrayList<AASMInstructionMeta> peepMeta = new ArrayList<>();
        
        // Optimize iteratively
        boolean optimized = true;
        while(optimized) {
            optimized = false;
            
            // For each instruction
            for(int idx = 0; idx < code.size(); idx++) {
                AASMPart part = code.get(idx);
                boolean flush = true;
                
                if(part instanceof AASMInstruction inst) {
                    AASMInstructionMeta meta = inst.getMeta();
                    
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
                    
                    optimizedCode.add(i);
                }
                
                if(flush) {
                    // Part flushes the peephole
                    while(!peepInst.isEmpty()) {
                        // Optimize until empty
                        optimized |= optimizeStep(peepInst, peepMeta);
                        
                        if(!peepInst.isEmpty()) {
                            AASMInstruction i = peepInst.removeFirst();
                            peepMeta.removeFirst();
                            
                            optimizedCode.add(i);
                        }
                    }
                    
                    // Special case - no-op jump elimination\
                    boolean specialEliminated = false;
                    if(part instanceof AASMInstruction inst) {
                        switch(inst.getOp()) {
                            case JMP, JCC:
                                // If a next part exists,
                                // that part is a label,
                                // the jump is to a label,
                                // and that label is the immediately subsequent label
                                if((idx + 1) < code.size() &&
                                   code.get(idx + 1) instanceof AASMLabel nextLabel) {
                                    if((inst.getSource() instanceof AASMLabel jumpLabel &&
                                        jumpLabel.name().equals(nextLabel.name())) ||
                                       (inst.getSource() instanceof AASMLinkConstant jumpLink &&
                                        jumpLink.id().toString().equals(nextLabel.name()))) {
                                        specialEliminated = true;
                                    }
                                }
                                break;
                            
                            default:
                        }
                    }
                    
                    // Then add part
                    if(!specialEliminated) {
                        optimizedCode.add(part);
                    }
                } else {
                    // Part was added to the peephole. Optimize!
                    optimized |= optimizeStep(peepInst, peepMeta);
                }
            }
            
            code = optimizedCode;
            optimizedCode = new ArrayList<>();
        }
        
        // Report results
        if(LOG.isLoggable(Level.FINEST)) {
            for(AASMPart part : code) {
                if(part instanceof AASMLabel) {
                    LOG.finest("" + part);
                } else {
                    LOG.finest("\t" + part);
                }
            }
        }
        
        return code;
    }
    
    /**
     * Perform an optimization step
     * @param peepInst
     * @param peepMeta
     */
    private static boolean optimizeStep(ArrayList<AASMInstruction> peepInst, ArrayList<AASMInstructionMeta> peepMeta) {
        boolean optimized = true;
        boolean optimizedAny = false;
        
        // Although we optimize iteratively globally, doing it locally can catch things without iterating over the entire function again
        while(optimized && peepInst.size() >= 3) {
            optimized = optimizeThree(peepInst, peepMeta);
            optimizedAny |= optimized;
        }
        
        optimized = true;
        while(optimized && peepInst.size() >= 2) {
            optimized = optimizeTwo(peepInst, peepMeta);
            optimizedAny |= optimized;
        }
        
        optimized = true;
        while(optimized && peepInst.size() >= 1) {
            optimized = optimizeOne(peepInst, peepMeta);
            optimizedAny |= optimized;
        }
        
        return optimizedAny;
    }
    
    /**
     * Optimizes using 1 instruction
     * @param peepInst
     * @param peepMeta
     */
    private static boolean optimizeOne(ArrayList<AASMInstruction> peepInst, ArrayList<AASMInstructionMeta> peepMeta) {
        AASMInstruction inst = peepInst.get(0);
        AASMInstructionMeta meta = peepMeta.get(0);
        
        //LOG.finest("O1: " + inst);
        
        if(meta.op == AASMOperation.MOV) { 
            // MOV x, x is a no-op
            if(meta.sourceIsRegister && meta.destIsRegister &&
               meta.sourceRegister == meta.destRegister) {
                //LOG.finest("Eliminated no-op move " + inst);
                peepInst.remove(0);
                peepMeta.remove(0);
                return true;
            }
        }
        
        // TODO
        
        return false;
    }
    
    /**
     * Optimizes using 2 instructions
     * @param peepInst
     * @param peepMeta
     * @param true if optimization performed
     */
    private static boolean optimizeTwo(ArrayList<AASMInstruction> peepInst, ArrayList<AASMInstructionMeta> peepMeta) {
        AASMInstruction i0 = peepInst.get(0);
        AASMInstruction i1 = peepInst.get(1);
        AASMInstructionMeta m0 = peepMeta.get(0);
        AASMInstructionMeta m1 = peepMeta.get(1);
        
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
                // Specifically BP rather than general memory access as the stack
                // is assumed to not be in a side-effecting memory region
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
                    peepMeta.set(1, new1.getMeta());
                    return true;
                }
            } else if(m0.sourceIsRegister && m0.destIsRegister &&
                      m1.sourceIsRegister && m1.destIsRegister) {
                // MOV R, R
                // MOV R, R
                
                // MOV A, B
                // MOV B, A
                // is equivalent to
                // MOV A, B
                if(m0.destRegister == m1.sourceRegister &&
                   m1.destRegister == m0.sourceRegister) {
                    peepInst.remove(1);
                    peepMeta.remove(1);
                    return true;
                }
            }
        } else if(m0.op == AASMOperation.PUSH && m1.op == AASMOperation.PUSH) {
            // Some pairs of pushes can be combined into single instructions
            if(m0.sourceIsCompileConstant && m1.sourceIsCompileConstant) {
                if((m0.sourceType == IRType.I8 && m1.sourceType == IRType.I8) ||
                   (m0.sourceType == IRType.I16 && m1.sourceType == IRType.I16)) {
                    // I8 I8 -> I16 or I16 I16 -> I32?
                    boolean i8 = m0.sourceType == IRType.I8; 
                    
                    // Merge constants
                    AASMCompileConstant newConst = new AASMCompileConstant(
                        (m0.sourceValue << (i8 ? 8 : 16)) | m1.sourceValue,
                        i8 ? IRType.I16 : IRType.I32
                    );
                    
                    // Substitute instruction
                    AASMInstruction newInst = new AASMInstruction(
                        AASMOperation.PUSH,
                        newConst
                    );
                    
                    peepInst.remove(1);
                    peepMeta.remove(1);
                    peepInst.set(0, newInst);
                    peepMeta.set(0, newInst.getMeta());
                    return true;
                }
            } else if(m0.sourceIsRegister && m1.sourceIsRegister) {
                // Register pairs
                boolean canReplace = false;
                Register reg = Register.NONE;
                
                switch(m0.sourceRegister) {
                    case A: canReplace = (m1.sourceRegister == Register.B); reg = Register.AB; break;
                    case B: canReplace = (m1.sourceRegister == Register.C); reg = Register.BC; break;
                    case C: canReplace = (m1.sourceRegister == Register.D); reg = Register.CD; break;
                    case D: canReplace = (m1.sourceRegister == Register.A); reg = Register.DA; break;
                    case J: canReplace = (m1.sourceRegister == Register.I); reg = Register.JI; break;
                    case L: canReplace = (m1.sourceRegister == Register.K); reg = Register.LK; break;
                    
                    case AH: canReplace = (m1.sourceRegister == Register.AL); reg = Register.A; break;
                    case BH: canReplace = (m1.sourceRegister == Register.BL); reg = Register.B; break;
                    case CH: canReplace = (m1.sourceRegister == Register.CL); reg = Register.C; break;
                    case DH: canReplace = (m1.sourceRegister == Register.DL); reg = Register.D; break;
                    
                    default:
                        // Can't pair
                }
                
                if(canReplace) {
                    // Found a pair
                    AASMInstruction newInst = new AASMInstruction(
                        AASMOperation.PUSH,
                        new AASMMachineRegister(reg)
                    );
                    
                    peepInst.remove(1);
                    peepMeta.remove(1);
                    peepInst.set(0, newInst);
                    peepMeta.set(0, newInst.getMeta());
                    return true;
                }
            }
        }
        
        // TODO
        
        return false;
    }
    
    /**
     * Optimizes using 3 instructions
     * @param peepInst
     * @param peepMeta
     * @return true if optimization performed
     */
    private static boolean optimizeThree(ArrayList<AASMInstruction> peepInst, ArrayList<AASMInstructionMeta> peepMeta) {
        AASMInstruction i0 = peepInst.get(0);
        AASMInstruction i1 = peepInst.get(1);
        AASMInstruction i2 = peepInst.get(2);
        AASMInstructionMeta m0 = peepMeta.get(0);
        AASMInstructionMeta m1 = peepMeta.get(1);
        AASMInstructionMeta m2 = peepMeta.get(2);
        
        // TODO
        
        return false;
    }
}
