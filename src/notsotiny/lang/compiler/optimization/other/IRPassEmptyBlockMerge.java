package notsotiny.lang.compiler.optimization.other;

import java.util.List;
import java.util.logging.Logger;

import notsotiny.lang.compiler.optimization.IROptimizationLevel;
import notsotiny.lang.compiler.optimization.IROptimizationPass;
import notsotiny.lang.ir.IRArgumentMapping;
import notsotiny.lang.ir.IRBasicBlock;
import notsotiny.lang.ir.IRBranchInstruction;
import notsotiny.lang.ir.IRBranchOperation;
import notsotiny.lang.ir.IRFunction;
import notsotiny.lang.ir.IRIdentifier;
import notsotiny.lang.ir.IRModule;

/**
 * Eliminates empty basic blocks
 */
public class IRPassEmptyBlockMerge implements IROptimizationPass {
    
    private static Logger LOG = Logger.getLogger(IRPassEmptyBlockMerge.class.getName());

    @Override
    public IROptimizationLevel level() {
        return IROptimizationLevel.ZERO;
    }

    @Override
    public IRModule optimize(IRModule module) {
        LOG.finer("Eliminating empty basic blocks from " + module.getName());
        
        // In each function
        for(IRFunction func : module.getFunctions().values()) {
            if(func.isExternal()) {
                continue;
            }
            
            // Check each BB
            List<IRBasicBlock> bbs = func.getBasicBlockList();
            
            for(int i = 0; i < bbs.size(); i++) {
                IRBasicBlock bb = bbs.get(i);
                
                if(canRemoveBB(bb)) {
                    removeBB(bb, func);
                    i--;
                }
            }
        }
        
        return module;
    }
    
    @Override
    public String toString() {
        return "EmptyBlockMerge"; 
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
         * - It has no arguments
         */
        
        if(bb.getInstructions().size() != 0) {
            LOG.finest(bb.getID() + " has instructions");
            return false;
        }
        
        if(bb.getExitInstruction().getOp() != IRBranchOperation.JMP) {
            LOG.finest(bb.getID() + " isnt jmp");
            return false;
        }
        
        if(bb.getArgumentList().getArgumentCount() != 0) {
            LOG.finest(bb.getID() + " has args");
            return false;
        }
        
        if(bb.getID().getName().equals("entry")) {
            LOG.finest(bb.getID() + " is entry");
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
        
        for(IRIdentifier predID : bb.getPredecessorBlocks()) {
            IRBasicBlock predBB = func.getBasicBlock(predID);
            IRBranchInstruction predBranch = predBB.getExitInstruction();
            
            if(bb.getID().equals(predBranch.getTrueTargetBlock())) {
                // We're true
                predBranch.setTrueTargetBlock(targetID);
                predBranch.setTrueArgumentMapping(mapping);
                targetBB.addPredecessor(predID);
            }
            
            if(bb.getID().equals(predBranch.getFalseTargetBlock())) {
                // We're false
                predBranch.setFalseTargetBlock(targetID);
                predBranch.setFalseArgumentMapping(mapping);
                targetBB.addPredecessor(predID);
            }
        }
        
        targetBB.removePredecessor(bb.getID());
        
        func.removeBasicBlock(bb.getID());
    }
    
}
