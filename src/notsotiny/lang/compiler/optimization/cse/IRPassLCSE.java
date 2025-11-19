package notsotiny.lang.compiler.optimization.cse;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import notsotiny.lang.compiler.optimization.IROptimizationLevel;
import notsotiny.lang.compiler.optimization.IROptimizationPass;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRDefinition;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.parts.IRLinearOperation;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lang.ir.util.IRUtil;

/**
 * Local common subexpression elimination
 * This pass is still useful in the face of GVN-PRE as it can
 * eliminate common subexpressions involving LOADs and STOREs 
 */
public class IRPassLCSE implements IROptimizationPass {
    
    private static Logger LOG = Logger.getLogger(IRPassLCSE.class.getName());

    @Override
    public IROptimizationLevel level() {
        return IROptimizationLevel.ONE;
    }

    @Override
    public IRModule optimize(IRModule module) {
        LOG.finer("Performing local CSE on " + module.getName());
        
        // Track purity as needed in module
        Map<IRIdentifier, Boolean> functionPurityMap = new HashMap<>();
        
        // In each function
        for(IRFunction func : module.getInternalFunctions().values()) {
            LOG.finest("Performing local CSE on " + func.getID());
            
            for(IRBasicBlock bb : func.getBasicBlockList()) {
                doCSE(bb, functionPurityMap);
            }
        }
        
        return module;
    }
    
    private static final Set<IRLinearOperation> LOAD_EFFECTORS = EnumSet.of(IRLinearOperation.STORE, IRLinearOperation.CALLR, IRLinearOperation.CALLN);
    
    /**
     * Does CSE on a basic block
     * @param bb
     * @param functionPurityMap
     */
    private void doCSE(IRBasicBlock bb, Map<IRIdentifier, Boolean> functionPurityMap) {
        // Track definitions
        List<IRDefinition> visibleDefinitions = new ArrayList<>();
        
        for(int i = 0; i < bb.getInstructions().size(); i++) {
            IRLinearInstruction li = bb.getInstructions().get(i);
            IRDefinition currentDef = new IRDefinition(li.getDestinationID(), li);
            boolean replaced = false;
            
            // Track purity if a function call
            boolean liPure = false;
            
            if(li.getOp() == IRLinearOperation.CALLR && bb.getModule().getInternalFunctions().containsKey(li.getCallTarget())) {
                liPure = IRUtil.isFunctionPure(bb.getModule().getInternalFunctions().get(li.getCallTarget()), true, functionPurityMap);
            }
            
            // Search for matching defs
            for(int j = visibleDefinitions.size() - 1; j >= 0; j--) {
                IRDefinition def = visibleDefinitions.get(j);
                
                if(li.getOp() == IRLinearOperation.CALLR && liPure) {
                    // CALLR can be equal to another CALLR if they call the same function, have the same arguments, and the called function is pure 
                    IRLinearInstruction defLI = def.getLinearInstruction();
                    
                    if(defLI.getOp() == IRLinearOperation.CALLR) {
                        // Both CALLR the same target with the same arguments and the target is pure
                        if(defLI.getCallTarget().equals(li.getCallTarget()) &&
                           defLI.getCallArgumentMapping().equals(li.getCallArgumentMapping())) {
                            IRIdentifier fromID = li.getDestinationID();
                            IRIdentifier toID = def.getID();
                            
                            if(LOG.isLoggable(Level.FINEST)) {
                                LOG.finest("Replacing " + fromID + " with " + toID + " in " + bb.getID());
                            }
                            
                            IRUtil.replaceInFunction(bb.getFunction(), fromID, toID);
                            
                            replaced = true;
                            break;
                        }
                    }
                } else if(li.getOp() == IRLinearOperation.LOAD) {
                    IRLinearInstruction defLI = def.getLinearInstruction();
                    IRLinearOperation defOp = defLI.getOp();
                    IRValue loadedFrom = li.getLeftSourceValue();
                    
                    // LOAD can be equal to the most recent value STOREd to its address
                    // LOAD can be equal to another LOAD from the same address, iff no effectors occur between them
                    
                    if(defOp == IRLinearOperation.STORE) {
                        // STORE: If value stored to li address, replace. Otherwise, this is an effector; don't search further
                        if(loadedFrom.equals(defLI.getRightSourceValue())) { // compare addresses
                            // Load from address. Replace.
                            IRIdentifier fromID = li.getDestinationID();    // replace loaded with stored
                            IRValue toValue = defLI.getLeftSourceValue();
                            
                            if(LOG.isLoggable(Level.FINEST)) {
                                LOG.finest("Replacing " + fromID + " with " + toValue + " in " + bb.getID());
                            }
                            
                            IRUtil.replaceInFunction(bb.getFunction(), fromID, toValue);
                            
                            replaced = true;
                        }
                        
                        break;
                    } else if(defOp == IRLinearOperation.LOAD) {
                        // LOAD: Check if this is a load from the same address
                        if(loadedFrom.equals(defLI.getLeftSourceValue())) { // compare addresses
                            // Two loads from the same address with no intervening store
                            IRIdentifier fromID = li.getDestinationID();
                            IRIdentifier toID = def.getID();
                            
                            if(LOG.isLoggable(Level.FINEST)) {
                                LOG.finest("Replacing " + fromID + " with " + toID + " in " + bb.getID());
                            }
                            
                            IRUtil.replaceInFunction(bb.getFunction(), fromID, toID);
                            
                            replaced = true;
                            break;
                        }
                    } else if(LOAD_EFFECTORS.contains(defOp)) {
                        // This is an effector. Don't search further
                        break;
                    }
                } else if(def.equals(currentDef)) {
                    // Common subexpression found
                    IRIdentifier fromID = li.getDestinationID();
                    IRIdentifier toID = def.getID();
                    
                    if(LOG.isLoggable(Level.FINEST)) {
                        LOG.finest("Replacing " + fromID + " with " + toID + " in " + bb.getID());
                    }
                    
                    IRUtil.replaceInFunction(bb.getFunction(), fromID, toID);
                    
                    replaced = true;
                    break;
                }
            }
            
            if(replaced) {
                i--;
            } else {
                visibleDefinitions.add(currentDef);
            }
        }
    }
    
}
