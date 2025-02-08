package notsotiny.lang.compiler.irgen;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.compiler.ASTUtil;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.irgen.context.ASTContextTree;
import notsotiny.lang.compiler.irgen.context.ASTContextVariable;
import notsotiny.lang.compiler.types.ArrayType;
import notsotiny.lang.compiler.types.FunctionHeader;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.RawType;
import notsotiny.lang.compiler.types.StructureType;
import notsotiny.lang.compiler.types.TypedRaw;
import notsotiny.lang.compiler.types.TypedValue;
import notsotiny.lang.ir.parts.IRArgumentList;
import notsotiny.lang.ir.parts.IRArgumentMapping;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRCondition;
import notsotiny.lang.ir.parts.IRConstant;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.parts.IRLinearOperation;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lang.parser.NstlgrammarLexer;
import notsotiny.lang.parser.NstlgrammarParser;
import notsotiny.lang.util.Pair;

// TODO replace @true and @false with context lookups

/**
 * Parses variable_expressions
 */
public class VariableParser {

    private static Logger LOG = Logger.getLogger(VariableParser.class.getName());
    private static ASTLogger ALOG = new ASTLogger(LOG);
    
    /**
     * Parses an integer variable_expression
     * expectedType must be convertible to an IRType
     * @param node
     * @param destName
     * @param expectedType
     * @param manager
     * @param context
     * @param sourceModule
     * @param func
     * @param irModul
     * @return {IR value, real type}
     * @throws CompilationException 
     */
    public static Pair<IRValue, NSTLType> parseIntegerExpression(ASTNode node, String destName, NSTLType expectedType, IRBasicBlock irBB, SSAManager manager, ASTContextTree context, ASTModule sourceModule, IRFunction func, IRModule irModule) throws CompilationException {
        LOG.finest("Parsing integer expression " + ASTUtil.detailed(node));
        
        // If the dest name is empty, generate one
        if(destName.equals("")) {
            destName = manager.getUniqueLocalID("").getName();
        }
        
        try {
            TypedValue tv = ConstantParser.parseConstantExpression(node, sourceModule, context, expectedType, false, Level.FINEST);
            
            ASTUtil.ensureTypesMatch(expectedType, tv.getType(), false, node, ALOG, "from constant variable expression");
            ASTUtil.ensureTypedRaw(tv.getType(), node, ALOG, "from constant variable expression");
            
            TypedRaw tr = (TypedRaw) tv;
            return new Pair<>(new IRConstant((int) tr.getResolvedValue(), tr.getType().getIRType()), tv.getType());
        } catch(CompilationException e) {
            // Not a constant
        }
        
        Pair<IRValue, NSTLType> retPair;
        
        // Defer to specific functions
        switch(node.getSymbol().getID()) {
            // reference/subreference valid
            // variable_structure, variable_array invalid
            case NstlgrammarParser.ID.VARIABLE_REFERENCE,
                 NstlgrammarParser.ID.VARIABLE_SUBREFERENCE:
                // References to values
                retPair = ReferenceParser.parseReferenceAsValue(node, destName, expectedType, irBB, manager, context, sourceModule, func, irModule);
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_SUBTRACT:
                // Can be 1 or 2 argument
                if(node.getChildren().size() == 1) {
                    retPair = parseIntegerUnary(node, destName, expectedType, irBB, manager, context, sourceModule, func, irModule);
                } else {
                    retPair = parseIntegerBinary(node, destName, expectedType, irBB, manager, context, sourceModule, func, irModule);
                }
                break;
                
            case NstlgrammarLexer.ID.TERMINAL_KW_NOT:
                // Always unary
                retPair = parseIntegerUnary(node, destName, expectedType, irBB, manager, context, sourceModule, func, irModule);
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_AS:
                // Casts
                retPair = parseIntegerCast(node, destName, expectedType, irBB, manager, context, sourceModule, func, irModule);
                break;
                
            case NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE,
                 NstlgrammarParser.ID.VARIABLE_VARIABLE_VALUE:
                retPair = parseIntegerValue(node, destName, expectedType, irBB, manager, context, sourceModule, func, irModule);
                break;
            
            case NstlgrammarParser.ID.VARIABLE_CONSTANT_STRUCTURE,
                 NstlgrammarParser.ID.VARIABLE_VARIABLE_STRUCTURE,
                 NstlgrammarParser.ID.VARIABLE_VARIABLE_ARRAY:
                ALOG.severe(node, "Node " + node + " is not valid in an integer expression");
                throw new CompilationException();
            
            default:
                // Two-argument operations
                retPair = parseIntegerBinary(node, destName, expectedType, irBB, manager, context, sourceModule, func, irModule);
        }
        
        return retPair;
    }
    
