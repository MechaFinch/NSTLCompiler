package notsotiny.lang.compiler.codegen.pattern;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import notsotiny.lang.compiler.aasm.AASMInstruction;
import notsotiny.lang.compiler.aasm.AASMLabel;
import notsotiny.lang.compiler.aasm.AASMPart;

/**
 * A pattern that maps a tree of DAG nodes to a segment of assembly
 * Patterns contain PatternNodes which represent DAG nodes or sub-patterns
 * Named parts of the pattern are substituted into the assembly
 * - Identifiers are substituted with their name
 * - Patterns are substituted with their output assembly parts
 */
public class ISelPattern {
    
    // The tree of ISelPatternNodes representing the pattern to be matched
    private ISelPatternNode pattern;
    
    // The AbstractAssembly objects representing the template to emit when matched
    private List<AASMPart> template;
    
    // Maps identifier strings to the root node of sub-patterns
    private Map<String, ISelPatternNode> subpatternMap;
    
    // Information for error messages
    private String groupIdentifier;
    private int patternNumber;
    
    /**
     * @param pattern Pattern to match
     * @param template Produced template
     * @param groupIdentifier Identifier of containing pattern group
     * @param patternNumber Number of this pattern within its containing group
     */
    public ISelPattern(ISelPatternNode pattern, List<AASMPart> template, String groupIdentifier, int patternNumber) {
        this.pattern = pattern;
        this.template = template;
        this.groupIdentifier = groupIdentifier;
        this.patternNumber = patternNumber;
        
        // Create subpattern map
        this.subpatternMap = new HashMap<>();
        fillPatternMap(this.pattern);
        checkPatternMap(this.pattern);
    }
    
    /**
     * Recursively fills out the pattern map
     * @param node
     */
    private void fillPatternMap(ISelPatternNode node) {
        switch(node) {
            case ISelPatternNodeNode nn:
                this.subpatternMap.put(nn.getIdentifier(), nn);
                
                for(ISelPatternNode argNode : nn.getArgumentNodes()) {
                    fillPatternMap(argNode);
                }
                break;
            
            case ISelPatternNodePattern pat:
                this.subpatternMap.put(pat.getNodeIdentifier(), pat);
                break;
            
            case ISelPatternNodeLocal loc:
                this.subpatternMap.put(loc.getIdentifier(), loc);
                break;
                
            case ISelPatternNodeArgument arg:
                this.subpatternMap.put(arg.getIdentifier(), arg);
                break;
                
            case ISelPatternNodeConstant con:
                if(con.isWildcard()) {
                    this.subpatternMap.put(con.getIdentifier(), con);
                }
                break;
            
                // no action
            case ISelPatternNodeReference _: break;
            
            default:
                // Invalid
                throw new IllegalArgumentException("Unexpected ISelPatternNode type: " + node);
        }
    }
    
    /**
     * Checks that subpattern references exist
     * @param node
     */
    private void checkPatternMap(ISelPatternNode node) {
        if(node instanceof ISelPatternNodeReference ref) {
            if(!this.subpatternMap.containsKey(ref.getReferencedIdentifier())) {
                throw new IllegalArgumentException(ref.getReferencedIdentifier() + " is not a subpattern in " + this.getDescription());
            }
        } else if(node instanceof ISelPatternNodeNode nodeNode) {
            for(ISelPatternNode argNode : nodeNode.getArgumentNodes()) {
                checkPatternMap(argNode);
            }
        }
    }
    
    /**
     * @return True if this pattern produces only instructions at the top level
     */
    public boolean producesInstructions() {
        for(AASMPart part : this.template) {
            if(!(part instanceof AASMInstruction || part instanceof AASMLabel)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * @return A description of this pattern
     */
    public String getDescription() {
        return "pattern " + this.patternNumber + " of group " + this.groupIdentifier;
    }
    
    public ISelPatternNode getRoot() { return this.pattern; }
    public List<AASMPart> getTemplate() { return this.template; }
    public Map<String, ISelPatternNode> getSubpatternMap() { return this.subpatternMap; }
    
}
