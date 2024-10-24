package notsotiny.lang.compiler;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.asm.Assembler.AssemblyObject;

/**
 * AST goes in code comes out
 * 
 * @author Mechafinch
 */
public interface NSTCompiler {
    
    public AssemblyObject compile(ASTNode astRoot, String defaultLibName, FileLocator locator) throws CompilationException;
    
    public default AssemblyObject compile(ASTNode astRoot, String defaultLibName, FileLocator locator, Path sourcePath) throws CompilationException {
        return compile(astRoot, defaultLibName, locator);
    }
    
}
