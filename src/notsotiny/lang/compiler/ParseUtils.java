package notsotiny.lang.compiler;

import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.asm.resolution.ResolvableConstant;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.PointerType;
import notsotiny.lang.compiler.types.RawType;
import notsotiny.lang.compiler.types.StringType;
import notsotiny.lang.compiler.types.TypedRaw;
import notsotiny.lang.ir.parts.IRConstant;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lib.util.ASTLogger;

/**
 * Provides utility funtions for printing AST nodes
 */
public class ParseUtils {
    
    public static final TypedRaw TRUE_TR = new TypedRaw(new ResolvableConstant(1), RawType.NONE),
                                 FALSE_TR = new TypedRaw(new ResolvableConstant(0), RawType.NONE);
    
    public static final IRValue TRUE_IR = new IRConstant(1, IRType.NONE),
                                FALSE_IR = new IRConstant(0, IRType.NONE);
    
    /**
     * Gets a name from a node. If the name is a library reference, throws a compilation exception
     * @param node
     * @param alog
     * @param contextMessage
     * @return
     * @throws CompilationException
     */
    public static String getNameNoLibraries(ASTNode node, ASTLogger alog, String contextMessage) throws CompilationException {
        String s = node.getValue();
        
        if(s.startsWith("_")) {
            alog.severe(node, "Cannot use library reference for " + contextMessage);
            throw new CompilationException();
        } else {
            return s;
        }
    }
    
    /**
     * Convert a value to be equal to its value of the given type
     * @param v
     * @param type
     * @return
     */
    public static long trimToSize(long v, RawType type) {
        if(type == RawType.NONE) {
            return v;
        }
        
        int b = type.getSize();
        
        if(type.isSigned()) {
            // sign extend
            return (v << (64 - b*8)) >> (64 - b*8);
        } else {
            // zero extend
            return (v << (64 - b*8)) >>> (64 - b*8);
        }
    }
    
    /**
     * Ensures a type is a TypedRaw
     * @param type
     */
    public static void ensureTypedRaw(NSTLType type, ASTNode source, ASTLogger alog, String contextMessage) throws CompilationException {
        if(!(type instanceof RawType || type instanceof PointerType)) {
            alog.severe(source, "Expected integer type, got " + type + " " + contextMessage);
            throw new CompilationException();
        }
    }
    
    /**
     * Ensures types are valid. LOGs an error and throws a CompilationException if they are not.
     * @param expected
     * @param actual
     * @param requireNotNone
     * @param source
     * @param alog
     * @param contextMessage
     * @throws CompilationException 
     */
    public static void ensureTypesMatch(NSTLType expected, NSTLType actual, boolean requireNotNone, ASTNode source, ASTLogger alog, String contextMessage) throws CompilationException {
        boolean actuallyNone = actual.equals(RawType.NONE);
        
        if(expected instanceof StringType && actual instanceof StringType) {
            return;
        }
        
        if(expected.equals(RawType.NONE)) {
            // No expected type
            if(requireNotNone && actuallyNone) {
                // Both are NONE, but actual must not be NONE
                alog.severe(source, "Could not infer type " + contextMessage);
                throw new CompilationException();
            }
        } else if(!expected.equals(actual) && !((expected instanceof RawType || expected instanceof PointerType) && actuallyNone)) {
            // Expected and actual don't match, and not because actual is none while expected is integer
            alog.severe(source, "Type " + actual + " does not match expected type " + expected + " " + contextMessage);
            throw new CompilationException();
        }
    }
    
    /**
     * Parse integer literals
     * 
     * @param s
     * @return
     */
    public static long parseInteger(String s, int bytes, boolean signed) {
        long v;
        
        // Ignore underscores
        s = s.replace("_", "");
        
        // Parse with specified base
        if(s.startsWith("0x")) {
            v = Integer.parseUnsignedInt(s.substring(2), 16);
        } else if(s.startsWith("0o")) {
            v = Integer.parseUnsignedInt(s.substring(2), 8);
        } else if(s.startsWith("0d")) {
            v = Integer.parseUnsignedInt(s.substring(2));
        } else if(s.startsWith("0b")) {
            v = Integer.parseUnsignedInt(s.substring(2), 2);
        } else {
            v = Integer.parseUnsignedInt(s);
        }
        
        // Make desired size
        if(bytes != 0) {
            if(signed) {
                // sign extend
                v = (v << (64 - bytes*8)) >> (64 - bytes*8);
            } else {
                // truncate
                v = (v << (64 - bytes*8)) >>> (64 - bytes*8);
            }
        }
        
        return v;
    }
}
