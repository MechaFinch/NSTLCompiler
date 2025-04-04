package notsotiny.lang.compiler.codegen.alloc;

import java.util.List;

import notsotiny.lang.compiler.aasm.AASMPart;

/**
 * The results of register allocation
 * @param allocatedCode Resulting code
 * @param stackAllocationSize Number of bytes that must be reserved below BP
 * @param i If true, I is used
 * @param j If true, J is used
 * @param k If true, K is used
 * @param l If true, L is used
 */
public record AllocationResult(List<AASMPart> allocatedCode, int stackAllocationSize, boolean i, boolean j, boolean k, boolean l) {
    
}
