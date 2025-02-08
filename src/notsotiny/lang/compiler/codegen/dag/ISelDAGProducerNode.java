package notsotiny.lang.compiler.codegen.dag;

import java.util.ArrayList;
import java.util.List;

import notsotiny.lang.ir.parts.IRCondition;
import notsotiny.lang.ir.parts.IRIdentifier;
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
    
    private IRCondition condition;
    
    /**
     * IN/VALUE constructor
     * @param dag
     * @param producedValue
     * @param producedType
     * @param op
     */
    public ISelDAGProducerNode(ISelDAG dag, IRValue producedValue, IRType producedType, ISelDAGProducerOperation op) {
        super(dag);
        
        this.producedValue = producedValue;
        this.producedType = producedType;
        this.op = op;
        
        this.consumers = new ArrayList<>();
        this.condition = null;
    }
    
    /**
     * One-argument constructor
     * @param dag
     * @param producedValue
     * @param producedType
     * @param op
     * @param input
     */
    public ISelDAGProducerNode(ISelDAG dag, IRIdentifier producedValue, IRType producedType, ISelDAGProducerOperation op, ISelDAGProducerNode input) {
        this(dag, producedValue, producedType, op);
        
        input.addConsumer(this);
        
        this.inputNodes.add(input);
        this.dag.addNode(this);
    }
    
    /**
     * Two-argument constructor
     * @param dag
     * @param producedValue
     * @param producedType
     * @param op
     * @param left
     * @param right
     */
    public ISelDAGProducerNode(ISelDAG dag, IRIdentifier producedValue, IRType producedType, ISelDAGProducerOperation op, ISelDAGProducerNode left, ISelDAGProducerNode right) {
        this(dag, producedValue, producedType, op);
        
        left.addConsumer(this);
        right.addConsumer(this);
        
        this.inputNodes.add(left);
        this.inputNodes.add(right);
        this.dag.addNode(this);
    }
    
    /**
     * CALLR constructor
     * @param dag
     * @param producedValue
     * @param producedType
     * @param op
     * @param target
     * @param arguments args where the first argument is the call target
     */
    public ISelDAGProducerNode(ISelDAG dag, IRValue producedValue, IRType producedType, ISelDAGProducerOperation op, List<ISelDAGProducerNode> arguments) {
        this(dag, producedValue, producedType, op);
        
        for(ISelDAGProducerNode arg : arguments) {
            arg.addConsumer(this);
        }
        
        this.inputNodes.addAll(arguments);
        this.dag.addNode(this);
    }
    
    /**
     * SELECT constructor
     * @param dag
     * @param producedValue
     * @param producedType
     * @param op
     * @param compLeft
     * @param compRight
     * @param trueValue
     * @param falseValue
     * @param condition
     */
    public ISelDAGProducerNode(ISelDAG dag, IRIdentifier producedValue, IRType producedType, ISelDAGProducerOperation op, ISelDAGProducerNode compLeft, ISelDAGProducerNode compRight, ISelDAGProducerNode trueValue, ISelDAGProducerNode falseValue, IRCondition condition) {
        this(dag, producedValue, producedType, op);
        
        this.condition = condition;
        
        compLeft.addConsumer(this);
        compRight.addConsumer(this);
        trueValue.addConsumer(this);
        falseValue.addConsumer(this);
        
        this.inputNodes.add(compLeft);
        this.inputNodes.add(compRight);
        this.inputNodes.add(trueValue);
        this.inputNodes.add(falseValue);
        this.dag.addNode(this);
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
    
    public IRCondition getCondition() { return this.condition; }
    
}
