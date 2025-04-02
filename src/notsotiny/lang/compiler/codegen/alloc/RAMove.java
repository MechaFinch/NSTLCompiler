package notsotiny.lang.compiler.codegen.alloc;

import java.util.Objects;

/**
 * Indicates a move from destination to source
 * @param destination
 * @param source
 */
public record RAMove(RAIGNode destination, RAIGNode source) {
    
    public RAMove {
        destination.addMove(this);
        source.addMove(this);
    }
    
    @Override
    public String toString() {
        return "(" + this.source.getIdentifier() + " -> " + this.destination.getIdentifier() + ")";
    }
    
    @Override
    public boolean equals(Object o) {
        return this == o;
    }
    
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
    
}
