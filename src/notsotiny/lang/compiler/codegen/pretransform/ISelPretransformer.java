package notsotiny.lang.compiler.codegen.pretransform;

import notsotiny.lang.ir.parts.IRFunction;

/**
 * A transformation to be performed on an IR function before generating its code
 */
public interface ISelPretransformer {
    
    /**
     * Perform the transformer's transformation on a function
     * @param function
     */
    public void transform(IRFunction function);
    
}
