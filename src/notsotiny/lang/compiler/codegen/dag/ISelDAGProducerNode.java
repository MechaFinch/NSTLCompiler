package notsotiny.lang.compiler.codegen.dag;

import java.util.ArrayList;
import java.util.List;

import notsotiny.lang.ir.parts.IRCondition;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;

/**
 * An instruction selection DAG node which produces a value
 */
public class ISelDAGProducerNode extends ISelDAGNode {
    
    private IRIdentifier producedName;
    
    private IRValue producedValue;
    
    private IRType producedType;
    
    private List<ISelDAGNode> consumers;
    
    private ISelDAGProducerOperation op;
    
    private IRCondition condition;
    
    /**
     * VALUE constructor
     * @param dag
     * @param producedValue
     * @param producedType
     * @param op
     */
    public ISelDAGProducerNode(ISelDAG dag, IRIdentifier producedName, IRValue producedValue, IRType producedType, ISelDAGProducerOperation op) {
        super(dag);
        
        this.producedValue = producedValue;
        this.producedType = producedType;
        this.op = op;
        
        this.consumers = new ArrayList<>();
        this.condition = null;
        
        // producedName must be a local.
        if(producedName.getIDClass() != IRIdentifierClass.LOCAL) {
            throw new IllegalArgumentException("produced name must be LOCAL");
        } else {
            // producedName is a local, just use it
            this.producedName = producedName;
        }
        
        this.dag.addNode(this);
    }
    
    /**
     * IN/ARG constructor
     * @param dag
     * @param producedValue
     * @param producedType
     * @param op
     */
    public ISelDAGProducerNode(ISelDAG dag, IRIdentifier producedValue, IRType producedType, ISelDAGProducerOperation op) {
        this(dag, producedValue, producedValue, producedType, op);
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
    public ISelDAGProducerNode(ISelDAG dag, IRIdentifier producedValue, IRType producedType, ISelDAGProducerOperation op, List<ISelDAGProducerNode> arguments) {
        this(dag, producedValue, producedType, op);
        
        for(ISelDAGProducerNode arg : arguments) {
            arg.addConsumer(this);
        }
        
        this.inputNodes.addAll(arguments);
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
    }
    
    /**
     * Adds a node to this node's consumer list
     * @param node
     */
    public void addConsumer(ISelDAGNode node) {
        this.consumers.add(node);
    }
    
    /**
     * Returns the identifier of the value produced by this node
     * @return
     */
    public IRIdentifier getProducedName() {
        return this.producedName;
    }
    
    /**
     * Returns the value produced by this node
     * Equivalent to getProducedName except for VALUE nodes
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
    
    /**
     * @return A description of the node
     */
    public String getDescription() {
        return this.producedType + " " + this.producedName + " = " + this.op;
    }
    
    public ISelDAGProducerOperation getOperation() { return this.op; }
    public List<ISelDAGNode> getConsumers() { return this.consumers; }
    public IRCondition getCondition() { return this.condition; }
    
    @Override
    public ISelDAGOperation getOp() { return this.op; }
    
}
