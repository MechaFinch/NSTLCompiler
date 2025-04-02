package notsotiny.lang.compiler.codegen.alloc;

import java.util.HashSet;
import java.util.Set;

import notsotiny.lang.ir.parts.IRIdentifier;

/**
 * A stack slot allocation graph node
 */
public class SAIGNode {
    
    // Slot identifier
    private IRIdentifier identifier;
    
    // Size of the slot
    private int size;
    
    // slot = [BP - (offset + globalOffset)] 
    private int offset;
    
    private Set<SAIGNode> interferingNodes;
    
    /**
     * Create a node
     * @param id
     * @param size
     */
    public SAIGNode(IRIdentifier id, int size) {
        this.identifier = id;
        this.size = size;
        
        this.offset = -1;
        this.interferingNodes = new HashSet<>();
    }
    
    /**
     * Adds an interference edge between this and other
     * @param other
     */
    public void addInterference(SAIGNode other) {
        this.interferingNodes.add(other);
        other.interferingNodes.add(this);
    }
    
    public IRIdentifier getIdentifier() { return this.identifier; }
    public int getSize() { return this.size; }
    public int getOffset() { return this.offset; }
    public Set<SAIGNode> getInterferingNodes() { return this.interferingNodes; }
    
    public void setOffset(int offset) { this.offset = offset; }
    
}
