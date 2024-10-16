package notsotiny.lang.compiler.irgen;

import java.nio.file.NoSuchFileException;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.ir.IRModule;

public class EmptyIRGenerator implements IRGenerator {

    @Override
    public IRModule generate(ASTNode astRoot, String defaultLibName, FileLocator locator) throws NoSuchFileException {
        // TODO Auto-generated method stub
        return null;
    }
    
}
