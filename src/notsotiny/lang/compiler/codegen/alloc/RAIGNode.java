package notsotiny.lang.compiler.codegen.alloc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import notsotiny.asm.Register;
import notsotiny.lang.ir.parts.IRIdentifier;

/**
 * A Register Allocation Interference Graph Node
 */
public class RAIGNode {
    
    // What local this represents
    private IRIdentifier identifier;
    
    // Register Class of the identifier
    private RARegisterClass rClass;
    
    // What collection of nodes is this in
    private RASet rSet;
    
    // Tracks number of colors available to this node
    private int availableColors;
    
    // Measure of how many register colors can be denied this node
    // Generalized form of degree
    private int squeeze;
    
    // Squeeze where aliased classes can double-count
    private Map<RAVertex, Integer> rawSqueeze;
    
    // Registers which this node cannot be colored with
    // Only current non-empty usecase is live ranges which include CALLs
    // Which exclude A B C D
    private Set<Register> excluded;
    
    private boolean precolored;
    
    // Assigned register
    private Register color;
    
    // The actual graph part
    private Set<RAIGNode> interferingNodes;
    
    // Moves involving this node
    private Set<RAMove> moves;
    
    // The node this aliases to via coalescing
    private RAIGNode alias;
    
    private int rawSpillCost;
    
    /**
     * Create a node
     * @param id
     * @param rClass
     * @param rSet
     */
    public RAIGNode(IRIdentifier id, RARegisterClass rClass, RASet rSet) {
        this.identifier = id;
        this.rClass = rClass;
        this.rSet = rSet;
        
        this.squeeze = 0;
        this.excluded = new HashSet<>();
        this.availableColors = rClass.registers().size();
        this.precolored = false;
        this.color = Register.NONE;
        this.interferingNodes = new HashSet<>();
        this.moves = new HashSet<>();
        
        this.rawSqueeze = new HashMap<>();
        
        for(RAVertex v : MachineRegisters.vertices()) {
            this.rawSqueeze.put(v, 0);
        }
    }
    
    /**
     * Create a precolored node
     * @param id
     * @param rClass
     * @param rSet
     * @param color
     */
    public RAIGNode(IRIdentifier id, RARegisterClass rClass, RASet rSet, Register color) {
        this(id, rClass, rSet);
        
        this.precolored = true;
        this.availableColors = 1;
        this.color = color;
    }
    
    /**
     * Update squeeze to reflect the addition or removal of a neighbor with class c
     * @param c Class to neighbor
     * @param add true = addition, false = removal
     */
    public void updateSqueeze(RARegisterClass c, boolean add) {
        int d = MachineRegisters.worst1(this.rClass, c);
        this.squeeze += squeezeChange(MachineRegisters.vertex(this.rClass), add ? d : -d);
    }
    
    /**
     * Computes the change in squeeze according to v and d
     * @param v
     * @param d
     * @return
     */
    private int squeezeChange(RAVertex v, int d) {
        int a = this.rawSqueeze.get(v);
        this.rawSqueeze.put(v, a + d);
        
        int filteredChange = delta(a, d, MachineRegisters.bound(this.rClass, v));
        
        if(filteredChange == 0 || v.parent() == null) {
            return filteredChange;
        }
        
        return squeezeChange(v.parent(), filteredChange);
    }
    
    /**
     * capital-delta function
     * @param a
     * @param d
     * @param b
     * @return
     */
    private int delta(int a, int d, int b) {
        if(d >= 0) {
            return Math.max(0, Math.min(d, b - a));
        } else {
            return Math.min(0, Math.max(d, d - (b - a)));
        }
    }
    
    /**
     * Adds an interference edge between this and other.
     * Ensures interference exists from the perspective of both nodes.
     * Updates this node's squeeze value.
     * @param other
     */
    public void addInterference(RAIGNode other) {
        //Objects.requireNonNull(other);
        
        // Are we already interfering
        if(this.identifier.equals(other.identifier) || this.interferingNodes.contains(other)) {
            return;
        }
        
        if(!this.precolored) {
            // No. Interfere
            this.interferingNodes.add(other);
            
            // Update squeeze
            updateSqueeze(other.rClass, true);
        }
        
        // Ensure it goes both ways
        if(!other.precolored) {
            other.addInterference(this);
        }
    }
    
    /**
     * Excludes a register from this node
     * @param register
     */
    public void addExclusion(Register register) {
        addExclusion(MachineRegisters.aliasSet(register));
    }
    
    /**
     * Excludes a register class from this node
     * @param regClass
     */
    public void addExclusion(RARegisterClass regClass) {
        addExclusion(MachineRegisters.aliasSet(regClass));
    }
    
    /**
     * Adds a set of exclusions to this node
     * @param registers
     */
    private void addExclusion(Set<Register> registers) {
        if(!this.precolored) {
            this.excluded.addAll(registers);
            this.availableColors = getAllowedColors().size();
        }
    }
    
    /**
     * Gets a set of allowed colors. This set is safely modifiable
     * @return
     */
    public Set<Register> getAllowedColors() {
        Set<Register> available = new HashSet<>(this.rClass.registers());
        available.removeAll(excluded);
        return available;
    }
    
    /**
     * Set this register's color
     * @param color
     */
    public void setColor(Register color) {
        this.color = color;
        // NOTE - could do validation
    }
    
    /**
     * Add a move associated with this node (handed by RAMove constructor)
     * @param move
     */
    public void addMove(RAMove move) {
        this.moves.add(move);
    }
    
    /**
     * Add a set of moves
     * @param moves
     */
    public void addMoves(Set<RAMove> moves) {
        this.moves.addAll(moves);
    }
    
    /**
     * Get the node this aliases to
     * @return
     */
    public RAIGNode getAlias() {
        if(this.rSet == RASet.COALESCED) {
            return this.alias.getAlias();
        } else {
            return this;
        }
    }
    
    /**
     * Add raw spill cost to this node
     * @param cost
     * @return
     */
    public void addRawSpillCost(int cost) {
        this.rawSpillCost += cost;
    }
    
    /**
     * Add the cost of a use to this node
     */
    public void addUseCost() {
        this.rawSpillCost += 10;
    }
    
    /**
     * Add the cost of a def to this node
     */
    public void addDefCost() {
        this.rawSpillCost += 10;
    }
    
    /**
     * @return The heuristic cost of spilling this node
     */
    public int getSpillCost() {
        return (this.rawSpillCost * 10) / (this.interferingNodes.size() + 1);
    }
    
    public IRIdentifier getIdentifier() { return this.identifier; }
    public RARegisterClass getRegisterClass() { return this.rClass; }
    public int numAvailable() { return this.availableColors; }
    public RASet getSet() { return this.rSet; }
    public int getSqueeze() { return this.squeeze; }
    public Set<Register> getExcludedColors() { return this.excluded; }
    public boolean isPrecolored() { return this.precolored; }
    public Register getColoring() { return this.color; }
    public Set<RAIGNode> getInterferingNodes() { return this.interferingNodes; }
    public Set<RAMove> getMoves() { return this.moves; }
    public int getRawSpillCost() { return this.rawSpillCost; }
    
    public void setSet(RASet set) { this.rSet = set; }
    public void setAlias(RAIGNode node) { this.alias = node; }
}
