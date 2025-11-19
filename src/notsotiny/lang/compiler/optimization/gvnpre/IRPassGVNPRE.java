package notsotiny.lang.compiler.optimization.gvnpre;

import java.util.logging.Logger;

import notsotiny.lang.compiler.optimization.IROptimizationLevel;
import notsotiny.lang.compiler.optimization.IROptimizationPass;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRModule;

/**
 * Global Value Numbering based Partial Redundancy Elimination
 * as described in VanDrunen & Hosking Value-Based Partial Redundancy Elimination
 */
public class IRPassGVNPRE implements IROptimizationPass {
    
    private static Logger LOG = Logger.getLogger(IRPassGVNPRE.class.getName());

    @Override
    public IROptimizationLevel level() {
        return IROptimizationLevel.TWO;
    }

    @Override
    public IRModule optimize(IRModule module) {
        LOG.finer("Performing GVN-PRE on " + module.getName());
        
        // In each function
        for(IRFunction func : module.getInternalFunctions().values()) {
            LOG.finest("Performing GVN-PRE on " + func.getID());
            
            // TODO
        }
        
        return module;
    }
    
}
