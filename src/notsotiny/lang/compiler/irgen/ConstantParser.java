package notsotiny.lang.compiler.irgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.asm.resolution.ResolvableConstant;
import notsotiny.lang.compiler.ASTUtil;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.irgen.context.ASTContextConstant;
import notsotiny.lang.compiler.irgen.context.ASTContextTree;
import notsotiny.lang.compiler.types.ArrayType;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.PointerType;
import notsotiny.lang.compiler.types.RawType;
import notsotiny.lang.compiler.types.StringType;
import notsotiny.lang.compiler.types.StructureType;
import notsotiny.lang.compiler.types.TypedArray;
import notsotiny.lang.compiler.types.TypedRaw;
import notsotiny.lang.compiler.types.TypedStructure;
import notsotiny.lang.compiler.types.TypedValue;
import notsotiny.lang.parser.NstlgrammarLexer;
import notsotiny.lang.parser.NstlgrammarParser;

/**
 * Parses constants
 */
public class ConstantParser {
    
    private static Logger LOG = Logger.getLogger(ConstantParser.class.getName());
    private static ASTLogger ALOG = new ASTLogger(LOG);
    
    /**
     * Parses a 'constant_expression' into a TypedValue
     * The TypedValue will be either a TypedRaw or a StringType
     * @param node
     * @param module
     * @param expectedType
     * @param requireNotNone
     * @param nonConstantSeverity Logging level if expression isn't a constant
     * @return
     * @throws CompilationException
     */
    public static TypedValue parseConstantExpression(ASTNode node, ASTModule module, ASTContextTree context, NSTLType expectedType, boolean requireNotNone, Level nonConstantSeverity) throws CompilationException {
        TypedValue tv;
        
        // Defer to specific functions for possible expressions
        switch(node.getSymbol().getID()) {
            case NstlgrammarParser.ID.VARIABLE_REFERENCE,
                 NstlgrammarParser.ID.VARIABLE_SUBREFERENCE:
                // Things that show up in variable_expression
                ALOG.log(nonConstantSeverity, node, "Node " + node + " is not a constant");
                throw new CompilationException();
            
            case NstlgrammarLexer.ID.TERMINAL_OP_SUBTRACT:
                // Subtract can either be 1 or 2 argument
                if(node.getChildren().size() == 1) {
                    tv = parseUnaryExpression(node, module, context, expectedType, requireNotNone, nonConstantSeverity);
                } else {
                    tv = parseBinaryExpression(node, module, context, expectedType, requireNotNone, nonConstantSeverity);
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_NOT:
                // Always unary
                tv = parseUnaryExpression(node, module, context, expectedType, requireNotNone, nonConstantSeverity);
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_AS:
                // Casts
                tv = parseCast(node, module, context, expectedType, requireNotNone, nonConstantSeverity);
                break;
            
            case NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE:
                // Actual values
                tv = parseConstantValue(node, module, context, expectedType, requireNotNone, nonConstantSeverity);
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL,
                 NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL,
                 NstlgrammarLexer.ID.TERMINAL_OP_GREATER,
                 NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL,
                 NstlgrammarLexer.ID.TERMINAL_OP_LESS,
                 NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL:
                // BOOLEAN-yielding comparisons
                tv = parseComparisonExpression(node, module, context, expectedType, requireNotNone, nonConstantSeverity);
                break;
            
            case NstlgrammarParser.ID.VARIABLE_CONSTANT_STRUCTURE,
                 NstlgrammarParser.ID.VARIABLE_VARIABLE_STRUCTURE:
                // Structure literals
                tv = parseConstantStructure(node, module, context, nonConstantSeverity);
                break;
            
            case NstlgrammarParser.ID.VARIABLE_VARIABLE_ARRAY:
                // Array literals
                tv = parseConstantArray(node, module, context, expectedType, nonConstantSeverity);
                break;
            
            default:
                // Two-argument operations
                tv = parseBinaryExpression(node, module, context, expectedType, requireNotNone, nonConstantSeverity);
        }
        
        tv = tv.convertCopy(expectedType);
        return tv;
    }
    
    /**
     * Parse a constant 'variable_array' node
     * @param node
     * @param module
     * @return
     * @throws CompilationException
     */
    private static TypedValue parseConstantArray(ASTNode node, ASTModule module, ASTContextTree context, NSTLType expectedType, Level nonConstantSeverity) throws CompilationException {
        LOG.finest("Parsing constant array " + ASTUtil.detailed(node));
        
        ArrayType eat;
        if(expectedType instanceof ArrayType at) {
            eat = at;
        } else {
            ALOG.severe(node, "Got array literal for non-array type " + expectedType);
            throw new CompilationException();
        }
        
        /*
         * variable_array -> type KW_ARRAY! KW_OF! argument_list KW_END! KW_ARRAY!;
         * 
         * type {expressions}
         */
        
        List<ASTNode> children = node.getChildren();
        NSTLType containedType = TypeParser.parseType(children.get(0), module, context);
        ASTUtil.ensureTypesMatch(eat.getMemberType(), containedType, true, node, ALOG, "for array literal");
        
        List<TypedValue> memberValues = new ArrayList<>();
        
        // Compute members
        for(ASTNode memberNode : children.get(1).getChildren()) {
            TypedValue memberValue = parseConstantExpression(memberNode, module, context, containedType, false, nonConstantSeverity);
            memberValue = memberValue.convertCopy(containedType);
            memberValues.add(memberValue);
        }
        
        TypedArray ta = new TypedArray(memberValues, eat);
        LOG.finest("Got array  " + ta);
        return ta;
    }
    
    /**
     * Parse a 'constant_structure' node
     * @param nod
     * @param module
     * @param expectedType
     * @return
     * @throws CompilationException 
     */
    private static TypedValue parseConstantStructure(ASTNode node, ASTModule module, ASTContextTree context, Level nonConstantSeverity) throws CompilationException {
        LOG.finest("Parsing constant structure " + ASTUtil.detailed(node));
        
        /*
         * constant_structure -> NAME KW_OF! constant_assignment_list KW_END! KW_STRUCTURE!;
         * constant_assignment_list -> {NAME KW_GETS! constant_expression} (COMMA! {NAME KW_GETS! constant_expression})*;
         * 
         * top:         NAME list
         * list values: NAME constant_expression
         */
        
        List<ASTNode> children = node.getChildren();
        
        // Get type
        NSTLType nameType = module.getTypeDefinitionMap().get(children.get(0).getValue());
        
        // Ensure it's a structure
        StructureType structType;
        if(nameType instanceof StructureType st) {
            structType = st;
        } else {
            ALOG.severe(node, "The type of a structure literal must be a structure");
            throw new CompilationException();
        }
        
        Map<String, TypedValue> members = new HashMap<>();
        TypedStructure structure = new TypedStructure(members, structType);
        
        // Collect members
        List<ASTNode> memberNodes = children.get(1).getChildren();
        List<String> expectedNames = structType.getMemberNames();
        List<NSTLType> expectedTypes = structType.getMemberTypes();
        
        boolean hasInvalidMember = false;
        
        for(ASTNode memberNode : memberNodes) {
            List<ASTNode> memberChildren = memberNode.getChildren();
            
            // Get name, make sure it exists in the structure
            String memberName = memberChildren.get(0).getValue();
            
            if(expectedNames.contains(memberName)) {
                // Name is a member. Compute it
                NSTLType expectedType = expectedTypes.get(expectedNames.indexOf(memberName));
                TypedValue value = parseConstantExpression(memberChildren.get(1), module, context, expectedType, false, nonConstantSeverity);
                
                members.put(memberName, value);
            } else {
                // Name isn't a member
                ALOG.severe(memberNode, "Member name " + memberName + " is not in structure type " + structType);
                hasInvalidMember = true;
            }
        }
        
        // Verify that all members exist
        for(int i = 0; i < expectedNames.size(); i++) {
            String expectedName = expectedNames.get(i);
            NSTLType expectedType = expectedTypes.get(i);
            
            if(!members.containsKey(expectedName) || !members.get(expectedName).getType().equals(expectedType)) {
                ALOG.severe(node, "Missing or incorrect structure member " + expectedType + " " + expectedName + " in " + structType);
                hasInvalidMember = true;
            }
        }
        
        if(hasInvalidMember) {
            throw new CompilationException();
        }
        
        LOG.finest("Got structure " + structure);
        
        return structure;
    }
    
    /**
     * Parse a 'constant_value' node
     * @param node
     * @param module
     * @return
     */
    private static TypedValue parseConstantValue(ASTNode node, ASTModule module, ASTContextTree context, NSTLType expectedType, boolean requireNotNone, Level nonConstantSeverity) throws CompilationException {
        LOG.finest("Parsing constant value " + ASTUtil.detailed(node));
        
        /*
         * constant_value ->
         *   OPEN_P! constant_expression^ CLOSE_P!
         * | type INTEGER
         * | KW_SIZEOF type
         * | INTEGER
         * | STRING
         * | NAME;
         * 
         * <type> INTEGER   Typed constant
         * KW_SIZEOF type   Type size
         * INTEGER          Untyped constant
         * STRING           String
         * NAME             Named value
         */
        
        List<ASTNode> children = node.getChildren();
        
        switch(children.get(0).getSymbol().getID()) {
            case NstlgrammarParser.ID.VARIABLE_TYPE: {
                // Typed constant
                // This one doesn't seem to get parsed? dunno why
                // Parse the type
                NSTLType t = TypeParser.parseType(children.get(0), module, context).getRealType();
                
                // t must be a RawType, but we can convert PointerTypes to RawTypes 
                if(t instanceof PointerType) {
                    t = RawType.PTR;
                }
                
                if(!(t instanceof RawType || t instanceof PointerType)) {
                    ALOG.severe(node, "Cannot create integer constant of type " + t);
                    throw new CompilationException();
                }
                
                // Verify type
                ASTUtil.ensureTypesMatch(expectedType, t, requireNotNone, node, ALOG, "from typed value" + ASTUtil.detailed(node));
                
                // Get value
                long v = ASTUtil.parseInteger(children.get(1).getValue(), 0, false);
                
                // Return it
                TypedRaw tr = new TypedRaw(new ResolvableConstant(v), (RawType) t);
                LOG.finest("Got typed value " + tr);
                return tr;
            }
            
            case NstlgrammarLexer.ID.TERMINAL_KW_SIZEOF: {
                // size of something
                ASTNode typeNode = children.get(1);
                List<ASTNode> typeChildren = typeNode.getChildren();
                
                // Try local names first
                if(typeChildren.size() == 1 && typeChildren.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_NAME) {
                    String name = typeChildren.get(0).getValue();
                    
                    if(module.variableExists(name, context)) {
                        // Found a variable
                        NSTLType t = module.getVariableType(name, context);
                        
                        TypedRaw tr = new TypedRaw(new ResolvableConstant(t.getSize()), RawType.NONE);
                        LOG.finest("Got size " + tr);
                        return tr;
                    } else if(module.constantExists(name, context)) {
                        // Found a constant
                        TypedValue tv = module.getConstantValue(name, context);
                        TypedRaw tr = new TypedRaw(new ResolvableConstant(tv.getType().getSize()), RawType.NONE);
                        LOG.finest("Got size " + tr);
                        return tr;
                    }
                }
                
                // Otherwise parse type
                NSTLType t = TypeParser.parseType(typeNode, module, context);
                ASTUtil.ensureTypesMatch(expectedType, RawType.NONE, requireNotNone, node, ALOG, "from type size");
                TypedRaw tr = new TypedRaw(new ResolvableConstant(t.getSize()), RawType.NONE);
                LOG.finest("Got type size " + tr);
                return tr;
            }
            
            case NstlgrammarLexer.ID.TERMINAL_INTEGER: {
                // Integer literal
                long v = ASTUtil.parseInteger(children.get(0).getValue(), expectedType.getSize(), false);
                ASTUtil.ensureTypesMatch(expectedType, RawType.NONE, requireNotNone, node, ALOG, "from integer literal");
                TypedRaw tr = new TypedRaw(new ResolvableConstant(v), RawType.NONE);
                LOG.finest("Got integer literal " + tr);
                return tr;
            }
            
            case NstlgrammarLexer.ID.TERMINAL_STRING: {
                // String literal
                String s = children.get(0).getValue();
                
                if(s.startsWith("\"") && s.endsWith("\"")) {
                    s = s.substring(1, s.length() - 1);
                }
                
                StringType st = new StringType(s);
                ASTUtil.ensureTypesMatch(expectedType, st, requireNotNone, node, ALOG, "from string literal");
                LOG.finest("Got string literal " + st);
                return st;
            }
            
            case NstlgrammarLexer.ID.TERMINAL_NAME: {
                // Named value
                // To be valid in a constant expression, must be a constant
                String name = children.get(0).getValue();
                if(module.constantExists(name, context)) {
                    TypedValue tv = module.getConstantValue(name, context);
                    LOG.finest("Got constant " + tv);
                    return tv;
                } else {
                    ALOG.log(nonConstantSeverity, node, "Name " + name + " is not a constant");
                    throw new CompilationException();
                }
            }
                
        }
        
        ALOG.severe(node, "Unexpected node: " + ASTUtil.detailed(node));
        throw new CompilationException();
    }
    
    /**
     * Parse unary expressions (negate and invert)
     * @param node
     * @param module
     * @return
     * @throws CompilationException 
     */
    private static TypedValue parseUnaryExpression(ASTNode node, ASTModule module, ASTContextTree context, NSTLType expectedType, boolean requireNotNone, Level nonConstantSeverity) throws CompilationException {
        LOG.finest("Parsing constant unary expression " + ASTUtil.detailed(node));
        
        /*
         * constant_unary ->
         *   OP_SUBTRACT^ constant_cast
         * | KW_NOT^ constant_cast
         * | constant_cast^;
         * 
         * OP_SUBTRACT const_expr   negation
         * KW_NOT const_expr        bitwise not
         */
        
        List<ASTNode> children = node.getChildren();
        ASTNode valueNode = children.get(0);
        
        // Compute right side value
        TypedValue rightTV = parseConstantExpression(valueNode, module, context, expectedType, requireNotNone, nonConstantSeverity);
        
        // Make sure it's a raw that exists
        String contextString = (node.getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_OP_SUBTRACT) ? "negation" : "logical NOT";
        ASTUtil.ensureTypedRaw(rightTV.getType(), valueNode, ALOG, "for constant " + contextString);
        
        long v;
        try {
            v = ((TypedRaw) rightTV).getResolvedValue();
        } catch(CompilationException e) {
            ALOG.severe(valueNode, "Constant value " + rightTV + " is not resolved.");
            throw e;
        }
        
        // Compute value & return it
        v = (node.getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_OP_SUBTRACT) ? -v : ~v;
        
        TypedRaw tv = new TypedRaw(new ResolvableConstant(v), (RawType) rightTV.getType());
        LOG.finest("Got value " + tv);
        return tv;
    }
    
    /**
     * Parse comparisons which yield BOOLEAN values
     * @param node
     * @param module
     * @param exepctedType
     * @param requireNotNone
     * @return
     * @throws CompilationException 
     */
    private static TypedValue parseComparisonExpression(ASTNode node, ASTModule module, ASTContextTree context, NSTLType exepctedType, boolean requireNotNone, Level nonConstantSeverity) throws CompilationException {
        LOG.finest("Parsing constant comparison expression " + ASTUtil.detailed(node));
        
        /*
         * constant_comparison ->
         *   constant_comparison OP_EQUAL^ constant_shift
         * | constant_comparison OP_NOT_EQUAL^ constant_shift
         * | constant_comparison OP_GREATER^ constant_shift
         * | constant_comparison OP_GREATER_EQUAL^ constant_shift
         * | constant_comparison OP_LESS^ constant_shift
         * | constant_comparison OP_LESS_EQUAL^ constant_shift
         * | constant_shift^;
         */
        
        List<ASTNode> children = node.getChildren();
        ASTNode leftNode = children.get(0);
        ASTNode rightNode = children.get(1);
        
        // Compute left & right values; infer type
        TypedValue leftTV = parseConstantExpression(leftNode, module, context, RawType.NONE, false, nonConstantSeverity);
        TypedValue rightTV = parseConstantExpression(rightNode, module, context, RawType.NONE, false, nonConstantSeverity);
        
        // Ensure they're raws that exist
        ASTUtil.ensureTypedRaw(leftTV.getType(), leftNode, ALOG, "for constant comparison");
        ASTUtil.ensureTypedRaw(rightTV.getType(), rightNode, ALOG, "for constant comparison");
        
        long v1, v2, v3;
        
        try {
            v1 = ((TypedRaw) leftTV).getResolvedValue();
        } catch(CompilationException e) {
            ALOG.severe(leftNode, "Constant value " + leftTV + " is not resolved.");
            throw e;
        }
        
        try {
            v2 = ((TypedRaw) rightTV).getResolvedValue();
        } catch(CompilationException e) {
            ALOG.severe(rightNode, "Constant value " + rightTV + " is not resolved.");
            throw e;
        }
        
        // make values equal to their logical values
        ASTUtil.trimToSize(v1, ((RawType) leftTV.getType()));
        ASTUtil.trimToSize(v2, ((RawType) rightTV.getType()));
        
        // Compute value & return result
        v3 = switch(node.getSymbol().getID()) {
            case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL          -> (v1 == v2) ? 1 : 0;
            case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL      -> (v1 != v2) ? 1 : 0;
            case NstlgrammarLexer.ID.TERMINAL_OP_GREATER        -> (v1 > v2) ? 1 : 0;
            case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL  -> (v1 >= v2) ? 1 : 0;
            case NstlgrammarLexer.ID.TERMINAL_OP_LESS           -> (v1 < v2) ? 1 : 0;
            case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL     -> (v1 <= v2) ? 1 : 0;
            default -> {
                ALOG.severe(node, "Unexpected node in constant comparison: " + ASTUtil.detailed(node));
                throw new CompilationException();
            }
        };
        
        TypedRaw tv = new TypedRaw(new ResolvableConstant(v3), RawType.BOOLEAN);
        LOG.finest("Got value " + tv);
        return tv;
    }
    
    /**
     * Parse type casts
     * @param node
     * @param module
     * @param exptectedType
     * @param requireNotNone
     * @return
     * @throws CompilationException 
     */
    private static TypedValue parseCast(ASTNode node, ASTModule module, ASTContextTree context, NSTLType exptectedType, boolean requireNotNone, Level nonConstantSeverity) throws CompilationException {
        LOG.finest("Parsing constant cast " + ASTUtil.detailed(node));
        
        /*
         * constant_cast ->
         *   constant_cast KW_AS^ type
         * | constant_value^;
         */
        
        // Get value
        List<ASTNode> children = node.getChildren();
        ASTNode leftNode = children.get(0);
        ASTNode typeNode = children.get(1);
        
        TypedValue leftTV = parseConstantExpression(leftNode, module, context, RawType.NONE, false, nonConstantSeverity);
        NSTLType desiredType = TypeParser.parseType(typeNode, module, context).getRealType();
        
        // bitcast
        leftTV = leftTV.convertCopy(desiredType);
        
        // Return
        LOG.finest("Got value " + leftTV);
        return leftTV;
    }
    
    /**
     * Parse binary expressions (most operators)
     * @param node
     * @param module
     * @return
     * @throws CompilationException 
     */
    private static TypedValue parseBinaryExpression(ASTNode node, ASTModule module, ASTContextTree context, NSTLType expectedType, boolean requireNotNone, Level nonConstantSeverity) throws CompilationException {
        LOG.finest("Parsing constant binary expression " + ASTUtil.detailed(node));
        
        /*
         * KW_AND
         * KW_OR
         * KW_XOR
         * OP_SHIFT_LEFT
         * OP_ARITH_SHIFT_RIGHT
         * OP_LOGIC_SHIFT_RIGHT
         * KW_ROL
         * KW_ROR
         * OP_ADD
         * OP_SUBTRACT
         * OP_MULTIPLY
         * OP_DIVIDE
         * OP_REMAINDER
         */
        
        List<ASTNode> children = node.getChildren();
        ASTNode leftNode = children.get(0);
        ASTNode rightNode = children.get(1);
        
        // Compute left & right values; infer type
        TypedValue leftTV = parseConstantExpression(leftNode, module, context, RawType.NONE, false, nonConstantSeverity);
        TypedValue rightTV = parseConstantExpression(rightNode, module, context, RawType.NONE, false, nonConstantSeverity);
        
        // Ensure they're raws that exist
        ASTUtil.ensureTypedRaw(leftTV.getType(), leftNode, ALOG, "for constant comparison");
        ASTUtil.ensureTypedRaw(rightTV.getType(), rightNode, ALOG, "for constant comparison");
        
        long v1, v2, v3;
        
        try {
            v1 = ((TypedRaw) leftTV).getResolvedValue();
        } catch(CompilationException e) {
            ALOG.severe(leftNode, "Constant value " + leftTV + " is not resolved.");
            throw e;
        }
        
        try {
            v2 = ((TypedRaw) rightTV).getResolvedValue();
        } catch(CompilationException e) {
            ALOG.severe(rightNode, "Constant value " + rightTV + " is not resolved.");
            throw e;
        }
        
        // make values equal to their logical values
        RawType leftType = (RawType) leftTV.getType();
        RawType rightType = (RawType) rightTV.getType();
        ASTUtil.trimToSize(v1, leftType);
        ASTUtil.trimToSize(v2, rightType);
        
        // Resulting type is the larger of the two
        RawType resultType = leftType.promote(rightType);
        ASTUtil.ensureTypesMatch(expectedType, resultType, requireNotNone, node, ALOG, "from constant expression");
        
        // Compute value & return result
        v3 = switch(node.getSymbol().getID()) {
            case NstlgrammarLexer.ID.TERMINAL_KW_AND                -> v1 & v2;
            case NstlgrammarLexer.ID.TERMINAL_KW_OR                 -> v1 | v2;
            case NstlgrammarLexer.ID.TERMINAL_KW_XOR                -> v1 ^ v2;
            case NstlgrammarLexer.ID.TERMINAL_OP_SHIFT_LEFT         -> v1 << v2;
            case NstlgrammarLexer.ID.TERMINAL_OP_ARITH_SHIFT_RIGHT  -> v1 >> v2;
            case NstlgrammarLexer.ID.TERMINAL_OP_LOGIC_SHIFT_RIGHT  -> v1 >>> v2;
            case NstlgrammarLexer.ID.TERMINAL_OP_ADD                -> v1 + v2;
            case NstlgrammarLexer.ID.TERMINAL_OP_SUBTRACT           -> v1 - v2;
            case NstlgrammarLexer.ID.TERMINAL_OP_MULTIPLY           -> v1 * v2;
            case NstlgrammarLexer.ID.TERMINAL_OP_DIVIDE             -> v1 / v2;
            case NstlgrammarLexer.ID.TERMINAL_OP_REMAINDER          -> v1 % v2;
            case NstlgrammarLexer.ID.TERMINAL_KW_ROL,
                 NstlgrammarLexer.ID.TERMINAL_KW_ROR -> {
                     ALOG.severe(node, "Unsupported operation in constant expression: "+ ASTUtil.detailed(node));
                     throw new CompilationException();
                 }
            default -> {
                ALOG.severe(node, "Unexpected node in constant expression: " + ASTUtil.detailed(node));
                throw new CompilationException();
            }
        };
        
        TypedRaw tv = new TypedRaw(new ResolvableConstant(v3), resultType);
        LOG.finest("Got value " + tv);
        return tv;
    }
}
