package notsotiny.lang.compiler.irgen;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import fr.cenotelie.hime.redist.ParseError;
import fr.cenotelie.hime.redist.ParseResult;
import fr.cenotelie.hime.redist.parsers.InitializationException;
import notsotiny.lang.compiler.ASTUtil;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.types.AliasType;
import notsotiny.lang.compiler.types.FunctionHeader;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.PointerType;
import notsotiny.lang.compiler.types.RawType;
import notsotiny.lang.compiler.types.StringType;
import notsotiny.lang.compiler.types.StructureType;
import notsotiny.lang.compiler.types.TypeContainer;
import notsotiny.lang.compiler.types.TypedValue;
import notsotiny.lang.parser.NstlgrammarLexer;
import notsotiny.lang.parser.NstlgrammarParser;

/**
 * Parses top level code
 */
public class TopLevelParser {
    
    private static Logger LOG = Logger.getLogger(TopLevelParser.class.getName());
    private static ASTLogger ALOG = new ASTLogger(LOG);
    
    /**
     * Parses a 'program' node into an ASTModule
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
        // Track errors. By not immediately failing, more issues can be detected per compilation attempt
        boolean encounteredErrors = false;
        
        LOG.fine("----Parsing top level code of " + name + "----");
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
        LOG.fine("Parsing library inclusions");
        for(int i = 0; i < allCode.size(); i++) {
            try {
                ASTNode topNode = allCode.get(i);
                
                if(topNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_LIBRARY_INCLUSION) {
                    ASTNode headerNode = includeLibrary(module, topNode, locator);
                    
                    if(headerNode != null) {
                        allCode.addAll(headerNode.getChildren());
                    }
                }
            } catch(CompilationException e) {
                encounteredErrors = true;
            }
        }
        
        // Determine global names (types, global variables, global constants, defines) 
        LOG.fine("Parsing global names");
        for(int i = 0; i < allCode.size(); i++) {
            try {
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
            } catch(CompilationException e) {
                encounteredErrors = true;
            }
        }
        
        // Fill out function headers, type definitions, and constants, and initial values
        LOG.fine("Populating types");
        for(int i = 0; i < allCode.size(); i++) {
            try {
                ASTNode topNode = allCode.get(i);
                
                switch(topNode.getSymbol().getID()) {
                    case NstlgrammarParser.ID.VARIABLE_TYPE_ALIAS:
                        // Fill in alias
                        fillTypeAlias(module, topNode);
                        break;
                    
                    case NstlgrammarParser.ID.VARIABLE_STRUCTURE_DEFINITION:
                        // Fill in structure
                        fillStructureDefinition(module, topNode);
                        break;
                    
                    case NstlgrammarParser.ID.VARIABLE_COMPILER_DEFINITION:
                        // Fill compiler definition
                        fillCompilerDefinition(module, topNode);
                        break;
                    
                    default:
                        // No action
                }
            } catch(CompilationException e) {
                encounteredErrors = true;
            }
        }
        
        LOG.fine("Populating definitions");
        for(int i = 0; i < allCode.size(); i++) {
            try {
                ASTNode topNode = allCode.get(i);
                
                switch(topNode.getSymbol().getID()) {    
                    case NstlgrammarParser.ID.VARIABLE_VALUE_CREATION:
                        // Create a global constant or variable
                        parseGlobalCreation(module, topNode);
                        break;
                    
                    case NstlgrammarParser.ID.VARIABLE_FUNCTION_DEFINITION:
                        parseFunctionDefinition(module, topNode);
                        break;
                    
                    default:
                        // No action
                }
            } catch(CompilationException e) {
                encounteredErrors = true;
            }
        }
        
        // Finalize structure sizes & check recursion
        LOG.fine("Finalizing structure types");
        boolean sizeChanged = true;
        List<String> updatedNames = new ArrayList<>();
        while(sizeChanged) {
            sizeChanged = false;
            updatedNames.clear();
            
            for(NSTLType t : module.getTypeDefinitionMap().values()) {
                sizeChanged |= t.updateSize(updatedNames);
            }
        }
        
        for(NSTLType t : module.getTypeDefinitionMap().values()) {
            if(t instanceof StructureType st) {
                if(st.checkRecursion(new ArrayList<>())) {
                    LOG.severe("Structure " + st + " is recursive");
                    encounteredErrors = true;
                }
                
                LOG.finest("Finalized structure " + st);
            }
        }
        
        // Remove external function headers for internal functions
        Set<String> functionNames = new HashSet<>(module.getFunctionMap().keySet());
        
        for(String functionName : functionNames) {
            if(functionName.startsWith(module.getName()) && module.getFunctionMap().get(functionName).isExternal()) {
                module.getFunctionMap().remove(functionName);
            }
        }
        
        // Throw an exception if one was caught before
        if(encounteredErrors) {
            throw new CompilationException();
        }
        
        return module;
    }
    
    /**
     * Parses a function definition
     * @param module
     * @param node
     * @throws CompilationException
     */
    private static void parseFunctionDefinition(ASTModule module, ASTNode node) throws CompilationException {
        List<ASTNode> children = node.getChildren();
        ASTNode headerNode = children.get(0);
        
        if(headerNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_INTERNAL_FUNCTION_HEADER) {
            // Internal
            ASTNode codeNode = children.get(1);
            parseInternalFunctionHeader(module, headerNode, codeNode);
        } else {
            // External
            parseExternalFunctionHeader(module, headerNode);
        }
    }
    
