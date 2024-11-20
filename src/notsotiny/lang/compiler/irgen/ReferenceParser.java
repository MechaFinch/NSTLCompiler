package notsotiny.lang.compiler.irgen;

import java.util.List;
import java.util.logging.Logger;

import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.compiler.ASTUtil;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.irgen.context.ASTContextConstant;
import notsotiny.lang.compiler.irgen.context.ASTContextEntry;
import notsotiny.lang.compiler.irgen.context.ASTContextTree;
import notsotiny.lang.compiler.irgen.context.ASTContextVariable;
import notsotiny.lang.compiler.types.ArrayType;
import notsotiny.lang.compiler.types.FunctionHeader;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.compiler.types.PointerType;
import notsotiny.lang.compiler.types.RawType;
import notsotiny.lang.compiler.types.StringType;
import notsotiny.lang.compiler.types.StructureType;
import notsotiny.lang.compiler.types.TypedRaw;
import notsotiny.lang.compiler.types.TypedValue;
import notsotiny.lang.ir.IRArgumentList;
import notsotiny.lang.ir.IRArgumentMapping;
import notsotiny.lang.ir.IRBasicBlock;
import notsotiny.lang.ir.IRConstant;
import notsotiny.lang.ir.IRFunction;
import notsotiny.lang.ir.IRIdentifier;
import notsotiny.lang.ir.IRIdentifierClass;
import notsotiny.lang.ir.IRLinearInstruction;
import notsotiny.lang.ir.IRLinearOperation;
import notsotiny.lang.ir.IRModule;
import notsotiny.lang.ir.IRType;
import notsotiny.lang.ir.IRValue;
import notsotiny.lang.parser.NstlgrammarLexer;
import notsotiny.lang.parser.NstlgrammarParser;
import notsotiny.lang.util.Pair;

/**
 * Parses reference and subreference nodes
 */
public class ReferenceParser {
    
    private static Logger LOG = Logger.getLogger(VariableParser.class.getName());
    private static ASTLogger ALOG = new ASTLogger(LOG);
    
