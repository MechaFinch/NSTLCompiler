package notsotiny.lang.compiler.optimization.transforms;

import java.util.ArrayList;
import java.util.List;

import notsotiny.lang.ir.parts.IRArgumentMapping;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRBranchInstruction;
import notsotiny.lang.ir.parts.IRBranchOperation;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;

/**
 * An IR transformation that removes critical edges
 * 
 * A critical edge is a CFG edge from a BB with multiple successors to a BB with multiple
 * predecessors. A critical edge is removed by inserting an empty BB between the two BBs
 * 
 * This transformation assists with partial redundancy elimination
 */
public class IRTransformCriticalEdgeRemoval {
    
    /**
     * Removes critical edges from the given function
     * @param func
     * @return true if any modifications were made to the function
     */
    public static boolean removeCriticalEdges(IRFunction func) {
        boolean changed = false;
        
        // Copy of the block list so we can modify it and skip checking new blocks 
        List<IRBasicBlock> originalBBs = new ArrayList<>(func.getBasicBlockList());
        
        // Process each BB
        for(IRBasicBlock fromBB : originalBBs) {
            // Does this block have multiple successors?
            IRIdentifier fromID = fromBB.getID();
            IRBranchInstruction branch = fromBB.getExitInstruction();
            
            if(branch.getOp() == IRBranchOperation.JCC) {
                // Yes
                // (A conditional branch with the same BB as both targets is assumed to have
                // different values assigned to its arguments)
                
                // Check each successor for having multiple predecessors
                // Check true successor
                IRIdentifier trueID = branch.getTrueTargetBlock();
                IRBasicBlock trueBlock = func.getBasicBlock(trueID);
                
                if(trueBlock.getPredecessorBlocks().size() > 1) {
                    // True edge is critical
                    // Create new basic block
                    IRIdentifier newID = func.getFUID(fromID.getName() + "%true", IRIdentifierClass.BLOCK);
                    IRBasicBlock newBB = new IRBasicBlock(newID, func.getModule(), func, fromBB.getSourceLineNumber());
                    func.addBasicBlock(newBB);
                    
                    // Make unconditional branch from new BB to true successor with original argument mapping
                    newBB.setExitInstruction(new IRBranchInstruction(IRBranchOperation.JMP, trueID, branch.getTrueArgumentMapping(), newBB, branch.getSourceLineNumber()));
                    
                    // Redirect original branch to new block
                    branch.setTrueTargetBlock(newID);
                    branch.setTrueArgumentMapping(new IRArgumentMapping());
                    
                    // Update predecessor information
                    trueBlock.removePredecessor(fromID);
                    trueBlock.addPredecessor(newID);
                    
                    changed = true;
                }
                
                // Check false successor
                IRIdentifier falseID = branch.getFalseTargetBlock();
                IRBasicBlock falseBlock = func.getBasicBlock(falseID);
                
                if(falseBlock.getPredecessorBlocks().size() > 1) {
                    // False edge is critical
                    // Create new basic block
                    IRIdentifier newID = func.getFUID(fromID.getName() + "%false", IRIdentifierClass.BLOCK);
                    IRBasicBlock newBB = new IRBasicBlock(newID, func.getModule(), func, branch.getSourceLineNumber());
                    func.addBasicBlock(newBB);
                    
                    // Make unconditional branch from new BB to false successor with original argument mapping
                    newBB.setExitInstruction(new IRBranchInstruction(IRBranchOperation.JMP, falseID, branch.getFalseArgumentMapping(), newBB, branch.getSourceLineNumber()));
                    
                    // Redirect original branch to new block
                    branch.setFalseTargetBlock(newID);
                    branch.setFalseArgumentMapping(new IRArgumentMapping());
                    
                    // Update predecessor information
                    falseBlock.removePredecessor(fromID);
                    falseBlock.addPredecessor(newID);
                    
                    changed = true;
                }
            }
        }
        
        return changed;
    }
    
}
