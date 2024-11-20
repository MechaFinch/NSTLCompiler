package notsotiny.lang.compiler.irgen.context;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Holds information about names in hierarchical context
 */
public class ASTContextTree {
    
    protected ASTContextTree parent;
    
    protected Deque<ASTContextEntry> entries;
    
    /**
     * @param parent
     */
    public ASTContextTree(ASTContextTree parent) {
        this.parent = parent;
        
        this.entries = new ArrayDeque<>();
    }
    
    /**
     * Add an entry to this local context
     * @param entry
     */
    public void addEntry(ASTContextEntry entry) {
        this.entries.addFirst(entry);
    }
    
    /**
     * Returns true if a label with the given name exists in this context
     * @param name
     * @return
     */
    public boolean labelExists(String name) {
        for(ASTContextEntry ace : this.entries) {
            if(ace.nameMatches(name) && ace instanceof ASTContextLabel) {
                return true;
            }
        }
        
        if(this.parent != null) {
            return this.parent.labelExists(name);
        }
        
        return false;
    }
    
    /**
     * Returns the most-local label with the given name in this context
     * @param name
     * @return
     */
    public ASTContextLabel getLabel(String name) {
        for(ASTContextEntry ace : this.entries) {
            if(ace.nameMatches(name) && ace instanceof ASTContextLabel acl) {
                return acl;
            }
        }
        
        if(this.parent != null) {
            return this.parent.getLabel(name);
        }
        
        return null;
    }
    
    /**
     * Returns the most-local label in this context
     * @return
     */
    public ASTContextLabel getLastLabel() {
        for(ASTContextEntry ace : this.entries) {
            if(ace instanceof ASTContextLabel acl) {
                return acl;
            }
        }
        
        if(this.parent != null) {
            return this.parent.getLastLabel();
        }
        
        return null;
    }
    
    /**
     * Returns true if a variable with the given name exists in this context
     * @param name
     * @return
     */
    public boolean variableExists(String name) {
        if(localVariableExists(name)) {
            return true;
        }
        
        if(this.parent != null) {
            return this.parent.variableExists(name);
        }
        
        return false;
    }
    
    /**
     * Returns true if a variable with the given name exists in the local context
     * @param name
     * @return
     */
    public boolean localVariableExists(String name) {
        for(ASTContextEntry ace : this.entries) {
            if(ace.nameMatches(name) && ace instanceof ASTContextVariable) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Gets the most-local variable with the given name
     * @param name
     * @return
     */
    public ASTContextVariable getVariable(String name) {
        for(ASTContextEntry ace : this.entries) {
            if(ace.nameMatches(name) && ace instanceof ASTContextVariable acv) {
                return acv;
            }
        }
        
        if(this.parent != null) {
            return this.parent.getVariable(name);
        }
        
        return null;
    }
    
    /**
     * Returns true if a constant with the given name exists
     * @param name
     * @return
     */
    public boolean constantExists(String name) {
        if(localConstantExists(name)) {
            return true;
        }
        
        if(this.parent != null) {
            return this.parent.constantExists(name);
        }
        
        return false;
    }
    
    /**
     * Returns true if a constant with the given name exists in the local context
     * @param name
     * @return
     */
    public boolean localConstantExists(String name) {
        for(ASTContextEntry ace : this.entries) {
            if(ace.nameMatches(name) && ace instanceof ASTContextConstant) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Returns the most-local constant with the given name
     * @param name
     * @return
     */
    public ASTContextConstant getConstant(String name) {
        for(ASTContextEntry ace : this.entries) {
            if(ace.nameMatches(name) && ace instanceof ASTContextConstant acc) {
                return acc;
            }
        }
        
        if(this.parent != null) {
            return this.parent.getConstant(name);
        }
        
        return null;
    }
    
    /**
     * Returns true if an entry with the given name exists
     * @param name
     * @return
     */
    public boolean entryExists(String name) {
        if(localEntryExists(name)) {
            return true;
        }
        
        if(this.parent != null) {
            return this.parent.entryExists(name);
        }
        
        return false;
    }
    
    /**
     * Returns true if an entry with the given name exists in the local context
     * @param name
     * @return
     */
    public boolean localEntryExists(String name) {
        for(ASTContextEntry ace : this.entries) {
            if(ace.nameMatches(name)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Returns the most-local entry with the given name
     * @param name
     * @return
     */
    public ASTContextEntry getEntry(String name) {
        for(ASTContextEntry ace : this.entries) {
            if(ace.nameMatches(name)) {
                return ace;
            }
        }
        
        if(this.parent != null) {
            return this.parent.getEntry(name);
        }
        
        return null;
    }
    
    public ASTContextTree getParent() { return this.parent; }
    public Deque<ASTContextEntry> getEntries() { return this.entries; }
}