    /**
     * Parses a binary expression
     * @param node
     * @param destName
     * @param expectedType
     * @param manager
     * @param context
     * @param sourceModule
     * @param func
     * @param irModule
     * @return
     * @throws CompilationException 
     */
    private static Pair<IRValue, NSTLType> parseIntegerBinary(ASTNode node, String destName, NSTLType expectedType, IRBasicBlock irBB, SSAManager manager, ASTContextTree context, ASTModule sourceModule, IRFunction func, IRModule irModule) throws CompilationException {
        LOG.finest("Parsing integer binary " + ASTUtil.detailed(node));
        
        List<ASTNode> children = node.getChildren();
        ASTNode leftNode = children.get(0);
        ASTNode rightNode = children.get(1);
        
        // Compute values
        Pair<IRValue, NSTLType> leftPair = parseIntegerExpression(leftNode, "", expectedType, irBB, manager, context, sourceModule, func, irModule);
        Pair<IRValue, NSTLType> rightPair = parseIntegerExpression(rightNode, "", expectedType, irBB, manager, context, sourceModule, func, irModule);
        IRValue leftValue = leftPair.a,
                rightValue = rightPair.a;
        NSTLType leftType = leftPair.b,
                 rightType = rightPair.b;
        
        if(expectedType.equals(RawType.NONE)) {
            expectedType = leftType;
        }
        
        ASTUtil.ensureTypesMatch(expectedType, leftType, false, leftNode, ALOG, "from integer expression");
        ASTUtil.ensureTypesMatch(expectedType, rightType, false, rightNode, ALOG, "from integer expression");
        
        IRIdentifier destID = new IRIdentifier(destName, IRIdentifierClass.LOCAL);
        boolean signed = expectedType.isSigned();
        
        // Comparisons use SELECT, which takes more arguments
        switch(node.getSymbol().getID()) {
            case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL,
                 NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL,
                 NstlgrammarLexer.ID.TERMINAL_OP_GREATER,
                 NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL,
                 NstlgrammarLexer.ID.TERMINAL_OP_LESS,
                 NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL: {
                
                // Comparisons
                IRLinearOperation op = IRLinearOperation.SELECT;
                IRCondition cond = switch(node.getSymbol().getID()) {
                    case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL          -> IRCondition.E;
                    case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL      -> IRCondition.NE;
                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER        -> signed ? IRCondition.G : IRCondition.A;
                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL  -> signed ? IRCondition.GE : IRCondition.AE;
                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS           -> signed ? IRCondition.L : IRCondition.B;
                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL     -> signed ? IRCondition.LE : IRCondition.BE;
                    default -> {
                        ALOG.severe(node, "Unexpected node in integer comparison: " + ASTUtil.detailed(node));
                        throw new CompilationException();
                    }
                };
                
                IRLinearInstruction inst = new IRLinearInstruction(op, destID, expectedType.getIRType(), ASTUtil.TRUE_IR, ASTUtil.FALSE_IR, cond, leftValue, rightValue, irBB, ASTUtil.getLineNumber(node));
                irBB.addInstruction(inst);
                break;
            }
            
            default: {
                // Non-comparisons
                IRLinearOperation op = switch(node.getSymbol().getID()) {
                    case NstlgrammarLexer.ID.TERMINAL_KW_AND                -> IRLinearOperation.AND;
                    case NstlgrammarLexer.ID.TERMINAL_KW_OR                 -> IRLinearOperation.OR;
                    case NstlgrammarLexer.ID.TERMINAL_KW_XOR                -> IRLinearOperation.XOR;
                    case NstlgrammarLexer.ID.TERMINAL_OP_SHIFT_LEFT         -> IRLinearOperation.SHL;
                    case NstlgrammarLexer.ID.TERMINAL_OP_ARITH_SHIFT_RIGHT  -> IRLinearOperation.SAR;
                    case NstlgrammarLexer.ID.TERMINAL_OP_LOGIC_SHIFT_RIGHT  -> IRLinearOperation.SHR;
                    case NstlgrammarLexer.ID.TERMINAL_KW_ROL                -> IRLinearOperation.ROL;
                    case NstlgrammarLexer.ID.TERMINAL_KW_ROR                -> IRLinearOperation.ROR;
                    case NstlgrammarLexer.ID.TERMINAL_OP_ADD                -> IRLinearOperation.ADD;
                    case NstlgrammarLexer.ID.TERMINAL_OP_SUBTRACT           -> IRLinearOperation.SUB;
                    case NstlgrammarLexer.ID.TERMINAL_OP_MULTIPLY           -> signed ? IRLinearOperation.MULS : IRLinearOperation.MULU;
                    case NstlgrammarLexer.ID.TERMINAL_OP_DIVIDE             -> signed ? IRLinearOperation.DIVS : IRLinearOperation.DIVU;
                    case NstlgrammarLexer.ID.TERMINAL_OP_REMAINDER          -> signed ? IRLinearOperation.REMS : IRLinearOperation.REMU;
                    default -> {
                        ALOG.severe(node, "Unexpected node in integer expression: " + ASTUtil.detailed(node));
                        throw new CompilationException();
                    }
                };
                
                IRLinearInstruction inst = new IRLinearInstruction(op, destID, expectedType.getIRType(), leftValue, rightValue, irBB, ASTUtil.getLineNumber(node));
                irBB.addInstruction(inst);
                break;
            }
        }
        
        return new Pair<>(destID, expectedType);
    }
    
