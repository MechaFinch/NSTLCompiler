package notsotiny.lang.compiler.codegen.alloc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import notsotiny.sim.Register;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.aasm.AASMAbstractRegister;
import notsotiny.lang.compiler.aasm.AASMCompileConstant;
import notsotiny.lang.compiler.aasm.AASMInstruction;
import notsotiny.lang.compiler.aasm.AASMLabel;
import notsotiny.lang.compiler.aasm.AASMLiveSet;
import notsotiny.lang.compiler.aasm.AASMMachineRegister;
import notsotiny.lang.compiler.aasm.AASMMemory;
import notsotiny.lang.compiler.aasm.AASMOperation;
import notsotiny.lang.compiler.aasm.AASMPart;
import notsotiny.lang.compiler.aasm.AASMRegister;
import notsotiny.lang.compiler.aasm.AASMStackSlot;
import notsotiny.lang.compiler.codegen.CodeGenV1;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRType;

/**
 * Allocates registers
 * 
 * Using the generalized colorability criteria described in Smith et al A Generalized Algorithm for Graph-Coloring Register Allocation
 * and the graph coloring register allocator described in George & Appel Iterated Register Coalescing
 */
public class RegisterAllocator {
    
    private static Logger LOG = Logger.getLogger(RegisterAllocator.class.getName());
    
    /**
     * Data structures used throughout register allocation
     */
    private static class RAData {
        // Data structures as described in George & Appel A.1
        // A.1.1 Nodes
        Set<RAIGNode> initial = new HashSet<>();
        Set<RAIGNode> simplifyWorklist = new HashSet<>();
        Set<RAIGNode> freezeWorklist = new HashSet<>();
        Set<RAIGNode> spillWorklist = new HashSet<>();
        Set<RAIGNode> spilledNodes = new HashSet<>();
        Set<RAIGNode> coalescedNodes = new HashSet<>();
        Set<RAIGNode> coloredNodes = new HashSet<>();
        Deque<RAIGNode> selectStack = new ArrayDeque<>();
        
        // A.1.2 Moves
        Set<RAMove> coalescedMoves = new HashSet<>();
        Set<RAMove> constrainedMoves = new HashSet<>();
        Set<RAMove> frozenMoves = new HashSet<>();
        Set<RAMove> worklistMoves = new HashSet<>();
        Set<RAMove> activeMoves = new HashSet<>();
    }
    
    /**
     * Helper class for recording whether IJKL are used
     */
    private static class CalleeSaveInfo {
        
        static final Set<Register> calleeSavedRegisters = EnumSet.of(Register.JI, Register.LK, Register.XP, Register.YP, Register.I, Register.J, Register.K, Register.L);
        
        Set<Register> used = new HashSet<>();
        
        /**
         * Include use of register r
         * @param r
         */
        void include(Register r) {
            used.add(r);
            
            switch(r) {
                case I:
                    if(used.contains(Register.J)) used.add(Register.JI);
                    break;
                
                case J:
                    if(used.contains(Register.I)) used.add(Register.JI);
                    break;
                
                case K:
                    if(used.contains(Register.L)) used.add(Register.LK);
                    break;
                
                case L:
                    if(used.contains(Register.K)) used.add(Register.LK);
                    break;
                
                default:
            }
        }
    }
    
