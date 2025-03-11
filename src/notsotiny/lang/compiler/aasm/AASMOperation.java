package notsotiny.lang.compiler.aasm;

/**
 * AbstractAssembly operations
 * A subset of assembly opcodes
 */
public enum AASMOperation implements AASMPart {
    MOV,
    MOVS,
    MOVZ,
    CMOV,
    XCHG,
    LEA,
    PUSH,
    POP,
    
    ADD,
    ADC,
    INC,
    ICC,
    SUB,
    SBB,
    DEC,
    DCC,
    
    MUL,
    MULH,
    MULSH,
    DIV,
    DIVS,
    DIVM,
    DIVMS,
    
    AND,
    OR,
    XOR,
    NOT,
    NEG,
    
    SHL,
    SHR,
    SAR,
    ROL,
    ROR,
    RCL,
    RCR,
    
    CALL,
    CALLA,
    RET,
    
    CMP,
    TST,
    JMP,
    JMPA,
    JCC,
    ;
}
