package notsotiny.lang.compiler.aasm;

/**
 * A label in the AbstractAssembly
 * Used for the tiny jumps found in wide comparisons
 * Used as both jump argument and in-code marker
 * A branch with an AAMLabel as the argument branches to the next AASMLabel with the
 * name found after the branch instruction
 */
public record AASMLabel(String name) implements AASMPart {
    
    /**
     * @return Name appropriate for AssemblyComponents
     */
    public String acName(String functionName) {
        if(this.name.startsWith("$")) {
            return functionName + "." + this.name.substring(1);
        } else {
            return this.name;
        }
    }
    
    @Override
    public String toString() {
        return this.name + ":";
    }
    
}
