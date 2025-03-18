package notsotiny.lang.compiler.codegen.dag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRIdentifier;

/**
 * A directed acyclic graph for instruction selection
 */
public class ISelDAG {
    
    // All nodes
    private Set<ISelDAGNode> allNodes;
    
    // Terminators - stores, live-outs, branches
    private Set<ISelDAGNode> terminators;
    
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
        
        this.allNodes = new HashSet<>();
        this.terminators = new HashSet<>();
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
        }
    }
    
    /**
     * Mark a node as a terminator
     * @param node
     */
    public void addTerminator(ISelDAGNode node) {
        this.terminators.add(node);
    }
    
    /**
     * Get the producer of a local
     * @param local
     * @return
     */
    public ISelDAGProducerNode getProducer(IRIdentifier local) {
        return this.idProducers.get(local);
    }
    
    /**
     * @return All nodes in the DAG in reverse topological sort order (producers first)
     */
    public List<ISelDAGNode> getReverseTopologicalSort(boolean respectChain) {
        List<ISelDAGNode> rtList = new ArrayList<>();
        Set<ISelDAGNode> unmarked = new HashSet<>(this.allNodes);
        
        while(!unmarked.isEmpty()) {
            rtsDFSVisit(unmarked.iterator().next(), unmarked, rtList, respectChain);
        }
        
        return rtList;
    }
    
    /**
     * depth-first search visit for getReverseTopologicalSort
     * @param node
     * @param permMarked
     * @param unmarked
     * @param rtList
     */
    private static void rtsDFSVisit(ISelDAGNode node, Set<ISelDAGNode> unmarked, List<ISelDAGNode> rtList, boolean respectChain) {
        if(!unmarked.contains(node)) {
            // Node has already been dealt with
            return;
        }
        
        // Visit inputs
        for(ISelDAGNode input : node.getInputNodes()) {
            rtsDFSVisit(input, unmarked, rtList, respectChain);
        }
        
        // Visit chain if applicable
        if(respectChain && node.getChain() != null) {
            rtsDFSVisit(node.getChain(), unmarked, rtList, respectChain);
        }
        
        // Mark node and add to rts list
        unmarked.remove(node);
        rtList.add(node); // topological sort would prepend
    }
    
    public Set<ISelDAGNode> getAllNodes() { return this.allNodes; }
    public Set<ISelDAGNode> getTerminators() { return this.terminators; }
    public Map<IRIdentifier, ISelDAGProducerNode> getProducers() { return this.idProducers; }
    public IRBasicBlock getBasicBlock() { return this.bb; }
    
}
