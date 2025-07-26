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
import notsotiny.asm.components.InitializedData;
import notsotiny.asm.resolution.ResolvableConstant;
import notsotiny.asm.resolution.ResolvableValue;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.aasm.AASMPart;
import notsotiny.lang.compiler.aasm.AASMPrinter;
import notsotiny.lang.compiler.aasm.AASMTranslator;
import notsotiny.lang.compiler.codegen.alloc.AllocationResult;
import notsotiny.lang.compiler.codegen.alloc.RegisterAllocator;
import notsotiny.lang.compiler.codegen.dag.ISelDAG;
import notsotiny.lang.compiler.codegen.dag.ISelDAGBuilder;
import notsotiny.lang.compiler.codegen.dag.ISelDAGNode;
import notsotiny.lang.compiler.codegen.dag.ISelDAGProducerNode;
import notsotiny.lang.compiler.codegen.dag.ISelDAGRenderer;
import notsotiny.lang.compiler.codegen.dag.ISelDAGTile;
import notsotiny.lang.compiler.codegen.pattern.ISelPattern;
import notsotiny.lang.compiler.codegen.pattern.ISelPatternCompiler;
import notsotiny.lang.compiler.codegen.pattern.ISelPatternMatcher;
import notsotiny.lang.compiler.codegen.pretransform.ISelPretransformConditionalArguments;
import notsotiny.lang.compiler.codegen.pretransform.ISelPretransformer;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRConstant;
import notsotiny.lang.ir.parts.IRDefinition;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRGlobal;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lang.ir.util.IRPrinter;
import notsotiny.lang.ir.util.IRUtil;
import notsotiny.lang.util.Pair;
import notsotiny.lang.util.StreamPrinter;

public class CodeGenV1 implements CodeGenerator {
    
    private static Logger LOG = Logger.getLogger(CodeGenV1.class.getName());
    
    // TODO: tie to optimization level?
    private static final int ALLOCATION_ITERATIONS = 4;
    
    private boolean showISelDAG = false;
    private boolean showRAIGUncolored = false;
    private boolean showRAIGColored = false;
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
        List<Component> assemblyComponents = new ArrayList<>();             // List of assembly Components
        HashMap<String, Integer> assemblyLabelIndexMap = new HashMap<>();   // Maps label name to index in assemblyComponents
        
        // pre-register allocation AASM so we can output 1 file per module instead of 1 file per function
        Map<IRIdentifier, List<List<AASMPart>>> abstractResults = new HashMap<>();
        
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
                //LOG.info(irBB.getID() + " liveness: " + livenessSets.get(irBB.getID()) + "");
                
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
            Map<IRIdentifier, List<List<AASMPart>>> bbAASMs = new HashMap<>(); 
            
            for(ISelDAG dag : bbDAGs.values()) {
                // Perform pattern matching to determine what tiles can be used for each node
                Map<ISelDAGNode, Set<ISelDAGTile>> matchingTilesMap = ISelPatternMatcher.matchPatterns(dag, typeMap);
                
                // Tile the DAG to select instructions
                Map<ISelDAGNode, ISelDAGTile> selectedTiles = new HashMap<>();
                Map<ISelDAGNode, Set<ISelDAGTile>> coveringTiles = new HashMap<>();
                ISelTileSelector.selectTiles(selectedTiles, coveringTiles, dag, matchingTilesMap);
                
                // Perform intra-block scheduling
                List<List<AASMPart>> schedule = IntraBlockScheduler.scheduleBlock(dag, selectedTiles, coveringTiles);
                bbAASMs.put(dag.getBasicBlock().getID(), schedule);
            }
            
            // Perform inter-block scheduling
            List<List<AASMPart>> scheduledCode = InterBlockScheduler.scheduleBlocks(function, bbAASMs, livenessSets);
            
            if(this.outputAbstractToFile) {
                abstractResults.put(function.getID(), scheduledCode);
            }
            
