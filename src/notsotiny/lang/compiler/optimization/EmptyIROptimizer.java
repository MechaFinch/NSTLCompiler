package notsotiny.lang.compiler.optimization;

import java.nio.file.Path;

import notsotiny.lang.ir.IRModule;

/**
 * Placeholder
 */
public class EmptyIROptimizer implements IROptimizer {

    @Override
    public IRModule optimize(IRModule module) {
        return module;
    }

    @Override
    public void setLevel(IROptimizationLevel level) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setFileOutput(boolean output, Path directory) {
        // TODO Auto-generated method stub
        
    }
    
}
