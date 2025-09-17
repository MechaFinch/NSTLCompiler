package notsotiny.lang.compiler.shitty;

import java.util.logging.Logger;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.compiler.NSTCompiler;
import notsotiny.nstasm.asmparts.ASMObject;

/**
 * Compiler template
 * 
 * @author Mechafinch
 */
public class EmptyCompiler implements NSTCompiler {
    
    public EmptyCompiler() {
        
    }
    
    @Override
    public ASMObject compile(ASTNode astRoot, String defaultLibName, FileLocator locator) {
        return new ASMObject(defaultLibName);
    }
    
}
