package notsotiny.lang.compiler.codegen.alloc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.Set;

import notsotiny.lang.compiler.aasm.AASMInstruction;
import notsotiny.lang.compiler.aasm.AASMLiveSet;
import notsotiny.lang.compiler.aasm.AASMOperation;
import notsotiny.lang.compiler.aasm.AASMPart;
import notsotiny.lang.compiler.aasm.AASMStackSlot;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;

/**
 * Allocates stack slots
 */
public class StackAllocator {
    
    private static Logger LOG = Logger.getLogger(StackAllocator.class.getName());
    
    /**
     * Performs stack slot allocation for the given code
     * @param abstractCode
     * @param preexistingSlots
     * @param slotSizes
     * @param sourceFunction
     * @return
     */
    public static Map<IRIdentifier, Integer> allocateStackSlots(List<List<AASMPart>> abstractCode, Set<IRIdentifier> preexistingSlots, Map<IRIdentifier, Integer> slotSizes, IRFunction sourceFunction) {
        SAInterferenceGraph graph = new SAInterferenceGraph();
        
        LOG.finest("Allocating stack slots for " + sourceFunction.getID());
        // TODO: untested
        
        // Build interference graph
        // Create a node for each slot
        for(Entry<IRIdentifier, Integer> slot : slotSizes.entrySet()) {
            if(!preexistingSlots.contains(slot.getKey())) {
                graph.addNode(new SAIGNode(slot.getKey(), slot.getValue()));
            }
        }
        
        // For each instruction backwards
        Set<IRIdentifier> currentlyLive = new HashSet<>();
        
        for(int g = abstractCode.size() - 1; g >= 0; g--) {
            List<AASMPart> group = abstractCode.get(g);
            
            for(int i = group.size() - 1; i >= 0; i--) {
                switch(group.get(i)) {
                    case AASMInstruction inst: {
                        // Instruction.
                        // LEA = preexisting
                        if(inst.getOp() == AASMOperation.LEA) {
                            break;
                        }
                        
                        // Is a stack slot invovled?
                        if(inst.getSource() instanceof AASMStackSlot sSlot) {
                            // Source is slot. This is a use. Add to live set
                            currentlyLive.add(sSlot.id());
                        } else if(inst.getDestination() instanceof AASMStackSlot dSlot) {
                            // Destination is slot. This is a def. Add interference and remove from live set
                            SAIGNode defNode = graph.getIDMap().get(dSlot.id());
                            
                            for(IRIdentifier live : currentlyLive) {
                                defNode.addInterference(graph.getIDMap().get(live));
                            }
                            
                            currentlyLive.remove(dSlot.id());
                        }
                        break;
                    }
                    
                    case AASMLiveSet ls: {
                        // Liveness set. Set currentlyLive accordingly
                        if(ls.isDef()) {
                            // Add interference between live-ins defined here and currently live
                            for(IRIdentifier liveIn : ls.stackSet()) {
                                SAIGNode liveInNode = graph.getIDMap().get(liveIn);
                                
                                for(IRIdentifier live : currentlyLive) {
                                    liveInNode.addInterference(graph.getIDMap().get(live));
                                }
                            }
                            
                            currentlyLive.clear();
                        } else {
                            // live-out use. Fill currently live with ls
                            currentlyLive.clear();
                            currentlyLive.addAll(ls.stackSet());
                        }
                        break;
                    }
                    
                    default:
                        // Label or w/e. no action
                        continue;
                }
            }
        }
        
        // Allocate by coloring
        // Since we have an unbounded number of colors, this is nice and simple
        int maxOffset = 0;
        
        for(SAIGNode node : graph.getAllNodes()) {
            // Get set of occupied addresses
            Set<Integer> occupied = new HashSet<>();
            
            for(SAIGNode other : node.getInterferingNodes()) {
                int offs = other.getOffset();
                
                if(offs != -1) {
                    // other is allocated
                    for(int i = (offs - other.getSize()) + 1; i <= offs; i++) {
                        occupied.add(i);
                    }
                }
            }
            
            // Find first unoccupied slot
            int size = node.getSize();
            
            outer:
            for(int i = 0; true; i++) {
                for(int j = 0; j < size; j++) {
                    if(occupied.contains(i + j)) {
                        // Can't fit. Go next.
                        i += j;
                        continue outer;
                    }
                }
                
                // Slot is open!
                int offset = i + size;
                
                if(offset > maxOffset) {
                    // Track offset for STACK instruction slots
                    maxOffset = offset;
                }
                
                LOG.finest("Allocated " + node.getIdentifier() + " to [BP - " + offset + "]");
                
                node.setOffset(offset);
                break;
            }
        }
        
        // Convert to identifier -> offset
        Map<IRIdentifier, Integer> allocationMap = new HashMap<>();
        int globalOffset = maxOffset;
        
        // Allocate pre-existing stack slots
        for(IRIdentifier preSlot : preexistingSlots) {
            int size = slotSizes.get(preSlot);
            int offset = globalOffset + size;
            
            LOG.finest("Allocated " + preSlot + " to [BP - " + offset + "]");
            
            allocationMap.put(preSlot, globalOffset + size);
            globalOffset += size;
        }
        
        // Convert SAIG to map
        for(SAIGNode node : graph.getAllNodes()) {
            allocationMap.put(node.getIdentifier(), node.getOffset());
        }
        
        return allocationMap;
    }
    
}
