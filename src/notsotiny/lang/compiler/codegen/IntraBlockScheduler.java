package notsotiny.lang.compiler.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Set;

import notsotiny.lang.compiler.aasm.AASMPart;
import notsotiny.lang.compiler.aasm.AASMPrinter;
import notsotiny.lang.compiler.codegen.dag.ISelDAG;
import notsotiny.lang.compiler.codegen.dag.ISelDAGNode;
import notsotiny.lang.compiler.codegen.dag.ISelDAGOperation;
import notsotiny.lang.compiler.codegen.dag.ISelDAGProducerOperation;
import notsotiny.lang.compiler.codegen.dag.ISelDAGTerminatorNode;
import notsotiny.lang.compiler.codegen.dag.ISelDAGTerminatorOperation;
import notsotiny.lang.compiler.codegen.dag.ISelDAGTile;
import notsotiny.lib.util.MapUtil;

/**
 * Schedules instructions within a basic block
 */
public class IntraBlockScheduler {
    
    private static Logger LOG = Logger.getLogger(IntraBlockScheduler.class.getName());
    
    /**
     * Schedules the AASM contents of the given set of tiles
     * @param dag
     * @param matchedTiles
     * @param coveringTiles
     * @return
     */
    public static List<List<AASMPart>> scheduleBlock(ISelDAG dag, Map<ISelDAGNode, ISelDAGTile> matchedTiles, Map<ISelDAGNode, Set<ISelDAGTile>> coveringTiles) {
        LOG.finer("Scheduling tiles in " + dag.getBasicBlock().getID());
        
        // Get a topological ordering of tiles (terminators first)
        List<ISelDAGTile> topologicalList = getTopologicalSort(matchedTiles, coveringTiles);
        
        // Compute the longest path from any terminator to any node
        Map<ISelDAGTile, Integer> globalLongest = new HashMap<>();
        
        for(ISelDAGNode node : dag.getTerminators()) {
            ISelDAGTile termTile = matchedTiles.get(node);
            
            // Compute longest path from this terminator to reachable nodes
            // Algorithm described by https://en.wikipedia.org/wiki/Topological_sorting#Application_to_shortest_path_finding
            Map<ISelDAGTile, Integer> lengthFromTerm = new HashMap<>();
            lengthFromTerm.put(termTile, 0);
            
            for(int i = topologicalList.indexOf(termTile); i < topologicalList.size(); i++) {
                ISelDAGTile currentTile = topologicalList.get(i);
                int lengthToTile = lengthFromTerm.getOrDefault(currentTile, 0);
                
                // Check inputs
                for(ISelDAGNode input : currentTile.inputNodes()) {
                    ISelDAGTile inputTile = matchedTiles.get(input);
                    int lengthToInput = lengthToTile + getEdgeWeight(inputTile, false);
                    
                    if(lengthFromTerm.getOrDefault(inputTile, 0) < lengthToInput) {
                        lengthFromTerm.put(inputTile, lengthToInput);
                    }
                }
                
                // Check chain
                if(currentTile.chainNode() != null) {
                    for(ISelDAGTile chainTile : coveringTiles.get(currentTile.chainNode())) {
                        int lengthToChain = lengthToTile + getEdgeWeight(chainTile, true);
                        
                        if(lengthFromTerm.getOrDefault(chainTile, 0) < lengthToChain) {
                            lengthFromTerm.put(chainTile, lengthToChain);
                        }
                    }
                }
            }
            
            // Merge information with global map
            for(Entry<ISelDAGTile, Integer> entry : lengthFromTerm.entrySet()) {
                if(entry.getValue() > globalLongest.getOrDefault(entry.getKey(), -1)) {
                    globalLongest.put(entry.getKey(), entry.getValue());
                }
            }
        }
        
        // Ensure that the exit instruction is scheduled last by assigning it negative priority
        out:
        for(ISelDAGTile tile : globalLongest.keySet()) {
            if(tile.rootNode() instanceof ISelDAGTerminatorNode tn) {
                switch(tn.getOperation()) {
                    case ISelDAGTerminatorOperation.JMP,
                         ISelDAGTerminatorOperation.JCC,
                         ISelDAGTerminatorOperation.RET:
                        // Exit instruction found
                        globalLongest.put(tile, -9999);
                        break out;
                        
                    default:
                        // no action
                }
            }
        }
        
        // Create producer -> consumer tile map
        Map<ISelDAGTile, Set<ISelDAGTile>> consumersMap = new HashMap<>();
        
        for(ISelDAGTile tile : globalLongest.keySet()) {
            // Inputs
            for(ISelDAGNode inputNode : tile.inputNodes()) {
                MapUtil.getOrCreateSet(consumersMap, matchedTiles.get(inputNode)).add(tile);
            }
            
            // Chain
            if(tile.chainNode() != null) {
                for(ISelDAGTile chainTile : coveringTiles.get(tile.chainNode())) {
                    MapUtil.getOrCreateSet(consumersMap, chainTile).add(tile);
                }
            }
        }
        
        // Schedule code
        List<List<AASMPart>> schedule = new ArrayList<>();
        PriorityQueue<ISelDAGTile> readyQueue = new PriorityQueue<>((a, b) -> globalLongest.get(b) - globalLongest.get(a));
        Set<ISelDAGTile> scheduled = new HashSet<>();
        
        // Initialize ready with leaves
        for(ISelDAGTile tile : globalLongest.keySet()) {
            // Tile is ready if nothing needs to be scheduled before it
            if(tile.inputNodes().size() == 0 && tile.chainNode() == null) {
                //LOG.finest("Initial tile: " + tile.rootNode().getDescription());
                readyQueue.add(tile);
            }
        }
        
        // Build schedule
        while(!readyQueue.isEmpty()) {
            // Get highest priority (distance) ready tile
            ISelDAGTile tile = readyQueue.poll();
            
            // Add tile's code to schedule
            scheduled.add(tile);
            schedule.add(tile.aasm());
            
            // Log the scheduling
            if(LOG.isLoggable(Level.FINEST)) {
                LOG.finest(tile.rootNode().getDescription() + ": " + AASMPrinter.getAASMString(tile.aasm()));
            }
            
            // Add successors which are now ready
            if(consumersMap.containsKey(tile)) {
                checking:
                for(ISelDAGTile successorTile : consumersMap.get(tile)) {
                    // Check that the successor is ready
                    // Check data dependencies
                    for(ISelDAGNode succInputNode : successorTile.inputNodes()) {
                        if(!scheduled.contains(matchedTiles.get(succInputNode))) {
                            // Found an unscheduled input. Not ready.
                            continue checking;
                        }
                    }
                    
                    // Check time dependencies
                    if(successorTile.chainNode() != null) {
                        for(ISelDAGTile succChainTile : coveringTiles.get(successorTile.chainNode())) {
                            if(!scheduled.contains(succChainTile)) {
                                // Found an unscheduled chain. Not ready.
                                continue checking;
                            }
                        }
                    }
                    
                    // If we're here, the successor is ready
                    readyQueue.offer(successorTile);
                }
            }
        }
        
        return schedule;
    }
    
