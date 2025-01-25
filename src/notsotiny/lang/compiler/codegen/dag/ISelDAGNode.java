package notsotiny.lang.compiler.codegen.dag;

import java.util.List;

/**
 * A node in a instruction selection DAG
 */
public abstract class ISelDAGNode {
    
    private ISelDAG dag;
    
    private ISelDAGNode chainNode;
    
    protected ISelDAGNode(ISelDAG dag) {
        this.dag = dag;
        this.chainNode = null;
    }
    
    /**
     * Returns the ISelDAG containing this node
     * @return
     */
    public ISelDAG getDAG() {
        return this.dag;
    }
    
    /**
     * Set the chain node of this node
     * @param node
     */
    public void setChain(ISelDAGNode node) {
        this.chainNode = node;
    }
    
    /**
     * Get the chain node of this node, or null. The chain node must be scheduled before this node.
     * @return
     */
    public ISelDAGNode getChain() {
        return this.chainNode;
    }
    
    /**
     * Returns a list of DAG nodes assigned as inputs, from left to right.
     * @return
     */
    public abstract List<ISelDAGNode> getInputNodes();
    
}