    /**
     * Parses a unary expression
     * @param node
     * @param destName
     * @param expectedType
     * @param manager
     * @param context
     * @param sourceModule
     * @param func
     * @param irModule
     * @return
     * @throws CompilationException 
     */
    private static Pair<IRValue, NSTLType> parseIntegerUnary(ASTNode node, String destName, NSTLType expectedType, IRBasicBlock irBB, SSAManager manager, ASTContextTree context, ASTModule sourceModule, IRFunction func, IRModule irModule) throws CompilationException {
        LOG.finest("Parsing integer unary " + ASTUtil.detailed(node));
        
        ASTNode valNode = node.getChildren().get(0);
        
        // Compute value
        Pair<IRValue, NSTLType> valPair = parseIntegerExpression(valNode, "", expectedType, irBB, manager, context, sourceModule, func, irModule);
        IRValue valVal = valPair.a;
        NSTLType valType = valPair.b;
        
        // Do the op
        IRIdentifier dest = new IRIdentifier(destName, IRIdentifierClass.LOCAL);
        IRType irType = valType.getIRType();
        IRLinearOperation op = (node.getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_OP_SUBTRACT) ? IRLinearOperation.NEG : IRLinearOperation.NOT;
        IRLinearInstruction inst = new IRLinearInstruction(op, dest, irType, valVal, irBB, ASTUtil.getLineNumber(node));
        irBB.addInstruction(inst);
        
        return new Pair<>(dest, valType);
    }
    
