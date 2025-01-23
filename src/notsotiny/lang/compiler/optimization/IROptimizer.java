package notsotiny.lang.compiler.optimization;

import java.nio.file.Path;

import notsotiny.lang.ir.parts.IRModule;

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
    
    /**
     * Set whether to output intermediate IR to a file between passes
     * @param output
     * @param directory
     */
    public void setIntermediateOutput(boolean output, Path directory);
    
    /**
     * Set whether to visualize the CFG of each function
     * @param iir Show intermediate IR CFGs
     * @param oir Show optimized IR CFG
     */
    public void setCFGVisualization(boolean iir, boolean oir); 
}
