package notsotiny.lang.compiler.codegen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import notsotiny.lang.compiler.aasm.AASMInstruction;
import notsotiny.lang.compiler.aasm.AASMMemory;
import notsotiny.lang.compiler.aasm.AASMPart;
import notsotiny.lang.compiler.aasm.AASMPrinter;
import notsotiny.lang.compiler.codegen.dag.ISelDAG;
import notsotiny.lang.compiler.codegen.dag.ISelDAGNode;
import notsotiny.lang.compiler.codegen.dag.ISelDAGTile;
import notsotiny.lang.util.MapUtil;
import notsotiny.lang.util.Pair;

/**
 * Performs instruction selection given the set of covering tiles for each node
 * Implements the NOLTIS algorithm found in Koes & Goldstein's Near-Optimal Instruction Selection on DAGs 
 */
public class ISelTileSelector {
    
    private static Logger LOG = Logger.getLogger(ISelTileSelector.class.getName());
    
    /**
     * Performs instruction selection by covering the DAG with ISelDAGTiles
     * @param matchedTiles Map from nodes to selected producing tile
     * @param chainMap Map from nodes to all selected covering tiles
     * @param dag
     * @param matchingTiles
     */
    public static void selectTiles(Map<ISelDAGNode, ISelDAGTile> matchedTiles, Map<ISelDAGNode, Set<ISelDAGTile>> coveringTiles, ISelDAG dag, Map<ISelDAGNode, Set<ISelDAGTile>> matchingTiles) {
        
        LOG.finer("Selecting tiles for " + dag.getBasicBlock().getID());
        
        // NOLTIS data
        // Maps node to best tile & best tile's cost
        Map<ISelDAGNode, Pair<ISelDAGTile, Integer>> bestChoiceForNode = new HashMap<>();
        // Set of nodes which should not be covered by multiple tiles
        Set<ISelDAGNode> fixedNodes = new HashSet<>();
        
        // Other data
        // DAG nodes in reverse topological sort order (producers first)
        List<ISelDAGNode> reverseTopologicalList = dag.getReverseTopologicalSort(false);
        
        // Remove a tile's root from its coveredNodes set.
        // Done to make the 'hasInteriorFixedNode' check simple
        for(Set<ISelDAGTile> tileSet : matchingTiles.values()) {
            for(ISelDAGTile tile : tileSet) {
                tile.coveredNodes().remove(tile.rootNode());
            }
        }
        
        // NOLTIS algorithm
        // Identify best choices
        bottomUpDP(dag, reverseTopologicalList, matchingTiles, bestChoiceForNode, fixedNodes);
        
        // Cover DAG according to initial choices
        topDownSelect(dag, bestChoiceForNode, matchedTiles, coveringTiles);
        
        // Identify fixed nodes
        improveCSEDecisions(dag, bestChoiceForNode, fixedNodes, coveringTiles);
        
        if(fixedNodes.size() == 0) {
            // If no fixed nodes were found, the next steps will yield the current results
            LOG.finest("No fixed nodes chosen");
            return;
        }
        
        // Identify best choices while respecting fixed nodes
        bottomUpDP(dag, reverseTopologicalList, matchingTiles, bestChoiceForNode, fixedNodes);
        
        // Cover DAG according to best choices
        topDownSelect(dag, bestChoiceForNode, matchedTiles, coveringTiles);
        
        return;
    }
    
