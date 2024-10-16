package notsotiny.lang.compiler.optimization;

import notsotiny.lang.ir.IRModule;

/**
 * Placeholder
 */
public class EmptyIROptimizer implements IROptimizer {

    @Override
    public IRModule optimize(IRModule module) {
        return module;
    }
    
}
