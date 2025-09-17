package notsotiny.lang.compiler.codegen;

import java.nio.file.Path;

import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.nstasm.asmparts.ASMObject;

/**
 * IR -> Assembly
 */
public interface CodeGenerator {
    
    /**
     * Generate assembly from IR
     * @param module
     * @return
     */
    public ASMObject generate(IRModule module) throws CompilationException;
    
    /**
     * Set whetherto print abstract assembly to a file
     * @param output
     * @param directory
     */
    public void setAbstractOutput(boolean output, Path directory);
    
    /**
     * Set what graphs are visualized
     * @param isel Instruction Selection DAG
     * @param raUncolored Uncolored register allocation interference graph
     * @param raColored Colored register allocation interference graph
     */
    public void setGraphVisualization(boolean isel, boolean raUncolored, boolean raColored);
    
}
