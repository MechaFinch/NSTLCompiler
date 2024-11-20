package notsotiny.lang.compiler.irgen;

import java.util.List;
import java.util.logging.Logger;

import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.lang.compiler.ASTUtil;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.irgen.context.ASTContextLabel;
import notsotiny.lang.compiler.irgen.context.ASTContextTree;
import notsotiny.lang.parser.NstlgrammarParser;

/*
 * ContextTree
 * Data structure to track what each BB is able to see
 * Each AST BB has a reference to a node. When it gets parsed, it places local names into the node
 * When looking up a local name, search the node, then its parent, etc
 */

/**
 * Parses the code of an ASTFunction into a control-flow graph of ASTBasicBlocks
 * 
 * Each Construct function returns the basic block the construct exits to
 */
public class CFGParser {
    
    private static Logger LOG = Logger.getLogger(CFGParser.class.getName());
    private static ASTLogger ALOG = new ASTLogger(LOG);
    
    /**
     * Parses the code of an ASTFunction to a CFG of ASTBasicBlocks
     * @param function
     */
    public static void parseFunctionCFG(ASTFunction function) throws CompilationException {
        LOG.fine("----Parsing function " + function.getHeader().getName() + " to CFG----");
        
        ASTBasicBlock entryBlock = new ASTBasicBlock(function, function.getContext(), "entry");
        function.getBasicBlocks().add(entryBlock);
        ASTBasicBlock exitBlock = parseFunctionCode(function.getContents(), entryBlock, function, function.getContext());
        
        if(exitBlock.getExitType() == ASTBasicBlock.ExitType.UNCONDITIONAL) {
            // Implicit return
            exitBlock.setExitCode(null, ASTBasicBlock.ExitType.RETURN);
        }
        
    }
    
    /**
     * Parse 'function_code' nodes
     * @param code
     * @param function
     */
    private static ASTBasicBlock parseFunctionCode(List<ASTNode> code, ASTBasicBlock basicBlock, ASTFunction function, ASTContextTree context) throws CompilationException {
        LOG.finer("Parsing function code: " + code);
        
        // For each item
        for(int i = 0; i < code.size(); i++) {
            ASTNode node = code.get(i);
            
            switch(node.getSymbol().getID()) {
                case NstlgrammarParser.ID.VARIABLE_IF_CONSTRUCT:
                    basicBlock = parseIfConstruct(node, basicBlock, function, context);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_WHILE_CONSTRUCT:
                    basicBlock = parseWhileConstruct(node, basicBlock, function, context);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_UNTIL_CONSTRUCT:
                    basicBlock = parseUntilConstruct(node, basicBlock, function, context);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_FOR_CONSTRUCT:
                    basicBlock = parseForConstruct(node, basicBlock, function, context);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_BREAK:
                    parseBreak(node, basicBlock, function, context);
                    
                    // This should be the last item in a block
                    if(i < code.size() - 1) {
                        ALOG.severe(node, "Found code after break statement");
                        throw new CompilationException();
                    }
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_CONTINUE:
                    parseContinue(node, basicBlock, function, context);
                    
                    // This should be the last item in a block
                    if(i < code.size() - 1) {
                        ALOG.severe(node, "Found code after continue statement");
                        throw new CompilationException();
                    }
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_RETURN:
                    LOG.finest("Added code " + ASTUtil.detailed(node) + " to " + basicBlock.getName());
                    basicBlock.setExitCode(node, ASTBasicBlock.ExitType.RETURN);
                    
                    // Remove successors if present
                    basicBlock.setTrueSuccessor(null);
                    basicBlock.setFalseSuccessor(null);
                    
                    // This should be the last item in a block
                    if(i < code.size() - 1) {
                        ALOG.severe(node, "Found code after return statement");
                        throw new CompilationException();
                    }
                    break;
                
                default:
                    // Not involved with control flow
                    LOG.finest("Added code " + ASTUtil.detailed(node) + " to " + basicBlock.getName());
                    basicBlock.addCode(node);
            }
        }
        
        return basicBlock;
    }
    
