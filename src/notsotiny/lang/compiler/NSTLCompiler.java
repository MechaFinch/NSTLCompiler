package notsotiny.lang.compiler;

import java.io.BufferedOutputStream;
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
import notsotiny.lang.compiler.codegen.CodeGenV1;
import notsotiny.lang.compiler.codegen.CodeGenerator;
import notsotiny.lang.compiler.codegen.EmptyCodeGenerator;
import notsotiny.lang.compiler.compilers.IRCompiler;
import notsotiny.lang.compiler.irgen.IRGenV1;
import notsotiny.lang.compiler.irgen.IRGenerator;
import notsotiny.lang.compiler.optimization.IROptV1;
import notsotiny.lang.compiler.optimization.IROptimizationLevel;
import notsotiny.lang.compiler.optimization.IROptimizer;
import notsotiny.lang.compiler.shitty.SAPCompiler;
import notsotiny.lang.parser.NstlgrammarLexer;
import notsotiny.lang.parser.NstlgrammarParser;

public class NSTLCompiler {
    
    private static Logger LOG = Logger.getLogger(NSTLCompiler.class.getName());
    
    public static void main(String[] args) throws IOException, InitializationException {
        // handle arguments
        if(args.length < 1 || args.length > 16) {
            System.out.println("Usage: NSTLCompiler [options] <input file>");
            System.out.println("Flags:");
            System.out.println("\t-d\t\t\tEnable debug-friendly object files. If enabled, references are verbose and files much larger.");
            System.out.println("\t-x <exec file>\t\tExec File. Specifies the .oex file to output. Default <input file>.oex");
            System.out.println("\t-e <entry function>\tEntry. Specifies an entry function. Default main");
            System.out.println("\t-o <output directory>\tOutput. Specifies the location of output object files. Default <working directory>\\out");
            System.out.println("\t-c <compiler name>\tCompiler. Specifies which compiler variant to use. Options: shit, ir. Default: ir");
            System.out.println("\t-cfg <type>\t\tShow function CFGs. Type = ast, uir, oir");
            System.out.println("\t-dag <type>\t\tShow basic block DAGs. Type = isel");
            System.out.println("\t-irfu <output directory>\tUnoptimized IR Output. Specifies where to output unoptimized IR and enables unoptimized IR file output");
            System.out.println("\t-irfi <output directory>\tIntermediate IR Output. Specifies where to output intermediate IR during optimization and enables intermediate IR file output");
            System.out.println("\t-irfo <output directory>\tOptimized IR Output. Specifies where to output optimzied IR and enables optimized IR file output");
            System.out.println("\t-asmfa <output directory>\tAbstract Assembly Output. Specifies where to output abstract assembly and enbles abstract assembly file output");
            System.out.println("\t-asmfo <output directory>\tFinal Assembly Output. Specifies where to output assembly and enables assembly file output");
            return; 
        }
        
        int flagCount = 0;
        boolean debug = false,
                showASTCFG = false,
                showUIRCFG = false,
                showIIRCFG = false,
                showOIRCFG = false,
                hasExecFile = false,
                hasOutputDir = false,
                hasUIROutputDir = false,
                hasIIROutputDir = false,
                hasOIROutputDir = false,
                hasAASMOutputDir = false,
                hasFASMOutputDir = false,
                showISelDAG = false;
        
        String inputFileArg = "",
               execFileArg = "",
               outputArg = "",
               uirOutputArg = "",
               iirOutputArg = "",
               oirOutputArg = "",
               aasmOutputArg = "",
               fasmOutputArg = "",
               compilerName = "ir",
               entry = "main";
        
        out:
        while(true) {
            switch(args[flagCount]) {
                case "-d":
                    flagCount++;
                    debug = true;
                    break;
                
                case "-irfu":
                    flagCount += 2;
                    hasUIROutputDir = true;
                    uirOutputArg = args[flagCount - 1];
                    break;
                    
                case "-irfi":
                    flagCount += 2;
                    hasIIROutputDir = true;
                    iirOutputArg = args[flagCount - 1];
                    break;
                
                case "-irfo":
                    flagCount += 2;
                    hasOIROutputDir = true;
                    oirOutputArg = args[flagCount - 1];
                    break;
                    
                case "-asmfa":
                    flagCount += 2;
                    hasAASMOutputDir = true;
                    aasmOutputArg = args[flagCount - 1];
                    break;
                
                case "-asmfo":
                    flagCount += 2;
                    hasFASMOutputDir = true;
                    fasmOutputArg = args[flagCount - 1];
                    break;
                
                case "-cfg":
                    flagCount += 2;
                    if(args[flagCount - 1].equals("ast")) {
                        showASTCFG = true;
                    } else if(args[flagCount - 1].equals("uir")) {
                        showUIRCFG = true;
                    } else if(args[flagCount - 1].equals("iir")) {
                        showIIRCFG = true;
                    } else if(args[flagCount - 1].equals("oir")) {
                        showOIRCFG = true;
                    }
                    break;
                
                case "-dag":
                    flagCount += 2;
                    if(args[flagCount - 1].equals("isel")) {
                        showISelDAG = true;
                    }
                
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
             uirOutDir = hasUIROutputDir ? Paths.get(uirOutputArg) : null,
             iirOutDir = hasIIROutputDir ? Paths.get(iirOutputArg) : null,
             oirOutDir = hasOIROutputDir ? Paths.get(oirOutputArg) : null,
             aasmOutDir = hasAASMOutputDir ? Paths.get(aasmOutputArg) : null,
             fasmOutDir = hasFASMOutputDir ? Paths.get(fasmOutputArg) : null,
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
                        case "ir"   -> {
                            IRGenerator generator = new IRGenV1();
                            generator.setCFGVisualization(showASTCFG, showUIRCFG);
                            
                            if(hasUIROutputDir) {
                                // make the output directory if it doesn't exist
                                if(!Files.exists(uirOutDir)) {
                                    LOG.finest(() -> "Creating output directory " + uirOutDir);
                                    Files.createDirectory(uirOutDir);
                                }
                                
                                generator.setFileOutput(true, uirOutDir);
                            }
                            
                            IROptimizer optimizer = new IROptV1();
                            optimizer.setCFGVisualization(showIIRCFG, showOIRCFG);
                            
                            if(hasOIROutputDir) {
                                // make the output directory if it doesn't exist
                                if(!Files.exists(oirOutDir)) {
                                    LOG.finest(() -> "Creating output directory " + oirOutDir);
                                    Files.createDirectory(oirOutDir);
                                }
                                
                                optimizer.setFileOutput(true, oirOutDir);
                            }
                            
                            if(hasIIROutputDir) {
                                // make the intermediate output directory if it doesn't exist
                                if(!Files.exists(iirOutDir)) {
                                    LOG.finest(() -> "Creating output directory" + iirOutDir);
                                    Files.createDirectory(iirOutDir);
                                }
                                
                                optimizer.setIntermediateOutput(true, iirOutDir);
                            }
                            
                            // TODO: set level from arg
                            optimizer.setLevel(IROptimizationLevel.THREE);
                            
                            CodeGenerator codegen = new CodeGenV1();
                            codegen.setDAGVisualization(showISelDAG);
                            
                            if(hasAASMOutputDir) {
                                // make the abstract assembly output directory if it doesn't exist
                                if(!Files.exists(aasmOutDir)) {
                                    LOG.finest(() -> "Creating output directory" + aasmOutDir);
                                    Files.createDirectory(aasmOutDir);
                                }
                                
                                codegen.setAbstractOutput(true, aasmOutDir);
                            }
                            
                            yield new IRCompiler(generator, optimizer, new EmptyCodeGenerator());
                        }
                        case "shit" -> new SAPCompiler();
                        default     -> throw new IllegalArgumentException("Unknown compiler: " + compilerName);
                    };
                    
                    if(hasFASMOutputDir) {
                        // make the final assembly output directory if it doesn't exist
                        if(!Files.exists(fasmOutDir)) {
                            LOG.finest(() -> "Creating output directory" + fasmOutDir);
                            Files.createDirectory(fasmOutDir);
                        }
                    }
                    
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
                            // Compile!
                            AssemblyObject obj = comp.compile(root, libname, locator, workingFile);
                            
                            // output AssemblyObject if applicable
                            if(hasFASMOutputDir) {
                                String fileNameName = fileName.substring(0, fileName.lastIndexOf("."));
                                Path fasmOutputFile = fasmOutDir.resolve(fileNameName + ".asm");
                                
                                LOG.info("Writing final assembly to " + fasmOutputFile);
                                
                                try(BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(fasmOutputFile))) {
                                    obj.print(bos);
                                } catch(IOException e) {}
                            }
                            
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
