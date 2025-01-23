package notsotiny.lang.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of the UNION-FIND data structure
 * Contains a forest of trees where each tree's root is the representative of its equivalence class
 */
public class UnionFindForest<T> {
    
    // Tree roots/equivalence class representatives
    private List<TreeNode<T>> representatives;
    
    // Maps elements to their nodes
    private Map<T, TreeNode<T>> elementNodeMap;
    
    /**
     * Creates a UNION-FIND data structure
     */
    public UnionFindForest() {
        this.representatives = new ArrayList<>();
        this.elementNodeMap = new HashMap<>();
    }
    
    /**
     * Add an element if it is not present. It will be in its own equivalency class.
     * @param element
     */
    public void add(T element) {
        // Is the node present
        if(this.elementNodeMap.containsKey(element)) {
            return;
        }
        
        // No, add it.
        TreeNode<T> node = new TreeNode<>(element);
        representatives.add(node);
        elementNodeMap.put(element, node);
    }
    
    /**
     * Performs FIND(element), returning the element associated with the representative node
     * @param element
     * @return
     */
    public T findElement(T element) {
        // Find
        TreeNode<T> node = findNode(element);
        
        // Return element
        return node.getElement();
    }
    
    /**
     * Performs FIND(element), returning the representative node
     * @param element
     * @return
     */
    public TreeNode<T> findNode(T element) {
        // Get leaf, find representative
        return findNode(this.elementNodeMap.get(element));
    }
    
    /**
     * Performs FIND(node), returning the representative node
     * @param node
     * @return
     */
    public TreeNode<T> findNode(TreeNode<T> node) {
        // Is it the representative
        if(node.getParent() == null || node.getParent() == node) {
            return node;
        } else {
            return findNode(node.getParent());
        }
    }
    
    /**
     * Merges the equivalency classes of elementA and elementB
     * @param elementA
     * @param elementB
     */
    public void union(T elementA, T elementB) {
        union(this.elementNodeMap.get(elementA), this.elementNodeMap.get(elementB));
    }
    
    /**
     * Merges the equivalency classes of nodeA and nodeB
     * @param nodeA
     * @param nodeB
     */
    public void union(TreeNode<T> nodeA, TreeNode<T> nodeB) {
        // Get representatives
        TreeNode<T> repA = findNode(nodeA),
                    repB = findNode(nodeB);
        
        // Make the representative of A the parent of the representative of B
        repB.setParent(repA);
        this.representatives.remove(repB);
    }
    
    public List<TreeNode<T>> getRoots() { return this.representatives; }
    public Map<T, TreeNode<T>> getElementNodeMap() { return this.elementNodeMap; }
    
    /**
     * logs the forest
     * @param log
     */
    public void logForest(Logger log, Level level) {
        if(log.isLoggable(level)) {
            for(TreeNode<T> node : this.elementNodeMap.values()) {
                node.logTree(log, level);
            }
        }
    }
    
}