    /**
     * Allocates registers given abstract assembly
     * @param abstractCode
     * @param sourceFunction
     * @param showRAIGUncolored
     * @param showRAIGColored
     * @return
     * @throws CompilationException
     */
    public static AllocationResult allocateRegisters(List<List<AASMPart>> abstractCode, IRFunction sourceFunction, boolean showRAIGUncolored, boolean showRAIGColored) throws CompilationException {
        
        LOG.finer("Performing register allocation for " + sourceFunction.getID());
        
        // Perform register class analysis
        Map<IRIdentifier, RARegisterClass> registerClassMap = new HashMap<>();  // Register class of each local
        Set<IRIdentifier> preexistingStackSlots = new HashSet<>();              // STACK instruction slots
        Map<IRIdentifier, Integer> stackSlotSizes = new HashMap<>();            // Size of each stack slot
        Set<IRIdentifier> spillLoads = new HashSet<>();                         // IDs loaded to by spilled nodes
        
        for(List<AASMPart> group : abstractCode) {
            for(AASMPart part : group) {
                if(part instanceof AASMInstruction) {
                    // For each instruction, identify and validate the register class of each local
                    assessRegisterClasses(part, registerClassMap, preexistingStackSlots, stackSlotSizes);
                }
            }
        }
        
        // Register allocation time!
        List<List<AASMPart>> currentCode = abstractCode;
        
        // Other information is contained in graph nodes
        RAInterferenceGraph graph = new RAInterferenceGraph();
        
        int iteration = 0;
        
        while(true) {
            LOG.finest("RA iteration " + iteration);
            
            // Main RA algorithm
            RAData data = new RAData();
            
            // Build interference graph
            graph = buildInterferenceGraph(currentCode, registerClassMap, spillLoads, data);
            
            if(showRAIGUncolored) {
                RAIGRenderer.renderRAIG(graph, sourceFunction.getID() + " " + iteration);
            }
            
            // Initialize worklists
            makeWorklists(data);
            
            while(true) {
                /*
                // Sanity check
                for(RAIGNode node : graph.getAllNodes()) {
                    int locations = 0;
                    boolean correct = false;
                    
                    if(node.isPrecolored()) {
                        locations++;
                        correct = true;
                    }
                    
                    if(data.coalescedNodes.contains(node)) {
                        locations++;
                        
                        if(node.getSet() == RASet.COALESCED) {
                            correct = true;
                        }
                    }
                    
                    if(data.coloredNodes.contains(node)) {
                        locations++;
                        
                        if(node.getSet() == RASet.COLORED) {
                            correct = true;
                        }
                    }
                    
                    if(data.freezeWorklist.contains(node)) {
                        locations++;
                        
                        if(node.getSet() == RASet.FREEZE) {
                            correct = true;
                        }
                    }
                    
                    if(data.selectStack.contains(node)) {
                        locations++;
                        
                        if(node.getSet() == RASet.SELECT) {
                            correct = true;
                        }
                    }
                    
                    if(data.simplifyWorklist.contains(node)) {
                        locations++;
                        
                        if(node.getSet() == RASet.SIMPLIFY) {
                            correct = true;
                        }
                    }
                    
                    if(data.spilledNodes.contains(node)) {
                        locations++;
                        
                        if(node.getSet() == RASet.SPILLED) {
                            correct = true;
                        }
                    }
                    
                    if(data.spillWorklist.contains(node)) {
                        locations++;
                        
                        if(node.getSet() == RASet.SPILL) {
                            correct = true;
                        }
                    }
                    
                    if(!correct) {
                        LOG.severe(node.getIdentifier() + " is misplaced");
                    }
                    
                    if(locations != 1) {
                        LOG.severe(node.getIdentifier() + " is in " + locations + " locations");
                    }
                    
                    if(!correct || locations != 1) {
                        throw new IllegalStateException();
                    }
                }
                */
                
                // Allocate!
                if(data.simplifyWorklist.size() != 0) {
                    simplify(data);
                } else if(data.worklistMoves.size() != 0) {
                    coalesce(data);
                } else if(data.freezeWorklist.size() != 0) {
                    freeze(data);
                } else if(data.spillWorklist.size() != 0) {
                    selectSpill(data);
                } else {
                    // Done!
                    break;
                }
            }
            
            assignColors(data);
            
            if(data.spilledNodes.isEmpty()) {
                // we're done!
                break;
            }
            
            // Something spilled. Not done.
            currentCode = rewriteProgram(currentCode, data.spilledNodes, registerClassMap, stackSlotSizes, spillLoads, sourceFunction);
            iteration++;
        }
        
        if(showRAIGColored) {
            RAIGRenderer.renderRAIG(graph, sourceFunction.getID() + " colored");
        }
        
        // Collect allocation information
        Map<IRIdentifier, Register> registerMapping = new HashMap<>();
        
        for(RAIGNode node : graph.getAllNodes()) {
            Register r = node.getColoring();
            
            if(r == Register.NONE) {
                throw new IllegalStateException("Identifier " + node.getIdentifier() + " is unallocated");
            }
            
            registerMapping.put(node.getIdentifier(), r);
        }
        
        // Allocate stack slots
        Map<IRIdentifier, Integer> stackMapping = StackAllocator.allocateStackSlots(currentCode, preexistingStackSlots, stackSlotSizes, sourceFunction);
        
        // Convert abstract registers and stack slots to concrete form and collect into single list
        LOG.finest("Realizing allocated code");
        
        List<AASMPart> allocatedCode = new ArrayList<>();
        
        int stackAllocationSize = stackMapping.size() == 0 ? 0 : Collections.max(stackMapping.values());
        CalleeSaveInfo calleeSaved = new CalleeSaveInfo();
        
        for(List<AASMPart> group : currentCode) {
            for(AASMPart part : group) {
                switch(part) {
                    case AASMInstruction inst: {
                        // Instruction. Convert source and dest
                        AASMPart source = realizePart(inst.getSource(), calleeSaved, registerMapping, stackMapping),
                                 dest = realizePart(inst.getDestination(), calleeSaved, registerMapping, stackMapping);
                        
                        allocatedCode.add(new AASMInstruction(
                            inst.getOp(),
                            dest,
                            source,
                            inst.getCondition()
                        ));
                        break;
                    }
                    
                    case AASMLabel lbl: {
                        // Label. Add.
                        allocatedCode.add(lbl);
                        break;
                    }
                    
                    case @SuppressWarnings("unused") AASMLiveSet ls: {
                        // Liveness set. Don't add.
                        break;
                    }
                    
                    default:
                        // oh no
                        throw new IllegalArgumentException("Invalid AASMPart in program code: " + part);
                }
            }
        }
        
        // Report allocated code
        if(LOG.isLoggable(Level.FINEST)) {
            for(AASMPart part : allocatedCode) {
                if(part instanceof AASMLabel) {
                    LOG.finest(part + "");
                } else {
                    LOG.finest("\t" + part);
                }
            }
        }
        
        return new AllocationResult(allocatedCode, stackAllocationSize, calleeSaved.used);
    }
    
