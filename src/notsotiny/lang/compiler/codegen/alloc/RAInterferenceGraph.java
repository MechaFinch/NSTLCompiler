package notsotiny.lang.compiler.codegen.alloc;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import notsotiny.asm.Register;
import notsotiny.lang.compiler.aasm.AASMMachineRegister;
import notsotiny.lang.ir.parts.IRIdentifier;

/**
 * A register allocation interference graph
 */
public class RAInterferenceGraph {
    
    private Set<RAIGNode> allNodes;
    
    private Map<IRIdentifier, RAIGNode> identifierMap;
    
    public RAInterferenceGraph() {
        this.allNodes = new HashSet<>();
        this.identifierMap = new HashMap<>();
        
        initializeMachineRegisters();
    }
    
    /**
     * Creates nodes for each machine register
     */
    private void initializeMachineRegisters() {
        initializeRegisterClass(RARegisterClass.I8);
        initializeRegisterClass(RARegisterClass.I16);
        initializeRegisterClass(RARegisterClass.I32);
    }
    
    private void initializeRegisterClass(RARegisterClass rclass) {
        for(Register r : rclass.registers()) {
            // Use to get ID of the register
            AASMMachineRegister mr = new AASMMachineRegister(r);
            
            // Make node
            addNode(new RAIGNode(
                mr.id(),
                rclass,
                RASet.PRECOLORED,
                r
            ));
        }
    }
    
    /**
     * Add a node to the graph
     * @param node
     */
    public void addNode(RAIGNode node) {
        this.allNodes.add(node);
        this.identifierMap.put(node.getIdentifier(), node);
    }
    
    /**
     * Gets the node with the given ID
     * @param id
     * @return
     */
    public RAIGNode getNode(IRIdentifier id) {
        return this.identifierMap.get(id);
    }
    
    /**
     * Add the cost of a def to the given node
     * @param id
     */
    public void addDefCost(IRIdentifier id) {
        this.identifierMap.get(id).addDefCost();
    }
    
    /**
     * Add the cost of a def to the given nodes
     * @param ids
     */
    public void addDefCost(Collection<IRIdentifier> ids) {
        for(IRIdentifier id : ids) {
            addDefCost(id);
        }
    }
    
    /**
     * Add the cost of a use to the given node
     * @param id
     */
    public void addUseCost(IRIdentifier id) {
        this.identifierMap.get(id).addUseCost();
    }
    
    /**
     * Add the cost of a use to the given nodes
     * @param ids
     */
    public void addUseCost(Collection<IRIdentifier> ids) {
        for(IRIdentifier id : ids) {
            addUseCost(id);
        }
    }
    
    /**
     * Adds interference between nodes with IDs a and b
     * @param a
     * @param b
     */
    public void addInterference(IRIdentifier a, IRIdentifier b) {
        this.identifierMap.get(a).addInterference(this.identifierMap.get(b));
    }
    
    public Set<RAIGNode> getAllNodes() { return this.allNodes; }
    public Map<IRIdentifier, RAIGNode> getIDMap() { return this.identifierMap; }
    
}
