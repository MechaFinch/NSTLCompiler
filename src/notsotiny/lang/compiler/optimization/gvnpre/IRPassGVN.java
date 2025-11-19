package notsotiny.lang.compiler.optimization.gvnpre;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import notsotiny.lang.compiler.optimization.IROptimizationLevel;
import notsotiny.lang.compiler.optimization.IROptimizationPass;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lang.ir.util.IRUtil;
import notsotiny.lib.data.Pair;
import notsotiny.lib.data.TreeNode;
import notsotiny.lib.data.Triple;
import notsotiny.lib.util.MapUtil;

/**
 * Global Value Numbering pass
 * Just GVN on its own
 */
public class IRPassGVN implements IROptimizationPass {

    private static Logger LOG = Logger.getLogger(IRPassGVN.class.getName());
    
    @Override
    public IROptimizationLevel level() {
        return IROptimizationLevel.ONE;
    }

    @Override
    public IRModule optimize(IRModule module) {
        LOG.finer("Performing GVN on " + module.getName());
        
        // In each function
        for(IRFunction func : module.getInternalFunctions().values()) {
            LOG.finest("Performing GVN on " + func.getID());
            
            // Get metadata
            List<IRIdentifier> reversePostorderList = IRUtil.getReversePostorderList(func);
            Map<IRIdentifier, Integer> reversePostorderMap = MapUtil.listToMap(reversePostorderList);
            Map<IRIdentifier, IRIdentifier> dominatorMap = IRUtil.getDominatorMap(func, reversePostorderList);
            Map<IRIdentifier, TreeNode<IRIdentifier>> dominatorTreeMap = MapUtil.mapToForest(dominatorMap);
            
            // Get function args in value table
            GVNValueTable valueTable = new GVNValueTable();
            
            for(IRIdentifier argID : func.getArguments().getNameList()) {
                int number = valueTable.addIRValue(argID).a;
                
                if(LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("Got number " + number + " for function arg " + argID);
                }
            }
            
            // Get GVNing
            doGVN(dominatorTreeMap.get(func.getEntryBlock().getID()), func, valueTable, new HashMap<>(), reversePostorderMap);
        }
        
        return module;
    }
    
    /**
     * Do GVN given a node in the dominator tree
     * @param domTreeNode
     * @param func
     * @param valueTable
     * @param functionPurityMap
     * @param reversePostorderIndexMap A map from BB ID to reverse postorder number
     */
    private void doGVN(TreeNode<IRIdentifier> domTreeNode, IRFunction func, GVNValueTable valueTable, Map<IRIdentifier, Boolean> functionPurityMap, Map<IRIdentifier, Integer> reversePostorderIndexMap) {
        IRIdentifier bbID = domTreeNode.getElement();
        IRBasicBlock bb = func.getBasicBlock(bbID);
        
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.finest("Processing basic block " + bbID);
        }
        
        // Save a copy of the set of available values
        Set<Integer> valuesBefore = new HashSet<>(valueTable.getAvailableNumbers());
        
        // Handle arguments
        // Get a copy of the arg ID list so we can modify it
        List<IRIdentifier> allArgIDs = new ArrayList<>(bb.getArgumentList().getNameList()); 
        
        for(IRIdentifier argID : allArgIDs) {
            // Add argument to value table
            Pair<Integer, IRValue> pair = valueTable.addBBArgument(argID, bbID, bb.getPredecessorBlocks(), func);
            int number = pair.a;
            IRValue representative = pair.b;
            
            // If the arg is redundant or meaningless, it will have a different representative from the arg name
            if(!representative.equals(argID)) {
                if(LOG.isLoggable(Level.FINEST)) { 
                    LOG.finest("Got number " + number + " for bb arg " + argID + "; replacing with " + representative);
                }
                
                // Replace arg with representative
                IRUtil.replaceInFunction(func, argID, representative);
            } else {
                if(LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("Got number " + number + " for bb arg " + argID);
                }
            }
        }
        
        // Handle instructions
        // Copy so we can modify
        List<IRLinearInstruction> instructions = new ArrayList<>(bb.getInstructions());
        
        for(IRLinearInstruction li : instructions) {
            // Number the instruction if appropriate
            switch(li.getOp()) {
                case CALLR:
                    // Numberable if the target is pure
                    if(func.getModule().getInternalFunctions().containsKey(li.getCallTarget())) {
                        if(!IRUtil.isFunctionPure(func.getModule().getInternalFunctions().get(li.getCallTarget()), false, functionPurityMap)) {
                            // impure
                            int number = valueTable.addIRValue(li.getDestinationID()).a;
                            
                            if(LOG.isLoggable(Level.FINEST)) {
                                LOG.finest("Got number " + number + " for impure function call for " + li.getDestinationID());
                            }
                            break;
                        }
                    } else {
                        // Non-internal target, impure
                        int number = valueTable.addIRValue(li.getDestinationID()).a;
                        
                        if(LOG.isLoggable(Level.FINEST)) {
                            LOG.finest("Got number " + number + " for impure function call for " + li.getDestinationID());
                        }
                        break;
                    }
                    
                    // pure, fallthrough
                
                case TRUNC, SX, ZX, SELECT,
                     ADD, SUB, MULU, MULS, DIVU, DIVS, REMU, REMS,
                     SHL, SHR, SAR, ROL, ROR, AND, OR, XOR, NOT, NEG:
                    // Numberable
                    Pair<Integer, IRValue> pair = valueTable.addInstruction(li);
                    int number = pair.a;
                    IRValue representative = pair.b;
                    
                    // If the representative is different from the destination, the instruction is eliminated
                    if(!representative.equals(li.getDestinationID())) {
                        if(LOG.isLoggable(Level.FINEST)) {
                            LOG.finest("Got number " + number + " for " + li.getDestinationID() + "; replacing with " + representative);
                        }
                        
                        IRUtil.replaceInFunction(func, li.getDestinationID(), representative);
                        continue;
                    } else {
                        if(LOG.isLoggable(Level.FINEST)) {
                            LOG.finest("Got number " + number + " for " + li.getDestinationID());
                        }
                    }
                    break;
                
                default:
                    // Not numberable
                    if(li.hasDestination()) {
                        // destination still needs a number
                        number = valueTable.addIRValue(li.getDestinationID()).a;
                        
                        if(LOG.isLoggable(Level.FINEST)) {
                            LOG.finest("Got number " + number + " for black box " + li.getDestinationID());
                        }
                    }
            }
            
            // At this point, the instruction has not been replaced
            // Thanks to replaceInFunction, propagation of numbered values has been handled already 
        }
        
        // Recurse on dominator tree children, in reverse postorder
        // Doing so in this order ensures no numberable bb args are missed
        List<TreeNode<IRIdentifier>> domChildren = new ArrayList<>(domTreeNode.getChildren());
        Collections.sort(domChildren, (a, b) -> reversePostorderIndexMap.get(a.getElement()) - reversePostorderIndexMap.get(b.getElement()));
        
        for(TreeNode<IRIdentifier> domChild : domChildren) {
            doGVN(domChild, func, valueTable, functionPurityMap, reversePostorderIndexMap);
        }
        
        // Remove any values made available in this BB
        Set<Integer> newValues = new HashSet<>(valueTable.getAvailableNumbers());
        newValues.removeAll(valuesBefore);
        
        for(int i : newValues) {
            valueTable.removeValue(i);
        }
    }
    
}