    /**
     * Parses a reference or subreference. Returns the value being referenced.
     * @param node
     * @param destName If returning an IRIdentifier, it will use this name.
     * @param expectedType
     * @param manager
     * @param context
     * @param sourceModule
     * @param func
     * @param irModule
     * @return {Value/ID, real type}
     * @throws CompilationException 
     */
    public static Pair<IRValue, NSTLType> parseReferenceAsValue(ASTNode node, String destName, NSTLType expectedType, IRBasicBlock irBB, SSAManager manager, ASTContextTree context, ASTModule sourceModule, IRFunction func, IRModule irModule) throws CompilationException {
        LOG.finest("Parsing value of reference " + ASTUtil.detailed(node));
        
        // If the dest name is empty, generate one
        if(destName.equals("")) {
            destName = manager.getUniqueLocalID("").getName();
        }
        
        List<ASTNode> children = node.getChildren();
        
        if(node.getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_NAME) {
            return getNameValue(node, destName, expectedType, irBB, manager, context, sourceModule, func, irModule);
        } else if(node.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_REFERENCE) {
            /*
             * reference ->
             *   KW_AT subreference
             * | type KW_AT subreference
             * | KW_TO subreference
             * 
             * AT subref        parseReferenceAsValue(subref), load
             * type AT subref   parseReferenceAsValue(subref), load
             * TO subref        If the subref is a DOT or INDEX, parseReferenceAsPointer(subref) and return it.
             *                  If the subref is a NAME
             *                      If the name is a global                         Return it
             *                      If the name is a local and an array/structure   Return readVariable(name)
             *                      Otherwise, invalid
             *                  otherwise, invalid
             */
            
            if(children.size() == 3 || children.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_AT) {
                // AT
                // Parse subref as value, load it
                if(children.size() == 3) {
                    // Parse conversion type
                    ASTNode typeNode = children.get(0);
                    NSTLType convertType = TypeParser.parseType(typeNode, sourceModule, context);
                    
                    ASTUtil.ensureTypesMatch(expectedType, convertType, false, typeNode, ALOG, "from typed AT");
                    
                    // Infer type if needed
                    if(expectedType.equals(RawType.NONE)) {
                        expectedType = convertType;
                    }
                }
                
                // Get subref value
                ASTNode subrefNode = children.get(children.size() - 1);
                Pair<IRValue, NSTLType> subrefPair = parseReferenceAsValue(subrefNode, "", RawType.PTR, irBB, manager, context, sourceModule, func, irModule);
                IRValue subrefVal = subrefPair.a;
                NSTLType subrefType = subrefPair.b;
                
                // Typed or untyped pointer?
                if(subrefType instanceof PointerType pt) {
                    // Typed. Check the type.
                    ASTUtil.ensureTypesMatch(expectedType, pt.getPointedType(), false, subrefNode, ALOG, "from reference");
                    
                    // Infer type if needed
                    if(expectedType.equals(RawType.NONE)) {
                        expectedType = pt.getPointedType();
                    }
                }
                
                // Load
                IRIdentifier destID = new IRIdentifier(destName, IRIdentifierClass.LOCAL);
                IRLinearInstruction irli = new IRLinearInstruction(IRLinearOperation.LOAD, destID, expectedType.getIRType(), subrefVal, irBB, ASTUtil.getLineNumber(node));
                irBB.addInstruction(irli);
                
                return new Pair<>(destID, expectedType);
            } else {
                // TO
                ASTNode subrefNode = children.get(1);
                List<ASTNode> subrefChildren = subrefNode.getChildren();
                
                // Ensure valid target
                if(subrefNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_REFERENCE ||
                   subrefChildren.get(0).getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_FUNCTION_CALL) {
                    // Invalid targets
                    ALOG.severe(subrefNode, "Invalid TO target: " + ASTUtil.detailed(subrefNode));
                    throw new CompilationException();
                }
                
                /*
                 * * TO subref  If the subref is a DOT or INDEX, parseReferenceAsPointer(subref) and return it.
                 *              If the subref is a NAME
                 *                  If the name is a global                         Return it
                 *                  If the name is a local and an array/structure   Return readVariable(name)
                 *                  Otherwise, invalid
                 *              otherwise, invalid
                 */
                if(subrefChildren.size() == 3) {
                    // DOT or INDEX. Parse subref as pointer & return it
                    return parseReferenceAsPointer(subrefNode, destName, expectedType, irBB, manager, context, sourceModule, func, irModule);
                } else {
                    // NAME
                    String targetName = subrefChildren.get(0).getValue();
                    
                    // Does a variable with the name exist
                    if(sourceModule.variableExists(targetName, context)) {
                        if(context.variableExists(targetName)) {
                            // It exists and is a local
                            ASTContextVariable acv = context.getVariable(targetName);
                            LOG.finest("Found local variable " + acv);
                            NSTLType targetType = acv.getType();
                            
                            // Is it an applicable type
                            if(!(targetType instanceof ArrayType || targetType instanceof StructureType)) {
                                // No
                                ALOG.severe(subrefNode, "Local variable " + targetName + " cannot be pointed to");
                                throw new CompilationException();
                            }
                            
                            // Return the pointer
                            try {
                                IRValue ptrVal = manager.readVariable(acv.getUniqueName(), irBB.getID());
                                NSTLType toType = new PointerType(acv.getType());
                                ASTUtil.ensureTypesMatch(expectedType, toType, false, subrefNode, ALOG, "from TO target");
                                return new Pair<>(ptrVal, toType);
                            } catch(NullPointerException e) {
                                ALOG.severe(node, "Variable " + acv.getSourceName() + " is undefined");
                                throw new CompilationException();
                            }
                        } else {
                            // It's a global. Return it.
                            LOG.finest("Found global variable " + targetName);
                            
                            NSTLType toType = new PointerType(sourceModule.getGlobalVariableMap().get(targetName).getType());
                            ASTUtil.ensureTypesMatch(expectedType, toType, false, subrefNode, ALOG, "from TO target");
                            return new Pair<>(new IRIdentifier(targetName, IRIdentifierClass.GLOBAL), toType);
                        }
                    } else if(context.constantExists(targetName)) {
                        // Local string constants can be pointed to
                        ASTContextConstant acc = context.getConstant(targetName);
                        TypedValue tv = acc.getValue();
                        
                        if(tv instanceof StringType) {
                            return new Pair<>(new IRIdentifier(func.getID().getName() + "." + acc.getUniqueName(), IRIdentifierClass.GLOBAL), new PointerType(RawType.U8));
                        } else {
                            ALOG.finest(subrefNode, "Cannot point to non-string local constants: TO " + ASTUtil.detailed(subrefNode));
                            throw new CompilationException();
                        }
                    } else if(sourceModule.getFunctionMap().containsKey(targetName)) {
                        // No variable, but a function does. Return it
                        LOG.finest("Found function " + targetName);
                        ASTUtil.ensureTypesMatch(expectedType, RawType.PTR, false, subrefNode, ALOG, "from TO target");
                        return new Pair<>(new IRIdentifier(targetName, IRIdentifierClass.GLOBAL), RawType.PTR);
                    } else if(targetName.startsWith("_")) {
                        // Refernces something from an import
                        LOG.finest("Found library reference " + targetName);
                        ASTUtil.ensureTypesMatch(expectedType, RawType.PTR, false, subrefNode, ALOG, "from TO target");
                        return new Pair<>(new IRIdentifier(targetName, IRIdentifierClass.GLOBAL), RawType.PTR);
                    } else {
                        // No variable or function has this name
                        ALOG.severe(subrefNode, "Name " + targetName + " is not a variable or function");
                        throw new CompilationException();
                    }
                }
            }
        } else {
            /*
             * subreference ->
             *   OPEN_P! reference^ CLOSE_P!
             * | subreference DOT NAME
             * | NAME DOT NAME
             * | subreference KW_INDEX variable_expression
             * | NAME KW_INDEX variable_expression
             * | OPEN_P! function_call CLOSE_P!
             * | NAME;
             * 
             * subref/NAME DOT NAME     parseReferenceAsPointer(node), load if integer
             * subref/NAME INDEX expr   parseReferenceAsPointer(node), load if integer
             * function_call            Call the function
             * NAME                     If name is a global, load it
             *                          If the name is a local, readVariable it
             */
            
            if(children.size() == 3) {
                // DOT/INDEX
                Pair<IRValue, NSTLType> pair = parseReferenceAsPointer(node, "", RawType.PTR, irBB, manager, context, sourceModule, func, irModule);
                IRValue ptrVal = pair.a;
                NSTLType ptrType = pair.b;
                
                // Check type if possible
                if(ptrType instanceof PointerType pt) {
                    ASTUtil.ensureTypesMatch(expectedType, pt.getPointedType(), false, node, ALOG, "from reference");
                    
                    if(expectedType.equals(RawType.NONE)) {
                        expectedType = pt.getPointedType();
                    }
                }
                
                if(expectedType instanceof ArrayType || expectedType instanceof StructureType) {
                    // Arrays/structures don't load
                    return new Pair<>(ptrVal, expectedType);
                }
                
                // Load
                IRIdentifier destID = new IRIdentifier(destName, IRIdentifierClass.LOCAL);
                IRLinearInstruction irli = new IRLinearInstruction(IRLinearOperation.LOAD, destID, expectedType.getIRType(), ptrVal, irBB, ASTUtil.getLineNumber(node));
                irBB.addInstruction(irli);
                
                return new Pair<>(destID, expectedType);
            } else if(children.get(0).getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_FUNCTION_CALL) {
                /*
                 * function_call ->
                 *   KW_CALL! reference KW_WITH! KW_NONE
                 * | KW_CALL! reference KW_WITH! argument_list;
                 * 
                 * argument_list -> variable_expression (COMMA! variable_expression)*;
                 */
                List<ASTNode> funcChildren = children.get(0).getChildren();
                ASTNode refNode = funcChildren.get(0);
                ASTNode argNode = funcChildren.get(1);
                
                // Get func pointer & header
                Pair<IRValue, NSTLType> refPair = ReferenceParser.parseReferenceAsValue(refNode, "", RawType.PTR, irBB, manager, context, sourceModule, func, irModule);
                IRValue refVal = refPair.a;
                
                FunctionHeader header = null;
                IRArgumentList irArgs = null;
                NSTLType returnType = RawType.NONE;
                
                // If the ref is a global ID and its a function, get header
                if(refVal instanceof IRIdentifier refID) {
                    if(refID.getIDClass() == IRIdentifierClass.GLOBAL && sourceModule.getFunctionMap().containsKey(refID.getName())) {
                        // yay
                        ASTFunction astFunc = sourceModule.getFunctionMap().get(refID.getName());
                        header = astFunc.getHeader();
                        irArgs = astFunc.getHeaderIR(irModule).getArguments();
                        returnType = header.getReturnType();
                        
                        if(expectedType.equals(RawType.NONE)) {
                            expectedType = returnType;
                        }
                    }
                }
                
                ASTUtil.ensureTypesMatch(expectedType, returnType, false, children.get(0), ALOG, "from function call");
                
                // Get args and construct CALLR instruction
                IRArgumentMapping argMap = VariableParser.parseFunctionArguments(argNode, irArgs, header, irBB, manager, context, sourceModule, func, irModule);
                IRIdentifier destID = new IRIdentifier(destName, IRIdentifierClass.LOCAL);
                IRLinearInstruction callInst = new IRLinearInstruction(IRLinearOperation.CALLR, destID, expectedType.getIRType(), refVal, argMap, irBB, ASTUtil.getLineNumber(node));
                irBB.addInstruction(callInst);
                
                return new Pair<>(destID, expectedType);
            } else {
                // Name
                return getNameValue(children.get(0), destName, expectedType, irBB, manager, context, sourceModule, func, irModule);
            }
        }
    }
    
