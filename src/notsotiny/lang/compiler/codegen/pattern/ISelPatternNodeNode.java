package notsotiny.lang.compiler.codegen.pattern;

import java.util.List;

import notsotiny.lang.compiler.codegen.dag.ISelDAGOperation;
import notsotiny.lang.ir.parts.IRType;

/**
 * An ISelPatternNode that matches a DAG node 
 */
public class ISelPatternNodeNode extends ISelPatternNode {
    
    private String identifier;
    
    private ISelDAGOperation op;
    
    private IRType type;
    
    private List<ISelPatternNode> arguments;
    
    /**
     * @param identifier Identifier of this node
     * @param type Type produced by this node. NONE when ANY or not present
     * @param op Operation of this node
     * @param arguments List of arguments
     */
    public ISelPatternNodeNode(String identifier, IRType type, ISelDAGOperation op, List<ISelPatternNode> arguments) {
        this.identifier = identifier;
        this.type = type;
        this.op = op;
        this.arguments = arguments;
    }
    
    public String getIdentifier() { return this.identifier; }
    public IRType getProducedType() { return this.type; }
    public ISelDAGOperation getOperation() { return this.op; }
    public List<ISelPatternNode> getArgumentNodes() { return this.arguments; }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        
        if(this.identifier != null) {
            sb.append(this.identifier);
            sb.append(" ");
        }
        
        if(this.type != IRType.NONE) {
            sb.append(this.type);
            sb.append(" ");
        }
        
        sb.append(this.op);
        
        for(ISelPatternNode node : this.arguments) {
            sb.append(" ");
            sb.append(node);
        }
        
        sb.append(")");
        
        return sb.toString();
    }
    
}
