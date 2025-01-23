package notsotiny.lang.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A tree with elements of type T
 * @param <T>
 */
public class TreeNode<T> {
    
    private T element;
    
    private TreeNode<T> parent;
    
    private List<TreeNode<T>> children;
    
    /**
     * Create a TreeNode
     * @param element
     */
    public TreeNode(T element) {
        this.element = element;
        this.parent = null;
        this.children = new ArrayList<>();
    }
    
    /**
     * Make child a child of this TreeNode
     * @param child
     */
    public void addChild(TreeNode<T> child) {
        child.parent = this;
        this.children.add(child);
    }
    
    public void setParent(TreeNode<T> parent) {
        parent.addChild(this);
    }
    
    /**
     * Unsets the parent of this node's current children, clears this node's children, then adds each node in children as a child of this node
     * @param children
     */
    public void setChildren(List<TreeNode<T>> children) {
        for(TreeNode<T> oldChild : this.children) {
            oldChild.setParent(null);
        }
        
        this.children.clear();
        
        for(TreeNode<T> child : children) {
            addChild(child);
        }
    }
    
    public void setElement(T element) { this.element = element; }
    
    public T getElement() { return this.element; }
    public TreeNode<T> getParent() { return this.parent; }
    public List<TreeNode<T>> getChildren() { return this.children; }
    
    @Override
    public String toString() {
        return this.element.toString();
    }
    
    @Override
    public boolean equals(Object o) {
        if(o instanceof TreeNode tn) {
            return this.element.equals(tn.element);
        }
        
        return false;
    }
    
    @Override
    public int hashCode() {
        return this.element.hashCode();
    }
    
    /**
     * Logs this tree
     * @param log
     */
    public void logTree(Logger log, Level level) {
        if(log.isLoggable(level)) {
            logTree(log, level, this, new boolean[] {});
        }
    }
    
    /**
     * Log with depth
     * @param log
     * @param depth
     */
    private void logTree(Logger log, Level level, TreeNode<T> node, boolean[] crossings) {
        StringBuilder sb = new StringBuilder();
        
        for(int i = 0; i < crossings.length - 1; i++) {
            sb.append(crossings[i] ? "|  " : "   ");
        }
        
        if(crossings.length > 0) {
            sb.append("+-> ");
        }
        
        if(node != null) {
            sb.append(node.getElement().toString());
            log.log(level, sb.toString());
            
            for(int i = 0; i < node.getChildren().size(); i++) {
                boolean[] childCrossings = Arrays.copyOf(crossings, crossings.length + 1);
                childCrossings[childCrossings.length - 1] = (i < node.getChildren().size() - 1);
                
                logTree(log, level, node.getChildren().get(i), childCrossings);
            }
        } else {
            log.finest("null");
        }
    }
    
}
