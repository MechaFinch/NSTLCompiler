package notsotiny.lang.compiler.optimization;

import java.nio.file.Path;

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
    
    /**
     * Sets the optimization level
     * @param level
     */
    public void setLevel(IROptimizationLevel level);
    
    /**
     * Set whether to output optimized IR to a file
     * @param output
     */
    public void setFileOutput(boolean output, Path directory);
}
