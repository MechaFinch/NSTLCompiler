package notsotiny.lang.compiler.compilers;

import java.nio.file.NoSuchFileException;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.asm.Assembler.AssemblyObject;
import notsotiny.lang.compiler.NSTCompiler;
import notsotiny.lang.compiler.codegen.CodeGenerator;
import notsotiny.lang.compiler.irgen.IRGenerator;
import notsotiny.lang.compiler.optimization.IROptimizer;

/**
 * Generic NSTL compiler utilizing IR to separate passes
 */
public class IRCompiler implements NSTCompiler {
    
    private IRGenerator irgen;
    
    private IROptimizer optimizer;
    
    private CodeGenerator codegen;
    
    public IRCompiler(IRGenerator irgen, IROptimizer optimizer, CodeGenerator codegen) {
        this.irgen = irgen;
        this.optimizer = optimizer;
        this.codegen = codegen;
    }

    @Override
    public AssemblyObject compile(ASTNode astRoot, String defaultLibName, FileLocator locator) throws NoSuchFileException {
        return this.codegen.generate(this.optimizer.optimize(this.irgen.generate(astRoot, defaultLibName, locator)));
    }
    
}