    /**
     * Rewrites the program to spill spilled nodes
     * @param program
     * @param spilledNodes
     * @param registerClassMap
     * @param stackSlotSizes
     * @param sourceFunction
     * @return
     */
    private static List<List<AASMPart>> rewriteProgram(List<List<AASMPart>> program, Set<RAIGNode> spilledNodes, Map<IRIdentifier, RARegisterClass> registerClassMap, Map<IRIdentifier, Integer> stackSlotSizes, Set<IRIdentifier> spillLoads, IRFunction sourceFunction) {
        LOG.finest("Rewriting with spills");
        
        // Create stack slots for spilled node
        Set<IRIdentifier> spilledIDs = new HashSet<>();
        
        for(RAIGNode spilled : spilledNodes) {
            stackSlotSizes.put(spilled.getIdentifier(), spilled.getRegisterClass().type().getSize());
            spilledIDs.add(spilled.getIdentifier());
        }
        
        List<List<AASMPart>> newProgram = new ArrayList<>();
        
        // For each group
        for(List<AASMPart> group : program) {
            // What values are spilled in this
            Set<IRIdentifier> usedInGroup = new HashSet<>(),
                              defdInGroup = new HashSet<>();
            Map<IRIdentifier, IRIdentifier> loadedBeforeGroup = new HashMap<>();
            
            findSpilledInGroup(group, usedInGroup, defdInGroup, spilledIDs);
            
            // Create loads for each loaded spilled value
            for(IRIdentifier id : usedInGroup) {
                // Create new ID
                IRIdentifier loadID = new IRIdentifier(id.getName() + "%" + sourceFunction.getFUID(), IRIdentifierClass.LOCAL);
                loadedBeforeGroup.put(id, loadID);
                spillLoads.add(loadID);
                
                // Get rClass/type info
                RARegisterClass rClass = registerClassMap.get(id);
                IRType type = rClass.type();
                
                // And give it to the new ID
                registerClassMap.put(loadID, rClass);
                
                // Add instruction group to load the local
                newProgram.add(List.of(
                    new AASMInstruction(
                        AASMOperation.MOV,
                        new AASMAbstractRegister(loadID, type),
                        new AASMStackSlot(id, type.getSize())
                    )
                ));
            }
            
            // Copy from old to new, substituting spilled values with loaded locals
            List<AASMPart> newGroup = new ArrayList<>();
            
            for(AASMPart part : group) {
                newGroup.add(copyPart(part, loadedBeforeGroup, spilledIDs));
            }
            
            newProgram.add(newGroup);
            
            // Create stores for each defd spilled value
            for(IRIdentifier id : defdInGroup) {
                IRType type = registerClassMap.get(id).type();
                
                newProgram.add(List.of(
                    new AASMInstruction(
                        AASMOperation.MOV,
                        new AASMStackSlot(id, type.getSize()),
                        new AASMAbstractRegister(id, type)
                    )
                ));
            }
        }
        
        // Report rewritten code
        /*
        if(LOG.isLoggable(Level.FINEST)) {
            for(List<AASMPart> group : newProgram) {
                for(AASMPart part : group) {
                    if(part instanceof AASMLabel) {
                        LOG.finest(part + "");
                    } else {
                        LOG.finest("\t" + part);
                    }
                }
            }
        }
        */
        
        return newProgram;
    }
    
    /**
     * Copies an AASMPart, replacing values in the loadedInGroup map
     * @param part
     * @param loadedInGroup
     * @return
     */
    private static AASMPart copyPart(AASMPart part, Map<IRIdentifier, IRIdentifier> loadedBeforeGroup, Set<IRIdentifier> spilledIDs) {
        if(part == null) {
            return part;
        }
        
        switch(part) {
            case AASMAbstractRegister areg: {
                // Replace ID if applicable
                return new AASMAbstractRegister(loadedBeforeGroup.getOrDefault(areg.id(), areg.id()), areg.getRegisterClass().type(), areg.half(), areg.upper());
            }
            
            case AASMInstruction inst: {
                // Recurse for sub-parts
                AASMPart destination = copyPart(inst.getDestination(), loadedBeforeGroup, spilledIDs),
                         source;
                
                if(inst.getSource() instanceof AASMAbstractRegister areg && spilledIDs.contains(areg.id()) && !loadedBeforeGroup.containsKey(areg.id())) {
                    // Source is a spilled register which is not loaded
                    source = new AASMStackSlot(areg.id(), areg.type().getSize());
                } else {
                    // Source treated normally
                    source = copyPart(inst.getSource(), loadedBeforeGroup, spilledIDs);
                }
                
                return new AASMInstruction(
                    inst.getOp(),
                    destination,
                    source,
                    inst.getCondition()
                );
            }
            
            case AASMLiveSet ls: {
                // Move spilled IDs from local set to stack set
                Set<IRIdentifier> localSet = new HashSet<>(),
                                  stackSet = new HashSet<>(ls.stackSet());
                
                for(IRIdentifier id : ls.localSet()) {
                    if(spilledIDs.contains(id)) {
                        stackSet.add(id);
                    } else {
                        localSet.add(id);
                    }
                }
                
                return new AASMLiveSet(localSet, stackSet, ls.isDef());
            }
            
            case AASMMemory mem: {
                // Recurse for sub-parts
                return new AASMMemory(
                    copyPart(mem.getBase(), loadedBeforeGroup, spilledIDs),
                    copyPart(mem.getIndex(), loadedBeforeGroup, spilledIDs),
                    mem.getScale(),
                    mem.getOffset(),
                    mem.getType()
                );
            }
            
            default:
                // No conversion necessary
                return part;
        }
    }
    
    /**
     * Finds the set of spilled IDs used and defd in the group
     * @param group
     * @param spilledUsed Set of spilled IDs used which must be loaded
     * @param spilledDefd Set of spilled IDs defined
     * @param spilledIDs
     */
    private static void findSpilledInGroup(List<AASMPart> group, Set<IRIdentifier> spilledUsed, Set<IRIdentifier> spilledDefd, Set<IRIdentifier> spilledIDs) {
        for(AASMPart part : group) {
            findSpilledInPart(part, spilledUsed, spilledDefd, spilledIDs);
        }
    }
    