    /**
     * Parse an 'if_construct' node
     * @param constructNode
     * @param parent
     * @param function
     * @return 
     * @throws CompilationException
     */
    private static ASTBasicBlock parseIfConstruct(ASTNode constructNode, ASTBasicBlock parent, ASTFunction function, ASTContextTree context) throws CompilationException {
        LOG.finer("Parsing IF construct " + ASTUtil.detailed(constructNode));
        
        /*
         * if_construct -> KW_IF! variable_expression KW_THEN! {function_code*} if_tail;
         * 
         *  - Create code block
         *  - Get exit block from parseIfTail with parent=parent
         *  - Set trueSuccessor of parent to code
         *  - Parse code block's code
         *  - Set trueSuccessor of code to exit
         *  - Return exit
         */
        
        List<ASTNode> children = constructNode.getChildren();
        
        // parent gets condition code
        parent.setExitCode(children.get(0), ASTBasicBlock.ExitType.CONDITIONAL);
        
        ASTBasicBlock codeBlock = new ASTBasicBlock(function, new ASTContextTree(context), function.getUnique("iftrue"));
        ASTBasicBlock exitBlock = parseIfTail(children.get(2), parent, function, context);
        
        parent.setTrueSuccessor(codeBlock);
        
        // Code block gets true code
        codeBlock = parseFunctionCode(children.get(1).getChildren(), codeBlock, function, codeBlock.getContext());
        codeBlock.setTrueSuccessorIfAbsent(exitBlock);
        
        return exitBlock;
    }
    
    /**
     * Parse an 'if_tail' node
     * @param tailNode
     * @param parent
     * @param function
     * @return
     * @throws CompilationException
     */
    private static ASTBasicBlock parseIfTail(ASTNode tailNode, ASTBasicBlock parent, ASTFunction function, ASTContextTree context) throws CompilationException {
        LOG.finest("Parsing IF tail " + ASTUtil.detailed(tailNode));
        
        /*
         * if_tail ->
         *   KW_ELSEIF! variable_expression KW_THEN! {function_code*} if_tail
         * | KW_ELSE! {function_code*} KW_END! KW_IF!
         * | KW_END! KW_IF!;
         * 
         * variable_expression {code} if_tail   ELSEIF
         * {code}                               ELSE
         * <nothing>                            ENDIF
         */
        
        List<ASTNode> children = tailNode.getChildren();
        
        if(children.size() == 0) {
            /*
             * END IF
             *  - Create exit block
             *  - Set falseSuccessor of parent to exit
             *  - Return exit
             */
            ASTBasicBlock exitBlock = new ASTBasicBlock(function, context, function.getUnique("endif"));
            
            parent.setFalseSuccessor(exitBlock);
            
            return exitBlock;
        } else if(children.size() == 1) {
            /*
             * ELSE
             *  - Create code and exit blocks
             *  - Set falseSuccessor of parent to code 
             *  - Parse code block's code
             *  - Set trueSuccessor of code to exit
             *  - Return exit
             */
            ASTBasicBlock codeBlock = new ASTBasicBlock(function, new ASTContextTree(context), function.getUnique("else"));
            ASTBasicBlock exitBlock = new ASTBasicBlock(function, context, function.getUnique("endif"));
            
            parent.setFalseSuccessor(codeBlock);
            
            // Code block gets code
            codeBlock = parseFunctionCode(children.get(0).getChildren(), codeBlock, function, codeBlock.getContext());
            codeBlock.setTrueSuccessorIfAbsent(exitBlock);
            
            return exitBlock;
        } else {
            /*
             * ELSEIF
             *  - Create condition and code blocks
             *  - Set falseSuccessor of parent to condition
             *  - Set trueSuccessor of condition to code
             *  - Get exit block from parseIfTail with parent=condition
             *  - Parse code block's code
             *  - Set trueSuccessor of code to exit
             *  - Return exit
             */
            ASTBasicBlock conditionBlock = new ASTBasicBlock(function, context, function.getUnique("elseif"));
            ASTBasicBlock codeBlock = new ASTBasicBlock(function, new ASTContextTree(context), function.getUnique("elseiftrue"));
            ASTBasicBlock exitBlock = parseIfTail(children.get(2), conditionBlock, function, context);
            
            // Condition block gets condition code
            conditionBlock.setExitCode(children.get(0), ASTBasicBlock.ExitType.CONDITIONAL);
            parent.setFalseSuccessor(conditionBlock);
            conditionBlock.setTrueSuccessor(codeBlock);
            
            // Code block gets true code
            codeBlock = parseFunctionCode(children.get(1).getChildren(), codeBlock, function, context);
            codeBlock.setTrueSuccessorIfAbsent(exitBlock);
            
            return exitBlock;
        }
    }
    
