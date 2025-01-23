package notsotiny.lang.compiler.codegen;

import java.nio.file.Path;

import notsotiny.asm.Assembler.AssemblyObject;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.ir.parts.IRModule;

/**
 * IR -> Assembly
 */
public interface CodeGenerator {
    
    /**
     * Generate assembly from IR
     * @param module
     * @return
     */
    public AssemblyObject generate(IRModule module) throws CompilationException;
    
    /**
     * Set whetherto print abstract assembly to a file
     * @param output
     * @param directory
     */
    public void setAbstractOutput(boolean output, Path directory);
    
    /**
     * Set whether to print generated assembly to a file
     * @param output
     * @param directory
     */
    public void setFinalOutput(boolean output, Path directory);
    
    /**
     * Set what DAGs are visualized
     * @param isel Instruction Selection
     */
    public void setDAGVisualization(boolean isel);
    
}
