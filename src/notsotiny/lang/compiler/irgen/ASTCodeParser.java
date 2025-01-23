package notsotiny.lang.compiler.irgen;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.cenotelie.hime.redist.ASTNode;
import fr.cenotelie.hime.redist.Symbol;
import notsotiny.lang.compiler.ASTUtil;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.irgen.context.ASTContextConstant;
import notsotiny.lang.compiler.irgen.context.ASTContextTree;
import notsotiny.lang.compiler.irgen.context.ASTContextVariable;
import notsotiny.lang.compiler.types.ArrayType;
import notsotiny.lang.compiler.types.FunctionHeader;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.PointerType;
import notsotiny.lang.compiler.types.RawType;
import notsotiny.lang.compiler.types.StringType;
import notsotiny.lang.compiler.types.StructureType;
import notsotiny.lang.compiler.types.TypedValue;
import notsotiny.lang.ir.parts.IRArgumentList;
import notsotiny.lang.ir.parts.IRArgumentMapping;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRBranchInstruction;
import notsotiny.lang.ir.parts.IRBranchOperation;
import notsotiny.lang.ir.parts.IRCondition;
import notsotiny.lang.ir.parts.IRConstant;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRGlobal;
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

/**
 * Parses the code that does stuff
 */
public class ASTCodeParser {
    
    private static Logger LOG = Logger.getLogger(ASTCodeParser.class.getName());
    private static ASTLogger ALOG = new ASTLogger(LOG);
    
    /**
     * Parses a function in CFG form into an IRFunction
     * @param function
     * @throws CompilationException
     */
    public static IRFunction parseFunction(ASTFunction sourceFunction, IRModule irModule) throws CompilationException {
        LOG.fine("----Parsing function " + sourceFunction.getHeader().getName() + " to IR----");
        
        // Convert header information
        IRFunction targetFunction;
        
        try {
            targetFunction = sourceFunction.getHeaderIR(irModule);
        } catch(CompilationException e) {
            ALOG.severe(sourceFunction.getHeader().getSource(), "Error converting external function: " + e.getMessage());
            throw e;
        }
        
        sourceFunction.addArgsToContext();
        
        SSAManager manager = new SSAManager(targetFunction);
        
        // parse away
        parseBasicBlock(sourceFunction.getBasicBlocks().get(0), null, manager, targetFunction, sourceFunction, irModule);
        
        // Sanity check: All BBs should be sealed
        for(IRBasicBlock irBB : targetFunction.getBasicBlockList()) {
            if(!manager.isSealed(irBB.getID())) {
                LOG.severe("Block " + irBB.getID() + " was not sealed");
                throw new CompilationException();
            }
        }
        
        return targetFunction;
    }
    