    /**
     * Finds the set of spilled IDs used and defd in the part
     * @param part
     * @param spilledUsed
     * @param spilledDefd
     * @param spilledIDs
     */
    private static void findSpilledInPart(AASMPart part, Set<IRIdentifier> spilledUsed, Set<IRIdentifier> spilledDefd, Set<IRIdentifier> spilledIDs) {
        if(part == null) {
            return;
        }
        
        switch(part) {
            case AASMAbstractRegister areg: {
                // Abstract register, might be spilled
                if(spilledIDs.contains(areg.id())) {
                    spilledUsed.add(areg.id());
                }
                break;
            }
            
            case AASMInstruction inst: {
                // Instruction, operands can have spilled
                // Register source may be substitutable with memory access
                if(!(inst.getSource() instanceof AASMAbstractRegister areg && !(areg.half() && areg.upper()) && (inst.getDestination() instanceof AASMRegister || inst.getDestination() == null))) {
                    // Source isn't a register, or source is an upper half (not representable from stackslot atm), or destination doesn't allow memory source
                    findSpilledInPart(inst.getSource(), spilledUsed, spilledDefd, spilledIDs);
                }
                
                findSpilledInPart(inst.getDestination(), spilledUsed, spilledDefd, spilledIDs);
                
                // Register destination doesn't count as a use
                // Remove unilaterally from Used as this is a def
                if(inst.getDestination() instanceof AASMRegister reg) {
                    if(inst.getOp().definesDestination()) {
                        spilledUsed.remove(reg.id());
                        
                        if(spilledIDs.contains(reg.id())) {
                            spilledDefd.add(reg.id());
                        }
                    } else if(!inst.getOp().usesDestination()) {
                        spilledUsed.remove(reg.id());
                    }
                }
                
                break;
            }
            
            case AASMMemory mem: {
                // Memory, base/index can be spilled
                findSpilledInPart(mem.getBase(), spilledUsed, spilledDefd, spilledIDs);
                findSpilledInPart(mem.getIndex(), spilledUsed, spilledDefd, spilledIDs);
                break;
            }
            
            default:
                // Nothing can be in it
        }
    }
    
    /**
     * Attempt to color the graph
     * @param data
     */
    private static void assignColors(RAData data) {
        LOG.finest("Assigning colors");
        
        // Avoid using Calle if they haven't been used already
        Set<Register> undesirable = EnumSet.copyOf(CalleeSaveInfo.calleeSavedRegisters);
        
        // Color the graph best we can
        while(!data.selectStack.isEmpty()) {
            // Pop a node
            RAIGNode node = data.selectStack.pop();
            
            // Initialize allowed colors
            Set<Register> okColors = node.getAllowedColors();
            
            // Remove the aliasing colors of each neighbor
            for(RAIGNode adjRaw : node.getInterferingNodes()) {
                RAIGNode adj = adjRaw.getAlias();
                
                if(adj.isPrecolored() || adj.getSet() == RASet.COLORED) {
                    okColors.removeAll(MachineRegisters.aliasSet(adj.getColoring()));
                }
            }
            
            if(okColors.isEmpty()) {
                // No colors, spill for realsies
                LOG.finest("Spilled " + node.getIdentifier());
                
                node.setSet(RASet.SPILLED);
                data.spilledNodes.add(node);
            } else {
                // Colors! color it!
                node.setSet(RASet.COLORED);
                data.coloredNodes.add(node);
                
                // When choosing a color, avoid callee-saved registers that haven't been used
                Set<Register> desirable = EnumSet.copyOf(okColors);
                desirable.removeAll(undesirable);
                
                if(desirable.isEmpty()) {
                    // sadge
                    Register color = colorFrom(node, okColors);
                    
                    // Used register is no longer undesirable
                    undesirable.remove(color);
                    try {
                        undesirable.remove(MachineRegisters.upperHalf(color));
                        undesirable.remove(MachineRegisters.lowerHalf(color));
                    } catch(IllegalArgumentException e) {}
                } else {
                    colorFrom(node, desirable);
                }
                
                LOG.finest(node.getIdentifier() + " = " + node.getColoring());
            }
        }
        
        // Propagate colors to aliases
        for(RAIGNode node : data.coalescedNodes) {
            node.setColor(node.getAlias().getColoring());
        }
    }
    
    /**
     * Color a node from the given set of colors
     * @param node
     * @param colors
     * @return Color used
     */
    private static Register colorFrom(RAIGNode node, Set<Register> colors) {
        // Biased coloring
        // Something's up with the coalescing criteria
        // It either misses things biased coloring catches, or causes extra spills
        
        Set<Register> biased = EnumSet.noneOf(Register.class);
        for(RAMove move : node.getMoves()) {
            RAIGNode src = move.source().getAlias();
            RAIGNode dst = move.destination().getAlias();
            
            if(colors.contains(src.getColoring())) {
                biased.add(src.getColoring());
            }
            
            if(colors.contains(dst.getColoring())) {
                biased.add(dst.getColoring());
            }
        }
        
        Register color = (biased.size() > 0) ? get(biased) : get(colors);
        
        //Register color = get(colors);
        node.setColor(color);
        return color;
    }
    
    /**
     * Selects a node heuristically to spill-simplify
     * @param data
     */
    private static void selectSpill(RAData data) {
        // Find the node in the spill list with the lowest cost
        RAIGNode node = null;
        float cost = Integer.MAX_VALUE;
        
        for(RAIGNode candidate : data.spillWorklist) {
            float candCost = candidate.getSpillCost();
            
            if(candCost < cost) {
                node = candidate;
                cost = candCost;
            }
        }
        
        LOG.finest("Selected " + node.getIdentifier() + " for spilling");
        
        // Simplify the node in hopes of optimistic coloring
        // Freeze its moves, though
        data.spillWorklist.remove(node);
        node.setSet(RASet.SIMPLIFY);
        data.simplifyWorklist.add(node);
        
        freezeMoves(node, data);
    }
    