    /**
     * Parses an internal function header.
     * @param module
     * @param node
     * @throws CompilationException
     */
    private static void parseInternalFunctionHeader(ASTModule module, ASTNode headerNode, ASTNode codeNode) throws CompilationException {
        LOG.finest("Parsing internal function header " + ASTUtil.detailed(headerNode));
        
        // Get header
        FunctionHeader header = parseFunctionHeader(module, headerNode);
        
        // Put in the map
        LOG.finer("Defined internal function header " + header);
        module.getFunctionMap().put(header.getName(), new ASTFunction(module, header, codeNode.getChildren()));
    }
    
    /**
     * Parses an external function header.
     * @param module
     * @param node
     * @throws CompilationException
     */
    private static void parseExternalFunctionHeader(ASTModule module, ASTNode node) throws CompilationException {
        LOG.finest("Parsing external function header " + ASTUtil.detailed(node));
        
        // just a header
        FunctionHeader header = parseFunctionHeader(module, node);
        
        // Put in the map
        LOG.finer("Defined external function header " + header);
        module.getFunctionMap().put(header.getName(), new ASTFunction(module, header));
    }
    
    /**
     * Parses a function header
     * @param module
     * @param node
     * @return
     * @throws CompilationException
     */
    private static FunctionHeader parseFunctionHeader(ASTModule module, ASTNode node) throws CompilationException {
        /*
         * NAME KW_NONE                 No arguments, no return
         * NAME KW_NONE type            No arguments, has return
         * NAME KW_NONE KW_NONE         No arguments, no return
         * NAME argument_list           Has arguments, no return
         * NAME argument_list KW_NONE   Has arguments, no return
         * NAME argument_list type      Has arguments, has return
         * 
         * named_argument_list -> named_argument (COMMA! named_argument)*;
         * named_argument -> type NAME;
         * nameless_argument_list -> nameless_argument (COMMA! nameless_argument)*;
         * nameless_argument ->
         *   named_argument^
         * | type;
         */
        
        List<ASTNode> children = node.getChildren();
        
        // Get name
        String name = ASTUtil.getName(children.get(0));
        
        // Get return type
        NSTLType returnType;
        
        if(children.size() == 3) {
            returnType = TypeParser.parseType(children.get(2), module, module.getContext());
        } else {
            returnType = RawType.NONE;
        }
        
        if(!(returnType instanceof RawType || returnType instanceof PointerType)) {
            ALOG.severe(node, "Invalid return type for function: " + returnType);
            throw new CompilationException();
        }
        
        // Parse arguments
        List<ASTNode> arguments = children.get(1).getChildren();
        List<String> argumentNames = new ArrayList<>();
        List<NSTLType> argumentTypes = new ArrayList<>();
        
        for(int i = 0; i < arguments.size(); i++) {
            ASTNode argumentNode = arguments.get(i);
            List<ASTNode> argumentChildren = argumentNode.getChildren();
            
            // Type is always first
            NSTLType argType = TypeParser.parseType(argumentChildren.get(0), module, module.getContext());
            
            if(!(argType instanceof RawType || argType instanceof PointerType)) {
                ALOG.severe(node, "Invalid argument type for function: " + argType);
                throw new CompilationException();
            }
            
            argumentTypes.add(argType);
            
            // named or nameless?
            if(argumentNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_NAMED_ARGUMENT) {
                argumentNames.add(ASTUtil.getNameNoLibraries(argumentChildren.get(1), ALOG, "argument name"));
            } else {
                argumentNames.add("arg" + i);
            }
        }
        
        // Assemble into header
        return new FunctionHeader(name, argumentNames, argumentTypes, returnType, node);
    }
    
