package notsotiny.lang.compiler.compilers;

import java.nio.file.Path;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.NSTCompiler;
import notsotiny.lang.compiler.codegen.CodeGenerator;
import notsotiny.lang.compiler.irgen.IRGenerator;
import notsotiny.lang.compiler.optimization.IROptimizer;
import notsotiny.nstasm.asmparts.ASMObject;

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
    public ASMObject compile(ASTNode astRoot, String defaultLibName, FileLocator locator) throws CompilationException {
        return this.codegen.generate(this.optimizer.optimize(this.irgen.generate(astRoot, defaultLibName, locator)));
    }
    
    @Override
    public ASMObject compile(ASTNode astRoot, String defaultLibName, FileLocator locator, Path sourcePath) throws CompilationException {
        return this.codegen.generate(this.optimizer.optimize(this.irgen.generate(astRoot, defaultLibName, locator, sourcePath)));
    }
    
    
}
