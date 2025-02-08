package notsotiny.lang.compiler.codegen.dag;

import java.util.ArrayList;
import java.util.List;

import notsotiny.lang.ir.parts.IRCondition;
import notsotiny.lang.ir.parts.IRIdentifier;

/**
 * An instruction selection DAG node which has input but not output
 */
public class ISelDAGTerminatorNode extends ISelDAGNode {
    
    private ISelDAGTerminatorOperation op;
    
    private IRIdentifier trueTargetBlock,
                         falseTargetBlock;
    
    private IRIdentifier target;
    
    private IRCondition condition;
    
    private ISelDAGTerminatorNode(ISelDAG dag, ISelDAGTerminatorOperation op) {
        super(dag);
        
        this.op = op;
        
        this.trueTargetBlock = null;
        this.falseTargetBlock = null;
        this.target = null;
        this.condition = null;
        
        this.dag.addNode(this);
        this.dag.addTerminator(this);
    }
    
    /**
     * STORE constructor
     * @param dag
     * @param op
     * @param value
     * @param target
     */
    public ISelDAGTerminatorNode(ISelDAG dag, ISelDAGTerminatorOperation op, ISelDAGProducerNode value, ISelDAGProducerNode target) {
        this(dag, op);
        
        value.addConsumer(this);
        target.addConsumer(this);
        
        this.inputNodes.add(value);
        this.inputNodes.add(target);
    }
    
    /**
     * CALLN constructor
     * @param dag
     * @param op
     * @param arguments args where the first argument is the call target
     */
    public ISelDAGTerminatorNode(ISelDAG dag, ISelDAGTerminatorOperation op, List<ISelDAGProducerNode> arguments) {
        this(dag, op);
        
        for(ISelDAGProducerNode arg : arguments) {
            arg.addConsumer(this);
        }
        
        this.inputNodes.addAll(arguments);
    }
    
    /**
     * JMP constructor
     * @param dag
     * @param op
     * @param target
     */
    public ISelDAGTerminatorNode(ISelDAG dag, ISelDAGTerminatorOperation op, IRIdentifier target) {
        this(dag, op);
        
        this.trueTargetBlock = target;
    }
    
    /**
     * OUT constructor
     * @param dag
     * @param op
     * @param target
     * @param value
     */
    public ISelDAGTerminatorNode(ISelDAG dag, ISelDAGTerminatorOperation op, IRIdentifier target, ISelDAGProducerNode value) {
        this(dag, op);
        
        this.target = target;
        
        value.addConsumer(this);
        
        this.inputNodes.add(value);
    }
    
    /**
     * JCC constructor
     * @param dag
     * @param op
     * @param trueTarget
     * @param falseTarget
     * @param compLeft
     * @param compRight
     * @param condition
     */
    public ISelDAGTerminatorNode(ISelDAG dag, ISelDAGTerminatorOperation op, IRIdentifier trueTarget, IRIdentifier falseTarget, ISelDAGProducerNode compLeft, ISelDAGProducerNode compRight, IRCondition condition) {
        this(dag, op);
        
        this.trueTargetBlock = trueTarget;
        this.falseTargetBlock = falseTarget;
        this.condition = condition;
        
        compLeft.addConsumer(this);
        compRight.addConsumer(this);
        
        this.inputNodes.add(compLeft);
        this.inputNodes.add(compRight);
    }
    
    /**
     * RET constructor
     * @param dag
     * @param op
     * @param value
     */
    public ISelDAGTerminatorNode(ISelDAG dag, ISelDAGTerminatorOperation op, ISelDAGProducerNode value) {
        this(dag, op);
        
        value.addConsumer(this);
        
        this.inputNodes.add(value);
    }
    
    public ISelDAGTerminatorOperation getOperation() { return this.op; }
    public IRIdentifier getTrueTargetBlock() { return this.trueTargetBlock; }
    public IRIdentifier getFalseTargetBlock() { return this.falseTargetBlock; }
    public IRIdentifier getTarget() { return this.target; }
    public IRIdentifier getCallTarget() { return this.target; }
    public IRIdentifier getTargetRegister() { return this.target; }
    public IRCondition getCondition() { return this.condition; }
    
}