    /**
     * Freezes moves associated with a node from the freeze worklist
     * @param data
     */
    private static void freeze(RAData data) {
        // Move node from freeze to simplify
        RAIGNode node = get(data.freezeWorklist);
        data.freezeWorklist.remove(node);
        node.setSet(RASet.SIMPLIFY);
        data.simplifyWorklist.add(node);
        
        LOG.finest("Froze " + node.getIdentifier());
        
        // And freeze related moves
        freezeMoves(node, data);
    }
    
    /**
     * Freezes moves associated with the node
     * @param node
     * @param data
     */
    private static void freezeMoves(RAIGNode node, RAData data) {
        for(RAMove move : nodeMoves(node, data)) {
            // Move move from its worklist to frozenMoves
            data.activeMoves.remove(move);
            data.worklistMoves.remove(move);
            data.frozenMoves.add(move);
            
            // Change the worklist of the other node in the move if appropriate
            RAIGNode src = move.source(),
                     dst = move.destination();
            
            if(src != node && !isMoveRelated(src, data) && src.getSqueeze() < src.numAvailable()) {
                data.freezeWorklist.remove(src);
                src.setSet(RASet.SIMPLIFY);
                data.simplifyWorklist.add(src);
            }
            
            if(dst != node && !isMoveRelated(dst, data) && dst.getSqueeze() < dst.numAvailable()) {
                data.freezeWorklist.remove(dst);
                dst.setSet(RASet.SIMPLIFY);
                data.simplifyWorklist.add(dst);
            }
        }
    }
    
    /**
     * Coalesces moves
     * @param data
     */
    private static void coalesce(RAData data) {
        // Pick a move from the worklist
        RAMove move = get(data.worklistMoves);
        data.worklistMoves.remove(move);
        
        RAIGNode x = move.source().getAlias();
        RAIGNode y = move.destination().getAlias();
        
        //LOG.finest("Attempting to coalesce " + move);
        
        RAIGNode retain, merge;
        
        // Precolored nodes must be retained
        if(y.isPrecolored()) {
            retain = y;
            merge = x;
        } else {
            retain = x;
            merge = y;
        }
        
        // If the nodes are equal, they're already coalesced
        if(retain == merge) {
            //LOG.finest("Already coalesced");
            
            data.coalescedMoves.add(move);
            addWorklist(retain, data);
            return;
        }
        
        // Things can constrain a move
        // - merge is precolored. implies retain is also precolored.
        // - merge and retained interfere
        // - retain is precolored and its color is excluded by merge
        if(merge.isPrecolored() ||
           (retain.isPrecolored() && merge.getExcludedColors().contains(retain.getColoring())) ||
           (merge.getInterferingNodes().contains(retain))) {
            //LOG.finest("Constrained");
            
            // A 4th check of if the classes are disjoint is potentially necessary.
            // However, there are no disjoint classes
            data.constrainedMoves.add(move);
            addWorklist(retain, data);
            addWorklist(merge, data);
            return;
        }
        
        // Absent precoloring, the node with the smaller class must be retained
        if(!retain.isPrecolored() && retain.getRegisterClass().size() > merge.getRegisterClass().size()) {
            RAIGNode t = retain;
            retain = merge;
            merge = t;
        }
        
        Set<RAIGNode> mergeAdj = adjacent(merge, data);
        if(retain.isPrecolored()) {
            // When precolored, use the OK heuristic
            for(RAIGNode adj : mergeAdj) {
                if(!(adj.getSqueeze() < adj.numAvailable() ||
                     adj.isPrecolored() ||
                     adj.getInterferingNodes().contains(retain))) {
                    // OK failed
                    //LOG.finest("OK failed");
                    data.activeMoves.add(move);
                    return;
                }
            }
        } else {
            // Conservative coalescing heuristic
            Set<RAIGNode> retainAdj = adjacent(retain, data);
            Set<RAIGNode> allNeighbors = new HashSet<>(retainAdj);
            allNeighbors.addAll(mergeAdj);
            
            //if(!allNeighbors.equals(retainAdj)) {
                Set<Register> coalescedAllowed = EnumSet.copyOf(retain.getAllowedColors());
                coalescedAllowed.retainAll(merge.getAllowedColors());
                int available = coalescedAllowed.size();
                
                int k = 0;
                for(RAIGNode neighbor : allNeighbors) {
                    if(neighbor.getSqueeze() >= neighbor.numAvailable()) {
                        k += 1;
                        
                        if(k >= available) {
                            // Conversative failed
                            //LOG.finest("Conservative failed");
                            data.activeMoves.add(move);
                            return;
                        }
                    }
                }
            //}
        }
        
        LOG.finest("Coalesced " + merge.getIdentifier() + " into " + retain.getIdentifier());
        
        // If we're here, we can coalesce
        // Move merge from its set to coalesced
        if(merge.getSet() == RASet.FREEZE) {
            data.freezeWorklist.remove(merge);
        } else {
            data.spillWorklist.remove(merge);
        }
        
        merge.setSet(RASet.COALESCED);
        data.coalescedNodes.add(merge);
        
        // Merge merge into retain
        merge.setAlias(retain);
        retain.addMoves(merge.getMoves());
        retain.addExclusions(merge.getExcludedColors());
        
        for(RAIGNode adj : adjacent(merge, data)) {
            retain.addInterference(adj);
            reduceSqueeze(adj, merge, data);
        }
        
        // Add to spill if appropriate
        if(retain.getSqueeze() >= retain.numAvailable() && retain.getSet() == RASet.FREEZE) {
            data.freezeWorklist.remove(retain);
            retain.setSet(RASet.SPILL);
            data.spillWorklist.add(retain);
        }
        
        addWorklist(retain, data);
    }
    
