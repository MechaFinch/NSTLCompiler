package notsotiny.lang.compiler.optimization;

import notsotiny.lang.ir.parts.IRModule;

/**
 * An optimization pass
 */
public interface IROptimizationPass {
    
    /**
     * @return The minimum level for this pass to be desirable
     */
    public IROptimizationLevel level();
    
    /**
     * Performs some optimization on a module
     * @param module
     * @return
     */
    public IRModule optimize(IRModule module);
    
}
