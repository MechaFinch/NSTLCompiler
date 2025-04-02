package notsotiny.lang.compiler.aasm;

import java.util.Set;

import notsotiny.lang.ir.parts.IRIdentifier;

/**
 * Indicates which locals are live at this point. Inserted during inter-block scheduling. 
 * @param localSet Set of locals which are live at this point
 * @param stackSet Set of stack slots which are live at this point
 * @param isDef true if this is a 'define'/live-in of the locals in the set, false if this is a 'use'/live-out of the locals in the set
 */
public record AASMLiveSet(Set<IRIdentifier> localSet, Set<IRIdentifier> stackSet, boolean isDef) implements AASMPart {
    
    @Override
    public String toString() {
        return "LIVE " + this.localSet + " " + stackSet;
    }
    
}