    /**
     * Gets the value of a name
     * @param nameNode
     * @param destName
     * @param expectedType
     * @param irBB
     * @param manager
     * @param context
     * @param sourceModule
     * @param func
     * @param irModule
     * @return
     * @throws CompilationException
     */
    private static Pair<IRValue, NSTLType> getNameValue(ASTNode node, String destName, NSTLType expectedType, IRBasicBlock irBB, SSAManager manager, ASTContextTree context, ASTModule sourceModule, IRFunction func, IRModule irModule) throws CompilationException {
        // Does it exist
        String refName = node.getValue();
        if(sourceModule.variableExists(refName, context) || sourceModule.constantExists(refName, context)) {
            // Local or global?
            if(context.entryExists(refName)) {
                // Local
                ASTContextEntry ace = context.getEntry(refName);
                if(ace instanceof ASTContextVariable acv) {
                    // Local variable
                    LOG.finest("Got local variable " + acv);
                    ASTUtil.ensureTypesMatch(expectedType, acv.getType(), false, node, ALOG, "from local variable " + acv);
                    
                    try {
                        IRValue irv = manager.readVariable(acv.getUniqueName(), irBB.getID());
                        return new Pair<>(irv, acv.getType());
                    } catch(NullPointerException e) {
                        ALOG.severe(node, "Variable " + acv.getSourceName() + " is undefined");
                        throw new CompilationException();
                    }
                } else if(ace instanceof ASTContextConstant acc) {
                    // Local constant
                    TypedValue tv = acc.getValue();
                    ASTUtil.ensureTypesMatch(expectedType, tv.getType(), false, node, ALOG, "from local constant " + acc);
                    
                    IRValue irv = new IRConstant((int) ((TypedRaw) tv).getResolvedValue(), tv.getType().getIRType());
                    return new Pair<>(irv, tv.getType());
                } else {
                    LOG.severe("Not a variable or constant: " + ace);
                    throw new IllegalStateException();
                }
            } else {
                // Global
                if(sourceModule.getGlobalVariableMap().containsKey(refName)) {
                    // Global variable
                    TypedValue tv = sourceModule.getGlobalVariableMap().get(refName);
                    NSTLType type = tv.getType();
                    
                    ASTUtil.ensureTypesMatch(expectedType, type, false, node, ALOG, "from global variable " + refName);
                    
                    IRIdentifier globalID = new IRIdentifier(refName, IRIdentifierClass.GLOBAL);
                    IRIdentifier localID = new IRIdentifier(destName, IRIdentifierClass.LOCAL);
                    
                    // Arrays and structures yield pointers to their contents
                    if(type instanceof ArrayType || type instanceof StructureType) {
                        return new Pair<>(globalID, type);
                    }
                    
                    LOG.finest("Loading " + globalID + " to " + localID);
                    IRLinearInstruction loadInst = new IRLinearInstruction(IRLinearOperation.LOAD, localID, type.getIRType(), globalID, irBB, ASTUtil.getLineNumber(node));
                    irBB.addInstruction(loadInst);
                    return new Pair<>(localID, type);
                } else {
                    // Global constant
                    TypedValue tv = sourceModule.getConstantValue(refName);
                    ASTUtil.ensureTypesMatch(expectedType, tv.getType(), false, node, ALOG, "from local constant " + refName);
                    
                    IRValue irv = new IRConstant((int) ((TypedRaw) tv).getResolvedValue(), tv.getType().getIRType());
                    return new Pair<>(irv, tv.getType());
                }
            }
        } else if(sourceModule.getFunctionMap().containsKey(refName)) {
            LOG.finest("Found function " + refName);
            ASTUtil.ensureTypesMatch(expectedType, RawType.PTR, false, node, ALOG, "from TO target");
            return new Pair<>(new IRIdentifier(refName, IRIdentifierClass.GLOBAL), RawType.PTR);
        } else {
            // No.
            ALOG.severe(node, "Name " + refName + " is not a constant, variable, or function");
            throw new CompilationException();
        }
    }
    
