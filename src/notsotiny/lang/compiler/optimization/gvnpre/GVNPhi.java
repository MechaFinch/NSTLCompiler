package notsotiny.lang.compiler.optimization.gvnpre;

import java.util.Map;

import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lib.data.Pair;

/**
 * A GVN value for a phi-node. While the IR uses BB args, phi is a more useful
 * representation in this context.
 * @param mappedValueNumbers Map from predecessor ID to mapped value number(s)
 */
public record GVNPhi(IRType type, Map<IRIdentifier, Pair<Integer, Integer>> mappedValueNumbers) implements GVNElement {
    
}
