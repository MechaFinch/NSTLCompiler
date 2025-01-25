package notsotiny.lang.compiler.codegen.dag;

import java.util.ArrayList;
import java.util.List;

import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;

/**
 * An instruction selection DAG node which produces a value
 */
public class ISelDAGProducerNode extends ISelDAGNode {
    
    private IRValue producedValue;
    
    private IRType producedType;
    
    private List<ISelDAGNode> consumers;
    
    private ISelDAGProducerOperation op;
    
    private ISelDAGProducerNode(ISelDAG dag, IRValue producedValue, IRType producedType, ISelDAGProducerOperation op) {
        super(dag);
        
        this.producedValue = producedValue;
        this.producedType = producedType;
        this.op = op;
        
        this.consumers = new ArrayList<>();
    }
    
    /**
     * Adds a node to this node's consumer list
     * @param node
     */
    public void addConsumer(ISelDAGNode node) {
        this.consumers.add(node);
    }
    
    public ISelDAGProducerOperation getOperation() { return this.op; }
    public List<ISelDAGNode> getConsumers() { return this.consumers; }
    
    /**
     * Returns the identifier of the value produced by this node
     * @return
     */
    public IRValue getProducedValue() {
        return this.producedValue;
    }
    
    /**
     * Returns the type of the value produced by this node
     * @return
     */
    public IRType getProducedType() {
        return this.producedType;
    }

    @Override
    public List<ISelDAGNode> getInputNodes() {
        // TODO method stub
        return new ArrayList<>();
    }
    
}
