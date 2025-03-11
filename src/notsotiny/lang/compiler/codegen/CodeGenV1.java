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
import java.util.MissingResourceException;
import java.util.Set;
import java.util.logging.Logger;

import notsotiny.asm.Assembler.AssemblyObject;
import notsotiny.asm.components.Component;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.codegen.dag.ISelDAG;
import notsotiny.lang.compiler.codegen.dag.ISelDAGBuilder;
import notsotiny.lang.compiler.codegen.dag.ISelDAGNode;
import notsotiny.lang.compiler.codegen.dag.ISelDAGRenderer;
import notsotiny.lang.compiler.codegen.dag.ISelDAGTile;
import notsotiny.lang.compiler.codegen.pattern.ISelPattern;
import notsotiny.lang.compiler.codegen.pattern.ISelPatternCompiler;
import notsotiny.lang.compiler.codegen.pattern.ISelPatternMatcher;
import notsotiny.lang.compiler.codegen.pretransform.ISelPretransformConditionalArguments;
import notsotiny.lang.compiler.codegen.pretransform.ISelPretransformer;
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
    
    // Transformations
    private static List<ISelPretransformer> pretransformers;
    
    static {
        // Populate transformations
        pretransformers = new ArrayList<>();
        pretransformers.add(new ISelPretransformConditionalArguments());
    }

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
            
            // Perform pre-DAG transformations
            for(ISelPretransformer transformer : pretransformers) {
                transformer.transform(function);
            }
            
            // Convert basic blocks into ISelDAGs
            // bb ID -> bb DAG
            Map<IRIdentifier, ISelDAG> bbDAGs = new HashMap<>();
            
            // Get information about locals
            // typeMap and livenessSets will be maintained during code generation. definitionMap will not.
            Map<IRIdentifier, IRType> typeMap = IRUtil.getTypeMap(function);
            Map<IRIdentifier, Pair<Set<IRIdentifier>, Set<IRIdentifier>>> livenessSets = IRUtil.getLivenessSets(function, false);
            Map<IRIdentifier, IRDefinition> definitionMap = IRUtil.getDefinitionMap(function);
            
            // Copy of the list as DAG construction adds BBs for conditional argument mappings
            // New BBs have their DAGs built when created so they don't need to be included in the loop
            // New BBs are also added to the liveness sets
            List<IRBasicBlock> sourceBBs = new ArrayList<>(function.getBasicBlockList());
            for(IRBasicBlock irBB : sourceBBs) {
                //LOG.info(livenessSets.get(irBB.getID()) + "");
                
                // Ensure we have no unnecessary NONEs
                IRUtil.inferNoneTypes(irBB, typeMap);
                
                // Build DAG
                bbDAGs.putAll(ISelDAGBuilder.buildDAG(irBB, typeMap, livenessSets, definitionMap));
            }
            
            if(this.showISelDAG) {
                for(Entry<IRIdentifier, ISelDAG> entry : bbDAGs.entrySet()) {
                    ISelDAGRenderer.renderDAG(entry.getValue(), function.getID() + " - " + entry.getKey());
                }
            }
            
            // Perform instruction selection
            for(ISelDAG dag : bbDAGs.values()) {
                try {
                // Perform pattern matching to determine what tiles can be used for each node
                Map<ISelDAGNode, List<ISelDAGTile>> matchingTilesMap = ISelPatternMatcher.matchPatterns(dag, typeMap);
                
                // Tile the DAG to select instructions
                } catch(CompilationException e) {
                    // TODO temporary
                }
            }
            
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
