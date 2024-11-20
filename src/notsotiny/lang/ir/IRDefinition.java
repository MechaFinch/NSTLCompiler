package notsotiny.lang.ir;

/**
 * Represents the definition of a value
 */
public class IRDefinition {
    
    private IRIdentifier id;
    
    private IRDefinitionType type;
    
    private IRLinearInstruction instruction;
    private IRBasicBlock bb;
    private IRFunction fun;
    
    /**
     * Internal constructor
     * @param type
     * @param id
     * @param inst
     * @param global
     */
    private IRDefinition(IRDefinitionType type, IRIdentifier id, IRLinearInstruction inst, IRBasicBlock bb, IRFunction fun) {
        this.type = type;
        this.id = id;
        this.instruction = inst;
        this.bb = bb;
        this.fun = fun;
    }
    
    /**
     * Global constructor
     * @param id
     */
    public IRDefinition(IRIdentifier id) {
        this(IRDefinitionType.GLOBAL, id, null, null, null);
    }
    
    /**
     * Instruction constructor
     * @param id
     * @param inst
     */
    public IRDefinition(IRIdentifier id, IRLinearInstruction inst) {
        this(IRDefinitionType.INSTRUCTION, id, inst, null, null);
    }
    
    /**
     * BB Argument constructor
     * @param id
     * @param global
     */
    public IRDefinition(IRIdentifier id, IRBasicBlock bb) {
        this(IRDefinitionType.BBARG, id, null, bb, null);
    }
    
    /**
     * Function argument constructor
     * @param id
     * @param fun
     */
    public IRDefinition(IRIdentifier id, IRFunction fun) {
        this(IRDefinitionType.FUNARG, id, null, null, fun);
    }
    
    public IRIdentifier getID() { return this.id; }
    public IRDefinitionType getType() { return this.type; }
    public IRLinearInstruction getInstruction() { return this.instruction; }
    public IRBasicBlock getBB() { return this.bb; }
    public IRFunction getFunction() { return this.fun; }
    
}
