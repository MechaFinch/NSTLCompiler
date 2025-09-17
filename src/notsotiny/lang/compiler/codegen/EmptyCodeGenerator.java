package notsotiny.lang.compiler.codegen;

import java.nio.file.Path;

import notsotiny.lang.ir.parts.IRModule;
import notsotiny.nstasm.asmparts.ASMObject;

public class EmptyCodeGenerator implements CodeGenerator {

    @Override
    public ASMObject generate(IRModule module) {
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
    
}