    /**
     * Parse a 'while_construct' node
     * @param constructNode
     * @param parent
     * @param function
     * @return
     * @throws CompilationException
     */
    private static ASTBasicBlock parseWhileConstruct(ASTNode constructNode, ASTBasicBlock parent, ASTFunction function, ASTContextTree context) throws CompilationException {
        LOG.finer("Parsing WHILE construct " + ASTUtil.detailed(constructNode));
        
        /*
         * while_construct -> {label?} KW_WHILE! variable_expression KW_DO! {function_code*} KW_END! KW_WHILE!;
         * 
         * {label} variable_expression {code} 
         * {} variable_expression {code}
         * 
         * - Create condition, code, and exit blocks
         * - Create label object with continue=condition and break=exit
         * - Set trueSuccessor of parent to condition
         * - Set trueSuccessor of condition to code
         * - Set falseSuccessor of condition to exit
         * - Parse code block's code
         * - Set trueSuccessor of code to condition
         * - Return exit
         */
        
        // extract parts
        List<ASTNode> children = constructNode.getChildren();
        List<ASTNode> labelEta = children.get(0).getChildren();
        
        String labelSourceName, labelUniqueName;
        ASTNode conditionNode = children.get(1);
        ASTNode codeNode = children.get(2);
        
        if(labelEta.size() != 0) {
            labelSourceName = labelEta.get(0).getChildren().get(0).getValue();
            labelUniqueName = function.getUnique(labelSourceName);
        } else {
            labelUniqueName = function.getUnique("while");
            labelSourceName = labelUniqueName;
        }
        
        // Blocks
        ASTBasicBlock conditionBlock = new ASTBasicBlock(function, context, function.getUnique("whilecond"));
        ASTBasicBlock codeBlock = new ASTBasicBlock(function, new ASTContextTree(context), function.getUnique("whiletrue"));
        ASTBasicBlock exitBlock = new ASTBasicBlock(function, context, function.getUnique("whiledone"));
        
        conditionBlock.setExitCode(conditionNode, ASTBasicBlock.ExitType.CONDITIONAL);
        
        // Label
        ASTContextLabel label = new ASTContextLabel(labelSourceName, labelUniqueName, conditionBlock, exitBlock);
        context.addEntry(label);
        
        // Successors
        parent.setTrueSuccessor(conditionBlock);
        conditionBlock.setTrueSuccessor(codeBlock);
        conditionBlock.setFalseSuccessor(exitBlock);
        codeBlock = parseFunctionCode(codeNode.getChildren(), codeBlock, function, codeBlock.getContext());
        codeBlock.setTrueSuccessorIfAbsent(conditionBlock);
        
        return exitBlock;
    }
    
