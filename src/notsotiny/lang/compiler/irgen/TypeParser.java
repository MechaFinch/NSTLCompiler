package notsotiny.lang.compiler.irgen;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.compiler.ASTUtil;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.irgen.context.ASTContextTree;
import notsotiny.lang.compiler.types.ArrayType;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.PointerType;
import notsotiny.lang.compiler.types.RawType;
import notsotiny.lang.compiler.types.StringType;
import notsotiny.lang.compiler.types.TypedRaw;
import notsotiny.lang.compiler.types.TypedValue;
import notsotiny.lang.parser.NstlgrammarLexer;

/**
 * Parses types
 */
public class TypeParser {
    
    private static Logger LOG = Logger.getLogger(TypeParser.class.getName());
    private static ASTLogger ALOG = new ASTLogger(LOG);
    
    /**
     * Parses a 'type' node into an NSTLType
     * @param node
     * @return
     * @throws CompilationException 
     */
    public static NSTLType parseType(ASTNode node, ASTModule module, ASTContextTree context) throws CompilationException {
        LOG.finest("Parsing type: " + ASTUtil.detailed(node));
        
        if(node.getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_NONE) {
            return RawType.NONE;
        }
        
        /*
         * type ->
         *   OPEN_P! type^ CLOSE_P!
         * | type KW_ARRAY! KW_SIZE! constant_expression
         * | type KW_POINTER
         * | NAME;
         * 
         * type constant_expression     Array 
         * type POINTER                 Typed pointer
         * name                         Named type
         */
        
        List<ASTNode> children = node.getChildren();
        
        if(children.size() == 2) {
            // Either a typed array or typed pointer
            // Either way, parse its contained type
            NSTLType containedType = parseType(children.get(0), module, context);
            
            if(children.get(1).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_POINTER) {
                // Pointer. Wrap the type and return it
                NSTLType t = new PointerType(containedType);
                LOG.finest("Constructed typed pointer " + t);
                return t;
            } else {
                // Array. Compute size, wrap and return
                ASTNode sizeNode = children.get(1);
                TypedValue size = ConstantParser.parseConstantExpression(sizeNode, module, context, RawType.NONE, false, Level.SEVERE);
                
                ASTUtil.ensureTypedRaw(size.getType(), sizeNode, ALOG, " for array size");
                
                // Got a size
                try {
                    int s = (int) ((TypedRaw) size).getResolvedValue();
                    
                    if(s <= 0) {
                        ALOG.severe(sizeNode, "Array size " + s + " must be positive");
                        throw new CompilationException();
                    }
                    
                    NSTLType t = new ArrayType(containedType, s);
                    LOG.finest("Constructed typed array " + t);
                    return t;
                } catch(CompilationException e) {
                    if(e.getMessage().equals(TypedRaw.CAUSE_UNRESOLVED)) {
                        ALOG.severe(sizeNode, "Array size " + size + " is not resolved.");
                    }
                    
                    throw e;
                }
            }
        } else {
            // Named type
            String name = children.get(0).getValue();
            NSTLType t = module.getTypeDefinitionMap().get(name).getRealType();
            
            if(t != null) {
                LOG.finest("Found type " + t);
                return t;
            } else if(module.constantExists(name, context)) {
                // Check for a string
                TypedValue tv = module.getConstantValue(name, context);
                
                if(tv instanceof StringType st) {
                    return st;
                } else {
                    ALOG.severe(node, "Constant " + name + " is not a string. Cannot get size.");
                    throw new CompilationException();
                }
            } else {
                // Nothin
                ALOG.severe(node, "Could not find type with name " + name);
                throw new CompilationException();
            }
        }
    }
}
