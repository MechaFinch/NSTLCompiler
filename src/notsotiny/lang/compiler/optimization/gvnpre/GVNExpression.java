package notsotiny.lang.compiler.optimization.gvnpre;

import java.util.List;

import notsotiny.lang.ir.parts.IRCondition;
import notsotiny.lang.ir.parts.IRLinearOperation;

/**
 * An expression in terms of value numbers
 * @param op Operation
 * @param cond Condition (for SELECT)
 * @param argValues Ordered list of argument value numbers
 */
public record GVNExpression(IRLinearOperation op, IRCondition cond, List<Integer> argValues) implements GVNElement {
    
    /**
     * No-condition constructor
     * @param op Operation
     * @param argValues
     */
    public GVNExpression(IRLinearOperation op, List<Integer> argValues) {
        this(op, IRCondition.NONE, argValues);
    }
    
    /**
     * Condition constructor
     * @param op
     * @param cond
     * @param argValues
     */
    public GVNExpression {
        /*
         * Ensure expressions exist in their canonical form
         */
        switch(op) {
            case ADD, MULU, MULS, AND, OR, XOR:
                // Operation is commutative and has two arguments
                int a = argValues.get(0),
                    b = argValues.get(1);
                argValues = List.of(Math.min(a, b), Math.max(a, b));
                break;
            
            case SELECT:
                // Some conditions commute
                switch(cond) {
                    case E, NE:
                        a = argValues.get(0);
                        b = argValues.get(1);
                        argValues = List.of(Math.min(a, b), Math.max(a, b), argValues.get(2), argValues.get(3));
                        break;
                    
                    default:
                        // Doesn't commute
                }
                break;
            
            default:
                // Operation is not commutative. Don't modify argument ordering
        }
    }
    
}