    /**
     * Adds a node to the appropriate worklist
     * @param node
     * @param data
     */
    private static void addWorklist(RAIGNode node, RAData data) {
        if(!node.isPrecolored() && node.getSqueeze() < node.numAvailable() && !isMoveRelated(node, data)) {
            data.freezeWorklist.remove(node);
            node.setSet(RASet.SIMPLIFY);
            data.simplifyWorklist.add(node);
        }
    }
    
    /**
     * Simplifies the graph by removing trivially-colorable nodes
     * @param data
     */
    private static void simplify(RAData data) {
        // Pick a node, any node
        RAIGNode node = get(data.simplifyWorklist);
        data.simplifyWorklist.remove(node);
        
        LOG.finest("Simplifying " + node.getIdentifier());
        
        // & put it on the stack
        node.setSet(RASet.SELECT);
        data.selectStack.push(node);
        
        // Update neighbors' squeeze
        for(RAIGNode neighbor : adjacent(node, data)) {
            reduceSqueeze(neighbor, node, data);
        }
    }
    
    /**
     * Returns the set of relevant adjacent nodes
     * @param node
     * @param data
     * @return
     */
    private static Set<RAIGNode> adjacent(RAIGNode node, RAData data) {
        Set<RAIGNode> adj = new HashSet<>(node.getInterferingNodes());
        adj.removeAll(data.selectStack);
        adj.removeAll(data.coalescedNodes);
        
        return adj;
    }
    
    /**
     * Updates squeeze of node according to the removal of removed
     * @param node
     * @param removed
     * @param data
     */
    private static void reduceSqueeze(RAIGNode node, RAIGNode removed, RAData data) {
        /*
         *  The non-generalized allocator uses degree == k to check if degree
         *  went from >= k to < k as removing a node always decreases degree
         *  by 1.
         *  Squeeze can additionally change by 0 or more than 1, so we need
         *  a different check
         *  A node being in the spill worklist is equivalent to
         *  degree/squeeze >= k
         */
        boolean highSqueeze = node.getSqueeze() >= node.numAvailable();
        
        node.updateSqueeze(removed.getRegisterClass(), false);
        
        if(highSqueeze && node.getSqueeze() < node.numAvailable()) {
            Set<RAIGNode> adj = adjacent(node, data);
            adj.add(node);
            enableMoves(adj, data);
            
            // Move from spill worklist to freeze or simplify as appropriate
            data.spillWorklist.remove(node);
            
            if(isMoveRelated(node, data)) {
                // move related -> freeze
                node.setSet(RASet.FREEZE);
                data.freezeWorklist.add(node);
            } else {
                // not move related -> simplify
                node.setSet(RASet.SIMPLIFY);
                data.simplifyWorklist.add(node);
            }
        }
    }
    
    /**
     * Enables moves related to the given nodes
     * @param nodes
     * @param data
     */
    private static void enableMoves(Set<RAIGNode> nodes, RAData data) {
        for(RAIGNode node : nodes) {
            for(RAMove move : nodeMoves(node, data)) {
                if(data.activeMoves.contains(move)) {
                    data.activeMoves.remove(move);
                    data.worklistMoves.add(move);
                }
            }
        }
    }
    
    /**
     * Construct initial worklists
     * @param data
     */
    private static void makeWorklists(RAData data) {
        LOG.finest("Initializing worklists");
        
        // For each node
        while(data.initial.size() > 0) {
            // Move from initial to appropriate worklist
            RAIGNode node = get(data.initial);
            data.initial.remove(node);
            
            if(node.getSqueeze() >= node.numAvailable()) {
                // equivalent to degree >= K
                node.setSet(RASet.SPILL);
                data.spillWorklist.add(node);
            } else if(isMoveRelated(node, data)) {
                // move related
                node.setSet(RASet.FREEZE);
                data.freezeWorklist.add(node);
            } else {
                // simplify
                node.setSet(RASet.SIMPLIFY);
                data.simplifyWorklist.add(node);
            }
        }
    }
    
    /**
     * Returns true if node is considered move related
     * @param node
     * @param data
     * @return
     */
    private static boolean isMoveRelated(RAIGNode node, RAData data) {
        // If any element in node.getMoves() is in activeMoves or worklistMoves, node is move related
        boolean hasActive = Collections.disjoint(node.getMoves(), data.activeMoves);
        boolean hasWork = Collections.disjoint(node.getMoves(), data.worklistMoves);
        
        /*
        LOG.finest(node.getIdentifier() + " active: " + hasActive + " work: " + hasWork);
        
        Set<RAMove> what1 = new HashSet<>(node.getMoves()),
                    what2 = new HashSet<>(node.getMoves());
        what1.retainAll(data.activeMoves);
        what2.retainAll(data.worklistMoves);
        
        LOG.info(nodeMoves(node, data) + "");
        LOG.info(what1 + "");
        LOG.info(what2 + "");
        
        boolean related = nodeMoves(node, data).size() != 0;
        
        LOG.finest(node.getIdentifier() + " related? " + related);
        */
        
        return !hasActive || !hasWork;
    }
    
