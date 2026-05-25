package notsotiny.lang.compiler.optimization.transforms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import notsotiny.lang.ir.parts.IRArgumentList;
import notsotiny.lang.ir.parts.IRArgumentMapping;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRBranchInstruction;
import notsotiny.lang.ir.parts.IRBranchOperation;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.util.IRUtil;
import notsotiny.lib.data.Pair;
import notsotiny.lib.data.TreeNode;
import notsotiny.lib.data.UnionFindForest;

/**
 * An IR transformation that rotates loops
 * 
 * The goal of loop rotation is to have a loop's body before its condition
 * Loop rotation itself has a minor performance benefit in eliminating a branch since the backedge
 * becomes conditional rather than unconditional
 * The purpose of loop rotation with respect to code motion is to make hoisting code from the body
 * to the pre-header safe.
 * 
 * A rotated loop is characterized in the loop-nesting forest as a loop whose header does not
 * branch outside of the loop
 */
public class IRTransformLoopRotation {
    
    /**
     * Rotates any un-rotated loops in the given function
     * @param func
     * @return true if any modifications were made to the function
     */
    public static boolean rotateLoops(IRFunction func) {
        // Gather information
        Pair<List<IRIdentifier>, Map<Integer, Integer>> preorderInfo = IRUtil.getPreorderInfo(func);
        List<IRIdentifier> preorderList = preorderInfo.a;
        Map<Integer, Integer> preorderAncestryMap = preorderInfo.b;
        
        UnionFindForest<IRIdentifier> loopNestingForest = IRUtil.getLoopNestingForest(func, preorderList, preorderAncestryMap);
        
        boolean changed = false;
        
        // Process each top-level loop
        for(TreeNode<IRIdentifier> loopHeaderNode : loopNestingForest.getRoots()) {
            changed |= rotateLoop(loopHeaderNode, func, loopNestingForest.getElementNodeMap());
        }
        
        return changed;
    }
    
    /**
     * Rotate the given loop if it is not already rotated, then rotate any loops contained in the
     * given loop
     * @param loopNode
     * @param func
     * @param loopNestingForest
     * @param dominatorMap
     * @return true if changed
     */
    private static boolean rotateLoop(TreeNode<IRIdentifier> loopNode, IRFunction func, Map<IRIdentifier, TreeNode<IRIdentifier>> nodeMap) {
        /*
         * Note: Loops are rotated from outermost to innermost, such that header information remains
         * accurate enough for its purpose (identifying whether a successor is inside or outside the loop)
         */
        
        boolean changed = false;
        IRIdentifier loopHeaderID = loopNode.getElement();
        IRBasicBlock loopHeaderBB = func.getBasicBlock(loopHeaderID);
        IRBranchInstruction loopHeaderExit = loopHeaderBB.getExitInstruction();
        
        // Is this a loop which needs rotating?
        // The loop must:
        //  Have a body
        //  Have a branch from the header -> outside the loop
        if(loopNode.getChildren().size() != 0) {
            // Loop has a body
            boolean trueIsOutside = isInsideLoop(loopHeaderID, nodeMap.get(loopHeaderExit.getTrueTargetBlock())),
                    falseIsOutside = isInsideLoop(loopHeaderID, nodeMap.get(loopHeaderExit.getFalseTargetBlock()));
            
            if(trueIsOutside || falseIsOutside) {
                // The loop header has a branch that is outside the loop
                // Gather IDs of blocks in the loop body
                Set<IRIdentifier> loopBlockIDs = nodeMap.get(loopHeaderID).getAllElements();
                
                loopBlockIDs.remove(loopHeaderID); // loop blocks -> body blocks
                doRotation(loopHeaderID, loopBlockIDs, trueIsOutside, func, nodeMap);
                changed = true;
            }
        }
        
        // Finally, ensure inner loops are rotated
        for(TreeNode<IRIdentifier> innerHeaderNode : loopNode.getChildren()) {
            changed |= rotateLoop(innerHeaderNode, func, nodeMap);
        }
        
        return changed;
    }
    
    /**
     * Given that loopHeader is the header of an un-rotated loop, rotate it
     * @param loopHeaderID
     * @param bodyBlockIDs
     * @param trueIsOutside
     * @param func
     * @param nodeMap
     */
    private static void doRotation(IRIdentifier loopHeaderID, Set<IRIdentifier> bodyBlockIDs, boolean trueIsOutside, IRFunction func, Map<IRIdentifier, TreeNode<IRIdentifier>> nodeMap) {
        /*
         * Transform
         * 
         * [entry blocks]<--+
         *  |               |
         *  v               |
         * <loop header>    |
         *  |  | ^          |
         *  |  v | continue | labeled continue
         *  | [loop body ]--+
         *  |  | break      | labeled break
         *  v  v            |
         * <after loop>     |
         *  ?               | 
         *  v               |
         * [outside loop]<--+
         * 
         * into
         * 
         * [entry blocks]<--------+
         *  |                     |
         *  v                     |
         * <loop guard>           |
         *  |  |                  |
         *  |  v                  |
         *  | <loop preheader>    |
         *  |  |                  |
         *  |  v                  |
         *  | <loop header><-+    |
         *  |  |             |    |
         *  |  v             |    | labeled continue
         *  | [loop body]----|----+
         *  |  |  | continue |    | labeled break
         *  |  |  v          |    |
         *  |  | <loop condition> |
         *  |  |       |          |
         *  |  | break |          |
         *  v  v       v          |
         * <after loop  >         |
         *  ?                     |
         *  v                     |
         * [outside loop]<--------+
         */
        
        /*
         * A definition in the original loop header appears in both the loop guard and the loop
         * condition. If a loop has multiple exits due to a labeled break (a labeled continue will
         * not cause issues) a use of a value defined in the original loop header is no longer
         * dominated by a single definition.
         * To enable the rotation of loops with multiple exits, we ignore the SSA property and
         * reconstruct it afterwards for affected values. 
         */
        
        /*
         * Call SSA reconstruction function to repair SSA property for duplicated definitions 
         */
    }
    
    /**
     * Returns true if node is inside the loop with header header 
     * @param header
     * @param node
     * @return
     */
    private static boolean isInsideLoop(IRIdentifier header, TreeNode<IRIdentifier> node) {
        do {
            // The target has the header as an ancestor
            if(node.getElement().equals(header)) {
                return true;
            }
            
            node = node.getParent();
        } while(node != null); // if no parent, header does not contain input node
        
        return false;
    }
    
}