    /**
     * Chooses the best tile for producing each node without considering overlap
     * @param dag
     * @param reverseTopologicalSort
     * @param matchingTiles
     * @param bestChoiceForNode
     * @param fixedNodes
     */
    private static void bottomUpDP(ISelDAG dag, List<ISelDAGNode> reverseTopologicalSort, Map<ISelDAGNode, Set<ISelDAGTile>> matchingTiles, Map<ISelDAGNode, Pair<ISelDAGTile, Integer>> bestChoiceForNode, Set<ISelDAGNode> fixedNodes) {
        LOG.finest("Choosing best tiles for nodes");
        
        // Ensure all inputs of a node are visited before it so that their best cost can be used 
        for(ISelDAGNode node : reverseTopologicalSort) {
            ISelDAGTile bestTile = null;
            int bestCost = Integer.MAX_VALUE;
            
            // Find best matching tile
            for(ISelDAGTile tile : matchingTiles.get(node)) {
                // Tile root was removed from coverdNodes, so this checks 'interior' nodes
                if(Collections.disjoint(tile.coveredNodes(), fixedNodes)) {
                    // The tile does have an interior fixed node
                    // Compute its cost
                    int cost = getTileCost(tile);
                    
                    for(ISelDAGNode input : tile.inputNodes()) {
                        cost += bestChoiceForNode.get(input).b;
                    }
                    
                    // Is it better
                    if(cost < bestCost) {
                        // Yes, use it
                        bestCost = cost;
                        bestTile = tile;
                    }
                }
            }
            
            if(LOG.isLoggable(Level.FINEST)) {
                LOG.finest("Best for " + node.getDescription() + ": " + bestCost + " " + AASMPrinter.getAASMString(bestTile.aasm()));
            }
            
            bestChoiceForNode.put(node, new Pair<>(bestTile, bestCost));
        }
    }
    
    /**
     * Determines the cost of a tile
     * @param tile
     * @return
     */
    private static int getTileCost(ISelDAGTile tile) {
        int cost = 0;
        
        // Cost = #instructions * 10 + #memory accesses
        for(AASMPart part : tile.aasm()) {
            if(part instanceof AASMInstruction inst) {
                cost += 10;
                
                if(inst.getSource() instanceof AASMMemory) {
                    cost++;
                }
                
                if(inst.getDestination() instanceof AASMMemory) {
                    cost++;
                }
            }
        }
        
        return cost;
    }
    
    /**
     * Tile the DAG according to bestChoiceForNode
     * @param dag
     * @param bestChoiceForNode
     * @param matchedTiles
     * @param coveringTiles
     */
    private static void topDownSelect(ISelDAG dag, Map<ISelDAGNode, Pair<ISelDAGTile, Integer>> bestChoiceForNode, Map<ISelDAGNode, ISelDAGTile> matchedTiles, Map<ISelDAGNode, Set<ISelDAGTile>> coveringTiles) {
        LOG.finest("Tiling DAG");
        
        // Note - tile root is not in tile.coveredNodes
        matchedTiles.clear();
        coveringTiles.clear();
        Deque<ISelDAGNode> queue = new ArrayDeque<>();
        
        queue.addAll(dag.getTerminators());
        
        // For each node that must be produced
        while(!queue.isEmpty()) {
            // Get node & its tile
            ISelDAGNode node = queue.poll();
            ISelDAGTile tile = bestChoiceForNode.get(node).a;
            
            if(!matchedTiles.containsValue(tile)) {
                if(LOG.isLoggable(Level.FINEST)) {
                    LOG.finest(node.getDescription() + ": " + AASMPrinter.getAASMString(tile.aasm()));
                }
                
                // Mark the tile as matched
                matchedTiles.put(tile.rootNode(), tile);
                
                // Mark covered nodes as being covered
                MapUtil.getOrCreateSet(coveringTiles, node).add(tile);
                
                for(ISelDAGNode interiorNode : tile.coveredNodes()) {
                    MapUtil.getOrCreateSet(coveringTiles, interiorNode).add(tile);
                }
                
                // Add tile inputs to queue
                queue.addAll(tile.inputNodes());
            }
        }
    }
    
    /**
     * Decide which nodes covered by multiple tiles are worth making common subexpressions
     * @param dag
     * @param bestChoiceForNode
     * @param fixedNodes
     * @param coveringTiles
     */
    private static void improveCSEDecisions(ISelDAG dag, Map<ISelDAGNode, Pair<ISelDAGTile, Integer>> bestChoiceForNode, Set<ISelDAGNode> fixedNodes, Map<ISelDAGNode, Set<ISelDAGTile>> coveringTiles) {
        LOG.finest("Choosing fixed nodes");
        
        // TODO
        // Might not be done for a while. Current patterns are highly unlikely
        // to produce 'costly' overlap
        // Note - DAG nodes track consumers
    }
    
}
