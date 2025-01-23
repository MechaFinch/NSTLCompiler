package notsotiny.lang.compiler.optimization.cse;

import java.util.logging.Logger;

import notsotiny.lang.compiler.optimization.IROptimizationLevel;
import notsotiny.lang.compiler.optimization.IROptimizationPass;
import notsotiny.lang.ir.parts.IRModule;

/**
 * Global common subexpression elimination
 */
public class IRPassGCSE implements IROptimizationPass {
    
    private static Logger LOG = Logger.getLogger(IRPassGCSE.class.getName());

    @Override
    public IROptimizationLevel level() {
        return IROptimizationLevel.TWO;
    }

    @Override
    public IRModule optimize(IRModule module) {
        // TODO Auto-generated method stub
        return null;
    }
    
}
