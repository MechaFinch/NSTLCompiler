package notsotiny.lang.compiler.optimization.gvnpre;

import notsotiny.lang.ir.parts.IRValue;

/**
 * A GVN value which is not represented by an expression or phi
 * @param val Value
 */
public record GVNValue(IRValue val) implements GVNElement {
    
}
