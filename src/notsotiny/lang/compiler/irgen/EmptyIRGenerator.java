package notsotiny.lang.compiler.irgen;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.ir.parts.IRModule;

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

    @Override
    public void setCFGVisualization(boolean ast, boolean ir) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setFileOutput(boolean output, Path directory) {
        // TODO Auto-generated method stub
        
    }
    
}
