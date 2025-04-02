package notsotiny.lang.compiler.codegen.alloc;

/**
 * Indicates which set a node is in.
 * The collections of nodes used by the iterated coalescing algorithm are mutually disjoint,
 * and every node is in exactly 1 set at any time.
 */
public enum RASet {
    PRECOLORED, // Node is precolored
    INITIAL,    // Node is not yet processed
    SIMPLIFY,   // Node is low-degree, non-move-related
    FREEZE,     // Node is low-degree, move-related
    SPILL,      // Node is high-degree
    SPILLED,    // Node is marked for spilling
    COALESCED,  // Node has been coalesced into another
    COLORED,    // Node has been successfully colored
    SELECT      // Node has been removed from the graph
}