    /**
     * Parses a reference or subreference. Returns a pointer to the value being referenced.
     * @param node
     * @param destName If returning an IRIdentifier, it will use this name. 
     * @param expectedType
     * @param manager
     * @param context
     * @param sourceModule
     * @param func
     * @param irModule
     * @return {Value/ID, real type}
     * @throws CompilationException 
     */
    public static Pair<IRValue, NSTLType> parseReferenceAsPointer(ASTNode node, String destName, NSTLType expectedType, IRBasicBlock irBB, SSAManager manager, ASTContextTree context, ASTModule sourceModule, IRFunction func, IRModule irModule) throws CompilationException {
        LOG.finest("Parsing pointer of reference " + ASTUtil.detailed(node));
        
        // If the dest name is empty, generate one
        if(destName.equals("")) {
            destName = manager.getUniqueLocalID("").getName();
        }
        
        // expectedType should be PTR, or typed pointer
        NSTLType expectedContainedType;
        
        if(expectedType.equals(RawType.PTR)) {
            expectedContainedType = RawType.NONE;
        } else if(expectedType instanceof PointerType pt) {
            expectedContainedType = pt.getPointedType();
        } else {
            ALOG.severe(node, "Invalid expected type for pointer computation: " + expectedType);
            throw new CompilationException();
        }
        
        /*
         * Only DOT and INDEX make it here
         * subreference ->
         * | subreference DOT NAME
         * | NAME DOT NAME
         * | subreference KW_INDEX variable_expression
         * | NAME KW_INDEX variable_expression
         * 
         * - Get value of name/subref
         *   - DOT values are allowed to be structs or struct pointers
         *   - INDEX values are allowed to be arrays, or typed pointers. Pointers to arrays use the size of the array's contained type.
         * - Add offset of name/index
         * - return it
         */
        
        List<ASTNode> children = node.getChildren();
        ASTNode sourceNode = children.get(0);
        ASTNode offsNode = children.get(2);
        
        // Get source
        Pair<IRValue, NSTLType> sourcePair = parseReferenceAsValue(sourceNode, "", RawType.NONE, irBB, manager, context, sourceModule, func, irModule);
        IRValue sourceValue = sourcePair.a;
        NSTLType sourceType = sourcePair.b;
        
        if(children.get(1).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_DOT) {
            // Structure access
            StructureType st;
            
            // Get structure type so we can get member offsets
            if(sourceType instanceof StructureType sst) {
                st = sst;
            } else if(sourceType instanceof PointerType pt && pt.getPointedType() instanceof StructureType sst) {
                st = sst;
            } else {
                ALOG.severe(sourceNode, "Structure access requires a structure or structure pointer. Got " + sourceType);
                throw new CompilationException();
            }
            
            // Does the member exist?
            String memberName = offsNode.getValue();
            if(!st.getMemberNames().contains(memberName)) {
                // Invalid member
                ALOG.severe(offsNode, "Structure " + st + " does not have member " + memberName);
                throw new CompilationException();
            }
            
            // Is it the right type?
            NSTLType memberType = st.getMemberType(memberName);
            ASTUtil.ensureTypesMatch(expectedContainedType, memberType, false, sourceNode, ALOG, "from structure access");
            
            // Emit addition if needed
            int offset = st.getMemberOffset(memberName);
            if(offset != 0) {
                IRIdentifier destID = new IRIdentifier(destName, IRIdentifierClass.LOCAL);
                IRLinearInstruction irli = new IRLinearInstruction(IRLinearOperation.ADD, destID, IRType.I32, sourceValue, new IRConstant(offset, IRType.I32));
                irBB.addInstruction(irli);
                
                return new Pair<>(destID, new PointerType(memberType));
            } else {
                return new Pair<>(sourceValue, new PointerType(memberType));
            }
        } else {
            // Array access
            NSTLType containedType;
            int size;
            
            // Get member type
            if(sourceType instanceof ArrayType at) {
                containedType = at.getMemberType();
                size = at.getLength();
            } else if(sourceType instanceof PointerType pt) {
                if(pt.getPointedType() instanceof ArrayType at) {
                    containedType = at.getMemberType();
                    size = at.getLength();
                } else {
                    containedType = pt.getPointedType();
                    size = 0;
                }
            } else {
                // Can't determine type
                ALOG.severe(sourceNode, "Indexed access requires an array, array pointer, or other typed pointer. Got " + sourceType);
                throw new CompilationException();
            }
            
            // Is it the right type?
            ASTUtil.ensureTypesMatch(expectedContainedType, containedType, false, sourceNode, ALOG, "from indexed access");
            
            // Get the index
            Pair<IRValue, NSTLType> indexPair = VariableParser.parseIntegerExpression(offsNode, "", RawType.NONE, irBB, manager, context, sourceModule, func, irModule);
            IRValue indexVal = indexPair.a;
            NSTLType indexType = indexPair.b;
            
            if(indexVal instanceof IRConstant irc) {
                // If the index is constant, check bounds
                if(irc.getValue() < 0 || (size != 0 && irc.getValue() >= size)) {
                    ALOG.severe(offsNode, "Constant index " + indexVal + " is out of bounds for array " + sourceType);
                    throw new CompilationException();
                }
                
                // If zero, we don't need to do anything 
                if(irc.getValue() == 0) {
                    return new Pair<>(sourceValue, new PointerType(containedType));
                }
            }
            
            // If the index is not an I32, zx it
            if(indexVal instanceof IRConstant irc && irc.getType() != IRType.I32) {
                indexVal = new IRConstant(irc.getValue(), IRType.I32);
            } else if(indexType.getSize() < 4) {
                IRIdentifier zxTmp = manager.getUniqueLocalID("");
                IRLinearInstruction zxInst = new IRLinearInstruction(IRLinearOperation.ZX, zxTmp, IRType.I32, indexVal, irBB, ASTUtil.getLineNumber(node));
                irBB.addInstruction(zxInst);
                indexVal = zxTmp;
            }
            
            // Emit multiply if needed
            if(containedType.getSize() != 1) {
                IRIdentifier mulTmp = manager.getUniqueLocalID("");
                IRLinearInstruction mulInst = new IRLinearInstruction(IRLinearOperation.MULU, mulTmp, IRType.I32, indexVal, new IRConstant(containedType.getSize(), IRType.I32), irBB, ASTUtil.getLineNumber(node));
                irBB.addInstruction(mulInst);
                indexVal = mulTmp;
            }
            
            // Emit add
            IRIdentifier destID = new IRIdentifier(destName, IRIdentifierClass.LOCAL); 
            IRLinearInstruction addInst = new IRLinearInstruction(IRLinearOperation.ADD, destID, IRType.I32, sourceValue, indexVal, irBB, ASTUtil.getLineNumber(node));
            irBB.addInstruction(addInst);
            
            return new Pair<>(destID, new PointerType(containedType));
        }
    }
    
}