    /**
     * Parses a type cast
     * @param node
     * @param destName
     * @param expectedType
     * @param manager
     * @param context
     * @param sourceModule
     * @param func
     * @param irModule
     * @return
     * @throws CompilationException 
     */
    private static Pair<IRValue, NSTLType> parseIntegerCast(ASTNode node, String destName, NSTLType expectedType, IRBasicBlock irBB, SSAManager manager, ASTContextTree context, ASTModule sourceModule, IRFunction func, IRModule irModule) throws CompilationException {
        LOG.finest("Parsing integer cast " + ASTUtil.detailed(node));
        
        /*
         * variable_expr type
         */
        
        List<ASTNode> children = node.getChildren();
        ASTNode exprNode = children.get(0);
        ASTNode typeNode = children.get(1);
        
        // Get value & type
        Pair<IRValue, NSTLType> exprPair = parseIntegerExpression(exprNode, "", RawType.NONE, irBB, manager, context, sourceModule, func, irModule);
        IRValue exprVal = exprPair.a;
        NSTLType exprType = exprPair.b;
        NSTLType targetType = TypeParser.parseType(typeNode, sourceModule, context);
        
        ASTUtil.ensureTypedRaw(exprType, exprNode, ALOG, "from integer expression");
        ASTUtil.ensureTypedRaw(targetType, typeNode, ALOG, "from cast");
        ASTUtil.ensureTypesMatch(expectedType, targetType, false, typeNode, ALOG, "from cast");
        
        // If the expr is untyped, return it as is
        if(exprType.equals(RawType.NONE)) {
            return new Pair<>(exprVal, targetType);
        } else if(exprVal instanceof IRConstant irc) {
            // Value is constant. Change its type.
            return new Pair<>(new IRConstant(irc.getValue(), targetType.getIRType()), targetType);
        } else {
            // Do a cast
            IRType targetIRType = targetType.getIRType();
            IRIdentifier targetID = new IRIdentifier(destName, IRIdentifierClass.LOCAL);
            
            if(exprType.getSize() == targetType.getSize()) {
                // No action. Return as is.
                return new Pair<>(exprVal, targetType);
            } else {
                IRLinearOperation op;
                
                if(exprType.getSize() > targetType.getSize()) {
                    // Target is smaller
                    op = IRLinearOperation.TRUNC;
                } else if(exprType.isSigned()) {
                    // Target is larger and expr is signed 
                    op = IRLinearOperation.SX;
                } else {
                    // Target is larger and expr is unsigned
                    op = IRLinearOperation.ZX;
                }
                
                IRLinearInstruction irli = new IRLinearInstruction(op, targetID, targetIRType, exprVal, irBB, ASTUtil.getLineNumber(node));
                irBB.addInstruction(irli);
            }
            
            return new Pair<>(targetID, targetType);
        }
    }
    
    /**
     * Parses a constant_value
     * @param node
     * @param destName
     * @param expectedType
     * @param manager
     * @param context
     * @param sourceModule
     * @param func
     * @param irModule
     * @return
     * @throws CompilationException 
     */
    private static Pair<IRValue, NSTLType> parseIntegerValue(ASTNode node, String destName, NSTLType expectedType, IRBasicBlock irBB, SSAManager manager, ASTContextTree context, ASTModule sourceModule, IRFunction func, IRModule irModule) throws CompilationException {
        LOG.finest("Parsing integer value " + ASTUtil.detailed(node));
        
        /*
         * constant_value ->
         *   OPEN_P! constant_expression^ CLOSE_P!
         * | type INTEGER
         * | KW_SIZEOF type
         * | INTEGER
         * | STRING
         * | NAME;
         * 
         * type INTEGER     Typed integer literal.  Handled by constant parse attempt
         * SIZEOF type      Type size constant      Handled by constant parse attempt
         * INTEGER          Integer literal         Handled by constant parse attempt
         * STRING           Invalid                 Handled by constant parse attempt
         * NAME             Direct name reference   < the only thing that shows up here
         */
        
        ASTNode nameNode = node.getChildren().get(0);
        String varName = ASTUtil.getName(nameNode);
        
        // Does a variable with the name exist
        if(sourceModule.variableExists(varName, context)) {
            boolean isGlobal;
            NSTLType varType;
            String varUName;
            
            // Variable exists. Is it local or global?
            if(context.variableExists(varName)) {
                // It's local
                ASTContextVariable acv = context.getVariable(varName);
                isGlobal = false;
                varType = acv.getType();
                varUName = acv.getUniqueName();
            } else {
                // It's global
                isGlobal = true;
                varType = sourceModule.getGlobalVariableMap().get(varName).getType();
                varUName = varName;
            }
            
            // Make sure it's an integer of the right type
            ASTUtil.ensureTypedRaw(varType, nameNode, ALOG, "from variable");
            ASTUtil.ensureTypesMatch(expectedType, varType, false, nameNode, ALOG, "from variable");
            
            IRValue irVal;
            
            // Type is good. Get it.
            if(isGlobal) {
                // Global = load to local
                IRIdentifier globalID = new IRIdentifier(varName, IRIdentifierClass.GLOBAL);
                IRIdentifier localID = new IRIdentifier(destName, IRIdentifierClass.LOCAL);
                
                LOG.finest("Loading " + globalID + " to local " + localID);
                
                IRLinearInstruction loadInst = new IRLinearInstruction(IRLinearOperation.LOAD, localID, varType.getIRType(), globalID, irBB, ASTUtil.getLineNumber(nameNode));
                irBB.addInstruction(loadInst);
                
                irVal = localID;
            } else {
                // Local = readVariable
                try {
                    irVal = manager.readVariable(varUName, irBB.getID());
                } catch(NullPointerException e) {
                    ALOG.severe(nameNode, "Variable " + varName + " is undefined");
                    throw new CompilationException();
                }
            }
            
            return new Pair<>(irVal, varType);
        } else {
            // Doesn't exist
            ALOG.severe(nameNode, "Name " + varName + " is not a variable");
            throw new CompilationException();
        }
    }
    
