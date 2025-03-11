package notsotiny.lang.compiler.codegen.pattern;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.cenotelie.hime.redist.ASTNode;
import fr.cenotelie.hime.redist.ParseError;
import fr.cenotelie.hime.redist.ParseResult;
import notsotiny.asm.Register;
import notsotiny.lang.compiler.ASTUtil;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.NSTLCompiler;
import notsotiny.lang.compiler.aasm.AASMCompileConstant;
import notsotiny.lang.compiler.aasm.AASMInstruction;
import notsotiny.lang.compiler.aasm.AASMMachineRegister;
import notsotiny.lang.compiler.aasm.AASMMemory;
import notsotiny.lang.compiler.aasm.AASMOperation;
import notsotiny.lang.compiler.aasm.AASMPart;
import notsotiny.lang.compiler.aasm.AASMPatternIndex;
import notsotiny.lang.compiler.aasm.AASMPatternReference;
import notsotiny.lang.compiler.codegen.dag.ISelDAGOperation;
import notsotiny.lang.compiler.codegen.dag.ISelDAGPatternOperation;
import notsotiny.lang.compiler.codegen.dag.ISelDAGProducerOperation;
import notsotiny.lang.ir.parts.IRType;

/**
 * Compiles ISelPatterns
 * 
 * Uses the generated lexer/parser and constructs pattern objects
 */
public class ISelPatternCompiler {
    
    private static Logger LOG = Logger.getLogger(ISelPatternCompiler.class.getName());
    
    /**
     * Compiles patterns from a file
     * @param patternStream
     * @return
     * @throws IOException 
     */
    public static Map<String, List<ISelPattern>> compilePatterns(InputStream patternStream) throws IOException, CompilationException {
        Map<String, List<ISelPattern>> patternMap = new HashMap<>();
        
        // Parse the given file
        IselPatternLexer lexer = new IselPatternLexer(new InputStreamReader(patternStream));
        IselPatternParser parser = new IselPatternParser(lexer);
        ParseResult result = parser.parse();
        
        if(result.getErrors().size() != 0) {
            LOG.severe("Encountered errors parsing instruction selection patterns.");
            for(ParseError e : result.getErrors()) {
                LOG.severe("ParseError: " + e);
            }
            
            throw new CompilationException();
        }
        
        // Parsed successfully. Compile patterns
        ASTNode root = result.getRoot();
        LOG.finest("Instruction selection pattern AST");
        LOG.finest(() -> { NSTLCompiler.printTree(LOG, root, new boolean[] {}); return ""; });
        
        // for each group
        for(ASTNode groupNode : root.getChildren()) {
            String groupName = groupNode.getChildren().get(0).getValue();
            List<ISelPattern> patterns = new ArrayList<>();
            
            LOG.fine("Compiling pattern group " + groupName);
            
            // Compile each conversion
            List<ASTNode> conversions = groupNode.getChildren().get(1).getChildren();
            
            for(int i = 0; i < conversions.size(); i++) {
                patterns.add(compileConversion(conversions.get(i), groupName, i));
            }
            
            // And add to group
            patternMap.put(groupName, patterns);
        }
        
        return patternMap;
    }
    
    /**
     * Compiles a conversion node to an ISelPattern
     * @param conversionNode
     * @param groupName
     * @param conversionNumber
     * @return
     */
    private static ISelPattern compileConversion(ASTNode conversionNode, String groupName, int conversionNumber) {
        List<ASTNode> children = conversionNode.getChildren();
        ASTNode expressionNode = children.get(0);
        ASTNode assemblyNode = children.get(1);
        
        LOG.finer("Compiling conversion " + conversionNumber + " of " + groupName);
        
        // Pattern
        ISelPatternNode pattern = compileExpression(expressionNode);
        
        // Assembly
        List<AASMPart> assembly = new ArrayList<>();
        
        for(ASTNode partNode : assemblyNode.getChildren()) {
            assembly.add(compileAssembly(partNode));
        }
        
        LOG.finer(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            
            boolean prev = false;
            for(AASMPart part : assembly) {
                if(prev) {
                    sb.append("; ");
                }
                sb.append(part);
                prev = true;
            }
            
            sb.append("]");
            return "got " + pattern + " -> " + sb.toString();
        });
        
        // Pack into pattern
        ISelPattern pat = new ISelPattern(pattern, assembly, groupName, conversionNumber);
        pattern.setAsRoot(pat);
        
