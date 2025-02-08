package notsotiny.lang.compiler.codegen.dag;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in a instruction selection DAG
 */
public abstract class ISelDAGNode {
    
    protected ISelDAG dag;
    
    protected ISelDAGNode chainNode;
    
    protected List<ISelDAGProducerNode> inputNodes;
    
    protected ISelDAGNode(ISelDAG dag) {
        this.dag = dag;
        
        this.chainNode = null;
        this.inputNodes = new ArrayList<>();
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
    public List<ISelDAGProducerNode> getInputNodes() {
        return this.inputNodes;
    }
    
}
