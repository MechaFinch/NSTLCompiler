package notsotiny.lang.compiler.codegen.dag;

/**
 * DAG operations
 */
public interface ISelDAGOperation {
    
    /**
     * Converts a name to an operation
     * @param name
     * @return
     */
    public static ISelDAGOperation fromString(String name) {
        String upper = name.toUpperCase().trim();
        
        try {
            return ISelDAGProducerOperation.valueOf(upper);
        } catch(IllegalArgumentException e1) {
            try {
                return ISelDAGTerminatorOperation.valueOf(upper);
            } catch(IllegalArgumentException e2) {
                return ISelDAGPatternOperation.valueOf(upper);
            }
        }
    }
    
}
