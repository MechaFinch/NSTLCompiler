package notsotiny.lang.compiler.aasm;

import java.util.Set;

import notsotiny.lang.ir.parts.IRIdentifier;

/**
 * Indicates which locals are live at this point. Inserted during inter-block scheduling. 
 */
public record AASMLiveSet(Set<IRIdentifier> liveSet) implements AASMPart {
    
    @Override
    public String toString() {
        return "LIVE " + this.liveSet;
    }
    
}