    /**
     * Parses a function call's argument list to an IRArgumentMapping
     * If astHeader is null, types are derived from irArgs.
     * If both astHeader and irArgs are both null, types are inferred and arguments are mapped to %0, %1, etc 
     * @param node
     * @param irArgs
     * @param astHeader
     * @param irBB
     * @param manager
     * @param context
     * @param sourceModule
     * @param func
     * @param irModule
     * @return
     * @throws CompilationException 
     */
    public static IRArgumentMapping parseFunctionArguments(ASTNode node, IRArgumentList irArgs, FunctionHeader astHeader, IRBasicBlock irBB, SSAManager manager, ASTContextTree context, ASTModule sourceModule, IRFunction func, IRModule irModule) throws CompilationException {
        IRArgumentMapping mapping;
        
        // Do we have a header
        if(irArgs != null) {
            mapping = new IRArgumentMapping(irArgs.getNameList());
            
            // Assumes if irArgs != null, astHeader != null. Current code only gets irArgs from source function
            boolean hasArguments = node.getSymbol().getID() != NstlgrammarLexer.ID.TERMINAL_KW_NONE;
            boolean needsArguments = irArgs.getArgumentCount() != 0;
            
            if(hasArguments && needsArguments) {
                // Necessary arguments
                List<String> argNames = astHeader.getArgumentNames();
                List<IRIdentifier> argIDs = irArgs.getNameList();
                List<NSTLType> argNSTLTypes = astHeader.getArgumentTypes();
                
                List<ASTNode> argNodes = node.getChildren();
                
                // Check counts
                if(argNodes.size() != argNames.size()) {
                    ALOG.severe(node, "Expected " + argNames.size() + " arguments for call to " + astHeader + ", got " + argNodes.size());
                    throw new CompilationException();
                }
                
                // Compute args
                for(int i = argNodes.size() - 1; i >= 0; i--) {
                    IRIdentifier id = argIDs.get(i);
                    NSTLType nstlType = argNSTLTypes.get(i);
                    ASTNode argNode = argNodes.get(i);
                    
                    // Compute
                    Pair<IRValue, NSTLType> argPair = VariableParser.parseIntegerExpression(argNode, "", nstlType, irBB, manager, context, sourceModule, func, irModule);
                    IRValue argVal = argPair.a;
                    NSTLType argType = argPair.b;
                    
                    // Check type
                    ASTUtil.ensureTypesMatch(nstlType, argType, false, argNode, ALOG, "from call argument");
                    
                    // Map
                    mapping.addMapping(id, argVal);
                }
                
                return mapping;
            } else if(!hasArguments && !needsArguments) {
                // No arguments, not needed
                return mapping;
            } else if(hasArguments && !needsArguments) {
                // Unnecessary arguments
                ALOG.severe(node, "Function call to " + astHeader + " requires no arguments, but found " + ASTUtil.detailed(node));
                throw new CompilationException();
            } else {
                // Missing arguments
                ALOG.severe(node, "Arguments required for call to " + astHeader + " but got none");
                throw new CompilationException();
            }
        } else {
            mapping = new IRArgumentMapping();
            
            // No header. Type inference only
            if(node.getChildren().size() == 0) {
                return mapping;
            }
            
            // TODO
            ALOG.severe(node, "No function header: " + ASTUtil.detailed(node));
            throw new CompilationException();
        }
    }
    
}
