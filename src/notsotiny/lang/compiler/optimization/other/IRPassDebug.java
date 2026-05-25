package notsotiny.lang.compiler.optimization.other;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.optimization.IROptimizationLevel;
import notsotiny.lang.compiler.optimization.IROptimizationPass;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.util.IRUtil;
import notsotiny.lib.data.TreeNode;
import notsotiny.lib.util.MapUtil;

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
        //LOG.finer("Doing debug for " + module.getName());
        
        for(IRFunction func : module.getInternalFunctions().values()) {
            checkSSA(func);
        }
        
        return module;
    }
    
    /**
     * Verify that func is in SSA form
     * @param func
     */
    private void checkSSA(IRFunction func) {
        // Check that each local ID is defined only once
        Set<IRIdentifier> defined = new HashSet<>();
        
        for(IRIdentifier arg : func.getArguments().getNameList()) {
            if(!defined.add(arg)) {
                LOG.severe("SSA Failure: local name " + arg + " from argument in " + func.getID());
                throw new IllegalStateException();
            }
        }
        
        for(IRBasicBlock bb : func.getBasicBlockList()) {
            if(!defined.add(bb.getID())) {
                LOG.severe("SSA Failure: duplicate block name " + bb.getID() + " in " + func.getID());
            }
            
            for(IRIdentifier bbArg : bb.getArgumentList().getNameList()) {
                if(!defined.add(bbArg)) {
                    LOG.severe("SSA Failure: duplicate local name " + bbArg + " from bb arg of " + bb.getID() + " in " + func.getID());
                    throw new IllegalStateException();
                }
            }
            
            for(IRLinearInstruction li : bb.getInstructions()) {
                IRIdentifier dest;
                
                if(li.hasDestination() && (dest = li.getDestinationID()).getIDClass() == IRIdentifierClass.LOCAL) {
                    if(!defined.add(dest)) {
                        LOG.severe("SSA Failure: Duplicate local name " + dest + " from li in " + bb.getID() + " in " + func.getID());
                    }
                }
            }
        }
    }
}