    /**
     * Returns the weight of a tile wrt scheduling
     * @param tile
     * @return
     */
    private static int getEdgeWeight(ISelDAGTile tile, boolean isChain) {
        int weight = 10;
        
        ISelDAGOperation rootOp = tile.rootNode().getOp();
        
        switch(rootOp) {
            case ISelDAGProducerOperation.VALUE:
                // please put immediates where they're used
                return 1;
            
            case ISelDAGProducerOperation.IN:
                // Live ins should happen on first use
                return 1;
            
            case ISelDAGProducerOperation.PUSH:
                // please push when you have the value 
                return 100;
            
            case ISelDAGTerminatorOperation.CALLN,
                 ISelDAGProducerOperation.CALLR:
                weight += 5;
                break;
            
            default:
        }
        // Assign chains some weight
        if(isChain) {
            weight += 10;
        }
        
        return weight;
    }
    
    /**
     * Determines a topological sort of the tiles
     * @param matchedTiles
     * @param coveringTiles
     * @return
     */
    private static List<ISelDAGTile> getTopologicalSort(Map<ISelDAGNode, ISelDAGTile> matchedTiles, Map<ISelDAGNode, Set<ISelDAGTile>> coveringTiles) {
        List<ISelDAGTile> tList = new ArrayList<>();
        Set<ISelDAGTile> unmarked = new HashSet<>(matchedTiles.values());
        
        while(!unmarked.isEmpty()) {
            tsDFSVisit(unmarked.iterator().next(), unmarked, tList, matchedTiles, coveringTiles);
        }
        
        return tList;
    }
    
    /**
     * depth-first search visit for getTopologicalSort
     * @param tile
     * @param unmarked
     * @param tList
     * @param matchedTiles
     * @param coveringTiles
     */
    private static void tsDFSVisit(ISelDAGTile tile, Set<ISelDAGTile> unmarked, List<ISelDAGTile> tList, Map<ISelDAGNode, ISelDAGTile> matchedTiles, Map<ISelDAGNode, Set<ISelDAGTile>> coveringTiles) {
        if(!unmarked.contains(tile)) {
            // Already dealt with
            return;
        }
        
        //LOG.finest("Visiting " + tile.rootNode().getDescription() + " " + AASMPrinter.getAASMString(tile.aasm()));
        
        // Visit inputs
        for(ISelDAGNode inputNode : tile.inputNodes()) {
            tsDFSVisit(matchedTiles.get(inputNode), unmarked, tList, matchedTiles, coveringTiles);
        }
        
        // Visit chain
        if(tile.chainNode() != null) {
            for(ISelDAGTile chainTile : coveringTiles.get(tile.chainNode())) {
                tsDFSVisit(chainTile, unmarked, tList, matchedTiles, coveringTiles);
            }
        }
        
        // Mark & add to ts list
        unmarked.remove(tile);
        tList.addFirst(tile); // reverse topological sort would append
    }
    
}
