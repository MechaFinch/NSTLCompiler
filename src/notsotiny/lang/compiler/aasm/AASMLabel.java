package notsotiny.lang.compiler.aasm;

/**
 * A label in the AbstractAssembly
 * Used for the tiny jumps found in wide comparisons
 * Used as both jump argument and in-code marker
 * A branch with an AAMLabel as the argument branches to the next AASMLabel with the
 * name found after the branch instruction
 */
public record AASMLabel(String name) implements AASMPart {
    
    @Override
    public String toString() {
        return this.name + ":";
    }
    
}
