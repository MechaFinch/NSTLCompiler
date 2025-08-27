package notsotiny.lang.compiler.irgen;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import notsotiny.asm.resolution.ResolvableConstant;
import notsotiny.lang.compiler.ParseUtils;
import notsotiny.lang.compiler.irgen.context.ASTContextConstant;
import notsotiny.lang.compiler.irgen.context.ASTContextEntry;
import notsotiny.lang.compiler.irgen.context.ASTContextTree;
import notsotiny.lang.compiler.irgen.context.ASTContextVariable;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.RawType;
import notsotiny.lang.compiler.types.StringType;
import notsotiny.lang.compiler.types.TypedRaw;
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
    private Map<String, NSTLType> typeDefinitionMap;
    
    // Maps names to functions
    private Map<String, ASTFunction> functionMap;
    
    // Maps names to global constants
    private Map<String, TypedValue> globalConstantMap;
    
    // Maps names to the initial values of global variables
    private Map<String, TypedValue> globalVariableMap;
    
    // Holds information about locals
    private ASTContextTree context;
    
    /**
     * @param name
     */
    public ASTModule(String name) {
        this.name = name;
        this.libraryFileMap = new HashMap<>();
        this.compilerDefinitionMap = new HashMap<>();
        this.typeDefinitionMap = new HashMap<>();
        this.functionMap = new HashMap<>();
        this.globalConstantMap = new HashMap<>();
        this.globalVariableMap = new HashMap<>();
        this.context = new ASTContextTree(null);
        
        // Setup default types
        this.typeDefinitionMap.put("u8", RawType.U8);
        this.typeDefinitionMap.put("u16", RawType.U16);
        this.typeDefinitionMap.put("u32", RawType.U32);
        this.typeDefinitionMap.put("i8", RawType.I8);
        this.typeDefinitionMap.put("i16", RawType.I16);
        this.typeDefinitionMap.put("i32", RawType.I32);
        this.typeDefinitionMap.put("char", RawType.U8);
        this.typeDefinitionMap.put("boolean", RawType.BOOLEAN);
        this.typeDefinitionMap.put("string", new StringType(""));
        this.typeDefinitionMap.put("ptr", RawType.U32);
        
        // Default constant
        this.context.addEntry(new ASTContextConstant("true", "true", ParseUtils.TRUE_TR));
        this.context.addEntry(new ASTContextConstant("false", "false", ParseUtils.FALSE_TR));
    }
    
    /**
     * Returns true if the name is present in the module
     * @param name
     * @return
     */
    public boolean nameExists(String name) {
        if(this.functionMap.containsKey(name) || this.typeDefinitionMap.containsKey(name) ||
           compilerDefinitionMap.containsKey(name) || this.globalConstantMap.containsKey(name) ||
           this.globalVariableMap.containsKey(name) || this.context.entryExists(name)) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Returns true if the name is present in the given context
     * @param name
     * @param context
     * @return
     */
    public boolean nameExists(String name, ASTContextTree context) {
        if(context.entryExists(name)) {
            return true;
        }
        
        return nameExists(name);
    }
    
    /**
     * Returns true if the name is present and represents a constant
     * @param name
     * @return
     */
    public boolean constantExists(String name) {
        if(this.compilerDefinitionMap.containsKey(name) || this.globalConstantMap.containsKey(name) ||
           this.context.getEntry(name) instanceof ASTContextConstant) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Returns true if the name is present in the given context and represents a constant
     * @param name
     * @param context
     * @return
     */
    public boolean constantExists(String name, ASTContextTree context) {
        if(context.constantExists(name)) {
            return true;
        }
        
        return constantExists(name);
    }
    
    /**
     * Returns true if the name is present and represents a variable
     * @param name
     * @return
     */
    public boolean variableExists(String name) {
        if(this.globalVariableMap.containsKey(name) || this.context.getEntry(name) instanceof ASTContextVariable) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Returns true if the nameis present in the given context and represents a variable
     * @param name
     * @param context
     * @return
     */
    public boolean variableExists(String name, ASTContextTree context) {
        if(context.variableExists(name)) {
            return true;
        }
        
        return variableExists(name);
    }
    
    /**
     * Returns true if the given library exists
     * @param name
     * @return
     */
    public boolean libraryExists(String name) {
        return this.libraryFileMap.containsValue(name);
    }
    
    /**
     * Gets the value of a constant, if it exists
     * @param name
     * @return TypedValue or null
     */
    public TypedValue getConstantValue(String name) {
        // try context
        ASTContextEntry ace = this.context.getEntry(name);
        
        if(ace != null) {
            // Local name exists
            if(ace instanceof ASTContextConstant acc) {
                // is a constant
                return acc.getValue();
            } else {
                // Not a constant
                return null;
            }
        } else {
            // Not in local names, check globals
            if(this.globalConstantMap.containsKey(name)) {
                return this.globalConstantMap.get(name);
            } else {
                return this.compilerDefinitionMap.get(name);
            }
        }
    }
    
    /**
     * Gets the value of a constant if it exists in the given context
     * @param name
     * @param context
     * @return TypedValue or null
     */
    public TypedValue getConstantValue(String name, ASTContextTree context) {
        ASTContextConstant acc = context.getConstant(name);
        
        if(acc != null) {
            return acc.getValue();
        } else {
            return getConstantValue(name);
        }
    }
    
    /**
     * Gets the type of a variable, if it exists
     * @param name
     * @return NSTLType or null
     */
    public NSTLType getVariableType(String name) {
        // try context
        ASTContextEntry ace = this.context.getEntry(name);
        
        if(ace != null) {
            // Local name exists
            if(ace instanceof ASTContextVariable acv) {
                // Is a variable
                return acv.getType();
            } else if(ace instanceof ASTContextConstant acc) {
                // Is a constant. return its type
                return acc.getValue().getType();
            } else {
                // Not a variable
                return null;
            }
        } else {
            // Not a local name, check globals
            if(this.globalVariableMap.containsKey(name)) {
                return this.globalVariableMap.get(name).getType();
            } else if(this.globalConstantMap.containsKey(name)) {
                return this.globalConstantMap.get(name).getType();
            } else {
                return this.compilerDefinitionMap.get(name).getType();
            }
        }
    }
    
    /**
     * Gets the type of a variable, if it exists
     * @param name
     * @param context
     * @return
     */
    public NSTLType getVariableType(String name, ASTContextTree context) {
        if(context.variableExists(name)) {
            return context.getVariable(name).getType();
        }
        
        return getVariableType(name);
    }
    
    public String getName() { return this.name; }
    public Map<Path, String> getLibraryFileMap() { return this.libraryFileMap; }
    public Map<String, TypedValue> getCompilerDefinitionMap() { return this.compilerDefinitionMap; }
    public Map<String, NSTLType> getTypeDefinitionMap() { return this.typeDefinitionMap; }
    public Map<String, ASTFunction> getFunctionMap() { return this.functionMap; }
    public Map<String, TypedValue> getGlobalConstantMap() { return this.globalConstantMap; }
    public Map<String, TypedValue> getGlobalVariableMap() { return this.globalVariableMap; }
    public ASTContextTree getContext() { return this.context; }
    
}