    /**
     * Parse an 'until_construct' node
     * @param constructNode
     * @param parent
     * @param function
     * @return
     * @throws CompilationException
     */
    private static ASTBasicBlock parseUntilConstruct(ASTNode constructNode, ASTBasicBlock parent, ASTFunction function, ASTContextTree context) throws CompilationException {
        LOG.finer("Parsing UNTIL construct " + ASTUtil.detailed(constructNode));
        
        /*
         * until_construct -> {label?} KW_UNTIL! variable_expression KW_DO! {function_code*} KW_END! KW_UNTIL!;
         * 
         * {label} variable_expression {code}
         * {} variable_expression {code}
         * 
         * - Create condition, code, and exit blocks
         * - Create label object with continue=condition and break=exit
         * - Set trueSuccessor of parent to code
         * - Set trueSuccessor of condition to exit
         * - Set falseSuccessor of condition to code
         * - Parse code block's code
         * - Set trueSuccessor of code to condition
         * - Return exit
         */
        
        // extract parts
        List<ASTNode> children = constructNode.getChildren();
        List<ASTNode> labelEta = children.get(0).getChildren();
        
        String labelSourceName, labelUniqueName;
        ASTNode conditionNode = children.get(1);
        ASTNode codeNode = children.get(2);
        
        if(labelEta.size() != 0) {
            labelSourceName = labelEta.get(0).getChildren().get(0).getValue();
            labelUniqueName = function.getUnique(labelSourceName);
        } else {
            labelUniqueName = function.getUnique("until");
            labelSourceName = labelUniqueName;
        }
        
        // Blocks
        ASTBasicBlock conditionBlock = new ASTBasicBlock(function, context, function.getUnique("untilcond"));
        ASTBasicBlock codeBlock = new ASTBasicBlock(function, new ASTContextTree(context), function.getUnique("untilfalse"));
        ASTBasicBlock exitBlock = new ASTBasicBlock(function, context, function.getUnique("untildone"));
        
        conditionBlock.setExitCode(conditionNode, ASTBasicBlock.ExitType.CONDITIONAL);
        
        // Label
        ASTContextLabel label = new ASTContextLabel(labelSourceName, labelUniqueName, conditionBlock, exitBlock);
        context.addEntry(label);
        
        // Successors
        parent.setTrueSuccessor(codeBlock);
        conditionBlock.setTrueSuccessor(exitBlock);
        conditionBlock.setFalseSuccessor(codeBlock);
        codeBlock = parseFunctionCode(codeNode.getChildren(), codeBlock, function, codeBlock.getContext());
        codeBlock.setTrueSuccessorIfAbsent(conditionBlock);
        
        return exitBlock;
    }
    
