package notsotiny.lang.compiler.aasm;

/**
 * AbstractAssembly operations
 * A subset of assembly opcodes
 */
public enum AASMOperation implements AASMPart {
    MOV     (false, true),
    MOVS    (false, true),
    MOVZ    (false, true),
    CMOV    (false, true),
    XCHG    (true, true),
    LEA     (false, true),
    PUSH    (false, false),
    POP     (false, true),
    
    ADD     (true, true),
    ADC     (true, true),
    INC     (true, true),
    ICC     (true, true),
    SUB     (true, true),
    SBB     (true, true),
    DEC     (true, true),
    DCC     (true, true),
    
    MUL     (true, true),
    MULH    (true, true),
    MULSH   (true, true),
    DIV     (true, true),
    DIVS    (true, true),
    DIVM    (true, true),
    DIVMS   (true, true),
    
    AND     (true, true),
    OR      (true, true),
    XOR     (true, true),
    NOT     (true, true),
    NEG     (true, true),
    
    SHL     (true, true),
    SHR     (true, true),
    SAR     (true, true),
    ROL     (true, true),
    ROR     (true, true),
    RCL     (true, true),
    RCR     (true, true),
    
    CALL    (false, false),
    CALLA   (false, false),
    RET     (false, false),
    
    CMP     (true, false),
    TST     (true, false),
    JMP     (false, false),
    JMPA    (false, false),
    JCC     (false, false),
    ;
    
    private boolean usesDestination, definesDestination;
    
    private AASMOperation(boolean usesDestination, boolean definesDestination) {
        this.usesDestination = usesDestination;
        this.definesDestination = definesDestination;
    }
    
    public boolean usesDestination() { return this.usesDestination; }
    public boolean definesDestination() { return this.definesDestination; }
}
