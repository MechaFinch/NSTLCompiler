package notsotiny.lang.compiler.irgen;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import javafx.scene.input.TransferMode;
import notsotiny.asm.resolution.ResolvableConstant;
import notsotiny.asm.resolution.ResolvableValue;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.types.ArrayType;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.PointerType;
import notsotiny.lang.compiler.types.RawType;
import notsotiny.lang.compiler.types.StringType;
import notsotiny.lang.compiler.types.StructureType;
import notsotiny.lang.compiler.types.TypeContainer;
import notsotiny.lang.compiler.types.TypedArray;
import notsotiny.lang.compiler.types.TypedRaw;
import notsotiny.lang.compiler.types.TypedStructure;
import notsotiny.lang.compiler.types.TypedValue;
import notsotiny.lang.ir.IRCFGRenderer;
import notsotiny.lang.ir.IRConstant;
import notsotiny.lang.ir.IRFunction;
import notsotiny.lang.ir.IRGlobal;
import notsotiny.lang.ir.IRIdentifier;
import notsotiny.lang.ir.IRIdentifierClass;
import notsotiny.lang.ir.IRModule;
import notsotiny.lang.ir.IRPrinter;
import notsotiny.lang.ir.IRType;
import notsotiny.lang.ir.IRValue;
import notsotiny.lang.util.LogPrinter;
import notsotiny.lang.util.Printer;
import notsotiny.lang.util.StreamPrinter;

/**
 * IR Generator
 */
public class IRGenV1 implements IRGenerator {
    
    private static Logger LOG = Logger.getLogger(IRGenV1.class.getName());
    private static ASTLogger ALOG = new ASTLogger(LOG);
    
    private boolean showASTCFG = false;
    private boolean showIRCFG = false;
    private boolean outputToFile = false;
    
    private Path fileOutputDirectory = null;

    @Override
    public IRModule generate(ASTNode astRoot, String defaultLibName, FileLocator locator) throws CompilationException {
        IRModule module = new IRModule(defaultLibName);
        
        generate(astRoot, module, locator);
        
        return module;
    }
    
    @Override
    public IRModule generate(ASTNode astRoot, String defaultLibName, FileLocator locator, Path sourcePath) throws CompilationException {
        IRModule module = new IRModule(defaultLibName, sourcePath);
        
        generate(astRoot, module, locator);
        
        return module;
    }
    
    /**
     * Generate the module
     * @param module
     * @param defaultLibName
     * @param locator
     * @throws CompilationException 
     */
    private void generate(ASTNode code, IRModule irModule, FileLocator locator) throws CompilationException {
        // Parse the top level code
        ASTModule astModule = TopLevelParser.parseTopLevel(irModule.getName(), code, locator);
        
        LogPrinter logPrinter = new LogPrinter(LOG, Level.FINEST);
        
        // Convert global constants & variables to IRGlobals
        // Global constants
        LOG.finer("Creating IR for global constants");
        for(String name : astModule.getGlobalConstantMap().keySet()) {
            // Value and ID
            TypedValue tv = astModule.getGlobalConstantMap().get(name);
            IRIdentifier irid = new IRIdentifier(name, IRIdentifierClass.GLOBAL);
            
            if(tv == null) {
                LOG.severe("Null global constant: " + name);
                throw new CompilationException();
            }
            
            // Convert and save
            IRGlobal global = convertToGlobal(irid, tv, true);
            
            if(logPrinter.isLoggable()) {
                try {
                    IRPrinter.printGlobal(logPrinter, global, 0);
                } catch(IOException e) {}
            }
            
            irModule.addGlobal(global);
        }
        
        LOG.finer("Creating IR for global variables");
        for(String name : astModule.getGlobalVariableMap().keySet()) {
            // Value and ID
            TypedValue tv = astModule.getGlobalVariableMap().get(name);
            IRIdentifier irid = new IRIdentifier(name, IRIdentifierClass.GLOBAL);
            
            if(tv == null) {
                LOG.severe("Null global variable: " + name);
                throw new CompilationException();
            }
            
            // Convert and save
            IRGlobal global = convertToGlobal(irid, tv, false);
            
            if(logPrinter.isLoggable()) {
                try {
                    IRPrinter.printGlobal(logPrinter, global, 0);
                } catch(IOException e) {}
            }
            
            irModule.addGlobal(global);
        }
        
        boolean hasErrors = false;
        
        // Compile each function
        for(ASTFunction function : astModule.getFunctionMap().values()) {
            // External functions are external
            if(function.isExternal()) {
                try {
                    irModule.addFunction(function.getHeaderIR(irModule));
                } catch(CompilationException e) {
                    ALOG.severe(function.getHeader().getSource(), "Error converting external function: " + e.getMessage());
                    throw e;
                }
                continue;
            }
            
            // Parse the function into a CFG
            try {
                CFGParser.parseFunctionCFG(function);
                
                // Display AST CFG if requested
                if(showASTCFG) {
                    ASTCFGRenderer.renderCFG(function);
                }
                
                // Parse to IR
                IRFunction irFunction = ASTCodeParser.parseFunction(function, irModule);
                irModule.addFunction(irFunction);
                
                // Display IR CFG if requested
                if(showIRCFG) {
                    IRCFGRenderer.renderCFG(irFunction);
                }
            } catch(CompilationException e) {
                hasErrors = true;
            }
        }
        
        // Log IR if applicable
        if(logPrinter.isLoggable()) {
            try {
                logPrinter.println("Generated IR:");
                IRPrinter.printModule(logPrinter, irModule, 0);
            } catch(IOException e) {}
        }
        
        // Output to file is applicable
        if(this.outputToFile) {
            // Get name, trim extension
            String sourceFileName = irModule.getSourceFile().getFileName().toString();
            sourceFileName = sourceFileName.substring(0, sourceFileName.lastIndexOf("."));
            Path outputFile = this.fileOutputDirectory.resolve(sourceFileName + ".nir");
            
            LOG.info("Writing generated IR to " + outputFile);
            
            try(BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(outputFile))) {
                StreamPrinter filePrinter = new StreamPrinter(bos);
                IRPrinter.printModule(filePrinter, irModule, 0);
            } catch(IOException e) {}
        }
        
