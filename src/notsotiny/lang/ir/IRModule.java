package notsotiny.lang.ir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    private List<IRFunction> functions;
    
    // Globals
    private List<IRGlobal> globals;
    
    /**
     * Full constructor
     * @param name
     * @param functions
     * @param globals
     * @param sourcePath
     */
    public IRModule(String name, List<IRFunction> functions, List<IRGlobal> globals, Path sourcePath) {
        this.moduleName = name;
        this.functions = functions;
        this.globals = globals;
        this.sourcePath = sourcePath;
    }
    
    /**
     * Full constructor, no source info
     * @param name
     * @param functions
     * @param globals
     */
    public IRModule(String name, List<IRFunction> functions, List<IRGlobal> globals) {
        this(name, functions, globals, null);
    }
    
    /**
     * Empty constructor
     * @param name
     * @param sourcePath
     */
    public IRModule(String name, Path sourcePath) {
        this(name, new ArrayList<>(), new ArrayList<>(), sourcePath);
    }
    
    /**
     * Empty constructor, no source info
     * @param name
     */
    public IRModule(String name) {
        this(name, new ArrayList<>(), new ArrayList<>(), null);
    }
    
    /**
     * Add a function
     * @param func
     */
    public void addFunction(IRFunction func) {
        this.functions.add(func);
    }
    
    /**
     * Add a global
     * @param global
     */
    public void addGlobal(IRGlobal global) {
        this.globals.add(global);
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
    public List<IRFunction> getFunctionsList() { return this.functions; }
    public List<IRGlobal> getGlobalsList() { return this.globals; }
    
}
