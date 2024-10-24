package notsotiny.lang.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import asmlib.lex.symbols.Symbol;
import asmlib.token.Tokenizer;
import asmlib.util.FileLocator;
import asmlib.util.relocation.RenameableRelocatableObject;
import fr.cenotelie.hime.redist.ASTNode;
import fr.cenotelie.hime.redist.ParseError;
import fr.cenotelie.hime.redist.ParseResult;
import fr.cenotelie.hime.redist.parsers.InitializationException;
import notsotiny.asm.Assembler;
import notsotiny.asm.Assembler.AssemblyObject;
import notsotiny.lang.compiler.codegen.EmptyCodeGenerator;
import notsotiny.lang.compiler.compilers.IRCompiler;
import notsotiny.lang.compiler.irgen.EmptyIRGenerator;
import notsotiny.lang.compiler.irgen.IRGenV1;
import notsotiny.lang.compiler.optimization.EmptyIROptimizer;
import notsotiny.lang.compiler.shitty.SAPCompiler;
import notsotiny.lang.parser.NstlgrammarLexer;
import notsotiny.lang.parser.NstlgrammarParser;

public class NSTLCompiler {
    
    private static Logger LOG = Logger.getLogger(NSTLCompiler.class.getName());
    
    public static void main(String[] args) throws IOException, InitializationException {
        // handle arguments
        if(args.length < 1 || args.length > 8) {
            System.out.println("Usage: NSTLCompiler [options] <input file>");
            System.out.println("Flags:");
            System.out.println("\t-d\t\t\tEnable debug-friendly object files. If enabled, references are verbose and files much larger.");
            System.out.println("\t-x <exec file>\t\tExec File. Specifies the .oex file to output. Default <input file>.oex");
            System.out.println("\t-e <entry function>\tEntry. Specifies an entry function. Default main");
            System.out.println("\t-o <output directory>\tOutput. Specifies the location of output object files. Default <working directory>\\out");
            System.out.println("\t-c <compiler name>\tCompiler. Specifies which compiler variant to use. Options: shit, ir. Default: ir");
            return; 
        }
        
        int flagCount = 0;
        boolean debug = false,
                hasExecFile = false,
                hasOutputDir = false;
        
        String inputFileArg = "",
               execFileArg = "",
               outputArg = "",
               compilerName = "ir",
               entry = "main";
        
        out:
        while(true) {
            switch(args[flagCount]) {
                case "-d":
                    flagCount++;
                    debug = true;
                    break;
                
                case "-e":
                    flagCount += 2;
                    entry = args[flagCount - 1];
                    break;
                
                case "-x":
                    flagCount += 2;
                    hasExecFile = true;
                    execFileArg = args[flagCount - 1];
                    break;
                
                case "-o":
                    flagCount += 2;
                    hasOutputDir = true;
                    outputArg = args[flagCount - 1];
                    break;
                
                case "-c":
                    flagCount += 2;
                    compilerName = args[flagCount - 1];
                    break;
                
                default:
                    break out;
            }
        }
        
        inputFileArg = args[flagCount];
        
        // get full path for file finding
        Path sourceFile = Paths.get(inputFileArg),
             execFile,
             sourceDir = sourceFile.toAbsolutePath().getParent(),
             outDir = hasOutputDir ? Paths.get(outputArg) : sourceDir.resolve("out"),
             standardDir = Paths.get("C:\\Users\\wetca\\data\\silly  code\\architecture\\NotSoTiny\\programming\\standard library");
        
        if(hasExecFile) {
            execFile = Paths.get(execFileArg);
        } else {
            String sourceName = sourceFile.getFileName().toString();
            sourceName = sourceName.substring(0, sourceName.lastIndexOf('.')) + ".oex";
            execFile = sourceFile.resolveSibling(sourceName);
        }
        
        FileLocator locator = new FileLocator(sourceDir, standardDir, List.of(".obj", ".asm", ".nstl"), List.of(".nsth"));
        if(!locator.addFile(sourceFile.toAbsolutePath())) {
            LOG.severe("Could not fine sounce file " + sourceFile);
            return;
        }
        
        List<AssemblyObject> compiledObjects = new ArrayList<>();
        List<RenameableRelocatableObject> assembledObjects = new ArrayList<>();
        
        Map<String, Path> libraryNameMap = new HashMap<>();
        
        boolean errorsEncountered = false;
        
        // process files and referenced files
        LOG.fine("Compiling files");
        while(locator.hasUnconsumed()) {
            Path workingFile = locator.consume();
            LOG.info("Processing file " + workingFile);
            
            String fileName = workingFile.getFileName().toString(),
                   extension = fileName.substring(fileName.indexOf('.'));
            
            locator.setWorkingDirectory(workingFile);
            
            switch(extension) {
                case ".nstl":
                    // compile
                    NstlgrammarLexer lexer = new NstlgrammarLexer(new InputStreamReader(Files.newInputStream(workingFile)));
                    NstlgrammarParser parser = new NstlgrammarParser(lexer);
                    NSTCompiler comp = switch(compilerName) {
                        case "ir"   -> new IRCompiler(new IRGenV1(), new EmptyIROptimizer(), new EmptyCodeGenerator());
                        case "shit" -> new SAPCompiler();
                        default     -> throw new IllegalArgumentException("Unknown compiler: " + compilerName);
                    };
                    
                    ParseResult result = parser.parse();
                    
                    if(result.getErrors().size() != 0) {
                        LOG.severe("Encountered errors parsing " + workingFile);
                        for(ParseError e : result.getErrors()) {
                            LOG.severe("ParseError: " + e);
                        }
                        
                        errorsEncountered = true;
                    } else {
                        ASTNode root = result.getRoot();
                        LOG.finest("AST");
                        LOG.finest(() -> {printTree(root, new boolean[] {}); return "";});
                        
                        String libname = workingFile.getFileName().toString();
                        libname = libname.substring(0, libname.lastIndexOf('.'));
                        
                        try {
                            AssemblyObject obj = comp.compile(root, libname, locator);
                            
                            compiledObjects.add(obj);
                            libraryNameMap.put(libname, workingFile);
                        } catch(CompilationException e) {
                            errorsEncountered = true;
                        } catch(IllegalStateException e) {
                            LOG.severe(e.getMessage());
                            errorsEncountered = true;
                        }
                    }
                    break;
                
                case ".asm":
                    // assemble
                    try(BufferedReader br = Files.newBufferedReader(workingFile)) {
                        List<Symbol> symbols = Assembler.lexer.lex(Tokenizer.tokenize(br.lines().toList()));
                        RenameableRelocatableObject obj = Assembler.assembleObjectFromSource(symbols, workingFile, locator, true);
                        
                        assembledObjects.add(obj);
                        libraryNameMap.put(obj.getName(), workingFile);
                    } catch(Exception e) {
                        LOG.severe("Exception while assembling " + workingFile);
                        e.printStackTrace();
                        errorsEncountered = true;
                    }
                    break;
                
                case ".obj":
                    // copy
                    RenameableRelocatableObject obj = new RenameableRelocatableObject(workingFile.toFile(), null);
                    libraryNameMap.put(obj.getName(), workingFile);
                    assembledObjects.add(obj);
                    break;
                
                default:
                    LOG.severe("Unknown file type: " + workingFile);
                    errorsEncountered = true;
                    break;
            }
        }
        
        if(errorsEncountered) {
            throw new IllegalStateException("Encountered errors during file processing. See severe logs above.");
        }
        
        // assemble compiled stuff
        LOG.fine("Assembling compiled objects");
        assembledObjects.addAll(Assembler.assemble(compiledObjects, true));
        
        // unify names
        LOG.fine("Unifying library names");
        for(String name : libraryNameMap.keySet()) {
            File f = libraryNameMap.get(name).toFile();
            LOG.finest(() -> "Naming \"" + f + "\" " + name);
            
            for(RenameableRelocatableObject obj : assembledObjects) {
                obj.renameLibraryFile(f, name);
            }
        }
        
        String mainFileName = sourceFile.getFileName().toString(),
               entrySymbolName = mainFileName.substring(0, mainFileName.lastIndexOf('.')) + "." + entry;
        
        // compact names if debug isn't enabled
        if(!debug) {
            LOG.fine("Compacting references");
            Map<String, String> nameIDMap = new HashMap<>(),
                                libraryIDMap = new HashMap<>();
            
            // generate compact names
            int lid = 0;
            for(RenameableRelocatableObject obj : assembledObjects) {
                libraryIDMap.put(obj.getName(), Integer.toHexString(lid++));
                LOG.fine("Renamed " + obj.getName() + " to " + libraryIDMap.get(obj.getName()));
                
                String n = obj.getName() + ".";
                int id = 0;
                
                for(String s : obj.getOutgoingReferenceNames()) {
                    // dont modify special references
                    if(s.equals("ORIGIN")) continue;
                    
                    String oldName = n + s,
                           newName = n + Integer.toHexString(id++);
                    
                    // track changes to entry symbol
                    if(oldName.equals(entrySymbolName)) {
                        entrySymbolName = newName;
                    }
                    
                    nameIDMap.put(oldName, newName);
                }
            }
            
            // rename references
            for(RenameableRelocatableObject obj : assembledObjects) {
                for(String old : nameIDMap.keySet()) {
                    obj.renameGlobal(old, nameIDMap.get(old));
                }
            }
            
            // rename libraries
            int entryIndex = entrySymbolName.indexOf('.');
            entrySymbolName = libraryIDMap.get(entrySymbolName.substring(0, entryIndex)) + entrySymbolName.substring(entryIndex);
            
            for(RenameableRelocatableObject obj : assembledObjects) {
                for(String old : libraryIDMap.keySet()) {
                    obj.renameLibrary(old, libraryIDMap.get(old));
                }
            }
        }
        
        // write output
        LOG.fine("Writing output files");
        Path execRelativeOutputDir = execFile.toAbsolutePath().getParent().relativize(outDir);
        
        // make the output directory if it doesn't exist
        if(!Files.exists(outDir)) {
            LOG.finest(() -> "Creating output directory " + outDir);
            Files.createDirectory(outDir);
        }
        
        // write object files
        Set<String> objectFileNames = new HashSet<>();
        for(RenameableRelocatableObject obj : assembledObjects) {
            String fileName = obj.getName() + ".obj";
            Path ofile = outDir.resolve(fileName);
            
            LOG.finer(() -> "Writing output file " + ofile);
            
            Files.write(ofile, obj.asObjectFile());
            objectFileNames.add(execRelativeOutputDir.resolve(fileName).toString());
        }
        
        // exec file
        LOG.finer(() -> "Writing exec file " + execFile);
        try(PrintWriter execWriter = new PrintWriter(Files.newBufferedWriter(execFile))) {
            // entry
            execWriter.println("#entry " + entrySymbolName);
            
            // object files
            objectFileNames.forEach(execWriter::println);
        }
        
        LOG.info("Done.");
    }
    
    public static void printTree(ASTNode node, boolean[] crossings) {
        StringBuilder sb = new StringBuilder();
        
        for(int i = 0; i < crossings.length - 1; i++) {
            sb.append(crossings[i] ? "|  " : "   ");
        }
        
        if(crossings.length > 0) {
            sb.append("+-> ");
        }
        
        if(node != null) {
            sb.append(node.toString());
            LOG.finest(sb.toString());
            
            for(int i = 0; i != node.getChildren().size(); i++) {
                boolean[] childCrossings = Arrays.copyOf(crossings, crossings.length + 1);
                childCrossings[childCrossings.length - 1] = (i < node.getChildren().size() - 1);
                
                printTree(node.getChildren().get(i), childCrossings);
            }
        } else {
            LOG.finest("null");
        }
    }
}
