package notsotiny.lang.compiler.irgen;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import fr.cenotelie.hime.redist.ParseError;
import fr.cenotelie.hime.redist.ParseResult;
import fr.cenotelie.hime.redist.parsers.InitializationException;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.types.AliasType;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.StructureType;
import notsotiny.lang.parser.NstlgrammarLexer;
import notsotiny.lang.parser.NstlgrammarParser;

/**
 * Parses top level code
 */
public class TopLevelParser {
    
    private static Logger LOG = Logger.getLogger(TopLevelParser.class.getName());
    private static ASTLogger ALOG = new ASTLogger(LOG);
    
    /**
     * Parses 'top_level_code' into an ASTModule
     * Parses:
     * - Type definitions
     * - Function headers
     * - Compiler definitions
     * - Globals
     * - Library inclusions 
     * @param name
     * @param code
     * @param locator
     * @return
     * @throws CompilationException 
     */
    public static ASTModule parseTopLevel(String name, ASTNode code, FileLocator locator) throws CompilationException {
        LOG.finer("Parsing top level code of " + name);
        ASTModule module = new ASTModule(name);
        
        // source + headers
        List<ASTNode> allCode = new ArrayList<>();
        allCode.addAll(code.getChildren());
        
        // Get own header
        try {
            ASTNode headerNode = getHeaderContents(Paths.get(name), name, locator);
            allCode.addAll(headerNode.getChildren());
        } catch(NoSuchFileException e) {
            // no header
        }
        
        // Do library inclusions
        LOG.finer("Parsing library inclusions");
        for(int i = 0; i < allCode.size(); i++) {
            ASTNode topNode = allCode.get(i);
            
            if(topNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_LIBRARY_INCLUSION) {
                ASTNode headerNode = includeLibrary(module, topNode, locator);
                
                if(headerNode != null) {
                    allCode.addAll(headerNode.getChildren());
                }
            }
        }
        
        // Determine global names (types, global variables, global constants, defines) 
        LOG.finer("Parsing global names");
        for(int i = 0; i < allCode.size(); i++) {
            ASTNode topNode = allCode.get(i);
            
            switch(topNode.getSymbol().getID()) {
                case NstlgrammarParser.ID.VARIABLE_TYPE_ALIAS:
                case NstlgrammarParser.ID.VARIABLE_STRUCTURE_DEFINITION:
                case NstlgrammarParser.ID.VARIABLE_COMPILER_DEFINITION:
                case NstlgrammarParser.ID.VARIABLE_VALUE_CREATION:
                    // Send directly
                    defineName(module, topNode);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_FUNCTION_DEFINITION:
                    // Need to unwrap the header first
                    defineName(module, topNode.getChildren().get(0));
                    break;
                    
                case NstlgrammarParser.ID.VARIABLE_LIBRARY_INCLUSION:
                    // No action
                    break;
                
                default:
                    LOG.severe("Unexpected top-level node: " + topNode);
                    throw new CompilationException();
            }
        }
        
        // Fill out function headers, type definitions, and constants
        for(int i = 0; i < allCode.size(); i++) {
            ASTNode topNode = allCode.get(i);
            
            /*
             * Prerequisites
             * In their own classes
             * TODO type parsing
             * TODO constant parsing
             * Here
             * TODO structure definitions
             */
            
            switch(topNode.getSymbol().getID()) {
                
                default:
                    // No action
            }
        }
        
        return module;
    }
    
    /**
     * Add a global name
     * @param module
     * @param node
     * @param sourceType
     * @throws CompilationException 
     */
    private static void defineName(ASTModule module, ASTNode node) throws CompilationException {
        String name;
        
        if(node.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_VALUE_CREATION) {
            // the only one of the group where name isn't the first child
            name = node.getChildren().get(1).getValue();
        } else {
            name = node.getChildren().get(0).getValue();
        }
        
        if(module.nameExists(name)) {
            // Report conflict
            ALOG.severe(node, "Duplicate top-level name: " + name);
            throw new CompilationException();
        } else {
            // Add name
            switch(node.getSymbol().getID()) {
                case NstlgrammarParser.ID.VARIABLE_TYPE_ALIAS:
                    LOG.finest("Named type alias " + name);
                    module.getTypeDefinitionMap().put(name, new AliasType(name));
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_STRUCTURE_DEFINITION:
                    LOG.finest("Named structure type " + name);
                    module.getTypeDefinitionMap().put(name, new StructureType(name));
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_INTERNAL_FUNCTION_HEADER:
                    LOG.finest("Named internal function " + name);
                    module.getFunctionMap().put(name, new ASTFunction(module, false));
                    break;
                    
                case NstlgrammarParser.ID.VARIABLE_EXTERNAL_FUNCTION_HEADER:
                    LOG.finest("Named external function " + name);
                    module.getFunctionMap().put(name, new ASTFunction(module, true));
                    break;
                    
                case NstlgrammarParser.ID.VARIABLE_COMPILER_DEFINITION:
                    LOG.finest("Named compiler definition " + name);
                    module.getCompilerDefinitionMap().put(name, null);
                    break;
                    
                case NstlgrammarParser.ID.VARIABLE_VALUE_CREATION:
                    if(node.getChildren().get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_VARIABLE) {
                        LOG.finest("Named global variable " + name);
                        module.getGlobalVariableMap().put(name, null);
                    } else {
                        LOG.finest("Named global constant " + name);
                        module.getGlobalConstantMap().put(name, null);
                    }
                    break;
                
                default:
                    // not possible
            }
        }
    }
    
