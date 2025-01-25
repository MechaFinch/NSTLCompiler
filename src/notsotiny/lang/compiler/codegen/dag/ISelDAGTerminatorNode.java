package notsotiny.lang.compiler.codegen.dag;

import java.util.ArrayList;
import java.util.List;

/**
 * An instruction selection DAG node which has input but not output
 */
public class ISelDAGTerminatorNode extends ISelDAGNode {
    
    private ISelDAGTerminatorOperation op;

    private ISelDAGTerminatorNode(ISelDAG dag, ISelDAGTerminatorOperation op) {
        super(dag);
        
        this.op = op;
    }
    
    public ISelDAGTerminatorOperation getOperation() { return this.op; }

    @Override
    public List<ISelDAGNode> getInputNodes() {
        // TODO method stub
        return new ArrayList<>();
    }
    
}
