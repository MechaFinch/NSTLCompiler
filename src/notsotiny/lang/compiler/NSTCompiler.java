package notsotiny.lang.compiler;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.asm.Assembler.AssemblyObject;

/**
 * AST goes in code comes out
 * 
 * @author Mechafinch
 */
public abstract class NSTCompiler {
    public abstract AssemblyObject compile(ASTNode astRoot, String defaultLibName, FileLocator locator);
}
