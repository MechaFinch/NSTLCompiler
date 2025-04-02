package notsotiny.lang.compiler.codegen;

import java.nio.file.Path;

import notsotiny.asm.Assembler.AssemblyObject;
import notsotiny.lang.ir.parts.IRModule;

public class EmptyCodeGenerator implements CodeGenerator {

    @Override
    public AssemblyObject generate(IRModule module) {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public void setGraphVisualization(boolean isel, boolean raUncolored, boolean raColored) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setAbstractOutput(boolean output, Path directory) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setFinalOutput(boolean output, Path directory) {
        // TODO Auto-generated method stub
        
    }
    
}
