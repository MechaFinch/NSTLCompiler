package notsotiny.lang.compiler.codegen.alloc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import notsotiny.lang.ir.parts.IRIdentifier;

/**
 * A stack slot allocation interference graph
 */
public class SAInterferenceGraph {
    
    private Set<SAIGNode> allNodes;
    
    private Map<IRIdentifier, SAIGNode> identifierMap;
    
    public SAInterferenceGraph() {
        this.allNodes = new HashSet<>();
        this.identifierMap = new HashMap<>();
    }
    
    /**
     * Add a node to the graph
     * @param node
     */
    public void addNode(SAIGNode node) {
        this.allNodes.add(node);
        this.identifierMap.put(node.getIdentifier(), node);
    }
    
    public Set<SAIGNode> getAllNodes() { return this.allNodes; }
    public Map<IRIdentifier, SAIGNode> getIDMap() { return this.identifierMap; }
    
}