    /**
     * Includes the contents of a library header. If a library has already been included, returns null.
     * @param topNode
     * @param locator
     * @return
     * @throws CompilationException 
     */
    private static ASTNode includeLibrary(ASTModule module, ASTNode topNode, FileLocator locator) throws CompilationException {
        /*
         * library_inclusion ->
         *   KW_LIBRARY! LNAME KW_IS! LNAME KW_FROM! STRING SEMI!   < Deprecate
         * | KW_LIBRARY! LNAME KW_IS! LNAME SEMI!                   < Deprecate
         * | KW_LIBRARY! LNAME KW_FROM! STRING SEMI!
         * | KW_LIBRARY! LNAME SEMI!;
         * 
         * LNAME STRING         Local name, file name
         * LNAME                Local name
         */
        List<ASTNode> children = topNode.getChildren();
        
        String localName, fileName;
        
        // Get local name
        localName = children.get(0).getValue().substring(1);
        fileName = localName;
        LOG.finest("Including library " + localName);
        
        // Get file name if specified
        if(children.size() == 2) {
            if(children.get(1).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_LNAME) {
                // Deprecated but I don't have access to the parser generator
                ALOG.severe(topNode, "Deprecated syntax: library inclusion with global name");
                throw new CompilationException("deprecated");
            }
            
            // Local name + file name
            fileName = children.get(1).getValue();
            fileName = fileName.substring(1, fileName.length() - 1); // trim quotation marks
        } else if(children.size() == 3) {
            // Deprecated but I don't have access to the parser generator
            ALOG.severe(topNode, "Deprecated syntax: library inclusion with global name");
            throw new CompilationException("deprecated");
        } // size 1 = fine
        
        // do we have this already?
        Map<Path, String> libraryFileMap = module.getLibraryFileMap();
        if(libraryFileMap.containsValue(localName)) {
            LOG.finest("Library already included");
            return null;
        }
        
        // get file
        Path givenPath = Paths.get(fileName);
        if(!locator.addFile(givenPath)) {
            ALOG.severe(topNode, "Could not find source file for library " + localName);
            throw new CompilationException();
        }
        
        try {
            Path truePath = locator.getSourceFile(givenPath);
            libraryFileMap.put(truePath, localName);
            
            LOG.finest("Included library " + localName + " from " + truePath);
            
            return getHeaderContents(givenPath, localName, locator);
        } catch(NoSuchFileException e) {
            ALOG.severe(topNode, "No header file for library " + localName + " from " + givenPath);
            throw new CompilationException();
        }
    }
    
    /**
     * Gets the contents of a header file
     * @param givenPath
     * @param libName
     * @param locator
     * @return
     * @throws NoSuchFileException 
     * @throws CompilationException 
     */
    private static ASTNode getHeaderContents(Path givenPath, String libName, FileLocator locator) throws NoSuchFileException, CompilationException {
        LOG.finest("Getting header file for " + libName + " from " + givenPath);
        
        Path headerPath;
        
        try {
            headerPath = locator.getHeaderFile(givenPath);
        } catch(NoSuchFileException e) {
            LOG.finest("Could not find header file.");
            throw e;
        }
        
        try {
            NstlgrammarLexer lexer = new NstlgrammarLexer(new InputStreamReader(Files.newInputStream(headerPath)));
            NstlgrammarParser parser = new NstlgrammarParser(lexer);
            
            ParseResult result = parser.parse();
            
            if(result.getErrors().size() != 0) {
                LOG.severe("Encountered errors parsing " + headerPath);
                for(ParseError pe : result.getErrors()) {
                    LOG.severe("ParseError: " + pe);
                }
                
                throw new CompilationException();
            } else {
                return result.getRoot();
            }
        } catch(IOException e) {
            LOG.severe("IOException parsing header file " + headerPath);
            throw new CompilationException();
        } catch(InitializationException e) {
            // TODO Auto-generated catch block
            LOG.severe("InitializationException parsing header file " + headerPath);
            throw new CompilationException();
        }
    }
}