        // Accumulated errors
        if(hasErrors) {
            throw new CompilationException();
        }
    }

    @Override
    public void setCFGVisualization(boolean ast, boolean ir) {
        this.showASTCFG = ast;
        this.showIRCFG = ir;
    }
    
    @Override
    public void setFileOutput(boolean output, Path directory) {
        this.outputToFile = output;
        this.fileOutputDirectory = directory;
    }
    
    /**
     * Convert a TypedValue to an IRGlobal
     * @param irid
     * @param tv
     * @param constant
     * @return
     * @throws CompilationException 
     */
    private static IRGlobal convertToGlobal(IRIdentifier irid, TypedValue tv, boolean constant) throws CompilationException {
        List<IRValue> values = new ArrayList<>();
        convertToValues(tv, values, irid);
        return new IRGlobal(irid, values, constant);
    }
    
    /**
     * Converts a TypedValue into one or more IRValues, adding them to dest
     * @param tv
     * @param dest
     * @return
     * @throws CompilationException
     */
    private static void convertToValues(TypedValue tv, List<IRValue> dest, IRIdentifier source) throws CompilationException {
        switch(tv) {
            case TypedRaw tr: {
                // Integers
                ResolvableValue rv = tr.getValue();
                if(rv.isResolved()) {
                    // If the raw is a known value, use it
                    dest.add(new IRConstant((int) tr.getResolvedValue(), tr.getType().getIRType()));
                } else if(rv instanceof ResolvableConstant rc) {
                    // If the raw is a name, use it
                    dest.add(new IRIdentifier(rc.getName(), IRIdentifierClass.GLOBAL));
                } else {
                    // Otherwise error
                    LOG.severe("Got unresolved non-trivial ResolvableValue from global " + source + ": " + tv);
                }
                break;
            }
            
            case TypedArray ta: {
                // Arrays
                for(int i = 0; i < ta.getArrayType().getLength(); i++) {
                    convertToValues(ta.getValue(i), dest, source);
                }
                break;
            }
            
            case TypedStructure ts: {
                // Structures
                List<String> memberNames = ts.getStructureType().getMemberNames();
                for(int i = 0; i < memberNames.size(); i++) {
                    convertToValues(ts.getMember(memberNames.get(i)), dest, source);
                }
                break;
            }
            
            case TypeContainer tc: {
                // Just a type. Convert it to the equivalent amount of zeroes
                NSTLType type = tc.getType();
                
                switch(type) {
                    case RawType rt:
                        dest.add(new IRConstant(0, rt.getIRType()));
                        break;
                    
                    case PointerType pt:
                        dest.add(new IRConstant(0, pt.getIRType()));
                        break;
                    
                    case ArrayType at:
                        for(int i = 0; i < at.getLength(); i++) {
                            convertToValues(new TypeContainer(at.getMemberType()), dest, source);
                        }
                        break;           
                    
                    case StructureType st:
                        for(int i = 0; i < st.getMemberNames().size(); i++) {
                            convertToValues(new TypeContainer(st.getMemberType(st.getMemberNames().get(i))), dest, source);
                        }
                        break;
                        
                        
                    default:
                        LOG.severe("Unexpected type from uninitialized global: " + type + " from " + source);
                        throw new CompilationException();
                }
                break;
            }
            
            default:
                LOG.severe("Unexpected TypedValue from global: " + tv);
                throw new CompilationException();
        }
    }
    
}
