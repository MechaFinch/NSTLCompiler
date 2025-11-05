# NSTLCompiler
**A compiler project for the NST architecture.**

This project contains a full three-stage compiler which produces NST architecture assembly. Specifically, it produces the `ASMObject` structure used by the NST Assembler ([repository](https://github.com/MechaFinch/NSTAssembler)), which it can additionally work with as part of its multiple language support.

Currently the compiler has a single front-end for the NSTL language. There are plans for additional front-end languages, particularly a re-syntaxing extension of NSTL referred to as NSTC.

## The NSTL Language
NSTL is a simple systems language with c-like capability and syntax styled after verbose languages such as VHDL. The `nstl` folder contains its documentation.

## Compiler Structure
### Intermediate Representation
The compiler uses an intermediate representation throughout. Classes can be found in the `notsotiny.lang.ir` package. The IR is a static single assignment (SSA) representation consisting of:

#### Modules
Modules correspond to relocatable object files. Modules contain functions ('internal' functions), headers for functions accessible to the module ('external functions'), and global constants & variables.

#### Functions
Functions have a return type, list of arguments, and a list of basic blocks. The first block in that list is the entry block, while others are in arbitrary order. Each function has an identifier in the `GLOBAL` class. Each function maintains a mapping from `LOCAL` and `BLOCK` identifiers to their respective objects.

#### Basic Blocks
Basic blocks contain a number of linear instructions and a single branch instruction at their exit. Basic blocks form a control-flow graph with successors in their exit branch instruction and a list of predecessor blocks. Each basic block has an identifier in the `BLOCK` class.

#### Linear Instructions
A linear instruction is a single instruction which does not perform control flow. If the instruction produces a value, it will be assigned to a `LOCAL` identifier.

#### Branch Instructions
A branch instruction is a single instruction which performs control flow. It can flow to a basic block conditionally or unconditionally, or return from the function.

#### Values
Values can be identifiers or constants. Identifiers reference values by name, and have three classes (`GLOBAL`, `LOCAL`, `BLOCK`) with separate namespaces. The `LOCAL` and `BLOCK` classes have separate namespaces for each function.

### Command Line Interface
The command line interface is found in the `NSTLCompiler` class. It handles arguments, and for each source file encountered, selects the front end or external program (for assembly files) to use. It collects assembly results and hands them off to the assembler to produce output relocatable object files.

Currently, each file is processed separately from any other files. In support of optimizations such as inlining, this will likely be changed in the future.

### Front End
The front end generates IR from source code. The front end operates through the `IRGenerator` interface. This interface takes in a himecc abstract syntax tree and yields an IR module. The current front end is found in the `notsotiny.lang.compiler.irgen` package. 

There are plans to refactor the front end in support of additional front-end languages such that language-dependent parts (e.g. parsers) are in a package unique to that language, and language-independent parts (e.g. the SSA manager) are available to all languages.

### Middle End
The middle end transforms IR to optimize it. The middle end operates through the `IROptimizer` interface. This interface takes in an IR module and yields an IR module. The operation may be (and is currently) done in-place. The middle end is found in the `notsotiny.lang.compiler.optimization` package.

The middle end consists of a series of optimization passes and a scheme for applying them in a given order. Each pass can be gated by an optimization level.

Currently implemented optimizations include:
* Sparse conditional constant propagation (my personal favorite algorithm)
* Local common subexpression elimination

That the interface works with a single module at a time is likely to change in the future as mentioned above. It is unlikely that more than one middle end will be produced.

### Back End
The back end generates assembly from IR. The back end operates through the `CodeGenerator` interface. This interface takes in an IR module and yields an assembly object. The back end is found in the `notsotiny.lang.compiler.codegen` package.

The back end generates code through a series of transformations: For each basic block, the IR is transformed from linear order to a directed acyclic graph (DAG). Tiles are selected for each IR instruction by subtree pattern matching. Each tile consists of a short series of abstract assembly instructions and information about the DAG nodes it covers. Instructions are selected by a covering of the DAG with those tiles via the NOLTIS algorithm. Selected tiles are then scheduled according to data dependencies to produce abstract assembly for the basic block. Basic blocks are scheduled non-taken-branch first, such that many unconditional jumps are eliminated, producing abstract assembly for the module. Register allocation is then performed via a generalized graph coloring which handles register aliasing. The resulting assembly is run through a peephole optimizer, which primarily eliminates no-op moves. Finally, the abstract assembly is translated directly to an assembly object.

A back end for another architecture could be made, but that is unlikely to be done.

### Legacy Code
Two packages, `notsotiny.lang.compiler.shitty` and `notsotiny.lang.compiler.context` remain in the repository but are excluded from the build path. These packages contain now-unused code from the original compiler, a single-pass AST to assembly translation which functioned adequately but was neither maintainable nor extensible and has since been rendered fully inoperable by language, ISA, and library changes.
