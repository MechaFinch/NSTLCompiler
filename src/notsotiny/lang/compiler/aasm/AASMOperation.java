package notsotiny.lang.compiler.aasm;

/**
 * AbstractAssembly operations
 * A subset of assembly opcodes
 */
public enum AASMOperation implements AASMPart {
    MOV     (false, true, false),
    MOVS    (false, true, true),
    MOVZ    (false, true, true),
    CMOV    (false, true, false),
    XCHG    (true, true, false),
    LEA     (false, true, true),
    PUSH    (false, false, true),
    POP     (false, true, true),
    
    ADD     (true, true, false),
    ADC     (true, true, false),
    INC     (true, true, false),
    ICC     (true, true, false),
    SUB     (true, true, false),
    SBB     (true, true, false),
    DEC     (true, true, false),
    DCC     (true, true, false),
    
    MUL     (true, true, false),
    MULH    (true, true, true),
    MULSH   (true, true, true),
    DIV     (true, true, false),
    DIVS    (true, true, false),
    DIVM    (true, true, true),
    DIVMS   (true, true, true),
    
    AND     (true, true, false),
    OR      (true, true, false),
    XOR     (true, true, false),
    NOT     (true, true, false),
    NEG     (true, true, false),
    
    SHL     (true, true, false),
    SHR     (true, true, false),
    SAR     (true, true, false),
    ROL     (true, true, false),
    ROR     (true, true, false),
    RCL     (true, true, false),
    RCR     (true, true, false),
    
    CALL    (false, false, false),
    CALLA   (false, false, false),
    RET     (false, false, false),
    
    CMP     (true, false, false),
    TST     (true, false, false),
    JMP     (false, false, false),
    JMPA    (false, false, false),
    JCC     (false, false, false),
    ;
    
    private boolean usesDestination, definesDestination, allowsHalfRegisterSource;
    
    private AASMOperation(boolean usesDestination, boolean definesDestination, boolean allowsHalfRegisterSource) {
        this.usesDestination = usesDestination;
        this.definesDestination = definesDestination;
        this.allowsHalfRegisterSource = allowsHalfRegisterSource;
    }
    
    public boolean usesDestination() { return this.usesDestination; }
    public boolean definesDestination() { return this.definesDestination; }
    public boolean allowsHalfRegisterSource() { return this.allowsHalfRegisterSource; }
}
