package notsotiny.lang.compiler.optimization.other;

import java.util.List;
import java.util.logging.Logger;

import notsotiny.lang.compiler.optimization.IROptimizationLevel;
import notsotiny.lang.compiler.optimization.IROptimizationPass;
import notsotiny.lang.ir.parts.IRArgumentMapping;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRBranchInstruction;
import notsotiny.lang.ir.parts.IRBranchOperation;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lang.ir.util.IRUtil;

/**
 * Eliminates empty basic blocks
 */
public class IRPassBasicBlockMerge implements IROptimizationPass {
    
    private static Logger LOG = Logger.getLogger(IRPassBasicBlockMerge.class.getName());

    @Override
    public IROptimizationLevel level() {
        return IROptimizationLevel.ZERO;
    }

    @Override
    public IRModule optimize(IRModule module) {
        LOG.finer("Minimizing basic blocks in " + module.getName());
        
        // In each function
        for(IRFunction func : module.getInternalFunctions().values()) {
            boolean changed = false;
            int passes = 0;
            
            do {
                LOG.finest("Minimizing " + func.getID() + ", pass " + passes++);
                
                changed = false;
                
                // Check each BB
                List<IRBasicBlock> bbs = func.getBasicBlockList();
                
                for(int i = 0; i < bbs.size(); i++) {
                    IRBasicBlock bb = bbs.get(i);
                    
                    if(canRemoveBB(bb)) {
                        removeBB(bb, func);
                        changed = true;
                        i--;
                    } else if(canMergeBB(bb, func)) {
                        mergeBB(bb, func);
                        changed = true;
                        i--;
                    }
                }
            } while(changed);
        }
        
        return module;
    }
    
    @Override
    public String toString() {
        return "BasicBlockMerge"; 
    }
    
    /**
     * @param bb
     * @return True if bb can be merged into its predecessor
     */
    private boolean canMergeBB(IRBasicBlock bb, IRFunction func) {
        /*
         * A basic block can be merged into a predecessor iff
         * - It has exactly one predecessor
         * - It is the only successor of its predecessor
         */
        
        if(bb.getPredecessorBlocks().size() != 1) {
            LOG.finest(bb.getID() + " has multiple predecessors");
            return false;
        }
        
        if(func.getBasicBlock(bb.getPredecessorBlocks().get(0)).getExitInstruction().getOp() == IRBranchOperation.JCC) {
            LOG.finest(bb.getID() + " is not the only successor");
            return false;
        }
        
        return true;
    }
    
    /**
     * @param bb
     * @return True if bb can be removed
     */
    private boolean canRemoveBB(IRBasicBlock bb) {
        /*
         * A basic block can be removed iff
         * - It has no body code
         * - It branches unconditionally to another basic block
         * - It is not the entry block
         * - It is the only predecessor of its successor OR It has no arguments
         */
        
        if(bb.getInstructions().size() != 0) {
            LOG.finest(bb.getID() + " has instructions");
            return false;
        }
        
        if(bb.getExitInstruction().getOp() != IRBranchOperation.JMP) {
            LOG.finest(bb.getID() + " isnt jmp");
            return false;
        }
        
        if(bb.getArgumentList().getArgumentCount() != 0 && 
           bb.getFunction().getBasicBlock(bb.getExitInstruction().getTrueTargetBlock()).getPredecessorBlocks().size() != 1) {
            LOG.finest(bb.getID() + " has args");
            return false;
        }
        
        if(bb.getID().getName().equals("entry")) {
            LOG.finest(bb.getID() + " is entry");
            return false;
        }
        
        if(bb.getID().equals(bb.getExitInstruction().getTrueTargetBlock())) {
            LOG.finest(bb.getID() + " branches to itself");
            return false;
        }
        
        return true;
    }
    
    /**
     * Removes a basic block
     * @param bb
     * @param func
     */
    private void removeBB(IRBasicBlock bb, IRFunction func) {
        LOG.finest("Eliminating basic block " + bb.getID() + " from " + func.getID());
        
        /*
         * To remove a basic block
         * - Redirect its predecessors to its target
         * - Remove it
         */
        
        IRIdentifier targetID = bb.getExitInstruction().getTrueTargetBlock();
        IRBasicBlock targetBB = func.getBasicBlock(targetID);
        IRArgumentMapping mapping = bb.getExitInstruction().getTrueArgumentMapping();
        
        // Move our arguments to target args
        targetBB.getArgumentList().addAll(bb.getArgumentList());
        
        for(IRIdentifier predID : bb.getPredecessorBlocks()) {
            IRBasicBlock predBB = func.getBasicBlock(predID);
            IRBranchInstruction predBranch = predBB.getExitInstruction();
            
            if(bb.getID().equals(predBranch.getTrueTargetBlock())) {
                // We're true
                predBranch.setTrueTargetBlock(targetID);
                predBranch.getTrueArgumentMapping().putAll(mapping);
                targetBB.addPredecessor(predID);
            }
            
            if(bb.getID().equals(predBranch.getFalseTargetBlock())) {
                // We're false
                predBranch.setFalseTargetBlock(targetID);
                predBranch.getFalseArgumentMapping().putAll(mapping);
                targetBB.addPredecessor(predID);
            }
        }
        
        targetBB.removePredecessor(bb.getID());
        
        func.removeBasicBlock(bb.getID());
    }
    
    /**
     * Merges a basic block into its predecessor
     * @param bb
     * @param func
     */
    private void mergeBB(IRBasicBlock bb, IRFunction func) {
        IRIdentifier predID = bb.getPredecessorBlocks().get(0);
        IRBasicBlock predBB = func.getBasicBlock(predID);
        
        // Map arguments if present
        List<IRIdentifier> argNames = bb.getArgumentList().getNameList();
        IRArgumentMapping mapping = predBB.getExitInstruction().getTrueArgumentMapping();
        
        for(int i = 0; i < argNames.size(); i++) {
            IRIdentifier argName = argNames.get(i);
            IRValue argMapping = mapping.getMapping(argName);
            IRUtil.replaceInFunction(func, argName, argMapping);
        }
        
        // Transfer instructions
        for(IRLinearInstruction li : bb.getInstructions()) {
            predBB.addInstruction(li);
        }
        
        predBB.setExitInstruction(bb.getExitInstruction());
        
        // Move predecessor information
        IRBranchInstruction bbExit = bb.getExitInstruction();
        IRIdentifier trueID = bbExit.getTrueTargetBlock();
        IRIdentifier falseID = bbExit.getFalseTargetBlock();
        
        if(trueID != null) {
            IRBasicBlock trueBB = func.getBasicBlock(trueID);
            trueBB.removePredecessor(bb.getID());
            trueBB.addPredecessor(predID);
        }
        
        if(falseID != null) {
            IRBasicBlock falseBB = func.getBasicBlock(falseID);
            falseBB.removePredecessor(bb.getID());
            falseBB.addPredecessor(predID);
        }
        
        func.removeBasicBlock(bb.getID());
    }
    
}
