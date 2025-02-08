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
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import notsotiny.asm.Assembler.AssemblyObject;
import notsotiny.asm.components.Component;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.codegen.dag.ISelDAG;
import notsotiny.lang.compiler.codegen.dag.ISelDAGBuilder;
import notsotiny.lang.compiler.codegen.dag.ISelDAGRenderer;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRDefinition;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.util.IRPrinter;
import notsotiny.lang.ir.util.IRUtil;
import notsotiny.lang.util.Pair;
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
        for(IRFunction function : module.getInternalFunctions().values()) {
            LOG.fine("----Generating code for " + function.getID().getName() + "----");
            
            // Convert basic blocks into ISelDAGs
            // bb ID -> bb DAG
            Map<IRIdentifier, ISelDAG> bbDAGs = new HashMap<>();
            
            // Get information about locals
            Map<IRIdentifier, IRType> typeMap = IRUtil.getTypeMap(function);
            Map<IRIdentifier, Pair<Set<IRIdentifier>, Set<IRIdentifier>>> livenessSets = IRUtil.getLivenessSets(function, false);
            Map<IRIdentifier, IRDefinition> definitionMap = IRUtil.getDefinitionMap(function);
            
            // Copy of the list as DAG construction adds BBs for conditional argument mappings
            // New BBs have their DAGs built when created so they don't need to be included in the loop
            List<IRBasicBlock> sourceBBs = new ArrayList<>(function.getBasicBlockList());
            for(IRBasicBlock irBB : sourceBBs) {
                //LOG.info(livenessSets.get(irBB.getID()) + "");
                
                // Build DAG
                bbDAGs.putAll(ISelDAGBuilder.buildDAG(irBB, typeMap, livenessSets, definitionMap));
            }
            
            if(this.showISelDAG) {
                for(Entry<IRIdentifier, ISelDAG> entry : bbDAGs.entrySet()) {
                    ISelDAGRenderer.renderDAG(entry.getValue(), function.getID() + " - " + entry.getKey());
                }
            }
            
            // Tile ISelDAGs to select instructions
            
            // Perform scheduling
            
            // Perform register allocation
            
            // Convert to assembly components
        }
        
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