            // Do several register allocation attempts to ensure the best is achieved
            int bestInstructions = Integer.MAX_VALUE;
            List<AASMPart> bestCode = null;
            AllocationResult bestResult = null;
            int iters = (this.showRAIGColored || this.showRAIGUncolored) ? 1 : ALLOCATION_ITERATIONS;
            
            for(int i = 0; i < iters; i++) {
                // Perform register allocation
                AllocationResult allocRes = RegisterAllocator.allocateRegisters(scheduledCode, function, showRAIGUncolored, showRAIGColored);
                
                // Perform peephole optimizations
                // Mainly cleaning up RA output
                List<AASMPart> optimizedCode = PeepholeOptimizer.optimize(allocRes.allocatedCode(), function);
                
                if(optimizedCode.size() < bestInstructions) {
                    bestCode = optimizedCode;
                    bestResult = allocRes;
                    bestInstructions = optimizedCode.size();
                }
            }
            
            // Convert to assembly components
            AASMTranslator.translate(new AllocationResult(bestCode, bestResult.stackAllocationSize(), bestResult.i(), bestResult.j(), bestResult.k(), bestResult.l()), assemblyComponents, assemblyLabelIndexMap, function);
        }
        
        // Output abstract assembly to file if needed
        if(this.outputAbstractToFile) {
            // Get name, trim extension
            String sourceFileName = module.getSourceFile().getFileName().toString();
            sourceFileName = sourceFileName.substring(0, sourceFileName.lastIndexOf("."));
            Path outputFile = this.abstractOutputDirectory.resolve(sourceFileName + ".aasm");
            
            LOG.info("Writing generated abstract assembly to " + outputFile);
            
            try(BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(outputFile))) {
                StreamPrinter filePrinter = new StreamPrinter(bos);
                AASMPrinter.printModule(filePrinter, abstractResults);
            } catch(IOException e) {}
        }
        
        // Convert globals to assembly components
        for(IRGlobal g : module.getGlobals().values()) {
            assemblyLabelIndexMap.put(g.getID().getName(), assemblyComponents.size());
            
            // Are we dealing with a single type or multiple
            boolean hasSingleType = true;
            IRValue firstValue = g.getContents().get(0);
            IRType firstType = (firstValue instanceof IRConstant c ? c.getType() : IRType.I32);
            
            for(IRValue v : g.getContents()) {
                if(v instanceof IRConstant c) {
                    if(c.getType() != firstType) {
                        hasSingleType = false;
                        break;
                    }
                } else {
                    if(firstType != IRType.I32) {
                        hasSingleType = false;
                        break;
                    }
                }
            }
            
            if(hasSingleType) {
                // Single type. Collect into 1 InitializedData
                List<ResolvableValue> values = new ArrayList<>();
                
                for(IRValue v : g.getContents()) {
                    if(v instanceof IRConstant c) {
                        values.add(new ResolvableConstant(c.getValue()));
                    } else {
                        values.add(new ResolvableConstant(((IRIdentifier) v).getName()));
                    }
                }
                
                assemblyComponents.add(new InitializedData(values, firstType.getSize()));
            } else {
                // Multiple types. One InitializedData per item
                for(IRValue v : g.getContents()) {
                    ResolvableValue rv;
                    IRType type;
                    
                    if(v instanceof IRConstant c) {
                        rv = new ResolvableConstant(c.getValue());
                        type = c.getType();
                    } else {
                        rv = new ResolvableConstant(((IRIdentifier) v).getName());
                        type = IRType.I32;
                    }
                    
                    assemblyComponents.add(new InitializedData(List.of(rv), type.getSize()));
                }
            }
        }
        
        // Done!
        AssemblyObject ao = new AssemblyObject(assemblyComponents, assemblyLabelIndexMap, module.getName(), libraryFilesMap, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        
        // Output concrete assembly to file if needed
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
    public void setGraphVisualization(boolean isel, boolean raUncolored, boolean raColored) {
        this.showISelDAG = isel;
        this.showRAIGUncolored = raUncolored;
        this.showRAIGColored = raColored;
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