    /**
     * Returns a set containing node.moves intersect (activeMoves union worklistMoves)
     * @param node
     * @param data
     * @return
     */
    private static Set<RAMove> nodeMoves(RAIGNode node, RAData data) {
        Set<RAMove> moves = new HashSet<>(node.getMoves());
        Set<RAMove> possible = new HashSet<>(data.worklistMoves);
        possible.addAll(data.activeMoves);
        moves.retainAll(possible);
        
        return moves;
    }
    
    /**
     * Builds the interference graph
     * @param code
     * @param registerClassMap
     * @param data
     * @return
     */
    private static RAInterferenceGraph buildInterferenceGraph(List<List<AASMPart>> code, Map<IRIdentifier, RARegisterClass> registerClassMap, Set<IRIdentifier> spillLoads, RAData data) {
        LOG.finest("Building interference graph");
        
        RAInterferenceGraph graph = new RAInterferenceGraph();
        
        // Initialize graph with a node for each local
        for(Entry<IRIdentifier, RARegisterClass> entry : registerClassMap.entrySet()) {
            RAIGNode node = new RAIGNode(entry.getKey(), entry.getValue(), RASet.INITIAL, spillLoads.contains(entry.getKey()));
            graph.addNode(node);
            data.initial.add(node);
        }
        
        // For each instruction in reverse order
        Set<IRIdentifier> currentlyLive = new HashSet<>();
        
        for(int g = code.size() - 1; g >= 0; g--) {
            List<AASMPart> group = code.get(g);
            
            for(int i = group.size() - 1; i >= 0; i--) {
                switch(group.get(i)) {
                    case AASMLiveSet ls: {
                        // Liveness set. Set currentlyLive accordingly
                        if(ls.isDef()) {
                            // Live-in/def
                            graph.addDefCost(ls.localSet());
                            currentlyLive.addAll(ls.localSet());
                            
                            // For each define, add an edge to each live
                            for(IRIdentifier def : ls.localSet()) {
                                for(IRIdentifier live : currentlyLive) {
                                    graph.addInterference(live, def);
                                }
                            }
                        } else {
                            // Live-out/use
                            graph.addUseCost(ls.localSet());
                            
                            currentlyLive.clear();
                            currentlyLive.addAll(ls.localSet());
                        }
                        break;
                    }
                    
                    case AASMInstruction inst: {
                        // If something is live during a CALL, exclude ABCD
                        // NOTE - some code (coalesce) relies on not having more nuanced exclusions
                        // Update that if different exclusions can happen
                        if(inst.getOp() == AASMOperation.CALL || inst.getOp() == AASMOperation.CALLA) {
                            for(IRIdentifier live : currentlyLive) {
                                graph.getNode(live).addExclusion(RARegisterClass.I16_HALF);
                            }
                        }
                        
                        // Collect use/def
                        Set<IRIdentifier> used = new HashSet<>();
                        IRIdentifier defined = null;
                        
                        // Populate uses
                        addUses(inst.getDestination(), used);
                        
                        // Set defined if applicable
                        if(inst.getDestination() instanceof AASMRegister reg) {
                            if(inst.getOp().definesDestination()) {
                                defined = reg.id();
                            }
                            
                            if(!inst.getOp().usesDestination()) {
                                used.remove(reg.id());
                            }
                        }
                        
                        addUses(inst.getSource(), used);
                        
                        // BP/SP are not relevant
                        used.remove(MachineRegisters.ID_BP);
                        used.remove(MachineRegisters.ID_SP);
                        
                        // ID-ID Moves have some special handling
                        if(inst.getOp() == AASMOperation.MOV && inst.getSource() instanceof AASMRegister srcReg && inst.getDestination() instanceof AASMRegister dstReg) {
                            // Halves are not their wholes
                            if((srcReg instanceof AASMAbstractRegister absSrc && absSrc.half()) ||
                               (dstReg instanceof AASMAbstractRegister absDst && absDst.half())) {
                                // so don't count them as such
                            } else {
                                // actual moves
                                IRIdentifier src = srcReg.id();
                                IRIdentifier dst = dstReg.id();
                                RAIGNode srcNode = graph.getNode(src);
                                RAIGNode dstNode = graph.getNode(dst);
                                
                                //LOG.info(src + " -> " + dst + "\t\t" + srcNode + " -> " + dstNode);
                                
                                // Remove use from live set
                                currentlyLive.remove(src);
                                
                                // Associate move with each node
                                RAMove move = new RAMove(dstNode, srcNode);
                                
                                // And add it to the move worklist
                                data.worklistMoves.add(move);
                            }
                        }
                        
                        // normal use/def handling
                        if(defined != null && !defined.equals(MachineRegisters.ID_SP)) {
                            graph.addDefCost(defined);
                            
                            for(IRIdentifier live : currentlyLive) {
                                //LOG.info(live + " " + defined);
                                graph.addInterference(live, defined);
                            }
                            
                            currentlyLive.remove(defined);
                        }
                        
                        graph.addUseCost(used);
                        currentlyLive.addAll(used);
                        break;
                    }
                    
                    default:
                        // No action.
                }
            }
        }
        
        return graph;
    }
    
