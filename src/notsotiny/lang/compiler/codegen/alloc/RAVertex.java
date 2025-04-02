package notsotiny.lang.compiler.codegen.alloc;

import java.util.Set;

/**
 * A register class tree vertex
 * @param classes The classes contained in this vertex
 * @param parent The parent vertex of this vertex, or null
 */
public record RAVertex(Set<RARegisterClass> classes, RAVertex parent) {
    
    // record for convenience but we dont want expensive equals/hashCode
    
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
    
    @Override
    public boolean equals(Object other) {
        return this == other;
    }
    
}
