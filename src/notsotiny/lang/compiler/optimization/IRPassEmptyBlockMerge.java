package notsotiny.lang.compiler.optimization;

import java.util.List;
import java.util.logging.Logger;

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
            // Check each BB
            List<IRBasicBlock> bbs = func.getBasicBlockList();
            
            for(int i = 0; i < bbs.size(); i++) {
                IRBasicBlock bb = bbs.get(i);
                
                if(canRemoveBB(bb)) {
                    removeBB(bb, func);
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
            return false;
        }
        
        if(bb.getExitInstruction().getOp() != IRBranchOperation.JMP) {
            return false;
        }
        
        if(bb.getArgumentList().getArgumentCount() != 0) {
            return false;
        }
        
        if(bb.getID().getName().equals("entry")) {
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
        IRIdentifier target = bb.getExitInstruction().getTrueTargetBlock();
        IRArgumentMapping mapping = bb.getExitInstruction().getTrueArgumentMapping();
        
        for(IRIdentifier predID : bb.getPredecessorBlocks()) {
            IRBranchInstruction predBranch = func.getBasicBlock(predID).getExitInstruction();
            
            if(predBranch.getTrueTargetBlock().equals(bb.getID())) {
                // We're true
                predBranch.setTrueTargetBlock(target);
                predBranch.setTrueArgumentMapping(mapping);
            }
            
            if(predBranch.getFalseTargetBlock().equals(bb.getID())) {
                // We're false
                predBranch.setFalseTargetBlock(target);
                predBranch.setFalseArgumentMapping(mapping);
            }
        }
        
        func.removeBasicBlock(bb.getID());
    }
    
}
