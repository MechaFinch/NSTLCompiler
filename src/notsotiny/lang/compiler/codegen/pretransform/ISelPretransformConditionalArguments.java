package notsotiny.lang.compiler.codegen.pretransform;

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
 * A pre-DAG transformation that moves conditional argument assignments into their own basic blocks
 * TODO: lift code used only to generate a conditionally assigned argument into its new basic block?
 * Just moving mappings should improve register allocation as it reduces inter-block interference
 * and might come in handy when generating code.
 * Moving the code that produces those assigned values has obvious benefits, but is likely better
 * implemented in the IR optimization phase
 */
public class ISelPretransformConditionalArguments implements ISelPretransformer {

    @Override
    public void transform(IRFunction function) {
        // For each basic block
        // Copy of the list as we'll be adding BBs
        List<IRBasicBlock> basicBlocks = new ArrayList<>(function.getBasicBlockList());
        
        for(IRBasicBlock bb : basicBlocks) {
            IRBranchInstruction branch = bb.getExitInstruction();
            
            if(branch.getOp() == IRBranchOperation.JCC) {
                // We've found a basic block with a conditional branch.
                // Note - it is assumed that a conditional branch will either branch to different
                // basic blocks or assign different values to the arguments
                
                // Does it map arguments?
                if(branch.getTrueArgumentMapping().getMap().size() > 0) {
                    // The true target has mappings. Move them to their own BB
                    moveMapping(branch, branch.getTrueTargetBlock(), branch.getTrueArgumentMapping(), true, bb, function);
                }
                
                if(branch.getFalseArgumentMapping().getMap().size() > 0) {
                    // The false target has mappings. Move them to their own BB
                    moveMapping(branch, branch.getFalseTargetBlock(), branch.getFalseArgumentMapping(), false, bb, function);
                }
            }
        }
    }
    
    /**
     * Moves a mapping to its own basic block
     * @param branch
     * @param target
     * @param mapping
     * @param isTrue
     * @param bb
     * @param function
     */
    private void moveMapping(IRBranchInstruction branch, IRIdentifier target, IRArgumentMapping mapping, boolean isTrue, IRBasicBlock bb, IRFunction function) {
        // Make BB
        IRIdentifier newID = new IRIdentifier(bb.getID().getName() + (isTrue ? "%true%" : "%false%") + function.getFUID(), IRIdentifierClass.BLOCK);
        IRBasicBlock newBB = new IRBasicBlock(newID, function.getModule(), function, branch.getSourceLineNumber());
        function.addBasicBlock(newBB);
        
        // Move original target to new BB
        newBB.setExitInstruction(new IRBranchInstruction(IRBranchOperation.JMP, target, mapping, bb, branch.getSourceLineNumber()));
        
        // Retarget to new BB
        if(isTrue) {
            branch.setTrueTargetBlock(newID);
            branch.setTrueArgumentMapping(new IRArgumentMapping());
        } else {
            branch.setFalseTargetBlock(newID);
            branch.setFalseArgumentMapping(new IRArgumentMapping());
        }
    }
    
}
