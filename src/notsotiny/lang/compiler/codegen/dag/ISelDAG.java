package notsotiny.lang.compiler.codegen.dag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRIdentifier;

/**
 * A directed acyclic graph for instruction selection
 */
public class ISelDAG {
    
    // All nodes
    private List<ISelDAGNode> allNodes;
    
    // Terminators - stores, live-outs, branches
    private List<ISelDAGNode> terminators;
    
    // Map of nodes that produce identifiers
    private Map<IRIdentifier, ISelDAGProducerNode> idProducers;
    
    // Source IR BB
    private IRBasicBlock bb;
    
    /**
     * Creates an ISelDAG with the given basic block as its parent
     * @param sourceBB
     */
    public ISelDAG(IRBasicBlock sourceBB) {
        this.bb = sourceBB;
        
        this.allNodes = new ArrayList<>();
        this.terminators = new ArrayList<>();
        this.idProducers = new HashMap<>();
    }
    
    /**
     * Add a node. Called by the node on construction
     * @param node
     */
    protected void addNode(ISelDAGNode node) {
        this.allNodes.add(node);
        
        // Producer or terminator?
        if(node instanceof ISelDAGProducerNode prod && prod.getProducedValue() instanceof IRIdentifier pid) {
            this.idProducers.put(pid, prod);
        } else {
            this.terminators.add(node);
        }
    }
    
    public List<ISelDAGNode> getAllNodes() { return this.allNodes; }
    public List<ISelDAGNode> getTerminators() { return this.terminators; }
    public Map<IRIdentifier, ISelDAGProducerNode> getProducers() { return this.idProducers; }
    public IRBasicBlock getBasicBlock() { return this.bb; }
    
}
