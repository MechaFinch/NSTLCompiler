package notsotiny.lang.ir;

/**
 * Represents the definition of a value
 */
public class IRDefinition {
    
    /*
     * ID of the thing being defined
     * Branch instructions have the ID of their parent BB, whose control flow they define
     */
    private IRIdentifier id;
    
    private IRDefinitionType type;
    
    private IRLinearInstruction linearInstruction;
    private IRBranchInstruction branchInstruction;
    private IRBasicBlock bb;
    private IRFunction fun;
    
    /**
     * Internal constructor
     * @param type
     * @param id
     * @param inst
     * @param global
     */
    private IRDefinition(IRDefinitionType type, IRIdentifier id, IRLinearInstruction linst, IRBranchInstruction binst, IRBasicBlock bb, IRFunction fun) {
        this.type = type;
        this.id = id;
        this.linearInstruction = linst;
        this.branchInstruction = binst;
        this.bb = bb;
        this.fun = fun;
    }
    
    /**
     * Global constructor
     * @param id
     */
    public IRDefinition(IRIdentifier id) {
        this(IRDefinitionType.GLOBAL, id, null, null, null, null);
    }
    
    /**
     * Linear Instruction constructor
     * @param id
     * @param inst
     */
    public IRDefinition(IRIdentifier id, IRLinearInstruction inst) {
        this(IRDefinitionType.LINEAR, id, inst, null, null, null);
    }
    
    /**
     * Branch Instruction constructor
     * @param inst
     */
    public IRDefinition(IRIdentifier id, IRBranchInstruction inst) {
        this(IRDefinitionType.BRANCH, id, null, inst, null, null);
    }
    
    /**
     * BB Argument constructor
     * @param id
     * @param global
     */
    public IRDefinition(IRIdentifier id, IRBasicBlock bb) {
        this(IRDefinitionType.BBARG, id, null, null, bb, null);
    }
    
    /**
     * Function argument constructor
     * @param id
     * @param fun
     */
    public IRDefinition(IRIdentifier id, IRFunction fun) {
        this(IRDefinitionType.FUNARG, id, null, null, null, fun);
    }
    
    public IRIdentifier getID() { return this.id; }
    public IRDefinitionType getType() { return this.type; }
    public IRLinearInstruction getLinearInstruction() { return this.linearInstruction; }
    public IRBranchInstruction getBranchInstruction() { return this.branchInstruction; }
    public IRBasicBlock getBB() { return this.bb; }
    public IRFunction getFunction() { return this.fun; }
    
    @Override
    public String toString() { 
        return this.type + " " + this.id;
    }
    
}