    /**
     * Creates a global constant or variable
     * @param module
     * @param node
     * @throws CompilationException
     */
    private static void parseGlobalCreation(ASTModule module, ASTNode node) throws CompilationException {
        LOG.finest("Parsing global creation " + ASTUtil.detailed(node));
        
        /*
         * value_creation ->
         *   KW_VARIABLE NAME KW_IS! type SEMI!
         * | KW_VARIABLE NAME KW_IS! type KW_GETS! variable_expression SEMI!
         * | KW_CONSTANT NAME KW_IS! type KW_GETS! constant_expression SEMI!
         * | KW_CONSTANT NAME KW_IS! type KW_GETS! constant_structure SEMI!;
         * 
         * VARIABLE NAME type                       Uninitialized global variable
         * VARIABLE NAME type varaible_expression   Initialized global variable, can be treated as a constant_expression
         * CONSTANT NAME type constant_expression   Global constant (general)
         * CONSTANT NAME type constant_structure    Global constant (structure)
         */
        
        List<ASTNode> children = node.getChildren();
        
        // Get name & type
        String name = ASTUtil.getNameNoLibraries(children.get(1), ALOG, "global name");
        NSTLType type = TypeParser.parseType(children.get(2), module, module.getContext());
        
        // Variable or constant?
        if(children.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_CONSTANT) {
            // Constant
            TypedValue initialValue = ConstantParser.parseConstantExpression(children.get(3), module, module.getContext(), type, false, Level.SEVERE);
            
            LOG.finer("Defined global constant " + name + " = " + initialValue);
            module.getGlobalConstantMap().put(name, initialValue);
        } else {
            // Variable
            TypedValue initialValue;
            
            // Do we have an initial value?
            if(children.size() == 4) {
                // Yes. Get it.
                initialValue = ConstantParser.parseConstantExpression(children.get(3), module, module.getContext(), type, false, Level.SEVERE);
            } else {
                // No.
                initialValue = new TypeContainer(type);
            }
            
            // Add to map
            LOG.finer("Defined global variable " + name + " = " + initialValue);
            module.getGlobalVariableMap().put(name, initialValue);
        }
    }
    
