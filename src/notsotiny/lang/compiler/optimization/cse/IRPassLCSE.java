package notsotiny.lang.compiler.optimization.cse;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import notsotiny.lang.compiler.optimization.IROptimizationLevel;
import notsotiny.lang.compiler.optimization.IROptimizationPass;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRDefinition;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.util.IRUtil;

/**
 * Local common subexpression elimination
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
        
        // In each function
        for(IRFunction func : module.getInternalFunctions().values()) {
            LOG.finest("Performing local CSE on " + func.getID());
            
            for(IRBasicBlock bb : func.getBasicBlockList()) {
                doCSE(bb);
            }
        }
        
        return module;
    }
    
    /**
     * Does CSE on a basic block
     * @param bb
     */
    private void doCSE(IRBasicBlock bb) {
        // Track definitions
        List<IRDefinition> visibleDefinitions = new ArrayList<>();
        
        for(int i = 0; i < bb.getInstructions().size(); i++) {
            IRLinearInstruction li = bb.getInstructions().get(i);
            IRDefinition currentDef = new IRDefinition(li.getDestinationID(), li);
            boolean replaced = false;
            
            // Search for matching defs
            for(int j = visibleDefinitions.size() - 1; j >= 0; j--) {
                IRDefinition def = visibleDefinitions.get(j);
                
                if(def.equals(currentDef)) {
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
