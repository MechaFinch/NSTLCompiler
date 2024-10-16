package notsotiny.lang.compiler.codegen;

import notsotiny.asm.Assembler.AssemblyObject;
import notsotiny.lang.ir.IRModule;

/**
 * IR -> Assembly
 */
public interface CodeGenerator {
    
    /**
     * Generate assembly from IR
     * @param module
     * @return
     */
    public AssemblyObject generate(IRModule module);
}