    /**
     * Fill out a structure definition
     * @param module
     * @param node
     * @throws CompilationException
     */
    private static void fillStructureDefinition(ASTModule module, ASTNode node) throws CompilationException {
        String name = ASTUtil.getNameNoLibraries(node.getChildren().get(0), ALOG, "structure name");
        List<ASTNode> members = node.getChildren().get(1).getChildren();
        List<String> memberNames = new ArrayList<>();
        List<NSTLType> memberTypes = new ArrayList<>();
        
        LOG.finest("Parsing structure definition " + ASTUtil.detailed(node));
        
        for(ASTNode member : members) {
            List<ASTNode> memberChildren = member.getChildren();
            
            String memberName = ASTUtil.getNameNoLibraries(memberChildren.get(0), ALOG, "structure member");
            NSTLType memberType = TypeParser.parseType(memberChildren.get(1), module, module.getContext()).getRealType();
            
            if(memberType instanceof StringType) {
                LOG.severe("Structures cannot have string members");
                throw new CompilationException();
            }
            
            LOG.finest("Structure member " + memberName + " = " + memberType);
            
            memberNames.add(memberName);
            memberTypes.add(memberType);
        }
        
        StructureType st = (StructureType) module.getTypeDefinitionMap().get(name);
        st.addMembers(memberNames, memberTypes);
        
        LOG.finer("Defined structure " + st);
        module.getTypeDefinitionMap().put(name, st);
    }
    
    /**
     * Fill out a compiler definition
     * @param module
     * @param node
     * @throws CompilationException
     */
    private static void fillCompilerDefinition(ASTModule module, ASTNode node) throws CompilationException {
        List<ASTNode> children = node.getChildren();
        
        // Get name
        String defName = ASTUtil.getNameNoLibraries(children.get(0), ALOG, "definition name");
        
        // Get value
        TypedValue tv = ConstantParser.parseConstantExpression(children.get(1), module, module.getContext(), RawType.NONE, false, Level.SEVERE);
        
        // Set definition
        LOG.finer("Defined compiler defintion " + defName + " = " + tv);
        module.getCompilerDefinitionMap().put(defName, tv);
    }
    
    /**
     * Fill out a type alias
     * @param module
     * @param node
     * @throws CompilationException
     */
    private static void fillTypeAlias(ASTModule module, ASTNode node) throws CompilationException {
        List<ASTNode> children = node.getChildren();
        
        // Get name
        String aliasName = ASTUtil.getNameNoLibraries(children.get(0), ALOG, "type name");
        
        // Get type
        NSTLType t = TypeParser.parseType(children.get(1), module, module.getContext());
        
        // Set definition
        LOG.finer("Defined type alias " + aliasName + " = " + t);
        ((AliasType) module.getTypeDefinitionMap().get(aliasName)).setRealType(t);
    }
    
    /**
     * Add a global name
     * @param module
     * @param node
     * @param sourceType
     * @throws CompilationException 
     */
    private static void defineName(ASTModule module, ASTNode node) throws CompilationException {
        ASTNode nameNode;
        String name;
        
        if(node.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_VALUE_CREATION) {
            // the only one of the group where name isn't the first child
            nameNode = node.getChildren().get(1);
        } else {
            nameNode = node.getChildren().get(0);
        }
        
        if(node.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_EXTERNAL_FUNCTION_HEADER) {
            name = ASTUtil.getName(nameNode);
        } else {
            name = ASTUtil.getNameNoLibraries(nameNode, ALOG, "top level name");
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
        LOG.finer("Including library " + localName);
        
        // Get file name if specified
        if(children.size() == 2) {
            if(children.get(1).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_LNAME) {
                // Deprecated but I don't wanna mess with the grammar
                ALOG.severe(topNode, "Deprecated syntax: library inclusion with global name");
                throw new CompilationException("deprecated");
            }
            
            // Local name + file name
            fileName = children.get(1).getValue();
            fileName = fileName.substring(1, fileName.length() - 1); // trim quotation marks
        } else if(children.size() == 3) {
            // Deprecated but I don't wanna mess with the grammar
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
            ALOG.warning(topNode, "No header file for library " + localName + " from " + givenPath);
            // new CompilationException();
            return null;
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
            LOG.severe("InitializationException parsing header file " + headerPath);
            throw new CompilationException();
        }
    }
}
