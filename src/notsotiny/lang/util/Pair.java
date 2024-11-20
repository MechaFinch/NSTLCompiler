package notsotiny.lang.util;

/**
 * Utility class. A tuple of two values.
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
    
    @Override
    public boolean equals(Object o) {
        if(o instanceof Pair p) {
            return this.a.equals(p.a) && this.b.equals(p.b);
        }
        
        return false;
    }
    
    @Override
    public int hashCode() {
        return this.a.hashCode() ^ this.b.hashCode();
    }
}
