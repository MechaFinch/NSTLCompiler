package notsotiny.lang.compiler.optimization;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import notsotiny.lang.compiler.optimization.cse.IRPassLCSE;
import notsotiny.lang.compiler.optimization.other.IRPassBasicBlockMerge;
import notsotiny.lang.compiler.optimization.other.IRPassDebug;
import notsotiny.lang.compiler.optimization.sccp.IRPassSCCP;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.util.IRCFGRenderer;
import notsotiny.lang.ir.util.IRPrinter;
import notsotiny.lang.util.StreamPrinter;

/**
 * IR Optimizer
 */
public class IROptV1 implements IROptimizer {
    
    private static Logger LOG = Logger.getLogger(IROptV1.class.getName());
    
    private static List<IROptimizationPass> passes = new ArrayList<>();
    
    static {
        passes.add(new IRPassSCCP());
        passes.add(new IRPassBasicBlockMerge());
        passes.add(new IRPassLCSE());
        passes.add(new IRPassDebug());
    }
    
    private IROptimizationLevel level = IROptimizationLevel.ONE;
    
    private boolean outputToFile = false,
                    outputIntermediate = false,
                    showIntermediateCFG = false,
                    showOptimizedCFG = false;
    
    private Path fileOutputDirectory = null,
                 intermediateOutputDirectory = null;

    @Override
    public IRModule optimize(IRModule module) {
        LOG.fine("Optimizing " + module.getName());
        
        int passNumber = 0;
        
        for(IROptimizationPass pass : passes) {
            if(this.level.isAbove(pass.level())) {
                module = pass.optimize(module);
                
                // Render intermediate CFG if applicable
                if(this.showIntermediateCFG) {
                    for(IRFunction fun : module.getInternalFunctions().values()) {
                        IRCFGRenderer.renderCFG(fun, "_inter" + passNumber + "_ir");
                    }
                }
                
                // Output intermediate to file if applicable
                if(this.outputIntermediate) {
                    // Get name, trim extension
                    String sourceFileName = module.getSourceFile().getFileName().toString();
                    sourceFileName = sourceFileName.substring(0, sourceFileName.lastIndexOf("."));
                    Path outputFile = this.intermediateOutputDirectory.resolve(sourceFileName + "_" + passNumber + ".nir");
                    
                    LOG.info("Writing intermediate IR to " + outputFile);
                    
                    try(BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(outputFile))) {
                        StreamPrinter filePrinter = new StreamPrinter(bos);
                        IRPrinter.printModule(filePrinter, module, 0);
                    } catch(IOException e) {}
                }
                
                passNumber++;
            }
        }
        
        // Render CFG if applicable
        if(this.showOptimizedCFG) {
            for(IRFunction fun : module.getInternalFunctions().values()) {
                IRCFGRenderer.renderCFG(fun, "_opt_ir");
            }
        }
        
        // Output to file is applicable
        if(this.outputToFile) {
            // Get name, trim extension
            String sourceFileName = module.getSourceFile().getFileName().toString();
            sourceFileName = sourceFileName.substring(0, sourceFileName.lastIndexOf("."));
            Path outputFile = this.fileOutputDirectory.resolve(sourceFileName + ".nir");
            
            LOG.info("Writing optimized IR to " + outputFile);
            
            try(BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(outputFile))) {
                StreamPrinter filePrinter = new StreamPrinter(bos);
                IRPrinter.printModule(filePrinter, module, 0);
            } catch(IOException e) {}
        }
        
        return module;
    }

    @Override
    public void setLevel(IROptimizationLevel level) {
        this.level = level;
    }
    
    @Override
    public void setFileOutput(boolean output, Path directory) {
        this.outputToFile = output;
        this.fileOutputDirectory = directory;
    }
    
    @Override
    public void setIntermediateOutput(boolean output, Path directory) {
        this.outputIntermediate = output;
        this.intermediateOutputDirectory = directory;
    }

    @Override
    public void setCFGVisualization(boolean iir, boolean oir) {
        this.showIntermediateCFG = iir;
        this.showOptimizedCFG = oir;
    }
}
