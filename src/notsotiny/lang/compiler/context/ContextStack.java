package notsotiny.lang.compiler.context;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;

public class ContextStack {
    
    private static Logger LOG = Logger.getLogger(ContextStack.class.getName());
    
    private Deque<ContextEntry> contextStack;
    
    private int contextCounter;
    
    public ContextStack(ContextMarker baseContext) {
        this.contextStack = new ArrayDeque<>();      // tracks contexts and symbol names
        this.contextStack.push(baseContext);
        this.contextCounter = 0;
    }
    
    /**
     * Returns true if a symbol with the name exists in the current context
     * 
     * @param name
     * @return
     */
    public boolean hasLocalSymbol(String name) {
        for(ContextEntry ce : contextStack) {
            if(ce instanceof ContextSymbol cs && cs.getName().equals(name)) {
                return true;
            } else if(ce instanceof ContextMarker) {
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Returns true if a symbol with the name exists
     * 
     * @param name
     * @return
     */
    public boolean hasSymbol(String name) {
        for(ContextEntry ce : contextStack) {
            if(ce instanceof ContextSymbol cs && cs.getName().equals(name))
                return true;
        }
        
        return false;
    }
    
    /**
     * Get the first symbol with the given name, or null
     * 
     * @return
     */
    public ContextSymbol getSymbol(String name) {
        // search
        for(ContextEntry ce : contextStack) {
            if(ce instanceof ContextSymbol cs && cs.getName().equals(name)) {
                return cs;
            }
        }
        
        return null;
    }
    
    /**
     * Gets the local context marker
     * 
     * @return
     */
    public ContextMarker getLocalMarker() {
        for(ContextEntry ce : contextStack) {
            if(ce instanceof ContextMarker cm) {
                return cm;
            }
        }
        
        return null; // a correctly used contextstack will never return null
    }
    
    /**
     * Pushes a symbol
     * 
     * @param cs
     */
    public void pushSymbol(ContextSymbol cs) {
        LOG.finest("Pushed symbol " + cs);
        this.contextStack.push(cs);
    }
    
    /**
     * Begins a new context
     */
    public void pushContext() {
        LOG.finest("Pushing context");
        this.contextCounter++;
        
        // search for last marker to duplicate
        for(ContextEntry ce : contextStack) {
            if(ce instanceof ContextMarker cm) {
                contextStack.push(cm.duplicate());
                break;
            }
        }
    }
    
    /**
     * Pops the current context
     * 
     * @return Popped ContextMarker or null
     */
    public ContextMarker popContext() {
        LOG.finest("Popped context");
        this.contextCounter--;
        
        // search for the last ContextMarker and pop it
        while(!contextStack.isEmpty() && contextStack.peek() instanceof ContextSymbol) contextStack.pop();
        
        if(contextStack.isEmpty()) return null;
        
        return (ContextMarker) contextStack.pop();
    }
    
    /**
     * @return The raw context stack
     */
    public Deque<ContextEntry> getStack() { return this.contextStack; }
    public int getContextCounter() { return this.contextCounter; }
}
