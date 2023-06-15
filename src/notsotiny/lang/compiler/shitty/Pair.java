package notsotiny.lang.compiler.shitty;

/**
 * Utility class. A tuple.
 * @param <A>
 * @param <B>
 */
public class Pair<A, B> {
    public A a;
    public B b;
    
    /**
     * @param a
     * @param b
     */
    public Pair(A a, B b) {
        this.a = a;
        this.b = b;
    }
}
