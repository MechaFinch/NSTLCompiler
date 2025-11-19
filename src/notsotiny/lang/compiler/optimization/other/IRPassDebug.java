package notsotiny.lang.compiler.optimization.other;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import notsotiny.lang.compiler.optimization.IROptimizationLevel;
import notsotiny.lang.compiler.optimization.IROptimizationPass;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.util.IRUtil;
import notsotiny.lib.data.TreeNode;
import notsotiny.lib.util.MapUtil;

/**
 * Outputs debugging information or w/e
 */
public class IRPassDebug implements IROptimizationPass {
    
    private static Logger LOG = Logger.getLogger(IRPassDebug.class.getName());

    @Override
    public IROptimizationLevel level() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IRModule optimize(IRModule module) {
        for(IRFunction func : module.getInternalFunctions().values()) {
            
        }
        
        return module;
    }
}
