package notsotiny.lang.compiler.optimization;

import java.util.logging.Logger;

import notsotiny.lang.ir.IRModule;

/**
 * Performs Sparse Conditional Constant Propagation
 * https://www.cs.wustl.edu/~cytron/531Pages/f11/Resources/Papers/cprop.pdf
 */
public class IRPassSCCP implements IROptimizationPass {

    private static Logger LOG = Logger.getLogger(IRPassSCCP.class.getName());
    
    @Override
    public IROptimizationLevel level() {
        return IROptimizationLevel.ONE;
    }

    @Override
    public IRModule optimize(IRModule module) {
        LOG.finer("Performing SCCP on " + module.getName());
        // TODO Auto-generated method stub
        return null;
    }
    
}
