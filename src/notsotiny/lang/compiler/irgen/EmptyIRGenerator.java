package notsotiny.lang.compiler.irgen;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.ir.IRModule;

public class EmptyIRGenerator implements IRGenerator {

    @Override
    public IRModule generate(ASTNode astRoot, String defaultLibName, FileLocator locator) throws CompilationException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IRModule generate(ASTNode astRoot, String defaultLibName, FileLocator locator, Path sourcePath) throws CompilationException {
        // TODO Auto-generated method stub
        return null;
    }
    
}
