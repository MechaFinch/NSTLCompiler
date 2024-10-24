package notsotiny.lang.compiler.irgen;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.ir.IRModule;

/**
 * AST goes in IR comes out
 */
public interface IRGenerator {
    
    /**
     * Transform an AST into an IRModule
     * @param astRoot
     * @param defaultLibName
     * @param locator
     * @return
     * @throws NoSuchFileException
     */
    public IRModule generate(ASTNode astRoot, String defaultLibName, FileLocator locator) throws CompilationException;
    
    /**
     * Transform an AST into an IRModule
     * @param astRoot
     * @param defaultLibName
     * @param locator
     * @param sourcePath
     * @return
     * @throws NoSuchFileException
     */
    public IRModule generate(ASTNode astRoot, String defaultLibName, FileLocator locator, Path sourcePath) throws CompilationException;
}
