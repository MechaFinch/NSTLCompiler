package notsotiny.lang.compiler.codegen.alloc;

import java.util.List;
import java.util.Set;

import notsotiny.sim.Register;
import notsotiny.lang.compiler.aasm.AASMPart;

/**
 * The results of register allocation
 * @param allocatedCode Resulting code
 * @param stackAllocationSize Number of bytes that must be reserved below BP
 * @param usedCalleeSavedRegisters Callee-saved registers which need to be saved & restored
 */
public record AllocationResult(List<AASMPart> allocatedCode, int stackAllocationSize, Set<Register> usedCalleeSavedRegisters) {
    
}
