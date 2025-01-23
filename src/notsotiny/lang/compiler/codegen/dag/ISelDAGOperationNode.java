package notsotiny.lang.compiler.codegen.dag;

import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRType;

/**
 * An instruction selection DAG node which has both input and output
 */
public class ISelDAGOperationNode extends ISelDAGProducerNode {

    // TODO
    private ISelDAGOperationNode(ISelDAG dag, IRIdentifier producedValue, IRType producedType) {
        super(dag, producedValue, producedType);
    }
    
}
