package notsotiny.lang.compiler;

import java.nio.file.Path;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.nstasm.asmparts.ASMObject;

/**
 * AST goes in code comes out
 * 
 * @author Mechafinch
 */
public interface NSTCompiler {
    
    public ASMObject compile(ASTNode astRoot, String defaultLibName, FileLocator locator) throws CompilationException;
    
    public default ASMObject compile(ASTNode astRoot, String defaultLibName, FileLocator locator, Path sourcePath) throws CompilationException {
        return compile(astRoot, defaultLibName, locator);
    }
    
}
