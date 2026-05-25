package notsotiny.lang.compiler.optimization.transforms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import notsotiny.lang.ir.parts.IRArgumentList;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRBranchInstruction;
import notsotiny.lang.ir.parts.IRDefinition;
import notsotiny.lang.ir.parts.IRDefinitionType;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.util.IRUtil;
import notsotiny.lib.util.MapUtil;

/**
 * An IR transformation which reconstructs the SSA property after an optimization/transofmration
 * which violates it.
 * 
 * Implementation based on Fabrice Rastello, Florent Bouchez Tichadou - SSA-based Compiler Design,
 * chapter 5
 */
public class IRTransformSSAReconstruction {
    
    /**
     * Reconstruct the SSA property for the given function
     * 
     * @param func Function to repair
     * @return true if any modification was made
     */
    public static boolean reconstructSSA(IRFunction func) {
        // Determine which variables violate SSA, and collect defs/uses along the way
        List<IRDefinition> defList = IRUtil.getDefinitionList(func);
        Map<IRIdentifier, List<IRDefinition>> useMap = IRUtil.getUseMap(func, defList);
        
        Set<IRIdentifier> defined = new HashSet<>(),
                          violatesSSA = new HashSet<>();
        Map<IRIdentifier, List<IRDefinition>> defineMap = new HashMap<>();
        
        // If an identifier is defined multiple times, it violates SSA
        for(IRDefinition def : defList) {
            MapUtil.getOrCreateList(defineMap, def.getID()).add(def);
            
            if(!defined.add(def.getID())) {
                // Make sure only LOCALs are problematic
                if(def.getType() != IRDefinitionType.LINEAR && def.getType() != IRDefinitionType.BBARG) {
                    throw new IllegalStateException("Non-bb-local violates SSA: " + def.getID() + " in " + func.getID());
                }
                
                violatesSSA.add(def.getID());
            }
        }
        
        // 
        
        // TODO
        return false;
    }
    
