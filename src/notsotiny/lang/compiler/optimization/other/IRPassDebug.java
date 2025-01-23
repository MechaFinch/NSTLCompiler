package notsotiny.lang.compiler.optimization.other;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import notsotiny.lang.compiler.optimization.IROptimizationLevel;
import notsotiny.lang.compiler.optimization.IROptimizationPass;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.util.IRUtil;
import notsotiny.lang.util.Pair;
import notsotiny.lang.util.UnionFindForest;

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
        // Determine and output the loop-nesting forest for each function
        for(IRFunction func : module.getInternalFunctions().values()) {
            
        }
        
        return module;
    }
}
