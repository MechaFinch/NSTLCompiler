package notsotiny.lang.ir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A group of functions & globals which translates to a single obj file.
 * 
 * @author Mechafinch
 */
public class IRModule implements IRSourceInfo {
    
    private Path sourcePath;
    
    // Library name
    private String moduleName;
    
    // Functions
    private Map<IRIdentifier, IRFunction> functions;
    private Map<IRIdentifier, IRFunction> internalFunctions;
    private Map<IRIdentifier, IRFunction> externalFunctions;
    
    // Globals
    private Map<IRIdentifier, IRGlobal> globals;
    
    /**
     * Full constructor
     * @param name
     * @param functions
     * @param globals
     * @param sourcePath
     */
    public IRModule(String name, Map<IRIdentifier, IRFunction> functions, Map<IRIdentifier, IRGlobal> globals, Path sourcePath) {
        this.moduleName = name;
        this.functions = functions;
        this.globals = globals;
        this.sourcePath = sourcePath;
        
        this.internalFunctions = new HashMap<>();
        this.externalFunctions = new HashMap<>();
        
        for(Entry<IRIdentifier, IRFunction> e : functions.entrySet()) {
            if(e.getValue().isExternal()) {
                this.externalFunctions.put(e.getKey(), e.getValue());
            } else {
                this.internalFunctions.put(e.getKey(), e.getValue());
            }
        }
    }
    
    /**
     * Full constructor, no source info
     * @param name
     * @param functions
     * @param globals
     */
    public IRModule(String name, Map<IRIdentifier, IRFunction> functions, Map<IRIdentifier, IRGlobal> globals) {
        this(name, functions, globals, null);
    }
    
    /**
     * Empty constructor
     * @param name
     * @param sourcePath
     */
    public IRModule(String name, Path sourcePath) {
        this(name, new HashMap<>(), new HashMap<>(), sourcePath);
    }
    
    /**
     * Empty constructor, no source info
     * @param name
     */
    public IRModule(String name) {
        this(name, new HashMap<>(), new HashMap<>(), null);
    }
    
    /**
     * Add a function
     * @param func
     */
    public void addFunction(IRFunction func) {
        this.functions.put(func.getID(), func);
        
        if(func.isExternal()) {
            this.externalFunctions.put(func.getID(), func);
        } else {
            this.internalFunctions.put(func.getID(), func);
        }
    }
    
    /**
     * Add a global
     * @param global
     */
    public void addGlobal(IRGlobal global) {
        this.globals.put(global.getID(), global);
    }
    
    @Override
    public Path getSourceFile() {
        return this.sourcePath;
    }

    @Override
    public int getSourceLineNumber() {
        return 0; // A module (likely) corresponds to an entire file
    }
    
    public String getName() { return this.moduleName; }
    public Map<IRIdentifier, IRFunction> getFunctions() { return this.functions; }
    public Map<IRIdentifier, IRFunction> getInternalFunctions() { return this.internalFunctions; }
    public Map<IRIdentifier, IRFunction> getExternalFunctions() { return this.externalFunctions; }
    public Map<IRIdentifier, IRGlobal> getGlobals() { return this.globals; }
    
    @Override
    public String toString() {
        return this.moduleName;
    }
    
}
