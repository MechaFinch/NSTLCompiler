package notsotiny.lang.compiler.irgen;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.TypedValue;

/**
 * Contains module data from the AST
 */
public class ASTModule {
    
    // The name of this module
    private String name;
    
    // Maps library path objects to local names
    private Map<Path, String> libraryFileMap;
    
    // Maps names to values
    private Map<String, TypedValue> compilerDefinitionMap;
    
    // Maps names to types
    private Map<String, NSTLType> typeDefintionMap;
    
    // Maps names to functions
    private Map<String, ASTFunction> functionMap;
    
    // Maps names to global constants
    private Map<String, TypedValue> globalConstantMap;
    
    // Maps names to the initial values of global variables
    private Map<String, TypedValue> globalVariableMap;
    
    /**
     * @param name
     */
    public ASTModule(String name) {
        this.name = name;
        this.libraryFileMap = new HashMap<>();
        this.compilerDefinitionMap = new HashMap<>();
        this.typeDefintionMap = new HashMap<>();
        this.functionMap = new HashMap<>();
        this.globalConstantMap = new HashMap<>();
        this.globalVariableMap = new HashMap<>();
    }
    
    /**
     * Returns true if the name is present in the module
     * @param name
     * @return
     */
    public boolean nameExists(String name) {
        if(this.functionMap.containsKey(name) || this.typeDefintionMap.containsKey(name) || compilerDefinitionMap.containsKey(name) || this.globalConstantMap.containsKey(name) || this.globalVariableMap.containsKey(name)) {
            return true;
        } else {
            return false;
        }
    }
    
    public String getName() { return this.name; }
    public Map<Path, String> getLibraryFileMap() { return this.libraryFileMap; }
    public Map<String, TypedValue> getCompilerDefinitionMap() { return this.compilerDefinitionMap; }
    public Map<String, NSTLType> getTypeDefinitionMap() { return this.typeDefintionMap; }
    public Map<String, ASTFunction> getFunctionMap() { return this.functionMap; }
    public Map<String, TypedValue> getGlobalConstantMap() { return this.globalConstantMap; }
    public Map<String, TypedValue> getGlobalVariableMap() { return this.globalVariableMap; }
    
}