    /**
     * Adds the uses of part to used
     * @param part
     * @param used
     */
    private static void addUses(AASMPart part, Set<IRIdentifier> used) {
        if(part != null) {
            switch(part) {
                case AASMRegister reg: {
                    used.add(reg.id());
                    break;
                }
                
                case AASMMemory mem: {
                    if(mem.getBase() instanceof AASMRegister reg) {
                        used.add(reg.id());
                    }
                    
                    if(mem.getIndex() instanceof AASMRegister reg) {
                        used.add(reg.id());
                    }
                    
                    // Scale and offset should be constants
                }
                
                default:
                    // No action
            }
        }
    }
    
    /**
     * Converts an AASMPart from abstract to concrete form
     * @param part
     * @param ijkl
     * @param registerMapping
     * @param stackMapping
     * @return
     */
    private static AASMPart realizePart(AASMPart part, CalleeSaveInfo ijkl, Map<IRIdentifier, Register> registerMapping, Map<IRIdentifier, Integer> stackMapping) {
        if(part == null) {
            return null;
        }
        
        switch(part) {
            case AASMAbstractRegister areg: {
                // Abstract register. Get mapped register
                Register reg = registerMapping.get(areg.id());
                
                if(areg.half()) {
                    reg = MachineRegisters.half(reg, areg.upper());
                }
                
                ijkl.include(reg);
                
                return new AASMMachineRegister(reg);
            }
            
            case AASMMemory mem: {
                // Memory. Convert subcomponents
                return new AASMMemory(
                    realizePart(mem.getBase(), ijkl, registerMapping, stackMapping),
                    realizePart(mem.getIndex(), ijkl, registerMapping, stackMapping),
                    realizePart(mem.getScale(), ijkl, registerMapping, stackMapping),
                    realizePart(mem.getOffset(), ijkl, registerMapping, stackMapping),
                    mem.getType()
                );
            }
            
            case AASMStackSlot slot: {
                // Stack slot. Convert to memory
                return new AASMMemory(
                    new AASMMachineRegister(Register.BP),
                    AASMCompileConstant.ZERO,
                    AASMCompileConstant.ONE,
                    new AASMCompileConstant(-stackMapping.get(slot.id())),
                    switch(slot.size()) {
                        case 1  -> IRType.I8;
                        case 2  -> IRType.I16;
                        case 4  -> IRType.I32;
                        default -> IRType.NONE;
                    }
                );
            }
            
            default:
                // Doesn't need realizing
                return part;
        }
    }
    
    /**
     * Assess the register class of locals involved in part.
     * Additionally collects pre-existing stack slot information
     * @param part
     * @param registerClassMap
     * @param preexistingStackSlots
     * @param stackSlotSizes
     * @throws CompilationException
     */
    private static void assessRegisterClasses(AASMPart part, Map<IRIdentifier, RARegisterClass> registerClassMap, Set<IRIdentifier> preexistingStackSlots, Map<IRIdentifier, Integer> stackSlotSizes) throws CompilationException {
        if(part == null) {
            return;
        }
        
        switch(part) {
            case AASMInstruction inst: {
                // Instruction. Assess arguemnts.
                assessRegisterClasses(inst.getSource(), registerClassMap, preexistingStackSlots, stackSlotSizes);
                assessRegisterClasses(inst.getDestination(), registerClassMap, preexistingStackSlots, stackSlotSizes);
                break;
            }
            
            case AASMAbstractRegister absReg: {
                // Abstract register. Determine register class
                updateClassWith(absReg.id(), absReg.getRegisterClass(), registerClassMap, part);
                break;
            }
            
            case AASMMemory mem: {
                // Ensure base is I32 and index is I16
                if(mem.getBase() instanceof AASMAbstractRegister absBase) {
                    updateClassWith(absBase.id(), RARegisterClass.I32, registerClassMap, part);
                }
                
                if(mem.getIndex() instanceof AASMAbstractRegister absIndex) {
                    updateClassWith(absIndex.id(), RARegisterClass.I16, registerClassMap, part);
                }
                
                break;
            }
            
            case AASMStackSlot slot: {
                // Add to stack slot information
                preexistingStackSlots.add(slot.id());
                stackSlotSizes.put(slot.id(), slot.size());
                break;
            }
            
            default:
                // No locals involved
        }
    }
    
    /**
     * Update a local with a register class, checking validity
     * @param local
     * @param regClass
     * @param registerClassMap
     */
    private static void updateClassWith(IRIdentifier local, RARegisterClass regClass, Map<IRIdentifier, RARegisterClass> registerClassMap, AASMPart source) throws CompilationException {
        if(registerClassMap.containsKey(local)) {
            // Local is already in map. Check validity
            RARegisterClass knownClass = registerClassMap.get(local);
            
            if(knownClass != regClass) {
                // Classes don't match.
                if((knownClass == RARegisterClass.I16 && regClass == RARegisterClass.I16_HALF) ||
                   (knownClass == RARegisterClass.I32 && regClass == RARegisterClass.I32_HALF)) {
                    // But we just need to convert to halvable
                    registerClassMap.put(local, regClass);
                } else if((knownClass == RARegisterClass.I16_HALF && regClass == RARegisterClass.I16) ||
                          (knownClass == RARegisterClass.I32_HALF && regClass == RARegisterClass.I32)) {
                    // But it's ok
                } else {
                    // And it's an error
                    LOG.severe("Mismatched register classes: " + local + " is " + knownClass + " and " + regClass + " (" + source + ")");
                    throw new CompilationException();
                }
            }
        } else {
            // Local is not in map. Assign class.
            registerClassMap.put(local, regClass);
        }
    }
    
    /**
     * Gets an arbitrary element from the set
     * @param <T>
     * @param set
     * @return
     */
    private static <T> T get(Set<T> set) {
        return set.iterator().next();
    }
    
}
