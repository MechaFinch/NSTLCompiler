package notsotiny.lang.compiler.optimization;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import notsotiny.lang.ir.IRModule;
import notsotiny.lang.ir.IRPrinter;
import notsotiny.lang.util.StreamPrinter;

/**
 * IR Optimizer
 */
public class IROptV1 implements IROptimizer {
    
    private static Logger LOG = Logger.getLogger(IROptV1.class.getName());
    
    private static List<IROptimizationPass> passes = new ArrayList<>();
    
    static {
        passes.add(new IRPassEmptyBlockMerge());
    }
    
    private IROptimizationLevel level = IROptimizationLevel.ONE;
    
    private boolean outputToFile = false;
    
    private Path fileOutputDirectory = null;

    @Override
    public IRModule optimize(IRModule module) {
        LOG.fine("Optimizing " + module.getName());
        
        for(IROptimizationPass pass : passes) {
            if(this.level.isAbove(pass.level())) {
                module = pass.optimize(module);
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
}
