package notsotiny.lang.compiler.optimization;

import java.util.List;

import notsotiny.lang.ir.IRModule;

public class IROptimizationChain implements IROptimizationPass {
    
    private List<IROptimizationPass> chain;
    
    /**
     * Full constructor
     * @param chain
     */
    public IROptimizationChain(List<IROptimizationPass> chain) {
        this.chain = chain;
    }

    @Override
    public IROptimizationLevel level() {
        IROptimizationLevel maxLevel = IROptimizationLevel.ZERO;
        
        for(IROptimizationPass pass : this.chain) {
            if(pass.level().isAbove(maxLevel)) {
                maxLevel = pass.level();
            }
        }
        
        return maxLevel;
    }

    @Override
    public IRModule optimize(IRModule module) {
        for(IROptimizationPass pass : this.chain) {
            module = pass.optimize(module);
        }
        
        return module;
    }
    
}
