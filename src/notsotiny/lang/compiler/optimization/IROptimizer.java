package notsotiny.lang.compiler.optimization;

import notsotiny.lang.ir.IRModule;

/**
 * IR -> Better IR
 */
public interface IROptimizer {
    
    /**
     * Optimize an IRModule
     * Working with a new copy of module is not required
     * @param module
     * @return
     */
    public IRModule optimize(IRModule module);
}
