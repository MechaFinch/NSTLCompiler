package notsotiny.lang.util;

/**
 * Utility class. A tuple of three values
 * @param <A>
 * @param <B>
 * @param <C>
 */
public class Triple<A, B, C> {
    public A a;
    public B b;
    public C c;
    
    /**
     * @param a
     * @param b
     * @param c
     */
    public Triple(A a, B b, C c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }
    
    @Override
    public boolean equals(Object o) {
        if(o instanceof Triple t) {
            return this.a.equals(t.a) && this.b.equals(t.b) && this.c.equals(t.c);
        }
        
        return false;
    }
    
    @Override
    public int hashCode() {
        return this.a.hashCode() ^ this.b.hashCode() ^ this.c.hashCode();
    }
    
    @Override
    public String toString() {
        return "(" + this.a + ", " + this.b + ", " + this.c + ")";
    }
}
