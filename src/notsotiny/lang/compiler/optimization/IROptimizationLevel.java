package notsotiny.lang.compiler.optimization;

/**
 * Represents the minimum level for an optimization pass to be run
 */
public enum IROptimizationLevel {
    ZERO,
    ONE,
    TWO,
    THREE;
    
    /**
     * Returns true if this level is greater than or equal to the given level
     * @param l
     * @return
     */
    public boolean isAbove(IROptimizationLevel l) {
        return (l == ZERO) || switch(this) {
            case ZERO   -> false;
            case ONE    -> l == ONE;
            case TWO    -> l != THREE;
            case THREE  -> true;
        };
    }
}
