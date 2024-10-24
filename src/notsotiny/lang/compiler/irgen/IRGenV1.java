package notsotiny.lang.compiler.irgen;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.ir.IRModule;

public class IRGenV1 implements IRGenerator {

    @Override
    public IRModule generate(ASTNode astRoot, String defaultLibName, FileLocator locator) throws CompilationException {
        IRModule module = new IRModule(defaultLibName);
        
        generate(astRoot, module, locator);
        
        return module;
    }
    
    @Override
    public IRModule generate(ASTNode astRoot, String defaultLibName, FileLocator locator, Path sourcePath) throws CompilationException {
        IRModule module = new IRModule(defaultLibName, sourcePath);
        
        generate(astRoot, module, locator);
        
        return module;
    }
    
    /**
     * Generate the module
     * @param module
     * @param defaultLibName
     * @param locator
     * @throws CompilationException 
     */
    private void generate(ASTNode code, IRModule irModule, FileLocator locator) throws CompilationException {
        ASTModule astModule = TopLevelParser.parseTopLevel(irModule.getName(), code, locator);
    }
    
}
