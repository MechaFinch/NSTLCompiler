package notsotiny.lang.compiler.codegen.dag;

import notsotiny.lang.ir.parts.IRConstant;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;

/**
 * An instruction selection DAG node which represents an existing value (live-in, global, constant)
 */
public class ISelDAGValueNode extends ISelDAGProducerNode {

    /**
     * Create a value node
     * @param dag
     * @param producedValue
     */
    public ISelDAGValueNode(ISelDAG dag, IRValue producedValue, IRType producedType) {
        super(dag, producedValue, producedType);
    }
    
}