    /**
     * Parse a 'for_construct' node
     * @param constructNode
     * @param parent
     * @param function
     * @return
     * @throws CompilationException
     */
    private static ASTBasicBlock parseForConstruct(ASTNode constructNode, ASTBasicBlock parent, ASTFunction function, ASTContextTree context) throws CompilationException {
        LOG.finer("Parsing FOR construct " + ASTUtil.detailed(constructNode));
        
        /*
         * for_construct ->
         * {label?} KW_FOR!
         * {NAME KW_IS! type KW_GETS! variable_expression SEMI!}
         * variable_expression SEMI!
         * {NAME KW_GETS! variable_expression}
         * KW_DO! {function_code*} KW_END! KW_FOR!;
         * 
         * - Append initialization to parent
         * - Create condition, code, update, and exit nodes
         * - Create label object with continue=update and break=exit
         * - Set trueSuccessor of parent to condition
         * - Set trueSuccessor of condition to code
         * - Set falseSuccessor of condition to exit
         * - Parse code block's code
         * - Set trueSuccessor of code to update
         * - Set trueSuccessor of update to condition
         * - Return exit
         */
        
        // Extract parts
        List<ASTNode> children = constructNode.getChildren();
        List<ASTNode> labelEta = children.get(0).getChildren();
        ASTNode initNode = children.get(1);
        ASTNode conditionNode = children.get(2);
        ASTNode updateNode = children.get(3);
        ASTNode codeNode = children.get(4);
        
        String labelSourceName, labelUniqueName;
        if(labelEta.size() != 0) {
            labelSourceName = labelEta.get(0).getChildren().get(0).getValue();
            labelUniqueName = function.getUnique(labelSourceName);
        } else {
            labelUniqueName = function.getUnique("until");
            labelSourceName = labelUniqueName;
        }
        
        // Blocks
        ASTContextTree innerContext = new ASTContextTree(context);
        ASTBasicBlock initBlock = new ASTBasicBlock(function, innerContext, function.getUnique("forinit"));
        ASTBasicBlock conditionBlock = new ASTBasicBlock(function, innerContext, function.getUnique("forcond"));
        ASTBasicBlock codeBlock = new ASTBasicBlock(function, innerContext, function.getUnique("fortrue"));
        ASTBasicBlock updateBlock = new ASTBasicBlock(function, innerContext, function.getUnique("forupd"));
        ASTBasicBlock exitBlock = new ASTBasicBlock(function, context, function.getUnique("fordone"));
        
        initBlock.addCode(initNode);
        conditionBlock.setExitCode(conditionNode, ASTBasicBlock.ExitType.CONDITIONAL);
        updateBlock.addCode(updateNode);
        
        // Label
        ASTContextLabel label = new ASTContextLabel(labelSourceName, labelUniqueName, updateBlock, exitBlock);
        context.addEntry(label);
        
        // Successors
        parent.setTrueSuccessor(initBlock);
        initBlock.setTrueSuccessor(conditionBlock);
        conditionBlock.setTrueSuccessor(codeBlock);
        conditionBlock.setFalseSuccessor(exitBlock);
        codeBlock = parseFunctionCode(codeNode.getChildren(), codeBlock, function, codeBlock.getContext());
        codeBlock.setTrueSuccessorIfAbsent(updateBlock);
        updateBlock.setTrueSuccessor(conditionBlock);
        
        return exitBlock;
    }
    
    /**
     * Parse a 'break' node
     * @param node
     * @param parent
     * @param function
     * @throws CompilationException
     */
    private static void parseBreak(ASTNode node, ASTBasicBlock parent, ASTFunction function, ASTContextTree context) throws CompilationException {
        LOG.finer("Parsing BREAK statement " + ASTUtil.detailed(node));
        
        /*
         * break ->
         *   KW_BREAK! NAME SEMI!
         * | KW_BREAK! SEMI!;
         */
        
        List<ASTNode> children = node.getChildren();
        ASTBasicBlock target;
        
        if(children.size() == 1) {
            // Has label
            String name = children.get(0).getValue();
            if(!context.labelExists(name)) {
                ALOG.severe(node, name + " is not a label name");
                throw new CompilationException();
            }
            
            target = context.getLabel(name).getBreakBlock();
        } else {
            // No label
            target = context.getLastLabel().getBreakBlock();
        }
        
        parent.setTrueSuccessor(target);
    }
    
    /**
     * Parse a 'continue' node
     * @param node
     * @param parent
     * @param function
     * @throws CompilationException
     */
    private static void parseContinue(ASTNode node, ASTBasicBlock parent, ASTFunction function, ASTContextTree context) throws CompilationException {
        LOG.finer("Parsing CONTINUE statement " + ASTUtil.detailed(node));
        
        /*
         * continue ->
         *   KW_CONTINUE! NAME SEMI!
         * | KW_CONTINUE! SEMI!;
         */
        
        List<ASTNode> children = node.getChildren();
        ASTBasicBlock target;
        
        if(children.size() == 1) {
            // Has label
            String name = children.get(0).getValue();
            if(!context.labelExists(name)) {
                ALOG.severe(node, name + " is not a label name");
                throw new CompilationException();
            }
            
            target = context.getLabel(name).getContinueBlock();
        } else {
            // No label
            target = context.getLastLabel().getContinueBlock();
        }
        
        parent.setTrueSuccessor(target);
    }
    
}
