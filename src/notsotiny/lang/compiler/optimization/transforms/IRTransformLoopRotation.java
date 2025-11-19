package notsotiny.lang.compiler.optimization.transforms;

import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
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
     * @param loopNestingForest
     * @return true if any modifications were made to the function
     */
    public static boolean rotateLoops(IRFunction func, UnionFindForest<IRIdentifier> loopNestingForest) {
        boolean changed = false;
        
        // Process each top-level loop
        for(TreeNode<IRIdentifier> loopHeaderNode : loopNestingForest.getRoots()) {
            changed |= rotateLoop(func, loopHeaderNode);
        }
        
        return changed;
    }
    
    /**
     * Rotate the given loop if it is not already rotated, then rotate any loops contained in the
     * given loop
     * @param func
     * @param loopNode
     * @return
     */
    private static boolean rotateLoop(IRFunction func, TreeNode<IRIdentifier> loopNode) {
        /*
         * <entry bb(s)>
         *  |
         *  v
         * <conditional bb>-+
         *  |   ^           |
         *  v   | cont      |
         * <body bb(s)>     |
         *  | break         |
         *  v               |
         * <exit bb> <------+
         *  
         *  transformed to
         *  
         * <entry bb(s)>
         *  |
         *  v
         * <conditional bb>----+
         *  |                  |
         *  v                  |
         * <body bbs> <+       |
         *  |  | cont  |       |
         *  |  v       |       |
         *  | <conditional bb> |
         *  | break  |         |
         *  v     v--+         |
         * <exit bb> <---------+
         */
        
        /*
         * Note: Loops are rotated from outermost to innermost, such that header information remains
         * accurate enough for its purpose (identifying whether a successor is inside or outside the loop)
         */
        
        // Is this a loop with a body?
        if(loopNode.getChildren().size() != 0) {
            boolean changed = false;
            
            // TODO
            
            // Finally, ensure inner loops are rotated
            for(TreeNode<IRIdentifier> innerHeaderNode : loopNode.getChildren()) {
                changed |= rotateLoop(func, innerHeaderNode);
            }
            
            return changed;
        }
        
        return false;
    }
    
    /**
     * Returns true if node is inside the loop with header header 
     * @param header
     * @param node
     * @return
     */
    private static boolean isInsideLoop(IRIdentifier header, TreeNode<IRIdentifier> node) {
        do {
            // A header of the nesting is the target
            if(node.getElement().equals(header)) {
                return true;
            }
            
            node = node.getParent();
        } while(node != null); // if no parent, header does not contain input node
        
        return false;
    }
    
}
