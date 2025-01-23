package notsotiny.lang.compiler.codegen;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import notsotiny.asm.Assembler.AssemblyObject;
import notsotiny.asm.components.Component;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.util.IRPrinter;
import notsotiny.lang.util.StreamPrinter;

public class CodeGenV1 implements CodeGenerator {
    
    private static Logger LOG = Logger.getLogger(CodeGenV1.class.getName());
    
    private boolean showISelDAG = false;
    private boolean outputAbstractToFile = false;
    private boolean outputFinalToFile = false;
    
    private Path abstractOutputDirectory = null;
    private Path finalOutputDirectory = null;

    @Override
    public AssemblyObject generate(IRModule module) throws CompilationException {
        // Convert from Path to File because I love technical debt 
        HashMap<File, String> libraryFilesMap = new HashMap<>();
        module.getLibraryFileMap().forEach((p, n) -> libraryFilesMap.put(p.toFile(), n));
        
        // Parts filled out by code generation
        List<Component> assemblyComponents = new ArrayList<>();                         // List of assembly Components
        HashMap<String, Integer> assemblyLabelIndexMap= new HashMap<>();                // Maps label name to index in assemblyComponents
        
        // Generate code :)
        
        // Done!
        AssemblyObject ao = new AssemblyObject(assemblyComponents, assemblyLabelIndexMap, module.getName(), libraryFilesMap, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        
        // Output to file if needed
        if(this.outputFinalToFile) {
            // Get name, trim extension
            String sourceFileName = module.getSourceFile().getFileName().toString();
            sourceFileName = sourceFileName.substring(0, sourceFileName.lastIndexOf("."));
            Path outputFile = this.finalOutputDirectory.resolve(sourceFileName + ".asm");
            
            LOG.info("Writing generated assembly to " + outputFile);
            
            try(BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(outputFile))) {
                ao.print(bos);
            } catch(IOException e) {}
        }
        
        return ao;
    }

    @Override
    public void setDAGVisualization(boolean isel) {
        this.showISelDAG = isel;
    }

    @Override
    public void setAbstractOutput(boolean output, Path directory) {
        this.outputAbstractToFile = output;
        this.abstractOutputDirectory = directory;
    }

    @Override
    public void setFinalOutput(boolean output, Path directory) {
        this.outputFinalToFile = output;
        this.finalOutputDirectory = directory;
    }
    
}