    /**
     * Parses an ASTBB
     * @param sourceBB
     * @param targetFunction
     * @param sourceFunction
     * @param module
     * @throws CompilationException 
     */
    private static void parseBasicBlock(ASTBasicBlock sourceBB, IRIdentifier callingPredecessor, SSAManager manager, IRFunction targetFunction, ASTFunction sourceFunction, IRModule irModule) throws CompilationException {
        LOG.finer("Parsing basic block " + sourceBB.getName());
        
        /*
         * SSAManager considerations
         * An IRBB must be filled (all assignments evaluated) before successors
         * can be added to it
         * When an IRBB has all predecessors added, it must be marked as sealed
         * 
         * To process an ASTBB:
         *  - Create IRBB
         *  - Fill IRBB (parse ASTBB contents)
         *      - Exit code will include all successor IDs
         *  - For each successor of ASTBB:
         *      - If IRBB exists, add it as successor to current IRBB
         *          - If successor ASTBB has all predecessor ASTBBs done, seal successor IRBB
         *      - If IRBB doesn't exist, process it
         */
        IRIdentifier irBBID = new IRIdentifier(sourceBB.getName(), IRIdentifierClass.BLOCK);
        IRBasicBlock irBB = new IRBasicBlock(irBBID, irModule, targetFunction, sourceBB.getSourceLine());
        
        targetFunction.addBasicBlock(irBB);
        
        // Add predecessor if present
        if(callingPredecessor != null) {
            irBB.addPredecessor(callingPredecessor);
        }
        
        if(irBB.getPredecessorBlocks().size() == sourceBB.getPredecessors().size()) {
            manager.sealBlock(irBBID);
        }
        
        // Fill basic block
        parseBBContents(irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
        
        // Successors
        if(sourceBB.getTrueSuccessor() != null) {
            // Get successor
            ASTBasicBlock trueSuccessor = sourceBB.getTrueSuccessor();
            IRIdentifier tsID = new IRIdentifier(trueSuccessor.getName(), IRIdentifierClass.BLOCK);
            IRBasicBlock tsBB = targetFunction.getBasicBlock(tsID);
            
            // If not found in targetFunction, process it
            if(tsBB == null) {
                parseBasicBlock(trueSuccessor, irBBID, manager, targetFunction, sourceFunction, irModule);
                tsBB = targetFunction.getBasicBlock(tsID);
            } else {
                // Add us as predecessor
                tsBB.addPredecessor(irBBID);
                
                if(tsBB.getPredecessorBlocks().size() == trueSuccessor.getPredecessors().size()) {
                    manager.sealBlock(tsID);
                }
            }
        }
        
        if(sourceBB.getFalseSuccessor() != null) {
            // Get successor
            ASTBasicBlock falseSuccessor = sourceBB.getFalseSuccessor();
            IRIdentifier fsID = new IRIdentifier(falseSuccessor.getName(), IRIdentifierClass.BLOCK);
            IRBasicBlock fsBB = targetFunction.getBasicBlock(fsID);
            
            // If not found in targetFunction, process it
            if(fsBB == null) {
                parseBasicBlock(falseSuccessor, irBBID, manager, targetFunction, sourceFunction, irModule);
                fsBB = targetFunction.getBasicBlock(fsID);
            } else {
                // Add us as predecessor
                fsBB.addPredecessor(irBBID);
                
                if(fsBB.getPredecessorBlocks().size() == falseSuccessor.getPredecessors().size()) {
                    manager.sealBlock(fsID);
                }
            }
        }
    }
    
    /**
     * Parses the contents of an ASTBB
     * @param sourceBB
     * @param manager
     * @param targetFunction
     * @param sourceFunction
     * @param irModule
     * @throws CompilationException 
     */
    private static void parseBBContents(IRBasicBlock irBB, ASTBasicBlock sourceBB, SSAManager manager, IRFunction targetFunction, ASTFunction sourceFunction, IRModule irModule) throws CompilationException {
        LOG.finest("Parsing contents of basic block " + sourceBB.getName());
        
        // Parse that code
        for(ASTNode codeNode : sourceBB.getCode()) {
            
            /*
             * function_code ->
             *   value_creation^
             * | assignment^
             * | function_call^ SEMI!;
             * (other nodes handled by CFG parse)
             */
            
            switch(codeNode.getSymbol().getID()) {
                case NstlgrammarParser.ID.VARIABLE_VALUE_CREATION:
                    // Value creation
                    parseValueCreation(codeNode, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_ASSIGNMENT:
                    // Assignment
                    parseAssignment(codeNode, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_FUNCTION_CALL:
                    // Free-standing function call
                    parseFreeFunctionCall(codeNode, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                    break;
                
                case Symbol.SID_EPSILON:
                    // for_construct places epsilons for initialization and update
                    if(codeNode.getChildren().size() == 3) {
                        // name type value -> init
                        parseValueCreation(codeNode, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                    } else {
                        // name value -> update
                        parseAssignment(codeNode, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                    }
                    break;
                
                default:
                    ALOG.severe(codeNode, "Unexpected node in BB contents: " + ASTUtil.detailed(codeNode));
                    throw new CompilationException();
            }
        }
        
        // Set exit code
        IRBranchInstruction exitInst;
        
        switch(sourceBB.getExitType()) {
            case CONDITIONAL:
                // Defer
                exitInst = parseCondition(irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                break;
                
            case RETURN:
                // Defer
                exitInst = parseReturn(irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                break;
                
            case UNCONDITIONAL:
                // Just create the instruction
                exitInst = new IRBranchInstruction(IRBranchOperation.JMP, new IRIdentifier(sourceBB.getTrueSuccessor().getName(), IRIdentifierClass.BLOCK), new IRArgumentMapping(), irBB, 0);
                break;
            
            default:
                LOG.severe("Invalid BB exit type: " + sourceBB.getExitType());
                throw new CompilationException();
        }
        
        irBB.setExitInstruction(exitInst);
    }
    
    /**
     * Parses a conditional branch
     * @param irBB
     * @param sourceBB
     * @param manager
     * @param targetFunction
     * @param sourceFunction
     * @param irModule
     * @return
     * @throws CompilationException
     */
    private static IRBranchInstruction parseCondition(IRBasicBlock irBB, ASTBasicBlock sourceBB, SSAManager manager, IRFunction targetFunction, ASTFunction sourceFunction, IRModule irModule) throws CompilationException {
        //exitInst = new IRBranchInstruction(IRBranchOperation.JCC, IRCondition.NE, null, null, new IRIdentifier(sourceBB.getTrueSuccessor().getName(), IRIdentifierClass.BLOCK), new IRArgumentMapping(), new IRIdentifier(sourceBB.getFalseSuccessor().getName(), IRIdentifierClass.BLOCK), new IRArgumentMapping(), irModule, ASTUtil.getLineNumber(sourceBB.getExitCode())); 
        
        // Conditional exit code = variable_expression, true if nonzero
        // If the exit code is a comparison, compute left/right and use comparison as condition
        // Otherwise, compute the expression and use NE 0 as the condition
        ASTNode conditionNode = sourceBB.getExitCode();
        
        IRIdentifier trueID = new IRIdentifier(sourceBB.getTrueSuccessor().getName(), IRIdentifierClass.BLOCK);
        IRIdentifier falseID = new IRIdentifier(sourceBB.getFalseSuccessor().getName(), IRIdentifierClass.BLOCK);
        int lineNum = ASTUtil.getLineNumber(sourceBB.getExitCode());
        
        switch(conditionNode.getSymbol().getID()) {
            case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL,
                 NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL,
                 NstlgrammarLexer.ID.TERMINAL_OP_GREATER,
                 NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL,
                 NstlgrammarLexer.ID.TERMINAL_OP_LESS,
                 NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL: {
                // Comparison
                // Get values
                List<ASTNode> children = conditionNode.getChildren();
                Pair<IRValue, NSTLType> leftPair = VariableParser.parseIntegerExpression(children.get(0), "", RawType.NONE, irBB, manager, sourceBB.getContext(), sourceFunction.getParentModule(), targetFunction, irModule);
                Pair<IRValue, NSTLType> rightPair = VariableParser.parseIntegerExpression(children.get(1), "", leftPair.b, irBB, manager, sourceBB.getContext(), sourceFunction.getParentModule(), targetFunction, irModule);
                IRValue leftVal = leftPair.a,
                        rightVal = rightPair.a;
                
                boolean signed = ((RawType) leftPair.b).isSigned();
                
                // Get condition
                IRCondition condition = switch(conditionNode.getSymbol().getID()) {
                    case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL          -> IRCondition.E;
                    case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL      -> IRCondition.NE;
                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER        -> signed ? IRCondition.G : IRCondition.A;
                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL  -> signed ? IRCondition.GE : IRCondition.AE;
                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS           -> signed ? IRCondition.L : IRCondition.B;
                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL     -> signed ? IRCondition.LE : IRCondition.BE;
                    default -> throw new IllegalStateException(); // unreachable
                };
                
                // Make instruction
                return new IRBranchInstruction(IRBranchOperation.JCC, condition, leftVal, rightVal, trueID, new IRArgumentMapping(), falseID, new IRArgumentMapping(), irBB, lineNum);
            }
                
                
            default:
                // Not a comparison
                Pair<IRValue, NSTLType> condPair = VariableParser.parseIntegerExpression(conditionNode, "", RawType.NONE, irBB, manager, sourceBB.getContext(), sourceFunction.getParentModule(), targetFunction, irModule);
                IRValue condVal = condPair.a;
                
                if(condVal instanceof IRConstant irc) {
                    // If the value is constant, lower to an unconditional jump
                    IRIdentifier dest = (irc.getValue() != 0) ? trueID : falseID;
                    IRIdentifier eliminatedID = (irc.getValue() != 0) ? falseID : trueID;
                    ASTBasicBlock elimSource = (irc.getValue() != 0) ? sourceBB.getFalseSuccessor() : sourceBB.getTrueSuccessor();
                    
                    // Remove block from eliminated successor's predecessor list
                    IRBasicBlock elimSuccessor = targetFunction.getBasicBlock(eliminatedID);
                    if(elimSuccessor != null) {
                        elimSuccessor.removePredecessor(irBB.getID());
                    }
                    
                    elimSource.removePredecessor(sourceBB);
                    
                    if(irc.getValue() != 0) {
                        sourceBB.setFalseSuccessor(null);
                    } else {
                        sourceBB.setTrueSuccessor(null);
                    }
                    
                    return new IRBranchInstruction(IRBranchOperation.JMP, dest, new IRArgumentMapping(), irBB, lineNum);
                } else {
                    // If the value isn't constant, conditional branch
                    return new IRBranchInstruction(IRBranchOperation.JCC, IRCondition.NE, condVal, new IRConstant(0, IRType.NONE), trueID, new IRArgumentMapping(), falseID, new IRArgumentMapping(), irBB, lineNum);
                }
        }
    }
    
    /**
     * Parses a return value
     * @param irBB
     * @param sourceBB
     * @param manager
     * @param targetFunction
     * @param sourceFunction
     * @param irModule
     * @return
     * @throws CompilationException
     */
    private static IRBranchInstruction parseReturn(IRBasicBlock irBB, ASTBasicBlock sourceBB, SSAManager manager, IRFunction targetFunction, ASTFunction sourceFunction, IRModule irModule) throws CompilationException {
        // Compute return value & put in instruction
        IRValue retVal = new IRConstant(0, IRType.NONE);
        ASTNode retNode = sourceBB.getExitCode();
        
        if(retNode != null) {
            LOG.finest("Parsing return statement " + ASTUtil.detailed(retNode));
            
            if(retNode.getSymbol().getID() != NstlgrammarLexer.ID.TERMINAL_KW_NONE && retNode.getChildren().size() != 0) {
                NSTLType retType = sourceFunction.getHeader().getReturnType();
                Pair<IRValue, NSTLType> retPair = VariableParser.parseIntegerExpression(retNode.getChildren().get(0), "", retType, irBB, manager, sourceBB.getContext(), sourceFunction.getParentModule(), targetFunction, irModule);
                retVal = retPair.a;
                NSTLType getIType = retPair.b;
                
                ASTUtil.ensureTypesMatch(retType, getIType, false, retNode, ALOG, "from return statement");
            }
        }
        
        return new IRBranchInstruction(IRBranchOperation.RET, retVal, irBB, ASTUtil.getLineNumber(sourceBB.getExitCode()));
    }
    
    /**
     * Parse a value creation node
     * @param node
     * @param irBB
     * @param sourceBB
     * @param manager
     * @param targetFunction
     * @param sourceFunction
     * @param irModule
     * @throws CompilationException 
     */
    private static void parseValueCreation(ASTNode node, IRBasicBlock irBB, ASTBasicBlock sourceBB, SSAManager manager, IRFunction targetFunction, ASTFunction sourceFunction, IRModule irModule) throws CompilationException {
        LOG.finest("Parsing value creation " + ASTUtil.detailed(node));
        
        /*
         * value_creation ->
         *   KW_VARIABLE NAME KW_IS! type SEMI!
         * | KW_VARIABLE NAME KW_IS! type KW_GETS! variable_expression SEMI!
         * | KW_CONSTANT NAME KW_IS! type KW_GETS! constant_expression SEMI!
         * | KW_CONSTANT NAME KW_IS! type KW_GETS! constant_structure SEMI!;
         * {NAME KW_IS! type KW_GETS! variable_expression SEMI!}
         * 
         * VARIABLE NAME type                       Uninitialized local
         * VARIABLE NAME type variable_expression   Initialized local
         * NAME type variable_expression            Initialized local (from FOR initialization)
         * CONSTANT NAME type constant_expression   Local constant (general)
         * CONSTANT NAME type constant_structure    Local constant (structure)
         */
        
        List<ASTNode> children = node.getChildren();
        
        // What are we dealing with
        ASTNode nameNode, typeNode, valueNode;
        boolean isConstant;
        
        if(children.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_CONSTANT) {
            // Constant
            isConstant = true;
            nameNode = children.get(1);
            typeNode = children.get(2);
            valueNode = children.get(3);
        } else if(children.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_VARIABLE) {
            // Variable
            isConstant = false;
            nameNode = children.get(1);
            typeNode = children.get(2);
            
            if(children.size() == 4) {
                valueNode = children.get(3);
            } else {
                valueNode = null;
            }
        } else {
            // FOR initialization
            isConstant = false;
            nameNode = children.get(0);
            typeNode = children.get(1);
            valueNode = children.get(2);
        }
        
        String sourceName = nameNode.getValue();
        NSTLType type = TypeParser.parseType(typeNode, sourceFunction.getParentModule(), sourceBB.getContext());
        
        // Make sure the name doesn't exist locally
        if(sourceBB.getContext().localEntryExists(sourceName)) {
            ALOG.severe(node, "Duplicate local name: " + sourceName);
            throw new CompilationException();
        }
        
        if(isConstant) {
            // Create a constant
            String uniqueName = sourceFunction.getUnique(sourceName);
            TypedValue val = ConstantParser.parseConstantExpression(valueNode, sourceFunction.getParentModule(), sourceBB.getContext(), type, false, Level.SEVERE);
            ASTContextConstant con = new ASTContextConstant(sourceName, uniqueName, val);
            sourceBB.getContext().addEntry(con);
            
            // String constants add globals
            if(val instanceof StringType st) {
                irModule.addGlobal(new IRGlobal(new IRIdentifier(targetFunction.getID().getName() + "." + uniqueName, IRIdentifierClass.GLOBAL), IRType.I8, st.getValue(), true));
            }
            
            LOG.finest("Added " + con + " to local context of " + sourceBB.getName());
        } else {
            // Create a variable
            IRIdentifier irID = manager.getUniqueLocalID(sourceName);
            String uniqueName = irID.getName();
            
            ASTContextVariable acv = new ASTContextVariable(sourceName, uniqueName, type);
            sourceBB.getContext().addEntry(acv);
            
            LOG.finest("Added " + acv + " to local context of " + sourceBB.getName());
            
            // If the variable is a memory type, get its pointer
            if(type instanceof StructureType || type instanceof ArrayType) {
                // Memory
                IRIdentifier destID = manager.getUniqueLocalID(uniqueName);
                IRLinearInstruction stackInst = new IRLinearInstruction(IRLinearOperation.STACK, destID, IRType.I32, new IRConstant(type.getSize(), IRType.NONE), irBB, ASTUtil.getLineNumber(node));
                irBB.addInstruction(stackInst);
                manager.writeVariable(uniqueName, irBB.getID(), destID, IRType.I32);
                
                if(valueNode != null) {
                    if(type instanceof StructureType st) {
                        parseStructureLiteralAssignment(valueNode, destID, st, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                    } else if(type instanceof ArrayType at) {
                        parseArrayLiteralAssignment(valueNode, destID, at, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                    }
                }
            } else {
                // Integer
                if(valueNode != null) {
                    Pair<IRValue, NSTLType> pair = VariableParser.parseIntegerExpression(valueNode, uniqueName, type, irBB, manager, sourceBB.getContext(), sourceFunction.getParentModule(), targetFunction, irModule);
                    ASTUtil.ensureTypesMatch(type, pair.b, false, valueNode, ALOG, "from constant body");
                    manager.writeVariable(uniqueName, irBB.getID(), pair.a, type.getIRType());
                }
            }
        }
    }
    
    /**
     * Parse an assignment node
     * @param node
     * @param irBB
     * @param sourceBB
     * @param manager
     * @param targetFunction
     * @param sourceFunction
     * @param irModule
     * @throws CompilationException 
     */
    private static void parseAssignment(ASTNode node, IRBasicBlock irBB, ASTBasicBlock sourceBB, SSAManager manager, IRFunction targetFunction, ASTFunction sourceFunction, IRModule irModule) throws CompilationException {
        LOG.finest("Parsing assignment " + ASTUtil.detailed(node));
        
        /*
         * assignment -> reference KW_GETS! variable_expression SEMI!;
         * 
         * reference ->
         *   KW_AT subreference
         * | type KW_AT subreference
         * | KW_TO subreference
         * | subreference^;
         *
         * subreference ->
         *   OPEN_P! reference^ CLOSE_P!
         * | subreference DOT NAME
         * | NAME DOT NAME
         * | subreference KW_INDEX variable_expression
         * | NAME KW_INDEX variable_expression
         * | OPEN_P! function_call CLOSE_P!
         * | NAME;
         * 
         * AT subreference                          Compute subreference as value, use as STORE address
         * type AT subreference                     Compute subreference as value, use as STORE address
         * TO subreference                          Invalid
         * subreference DOT NAME                    Compute subreference DOT NAME as pointer, use as STORE address
         * NAME DOT NAME                            Compute NAME DOT NAME as pointer, use as STORE address
         * subreference INDEX variable_expression   Compute subreference INDEX variable_expression as pointer, use as STORE address
         * NAME INDEX variable_expression           Compute NAME INDEX variable_expression as pointer, use as STORE address
         * function_call                            Invalid
         * NAME                                     writeVariable(name, value)
         */
        
        List<ASTNode> children = node.getChildren();
        ASTNode refNode = children.get(0);
        ASTNode valNode = children.get(1);
        
        List<ASTNode> refChildren = children.get(0).getChildren();
        
        if(refNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_REFERENCE) {
            /*
             * AT subreference      Compute subreference as value, use as STORE address
             * type AT subreference Compute subreference as value, use as STORE address
             * TO subreference      Invalid
             */
            
            // Check that it isn't TO
            if(refChildren.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_TO) {
                ALOG.severe(refNode, "Invalid GETS target: " + ASTUtil.detailed(refNode));
                throw new CompilationException();
            }
            
            ASTNode subrefNode = refChildren.get(refChildren.size() - 1);
            
            Pair<IRValue, NSTLType> pair = ReferenceParser.parseReferenceAsValue(subrefNode, "", RawType.NONE, irBB, manager, sourceBB.getContext(), sourceFunction.getParentModule(), targetFunction, irModule);
            IRValue pointerValue = pair.a;
            NSTLType pointedType = pair.b;
            
            // Are we overriding the type of the subreference?
            if(refChildren.size() == 3) {
                // yes
                pointedType = TypeParser.parseType(refChildren.get(0), sourceFunction.getParentModule(), sourceBB.getContext());
            }
            
            // Get the assignment value
            if(pointedType instanceof StructureType st) {
                parseStructureLiteralAssignment(valNode, pointerValue, st, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
            } else if(pointedType instanceof ArrayType at) {
                parseArrayLiteralAssignment(valNode, pointerValue, at, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
            } else {
                // Integer
                Pair<IRValue, NSTLType> valPair = VariableParser.parseIntegerExpression(valNode, "", pointedType, irBB, manager, sourceBB.getContext(), sourceFunction.getParentModule(), targetFunction, irModule);
                ASTUtil.ensureTypesMatch(pointedType, valPair.b, false, valNode, ALOG, "from variable body");
                IRValue result = valPair.a;
                
                // STORE
                IRLinearInstruction storeInst = new IRLinearInstruction(IRLinearOperation.STORE, result, pointerValue, irBB, ASTUtil.getLineNumber(node));
                irBB.addInstruction(storeInst);
            }
        } else if(refChildren.size() == 3) {
            /*
             * subreference DOT NAME                    Compute subreference DOT NAME as pointer, use as STORE address
             * NAME DOT NAME                            Compute NAME DOT NAME as pointer, use as STORE address
             * subreference INDEX variable_expression   Compute subreference INDEX variable_expression as pointer, use as STORE address
             * NAME INDEX variable_expression           Compute NAME INDEX variable_expression as pointer, use as STORE address
             */
            Pair<IRValue, NSTLType> pair = ReferenceParser.parseReferenceAsPointer(refNode, "", RawType.PTR, irBB, manager, sourceBB.getContext(), sourceFunction.getParentModule(), targetFunction, irModule);
            IRValue pointerValue = pair.a;
            NSTLType pointerType = pair.b;
            
            // These references must return typed pointers
            if(pointerType instanceof PointerType pt) {
                NSTLType pointedType = pt.getPointedType();
                
                if(pointedType instanceof StructureType st) {
                    parseStructureLiteralAssignment(valNode, pointerValue, st, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                } else if(pointedType instanceof ArrayType at) {
                    parseArrayLiteralAssignment(valNode, pointerValue, at, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                } else {
                    // Integer
                    Pair<IRValue, NSTLType> valPair = VariableParser.parseIntegerExpression(valNode, "", pointedType, irBB, manager, sourceBB.getContext(), sourceFunction.getParentModule(), targetFunction, irModule);
                    ASTUtil.ensureTypesMatch(pointedType, valPair.b, false, valNode, ALOG, "from variable body");
                    IRValue result = valPair.a;
                    
                    // STORE
                    IRLinearInstruction storeInst = new IRLinearInstruction(IRLinearOperation.STORE, result, pointerValue, irBB, ASTUtil.getLineNumber(node));
                    irBB.addInstruction(storeInst);
                }
            } else {
                LOG.severe("parseReferenceAsPointer returned " + pointerType + " for " + ASTUtil.detailed(refNode));
                throw new IllegalStateException();
            }
        } else {
            // NAME writeVariable(name, value)
            String varName;
            
            if(refNode.getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_NAME) {
                varName = refNode.getValue();
            } else if(refChildren.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_NAME) {
                varName = refChildren.get(0).getValue();
            } else {
                // function_call    Invalid
                ALOG.severe(refNode, "Invalid GETS target: " + ASTUtil.detailed(refNode));
                throw new CompilationException();
            }
            
            // Does a variable with the name exist
            if(sourceFunction.getParentModule().variableExists(varName, sourceBB.getContext())) {
                boolean isGlobal;
                NSTLType varType;
                String varUName;
                
                // Yes. Is the variable local or global?
                if(sourceBB.getContext().variableExists(varName)) {
                    // It's local
                    ASTContextVariable acv = sourceBB.getContext().getVariable(varName);
                    isGlobal = false;
                    varType = acv.getType();
                    varUName = acv.getUniqueName();
                } else {
                    // It's global
                    isGlobal = true;
                    varType = sourceFunction.getParentModule().getGlobalVariableMap().get(varName).getType();
                    varUName = varName;
                }
                
                if(varType instanceof StructureType st) {
                    parseStructureLiteralAssignment(valNode, new IRIdentifier(isGlobal ? varName : varUName, isGlobal ? IRIdentifierClass.GLOBAL : IRIdentifierClass.LOCAL), st, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                } else if(varType instanceof ArrayType at) {
                    parseArrayLiteralAssignment(valNode, new IRIdentifier(isGlobal ? varName : varUName, isGlobal ? IRIdentifierClass.GLOBAL : IRIdentifierClass.LOCAL), at, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                } else {
                    // Integer
                    Pair<IRValue, NSTLType> valPair = VariableParser.parseIntegerExpression(valNode, manager.getUniqueLocalID(varUName).getName(), varType, irBB, manager, sourceBB.getContext(), sourceFunction.getParentModule(), targetFunction, irModule);
                    ASTUtil.ensureTypesMatch(varType, valPair.b, false, valNode, ALOG, "from variable body");
                    IRValue val = valPair.a;
                    
                    if(isGlobal) {
                        // Global = write to memory
                        IRLinearInstruction storeInst = new IRLinearInstruction(IRLinearOperation.STORE, val, new IRIdentifier(varName, IRIdentifierClass.GLOBAL), irBB, ASTUtil.getLineNumber(node));
                        irBB.addInstruction(storeInst);
                    } else {
                        // Local = assign
                        manager.writeVariable(varUName, irBB.getID(), val, varType.getIRType());
                    }
                }
            } else {
                // No
                ALOG.severe(refChildren.get(0), "Name " + varName + " is not a variable");
                throw new CompilationException();
            }
        }
    }
    
    /**
     * Parse a free-standing function call node
     * @param node
     * @param irBB
     * @param sourceBB
     * @param manager
     * @param targetFunction
     * @param sourceFunction
     * @param irModule
     * @throws CompilationException 
     */
    private static void parseFreeFunctionCall(ASTNode node, IRBasicBlock irBB, ASTBasicBlock sourceBB, SSAManager manager, IRFunction targetFunction, ASTFunction sourceFunction, IRModule irModule) throws CompilationException {
        LOG.finest("Parsing free-standing function call " + ASTUtil.detailed(node));
        
        /*
         * function_call ->
         *   KW_CALL! reference KW_WITH! KW_NONE
         * | KW_CALL! reference KW_WITH! argument_list;
         * 
         * argument_list -> variable_expression (COMMA! variable_expression)*;
         */
        ASTModule module = sourceFunction.getParentModule();
        
        List<ASTNode> children = node.getChildren();
        ASTNode refNode = children.get(0);
        ASTNode argNode = children.get(1);
        
        // Get func pointer & header
        Pair<IRValue, NSTLType> refPair = ReferenceParser.parseReferenceAsValue(refNode, "", RawType.PTR, irBB, manager, sourceBB.getContext(), module, targetFunction, irModule);
        IRValue refVal = refPair.a;
        
        FunctionHeader header = null;
        IRArgumentList irArgs = null;
        
        // If the ref is a global ID and its a function, get header
        if(refVal instanceof IRIdentifier refID) {
            if(refID.getIDClass() == IRIdentifierClass.GLOBAL && module.getFunctionMap().containsKey(refID.getName())) {
                // yay
                ASTFunction astFunc = module.getFunctionMap().get(refID.getName());
                header = astFunc.getHeader();
                irArgs = astFunc.getHeaderIR(irModule).getArguments();
            }
        }
        
        // Get args and construct CALLN instruction
        IRArgumentMapping argMap = VariableParser.parseFunctionArguments(argNode, irArgs, header, irBB, manager, sourceBB.getContext(), module, targetFunction, irModule);
        
        IRLinearInstruction callInst = new IRLinearInstruction(IRLinearOperation.CALLN, refVal, argMap, irBB, ASTUtil.getLineNumber(node));
        irBB.addInstruction(callInst);
    }
    
    /**
     * Handles assignment of a structure literal to a pointer
     * @param structNode
     * @param destPointer
     * @param destType
     * @param irBB
     * @param sourceBB
     * @param manager
     * @param targetFunction
     * @param sourceFunction
     * @param irModule
     * @throws CompilationException
     */
    private static void parseStructureLiteralAssignment(ASTNode structNode, IRValue destPointer, StructureType destType, IRBasicBlock irBB, ASTBasicBlock sourceBB, SSAManager manager, IRFunction targetFunction, ASTFunction sourceFunction, IRModule irModule) throws CompilationException {
        List<ASTNode> structChildren = structNode.getChildren();
        String structTypeName = structChildren.get(0).getValue();
        List<ASTNode> assignments = structChildren.get(1).getChildren();
        
        // Verify type matches
        if(!structTypeName.equals(destType.getName())) {
            ALOG.severe(structNode, "Structure type " + structTypeName + " does not match expected type " + destType.getName());
            throw new CompilationException();
        }
        
        // Verify assignment number matches
        if(assignments.size() != destType.getMemberNames().size()) {
            ALOG.severe(structNode, destType.getName() + " expects " + destType.getMemberNames().size() + " members, got " + assignments.size());
            throw new CompilationException();
        }
        
        Set<String> assignedMembers = new HashSet<>();
        
        // Do assignments in written order
        for(ASTNode assignment : assignments) {
            List<ASTNode> assignmentChildren = assignment.getChildren();
            /*
             * NAME expression
             * NAME structure
             */
            String memberName = assignmentChildren.get(0).getValue();
            ASTNode valNode = assignmentChildren.get(1);
            
            // Ensure member is in the struct and exists
            if(assignedMembers.contains(memberName)) {
                ALOG.severe(assignment, "Member " + memberName + " in " + structTypeName + " is assigned twice in structure literal");
                throw new CompilationException();
            } else if(!destType.getMemberNames().contains(memberName)) {
                ALOG.severe(assignment, "Member " + memberName + " does not exist in structure type " + structTypeName);
                throw new CompilationException();
            }
            
            int memberOffset = destType.getMemberOffset(memberName);
            NSTLType memberType = destType.getMemberType(memberName);
            
            // Destination = pointer + member offset
            IRValue memberPointer;
            
            if(memberOffset == 0) {
                memberPointer = destPointer;
            } else {
                IRIdentifier mpid = manager.getUniqueLocalID("");
                memberPointer = mpid;
                
                IRLinearInstruction mpli = new IRLinearInstruction(IRLinearOperation.ADD, mpid, IRType.I32, destPointer, new IRConstant(memberOffset, IRType.I32), irBB, ASTUtil.getLineNumber(assignment));
                irBB.addInstruction(mpli);
            }
            
            if(valNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_CONSTANT_STRUCTURE || valNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_VARIABLE_STRUCTURE) {
                // Value is a structure, recurse
                if(memberType instanceof StructureType st) {
                    parseStructureLiteralAssignment(valNode, memberPointer, st, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                } else {
                    ALOG.severe(valNode, "Expected " + memberType + ", got structure " + ASTUtil.detailed(valNode));
                }
            } else if(valNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_VARIABLE_ARRAY) {
                // Value is an array, call function
                if(memberType instanceof ArrayType at) {
                    parseArrayLiteralAssignment(valNode, memberPointer, at, irBB, sourceBB, manager, targetFunction, sourceFunction, irModule);
                } else {
                    ALOG.severe(valNode, "Expected " + memberType + ", got array " + ASTUtil.detailed(valNode));
                }
            } else {
                // Integer. Compute & store
                Pair<IRValue, NSTLType> memberPair = VariableParser.parseIntegerExpression(valNode, "", memberType, irBB, manager, sourceBB.getContext(), sourceFunction.getParentModule(), targetFunction, irModule);
                IRValue memberVal = memberPair.a;
                
                ASTUtil.ensureTypesMatch(memberType, memberPair.b, false, structNode, ALOG, "from strucrure member");
                
                IRLinearInstruction storeInst = new IRLinearInstruction(IRLinearOperation.STORE, memberVal, memberPointer, irBB, ASTUtil.getLineNumber(valNode));
                irBB.addInstruction(storeInst);
            }
        }
    }
    
    /**
     * Handles assignment of an array literal to a pointer
     * @param arrayNode
     * @param destPointer
     * @param destType
     * @param irBB
     * @param sourceBB
     * @param manager
     * @param targetFunction
     * @param sourceFunction
     * @param irModule
     * @throws CompilationException
     */
    private static void parseArrayLiteralAssignment(ASTNode arrayNode, IRValue destPointer, ArrayType destType, IRBasicBlock irBB, ASTBasicBlock sourceBB, SSAManager manager, IRFunction targetFunction, ASTFunction sourceFunction, IRModule irModule) throws CompilationException {
        // TODO
        LOG.severe("UNIMPLEMENTED: ARRAY ASSIGNEMNT");
        throw new CompilationException();
    }
}