        return pat;
    }
    
    /**
     * Compiles an expression
     * @param expressionNode
     * @return
     */
    private static ISelPatternNode compileExpression(ASTNode expressionNode) {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.finest("Compiling expression " + ASTUtil.detailed(expressionNode));
        }
        
        List<ASTNode> children = expressionNode.getChildren();
        int i = 0;
        
        switch(expressionNode.getSymbol().getID()) {
            case IselPatternParser.ID.VARIABLE_EXPR_NODE: {
                // ISelPatternNodeNode
                String identifier = null;
                IRType type = IRType.NONE;
                ISelDAGOperation op;
                List<ISelPatternNode> arguments = new ArrayList<>();
                
                // do we have id/type
                if(children.size() > 2) {
                    // ye
                    if(children.size() > 3) {
                        // both
                        ASTNode idNode = children.get(i++);
                        ASTNode typeNode = children.get(i++);
                        
                        identifier = idNode.getValue();
                        type = IRType.fromString(typeNode.getValue());
                    } else {
                        // one
                        ASTNode idTypeNode = children.get(i++);
                        
                        if(idTypeNode.getSymbol().getID() == IselPatternLexer.ID.TERMINAL_IDENTIFIER) {
                            // just ID
                            identifier = idTypeNode.getValue();
                        } else {
                            // just type
                            type = IRType.fromString(idTypeNode.getValue());
                        }
                    }
                }
                
                // operation & arguments
                op = ISelDAGOperation.fromString(children.get(i++).getValue());
                
                List<ASTNode> args = children.get(i).getChildren();
                
                if(op == ISelDAGPatternOperation.LOCAL) {
                    // argument should be a single identifier
                    if(args.size() != 1) {
                        throw new IllegalArgumentException("Expected identifier for LOCAL, got " + args);
                    }
                    
                    ASTNode id = args.get(0);
                    
                    if(id.getSymbol().getID() != IselPatternLexer.ID.TERMINAL_IDENTIFIER) {
                        throw new IllegalArgumentException("Expected identifier for LOCAL, got " + ASTUtil.detailed(id));
                    }
                    
                    return new ISelPatternNodeLocal(id.getValue(), type);
                } else if(op == ISelDAGPatternOperation.CONSTANT) {
                    // argument should be a single number or single identifier
                    if(args.size() != 1) {
                        throw new IllegalArgumentException("Expected number or identifier for CONSTANT, got " + args);
                    }
                    
                    ASTNode val = args.get(0);
                    
                    switch(val.getSymbol().getID()) {
                        case IselPatternLexer.ID.TERMINAL_IDENTIFIER:
                            return new ISelPatternNodeConstant(val.getValue(), 0, type, true);
                        
                        case IselPatternLexer.ID.TERMINAL_NUMBER:
                            return new ISelPatternNodeConstant(null, Integer.parseInt(val.getValue()), type, false);
                        
                        default:
                            throw new IllegalArgumentException("Expected number or identifier for CONSTANT, got " + ASTUtil.detailed(val));
                    }
                } else {
                    // Compile arguments
                    for(ASTNode argNode : args) {
                        arguments.add(compileExpression(argNode));
                    }
                    
                    return new ISelPatternNodeNode(identifier, type, op, arguments);
                }
            }
            
            case IselPatternParser.ID.VARIABLE_EXPR_PAT: {
                // ISelPatternNodePattern
                String nodeIdentifier = children.get(0).getValue();
                String patternName = children.get(1).getValue();
                
                return new ISelPatternNodePattern(nodeIdentifier, patternName);
            }
            
            case IselPatternParser.ID.VARIABLE_EXPR_REF: {
                // ISelPatternNodeReference
                String identifier = children.get(0).getValue();
                
                return new ISelPatternNodeReference(identifier);
            }
            
            default:
                throw new IllegalArgumentException("Unexpected node " + ASTUtil.detailed(expressionNode) + " as expression");
        }
    }
    
    /**
     * Compiles an assembly part
     * @param assemblyNode
     * @return
     */
    private static AASMPart compileAssembly(ASTNode assemblyNode) {
        switch(assemblyNode.getSymbol().getID()) {
            case IselPatternParser.ID.VARIABLE_INSTRUCTION:
                return compileInstruction(assemblyNode.getChildren());
            
            case IselPatternParser.ID.VARIABLE_MEMORY:
                return compileMemory(assemblyNode);
            
            case IselPatternParser.ID.VARIABLE_INDEX:
                return compileIndex(assemblyNode);
            
            case IselPatternLexer.ID.TERMINAL_IDENTIFIER:
                return compileIdentifier(assemblyNode);
            
            default:
                throw new IllegalArgumentException("Unexpected node " + ASTUtil.detailed(assemblyNode) + " as assembly");
        }
    }
    
    /**
     * Compiles an assembly instruction
     * @param instNodes
     * @return
     */
    private static AASMInstruction compileInstruction(List<ASTNode> instNodes) {
        LOG.finest("Compiling instruction " + instNodes);
        
        // NAME [arg [arg]]
        AASMOperation op = AASMOperation.valueOf(instNodes.get(0).getValue().toUpperCase());
        
        if(instNodes.size() == 1) {
            return new AASMInstruction(op);
        } else if(instNodes.size() == 2) {
            return new AASMInstruction(op, compileArgument(instNodes.get(1)));
        } else {
            return new AASMInstruction(op, compileArgument(instNodes.get(1)), compileArgument(instNodes.get(2)));
        }
    }
    
    /**
     * Compiles an iarg or memory node
     * @param argNode
     * @return
     */
    private static AASMPart compileArgument(ASTNode argNode) {
        switch(argNode.getSymbol().getID()) {
            case IselPatternParser.ID.VARIABLE_IARG:
                return compileIdentifierChain(argNode);
            
            case IselPatternParser.ID.VARIABLE_MEMORY:
                return compileMemory(argNode);
            
            case IselPatternLexer.ID.TERMINAL_IDENTIFIER:
                return compileIdentifier(argNode);
            
            case IselPatternLexer.ID.TERMINAL_NAME,
                 IselPatternLexer.ID.TERMINAL_NUMBER:
                return compileString(argNode);
            
            default:
                throw new IllegalArgumentException("Unexpected node " + argNode + " as argument");
        }
    }
    
    /**
     * Compiles a memory node
     * @param memNode
     * @return
     */
    private static AASMMemory compileMemory(ASTNode memNode) {
        if(LOG.isLoggable(Level.FINEST)) { 
            LOG.finest("Compiling memory " + ASTUtil.detailed(memNode));
        }
        
        List<ASTNode> children = memNode.getChildren();
        
        // Memory is either
        // [base/offset/reference]
        // [base + index + offset]
        
        // parts appear in BIO order
        
        if(children.size() == 1) {
            AASMPart part = compileArgument(children.get(0));
            
            return new AASMMemory(part, null, null, null, IRType.NONE);
        } else {
            // base index offset
            AASMPart base = compileArgument(children.get(0)),
                     index = compileArgument(children.get(1)),
                     offset = compileArgument(children.get(2));
            
            if(index instanceof AASMPatternIndex idx) {
                return new AASMMemory(base, idx.index(), idx.ccScale(), offset, IRType.NONE);
            } else {
                return new AASMMemory(base, index, AASMCompileConstant.ONE, offset, IRType.NONE);
            }
        }        
    }
    
    /**
     * Compiles an index node
     * @param indNode
     * @return
     */
    private static AASMPatternIndex compileIndex(ASTNode indNode) {
        if(LOG.isLoggable(Level.FINEST)) { 
            LOG.finest("Compiling index " + ASTUtil.detailed(indNode));
        }
        
        List<ASTNode> children = indNode.getChildren();
        
        return new AASMPatternIndex(compileIdentifier(children.get(0)), Integer.parseInt(children.get(1).getValue()));
    }
    
    /**
     * Compiles an identifier node
     * @param idNode
     * @return
     */
    private static AASMPatternReference compileIdentifier(ASTNode idNode) {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.finest("Compiling pattern reference " + ASTUtil.detailed(idNode));
        }
        
        return new AASMPatternReference(List.of(idNode.getValue()));
    }
    
    /**
     * Compiles an iarg node
     * @param iargNode
     * @return
     */
    private static AASMPatternReference compileIdentifierChain(ASTNode iargNode) {
        if(LOG.isLoggable(Level.FINEST)) { 
            LOG.finest("Compiling identifier chain " + ASTUtil.detailed(iargNode));
        }
        
        List<String> identifiers = new ArrayList<>();
        
        for(ASTNode idNode : iargNode.getChildren()) {
            identifiers.add(idNode.getValue());
        }
        
        return new AASMPatternReference(identifiers);
    }
    
    /**
     * Compiles name nodes (unspecified strings)
     * @param strNode
     * @return
     */
    private static AASMPart compileString(ASTNode strNode) {
        if(LOG.isLoggable(Level.FINEST)) { 
            LOG.finest("Compiling string " + ASTUtil.detailed(strNode));
        }
        
        // number or register
        String v = strNode.getValue();
        
        try {
            return new AASMMachineRegister(Register.valueOf(v.toUpperCase()));
        } catch(IllegalArgumentException e) {
            return new AASMCompileConstant(Integer.parseInt(v));
        }
    }
    
}