    /**
     * Repairs the SSA property for a multiply-defined LOCAL violatorID
     * @param violatorID
     * @param func
     */
    private static void repairSSA(IRIdentifier violatorID, List<IRDefinition> violatorDefs, List<IRDefinition> violatorUses, IRFunction func) {
        // bbID -> defs of violatorID in bb
        Map<IRIdentifier, List<IRDefinition>> unorderedDefsInBlocks = new HashMap<>();
        
        // Split into a variable for each def, collecting location information
        for(IRDefinition def : violatorDefs) {
            IRIdentifier newID = func.getFUID(violatorID.getName());
            
            if(def.getType() == IRDefinitionType.BBARG) {
                // Defined by BB arg
                // Replace in arg list
                IRArgumentList argList = def.getBB().getArgumentList(); 
                argList.replaceArgument(def.getID(), newID, argList.getType(def.getID()));
                
                // Replace in arg mappings
                IRIdentifier defBBID = def.getBB().getID();
                
                for(IRIdentifier predID : def.getBB().getPredecessorBlocks()) {
                    IRBranchInstruction predExit = func.getBasicBlock(predID).getExitInstruction();
                    
                    if(defBBID.equals(predExit.getTrueTargetBlock())) {
                        predExit.getTrueArgumentMapping().renameArgument(def.getID(), newID);
                    }
                    
                    if(defBBID.equals(predExit.getFalseTargetBlock())) {
                        predExit.getFalseArgumentMapping().renameArgument(def.getID(), newID);
                    }
                }
                
                MapUtil.getOrCreateList(unorderedDefsInBlocks, defBBID).add(def);
            } else {
                // Defined by LI
                def.getLinearInstruction().setDestination(newID);
                def.setID(newID);
                
                MapUtil.getOrCreateList(unorderedDefsInBlocks, def.getLinearInstruction().getBasicBlock().getID()).add(def);
            }
        }
        
        // Sort defs in block according to index
        Map<IRIdentifier, List<IRDefinition>> defsInBlocks = new HashMap<>();   // BB ID -> defs of violator in BB
        Map<IRIdentifier, Integer> defIndexMap = new HashMap<>();               // def'd ID -> index of defining LI (-1 = bb arg)
        
        for(Entry<IRIdentifier, List<IRDefinition>> entry : unorderedDefsInBlocks.entrySet()) {
            IRIdentifier bbID = entry.getKey();
            IRBasicBlock bb = func.getBasicBlock(bbID);
            List<IRDefinition> unorderedDefs = entry.getValue();
            
            // Search for def LIs in bottom-to-top order
            Map<IRIdentifier, IRDefinition> idDefMap = new HashMap<>(); // ID -> defining def
            List<IRDefinition> orderedDefs = new ArrayList<>();         // Ordered list being assembled
            Set<IRIdentifier> targets = new HashSet<>();                // What IDs are we looking for
            
            // Assemble target IDs to find in BB
            for(IRDefinition def : unorderedDefs) {
                targets.add(def.getID());
                idDefMap.put(def.getID(), def);
            }
            
            // (branches can't define a violator)
            
            // Search linear instructions in reverse order
            List<IRLinearInstruction> bbLIs = bb.getInstructions();
            
            for(int i = bbLIs.size() - 1; i >= 0; i--) {
                IRLinearInstruction li = bbLIs.get(i);
                
                // Does this LI define a target
                if(targets.contains(li.getDestinationID())) {
                    // Yes, add its def
                    orderedDefs.add(idDefMap.get(li.getDestinationID()));
                    defIndexMap.put(li.getDestinationID(), i);
                }
            }
            
            // Add args last
            // BB args don't have a defined order, so just find any from the defs 
            for(IRDefinition maybeArg : unorderedDefs) {
                if(maybeArg.getType() == IRDefinitionType.BBARG) {
                    orderedDefs.add(maybeArg);
                    defIndexMap.put(maybeArg.getID(), -1);
                }
            }
            
            // & associate with the block
            defsInBlocks.put(bbID, orderedDefs);
        }
        
        Map<IRIdentifier, IRDefinition> blockTopMap = new HashMap<>();
        
        // Rewrite each use to use the correct definition
        for(IRDefinition use : violatorUses) {
            IRDefinition def = null;
            
            switch(use.getType()) {
                case BRANCH:
                    // Branch - Appropriate define can come from anywhere in the BB
                    IRBranchInstruction branch = use.getBranchInstruction();
                    def = findDefFromBottom(violatorID, branch.getBasicBlock(), blockTopMap, defsInBlocks);
                    
                    // Re-write use
                    IRUtil.replaceInBI(branch, violatorID, def.getID());
                    break;
                    
                case LINEAR:
                    // LI - Appropriate definition must come before the using LI
                    IRLinearInstruction li = use.getLinearInstruction();
                    
                    // Find index of li in its bb
                    int liIdx = li.getBasicBlock().getInstructions().indexOf(li);
                    
                    // Try to find the latest-most def that is before the use in the BB
                    for(IRDefinition candidateDef : defsInBlocks.get(li.getBasicBlock().getID())) {
                        if(defIndexMap.get(candidateDef.getID()) < liIdx) {
                            def = candidateDef;
                            break;
                        }
                    }
                    
                    // If we didn't find an appropriate def in the block, search preds
                    if(def == null) {
                        def = findDefFromTop(violatorID, li.getBasicBlock(), blockTopMap, defsInBlocks);
                    }
                    
                    // rewrite use
                    IRUtil.replaceInLI(li, violatorID, def.getID());
                    break;
                
                default:
                    // BB arg. This use isn't meaningful here since the re-writing will occur at
                    // the BRANCH use
                    break;
            }
        }
    }
    
    /**
     * Find the appropriate def for target, starting from the bottom of bb
     * @param target
     * @param bb
     * @param defsInBlocks
     * @return
     */
    private static IRDefinition findDefFromBottom(IRIdentifier target, IRBasicBlock bb, Map<IRIdentifier, IRDefinition> blockTopMap, Map<IRIdentifier, List<IRDefinition>> defsInBlocks) {
        // Get defs of the violator in this block
        List<IRDefinition> bbDefs = defsInBlocks.get(bb.getID());
        
        // Are there any?
        if(bbDefs.size() != 0) {
            // Yes, get bottom-most
            return bbDefs.get(0);
        } else {
            // No, find from predecessors
            return findDefFromTop(target, bb, blockTopMap, defsInBlocks);
        }
    }
    
    /**
     * Find the appropriate def for target, starting from the predecessors of bb
     * @param target
     * @param bb
     * @param blockTopMap bbID -> 'top' known appropriate def from bb
     * @param defsInBlocks
     * @return
     */
    private static IRDefinition findDefFromTop(IRIdentifier target, IRBasicBlock bb, Map<IRIdentifier, IRDefinition> blockTopMap, Map<IRIdentifier, List<IRDefinition>> defsInBlocks) {
        IRIdentifier bbID = bb.getID();
        
        // Do we have a known def?
        if(blockTopMap.containsKey(bbID)) {
            return blockTopMap.get(bbID);
        }
        
        // Create pending phi in case we don't find a def
        IRIdentifier pendingID = bb.getFunction().getFUID(target.getName());
        IRDefinition pendingDef = new IRDefinition(pendingID, bb);
        
        blockTopMap.put(bbID, pendingDef);
        
        // TODO
        return null;
    }
    
}
