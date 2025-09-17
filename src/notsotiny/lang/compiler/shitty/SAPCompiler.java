package notsotiny.lang.compiler.shitty;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import fr.cenotelie.hime.redist.ParseResult;
import fr.cenotelie.hime.redist.ParseError;
import fr.cenotelie.hime.redist.SymbolType;
import fr.cenotelie.hime.redist.parsers.InitializationException;
import notsotiny.asm.Assembler.AssemblyObject;
import notsotiny.sim.Register;
import notsotiny.asm.components.Component;
import notsotiny.asm.components.InitializedData;
import notsotiny.asm.components.Instruction;
import notsotiny.asm.components.UninitializedData;
import notsotiny.asm.resolution.ResolvableConstant;
import notsotiny.asm.resolution.ResolvableLocationDescriptor;
import notsotiny.asm.resolution.ResolvableLocationDescriptor.LocationType;
import notsotiny.asm.resolution.ResolvableMemory;
import notsotiny.asm.resolution.ResolvableValue;
import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.NSTCompiler;
import notsotiny.lang.compiler.context.*;
import notsotiny.lang.compiler.types.*;
import notsotiny.lang.parser.NstlgrammarParser;
import notsotiny.lib.data.Pair;
import notsotiny.lang.parser.NstlgrammarLexer;
import notsotiny.sim.ops.Opcode;
import notsotiny.sim.ops.Operation;

/**
 * First version of the compiler
 * This compiler is fully deprecated, as it
 * - is missing features
 * - produces incorrect results in some cases
 * - is no longer compatible with the CLI frontend
 * - is just bad in general
 * 
 */
public class SAPCompiler implements NSTCompiler {
    
    private static Logger LOG = Logger.getLogger(SAPCompiler.class.getName());
    
    public SAPCompiler() {
        
    }
    
    // TODO: multi-word comparisons are wrong, low comp should be always unsigned
    // TODO: BIO indeces have changed from sign extend to zero extend. This might have broken things
    
    // special function names
    private static final Set<String> specialFunctionNames = new HashSet<>();
    
    static {
        specialFunctionNames.add("sxu8");
        specialFunctionNames.add("sxi8");
        specialFunctionNames.add("sxu16");
        specialFunctionNames.add("sxi16");
        specialFunctionNames.add("sxu32");
        specialFunctionNames.add("sxi32");
        specialFunctionNames.add("sxptr");
        
        specialFunctionNames.add("zxu8");
        specialFunctionNames.add("zxi8");
        specialFunctionNames.add("zxu16");
        specialFunctionNames.add("zxi16");
        specialFunctionNames.add("zxu32");
        specialFunctionNames.add("zxi32");
        specialFunctionNames.add("zxptr");
        
        specialFunctionNames.add("mulh");
        specialFunctionNames.add("mulsh");
        
        specialFunctionNames.add("padd4");
        specialFunctionNames.add("padd8");
        specialFunctionNames.add("psub4");
        specialFunctionNames.add("psub8");
        specialFunctionNames.add("pinc4");
        specialFunctionNames.add("pinc8");
        specialFunctionNames.add("pdec4");
        specialFunctionNames.add("pdec8");
        
        specialFunctionNames.add("pmul4");
        specialFunctionNames.add("pmul8");
        specialFunctionNames.add("pmulh4");
        specialFunctionNames.add("pmulh8");
        specialFunctionNames.add("pmulsh4");
        specialFunctionNames.add("pmulsh8");
        
        specialFunctionNames.add("pdiv4");
        specialFunctionNames.add("pdiv8");
        specialFunctionNames.add("pdivm4");
        specialFunctionNames.add("pdivm8");
        specialFunctionNames.add("pdivs4");
        specialFunctionNames.add("pdivs8");
        specialFunctionNames.add("pdivms4");
        specialFunctionNames.add("pdivms8");
        
        specialFunctionNames.add("std.halt");
    }
    
    /*
     * Compilation State
     * so we don't have to pass it around
     */
    // AssemblyObject parts
    List<Component> allInstructions = new ArrayList<>();
    Map<String, Integer> labelIndexMap = new HashMap<>();
    String libraryName = "";
    HashMap<String, String> libraryNamesMap = new HashMap<>();
    HashMap<File, String> libraryFilesMap = new HashMap<>();
    HashMap<String, List<Integer>> incomingReferences = new HashMap<>();
    HashMap<String, Integer> outgoingReferences = new HashMap<>(),
                             incomingReferenceWidths = new HashMap<>(),
                             outgoingReferenceWidths = new HashMap<>();
    
    // compilation stuff
    // reset per file
    HashMap<String, Component> globalComponents = new HashMap<>();          // globals
    HashMap<String, String> stringConstants = new HashMap<>();              // string constants can't just be slapped into program code, put them at the end
    ContextStack contextStack = new ContextStack(new AllocatedContextMarker(new HashMap<>(), 0));
    HashMap<String, String> knownLibraryNames = new HashMap<>();            // library names for refernce validation
    HashMap<String, TypedValue> compilerDefinitions = new HashMap<>();      // maps names to values
    HashMap<String, NSTLType> typeDefinitions = new HashMap<>();            // defined types
    HashMap<String, FunctionHeader> functionDefinitions = new HashMap<>();  // defined functions
    boolean errorsEncountered = false;
    
    // reset by each function
    boolean regIUsed = false,
            regJUsed = false,
            regKUsed = false,
            regLUsed = false;
    int stackAllocationSize = 0,
        endifLabelCounter = 0,
        loopLabelCounter = 0,
        endLoopLabelCounter = 0,
        longComparisonCounter = 0;
    boolean inConditionalContext = false,
            inLoopContext = false,
            lastWasReturn = false;
    String functionName = "";
    FunctionHeader functionHeader = null;
    
    @Override
    public AssemblyObject compile(ASTNode astRoot, String defaultLibName, FileLocator locator) throws CompilationException {
        LOG.info(() -> "Compiling file " + defaultLibName);
        
        // initialize state
        allInstructions = new ArrayList<>();
        labelIndexMap = new HashMap<>();
        libraryName = defaultLibName;
        libraryNamesMap = new HashMap<>();
        incomingReferences = new HashMap<>();
        outgoingReferences = new HashMap<>();
        incomingReferenceWidths = new HashMap<>();
        outgoingReferenceWidths = new HashMap<>();
        
        globalComponents = new HashMap<>();
        stringConstants = new HashMap<>();
        knownLibraryNames = new HashMap<>();
        compilerDefinitions = new HashMap<>();
        typeDefinitions = generateDefaultTypemap();
        functionDefinitions = new HashMap<>();
        
        errorsEncountered = false;
        
        knownLibraryNames.put("std", "std");
        knownLibraryNames.put(defaultLibName, defaultLibName);
        
        // create base context
        contextStack = new ContextStack(new AllocatedContextMarker(new HashMap<>(), 0));
        contextStack.pushSymbol(new ContextSymbol("true", new TypedRaw(new ResolvableConstant(1), RawType.BOOLEAN)));
        contextStack.pushSymbol(new ContextSymbol("false", new TypedRaw(new ResolvableConstant(0), RawType.BOOLEAN)));
        contextStack.pushSymbol(new ContextSymbol("%ji", RawType.PTR, new ResolvableLocationDescriptor(LocationType.REGISTER, Register.JI)));
        
        // library headers
        List<ASTNode> headerCode = new ArrayList<>();
        headerCode.addAll(astRoot.getChildren());
        
        // include our own header if possible
        ASTNode headerNode;
        
        try {
            headerNode = this.getHeaderContents(Paths.get(libraryName), libraryName, locator);
            
            if(headerNode != null) {
                // we can't addAll as we don't want external defs of our own functions
                headerCode.addAll(headerNode.getChildren());
            }
        } catch(NoSuchFileException e) {
            
        }
        
        // define type, function, and libraray names
        LOG.fine("Evaluating names");
        for(int i = 0; i < headerCode.size(); i++) {
            ASTNode topNode = headerCode.get(i);
            String n;
            
            switch(topNode.getSymbol().getID()) {
                case NstlgrammarParser.ID.VARIABLE_STRUCTURE_DEFINITION:
                    // name the structure
                    n = topNode.getChildren().get(0).getValue();
                    checkNameConflicts(n);
                    LOG.finer("Named structure " + n);
                    typeDefinitions.put(n, new StructureType(n));
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_TYPE_ALIAS:
                    // name the alias
                    n = topNode.getChildren().get(0).getValue();
                    checkNameConflicts(n);
                    LOG.finer("Named alias " + n);
                    typeDefinitions.put(n, new AliasType(n));
                    break;
                    
                case NstlgrammarParser.ID.VARIABLE_LIBRARY_INCLUSION:
                    try {
                        headerNode = compileLibraryInclusion(topNode, locator);
                        
                        if(headerNode != null) {
                            headerCode.addAll(headerNode.getChildren());
                        }
                    } catch(NoSuchFileException e) {
                        LOG.warning("Could not find header");
                        //throw new CompilationException();
                    }
                    break;
                    
                default:
            }
        }
        
        // finalize types
        LOG.fine("Finalizing types");;
        for(ASTNode topNode : headerCode) {
            String n;
            
            switch(topNode.getSymbol().getID()) {
                case NstlgrammarParser.ID.VARIABLE_TYPE_ALIAS:
                    // fill in the alias
                    n = topNode.getChildren().get(0).getValue();
                    NSTLType t = constructType(topNode.getChildren().get(1)); 
                    
                    LOG.finest("Defined type alias " + n + " = " + t);
                    
                    ((AliasType) typeDefinitions.get(n)).setRealType(t);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_STRUCTURE_DEFINITION:
                    // fill in the structure
                    n = topNode.getChildren().get(0).getValue();
                    //checkNameConflicts(n); //already done
                    StructureType st = (StructureType) compileStructureDefinition(n, topNode.getChildren().get(1));
                    
                    LOG.finest("Defined incomplete structure " + n + " = " + st);
                    
                    ((StructureType) typeDefinitions.get(n)).addMembers(st.getMemberNames(), st.getMemberTypes());
                    break;
                
                default:
            }
        }
        
        // define libraries, functions, constants, and globals
        LOG.fine("Creating definitions");
        for(ASTNode topNode : headerCode) {
            String n;
            //NSTLType t;
            
            switch(topNode.getSymbol().getID()) {
                case NstlgrammarParser.ID.VARIABLE_FUNCTION_DEFINITION:
                    FunctionHeader fh = compileFunctionHeader(topNode.getChildren().get(0));
                    functionDefinitions.put(fh.getName(), fh);
                    
                    // verify return type fits in D:A
                    if(fh.getReturnType().getSize() > 4) {
                        LOG.severe("Function return types cannot be more than 4 bytes: " + fh);
                        errorsEncountered = true;
                        break;
                    }
                    
                    // external = done, internal = do later
                    if(topNode.getChildren().get(0).getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_EXTERNAL_FUNCTION_HEADER) {
                        // external
                        LOG.finer("Defined external function: " + fh);
                    } else {
                        LOG.finer("Defined internal function: " + fh);
                    }
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_COMPILER_DEFINITION:
                    compileDefinition(topNode);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_VALUE_CREATION:
                    compileValueCreation(topNode, allInstructions, labelIndexMap);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_LIBRARY_INCLUSION:
                case NstlgrammarParser.ID.VARIABLE_STRUCTURE_DEFINITION:
                case NstlgrammarParser.ID.VARIABLE_TYPE_ALIAS:
                    // already handled
                    break;
                
                default:
                    LOG.severe("Unexpected node: " + detailed(topNode));
                    errorsEncountered = true;
            }
        }
        
        // get structure sizes up to date
        LOG.fine("Finalizing structure sizes");
        boolean sizeChanged = true;
        List<String> updatedNames = new ArrayList<>();
        while(sizeChanged) {
            sizeChanged = false;
            updatedNames.clear();
            
            for(NSTLType t : typeDefinitions.values()) {
                sizeChanged |= t.updateSize(updatedNames);
            }
        }
        
        for(NSTLType t : typeDefinitions.values()) {
            if(t instanceof StructureType st) {
                // structure sizes are calculated in a way that won't crash if they recurse, check explicitly
                if(st.checkRecursion(new ArrayList<>())) {
                    LOG.severe("Invalid type: structure " + st + " is recursive.");
                    errorsEncountered = true;
                }
                
                LOG.finer("Defined structure " + st);
            }
        }        
        
        // compile actual code
        LOG.fine("Compiling functions");
        for(ASTNode topNode : astRoot.getChildren()) {
            if(topNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_FUNCTION_DEFINITION) {
                ASTNode header = topNode.getChildren().get(0);
                
                if(header.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_INTERNAL_FUNCTION_HEADER) {
                    functionHeader = functionDefinitions.get(header.getChildren().get(0).getValue());
                    compileFunction(topNode);
                }
            }
        }
        
        // log final global context
        LOG.finest("FINAL GLOBAL CONTEXT");
        for(ContextEntry ce : contextStack.getStack()) {
            LOG.finest(ce.toString());
        }
        
        if(contextStack.getContextCounter() != 0) {
            LOG.severe("Error: malformed final context. Counter: " + contextStack.getContextCounter());
            errorsEncountered = true;
        }
        
        if(errorsEncountered) {
            throw new IllegalStateException("Errors encountered during compilation. See severe logs above.");
        }
        
        /*
         * PEEPHOLE OPTIMZIATIONS
         * with UninitializedData(0, 0) we can eliminate instructions without recomputing the label index map over and over
         */
        Component nullComponent = new UninitializedData(0, 0);
        
        LOG.fine("Running peephole optimizations");
        if(allInstructions.size() > 3) {
            int aiSize = allInstructions.size();
            
            Component peep0 = allInstructions.get(aiSize - 4),
                      peep1 = allInstructions.get(aiSize - 3),
                      peep2 = allInstructions.get(aiSize - 2),
                      peep3 = allInstructions.get(aiSize - 1);
            
            for(int i = aiSize - 1; i >= 3; i--) {
                peep0 = allInstructions.get(i - 3);
                peep1 = allInstructions.get(i - 2);
                peep2 = allInstructions.get(i - 1);
                peep3 = allInstructions.get(i);
                
                Instruction i0 = (peep0 instanceof Instruction inst) ? inst : null,
                            i1 = (peep1 instanceof Instruction inst) ? inst : null,
                            i2 = (peep2 instanceof Instruction inst) ? inst : null,
                            i3 = (peep3 instanceof Instruction inst) ? inst : null;
                
                boolean i0Labeled = labelIndexMap.containsValue(i - 3),
                        i1Labeled = labelIndexMap.containsValue(i - 2),
                        i2Labeled = labelIndexMap.containsValue(i - 1),
                        i3Labeled = labelIndexMap.containsValue(i - 0);
                
                // no-op jump elimination
                // if we find JMP 0, we can remove it
                if(i3 != null && i3.getOpcode().getType() == Operation.JMP) {
                    ResolvableLocationDescriptor rld = i3.getSourceDescriptor();
                    
                    if(rld.getType() == LocationType.IMMEDIATE && rld.getImmediate() instanceof ResolvableConstant rc) {
                        // check resolved zeroes
                        if(rc.isResolved() && rc.value() == 0) {
                            allInstructions.set(i, nullComponent);
                        } else if(!rc.isResolved()) {
                            // check unresolved zeroes
                            String n = rc.getName();
                            
                            if(labelIndexMap.containsKey(n) && labelIndexMap.get(n) == i + 1) {
                                allInstructions.set(i, nullComponent);
                                LOG.finest("Eliminated no-op jump");
                            }
                        }
                    }
                }
                
                // no-op LEA elimination
                // LEA <pair>, [<pair>] is a no-op
                if(i3 != null && i3.getOpcode() == Opcode.LEA_RIM) {
                    ResolvableLocationDescriptor sd = i3.getSourceDescriptor(),
                                                 dd = i3.getDestinationDescriptor();
                    
                    if(dd.getRegister() == sd.getMemory().getBase() && sd.getMemory().getOffset().value() == 0) {
                        allInstructions.set(i, nullComponent);
                        LOG.finest("Eliminated no-op LEA");
                    }
                }
                
                // LEA as address elimination
                if(i2 != null && i2.getOpcode() == Opcode.LEA_RIM && i3 != null) {
                    ResolvableLocationDescriptor ptrReg = i2.getDestinationDescriptor(),
                                                 ptrVal = i2.getSourceDescriptor(),
                                                 dst = i3.getDestinationDescriptor(),
                                                 src = i3.getSourceDescriptor();
                    
                    // LEA <pair>, [whatever]   -> op <x>, [whatever]
                    // MOV <x>, [<pair>]
                    if(!i3Labeled && src.getType() == LocationType.MEMORY && src.getMemory().getBase() == ptrReg.getRegister()) {
                        // if the ptr is resolved we can eliminate, but if it's unresolved we can only do so if the second offset is zero
                        ResolvableMemory ptrMem = ptrVal.getMemory();
                        
                        if(ptrMem.getOffset().isResolved()) {
                            ptrVal = new ResolvableLocationDescriptor(LocationType.MEMORY, src.getSize(),
                                     new ResolvableMemory(ptrMem.getBase(), ptrMem.getIndex(), ptrMem.getScale(), new ResolvableConstant(ptrMem.getOffset().value() + src.getMemory().getOffset().value())));
                            
                            allInstructions.set(i - 1, new Instruction(
                                i3.getOpcode(),
                                dst,
                                ptrVal,
                                i3.hasFixedSize()
                            ));
                            
                            allInstructions.set(i, nullComponent);
                            LOG.finest("Eliminated LEA as address");
                        } else if(src.getMemory().getOffset().value() == 0) {
                            ptrVal.setSize(src.getSize(), false);
                            allInstructions.set(i - 1, new Instruction(
                                i3.getOpcode(),
                                dst,
                                ptrVal,
                                i3.hasFixedSize()
                            ));
                            allInstructions.set(i, nullComponent);
                            LOG.finest("Eliminated LEA as address");
                        }
                    }
                }
                
                // move as address elimination
                // MOVW D:A, <pair>     -> MOV(W) D:A, [<pair>]
                // MOV(W) D:A, [D:A]
                if(!i3Labeled && i2 != null && i2.getOpcode() == Opcode.MOVW_RIM && i3 != null && (i3.getOpcode() == Opcode.MOV_RIM || i3.getOpcode() == Opcode.MOVW_RIM)) {
                    ResolvableLocationDescriptor sd2 = i2.getSourceDescriptor(),
                                                 dd2 = i2.getDestinationDescriptor(),
                                                 sd3 = i3.getSourceDescriptor(),
                                                 dd3 = i3.getDestinationDescriptor();
                    
                    if(dd2.equals(new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA)) &&
                       sd2.getType() == LocationType.REGISTER && sd2.getSize() == 4 &&
                       sd3.getType() == LocationType.MEMORY && sd3.getMemory().equals(new ResolvableMemory(Register.DA, Register.NONE, 0, 0)) &&
                       dd3.getType() == LocationType.REGISTER && (dd3.getRegister() == Register.DA || dd3.getRegister() == Register.A || dd3.getRegister() == Register.AL)) {
                        allInstructions.set(i - 1, new Instruction(
                            i3.getOpcode(),
                            dd3,
                            new ResolvableLocationDescriptor(LocationType.MEMORY, sd3.getSize(), new ResolvableMemory(sd2.getRegister(), Register.NONE, 0, 0)),
                            false
                        ));
                        allInstructions.set(i, nullComponent);
                        LOG.finest("Eliminated MOV as address");
                    }
                }
                
                // extra move elimination
                // MOV <a>, <b>     -> MOV <a>, <b>
                // MOV <b>, <a>
                //
                // MOV <a>, <b>     -> MOV <a>, <c>
                // MOV <a>, <c>
                if(!i1Labeled && i0 != null && i1 != null) {
                    // MOV-MOV
                    if(i0.getOpcode() == Opcode.MOVW_RIM && i1.getOpcode() == Opcode.MOVW_RIM) {
                        ResolvableLocationDescriptor sd0 = i0.getSourceDescriptor(),
                                                     dd0 = i0.getDestinationDescriptor(),
                                                     sd1 = i1.getSourceDescriptor(),
                                                     dd1 = i1.getDestinationDescriptor(),
                                                     acd = new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA);
                        
                        // un-move elimination
                        if(sd0.equals(dd1) && dd0.equals(sd1)) {
                            allInstructions.set(i - 2, nullComponent);
                            LOG.finest("Eliminated unmove");
                        }
                        
                        // move-via-accumulator elimination
                        // we can do this because the accumulator won't get used again
                        if(dd0.equals(acd) && sd1.equals(acd) && (sd0.getType() == LocationType.REGISTER || dd1.getType() == LocationType.REGISTER)) {
                            allInstructions.set(i - 3, new Instruction(
                                Opcode.MOVW_RIM,
                                dd1,
                                sd0,
                                false
                            ));
                            allInstructions.set(i - 2, nullComponent);
                            LOG.finest("Eliminated move-via-accumulator");
                        }
                        
                        // move-overwrite elimination
                        // only apply to register dest in case of side effects
                        // however, we can't implement this without dealing with MOV <dst>, <src>; MOV <dst>, [<dst>] and the like
                        if(dd0.equals(dd1) && dd0.getType() == LocationType.REGISTER && sd0.getType() == LocationType.REGISTER && sd1.getType() == LocationType.REGISTER) {
                            allInstructions.set(i - 3, nullComponent);
                            LOG.finest("Eliminated move-overwrite");
                        }
                    } else if(i0.getOpcode() == Opcode.MOV_RIM && i1.getOpcode() == Opcode.MOV_RIM) {
                        // move-via-accumulator elimination
                        ResolvableLocationDescriptor sd0 = i0.getSourceDescriptor(),
                                                     dd0 = i0.getDestinationDescriptor(),
                                                     sd1 = i1.getSourceDescriptor(),
                                                     dd1 = i1.getDestinationDescriptor(),
                                                     aad = new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                                     ald = new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL);
                        
                        // un-move elimination
                        if(sd0.equals(dd1) && dd0.equals(sd1)) {
                            allInstructions.set(i - 2, nullComponent);
                            LOG.finest("Eliminated unmove");
                        }
                        
                        if(((dd0.equals(aad) && sd1.equals(aad)) || (dd0.equals(ald) && sd1.equals(ald))) && (sd0.getType() == LocationType.REGISTER || dd1.getType() == LocationType.REGISTER)) {
                            allInstructions.set(i - 3, new Instruction(
                                Opcode.MOV_RIM,
                                dd1,
                                sd0,
                                false
                            ));
                            allInstructions.set(i - 2, nullComponent);
                            LOG.finest("Eliminated move-via-accumulator");
                        }
                        
                        // move-overwrite elimination
                        // only apply to register dest in case of side effects
                        // however, we can't implement this without dealing with MOV <dst>, <src>; MOV <dst>, [<dst>] and the like
                        if(dd0.equals(dd1) && dd0.getType() == LocationType.REGISTER && sd0.getType() == LocationType.REGISTER && sd1.getType() == LocationType.REGISTER) {
                            allInstructions.set(i - 3, nullComponent);
                            LOG.finest("Eliminated move-overwrite");
                        }
                    }
                }
            }
        }
        
        // label optimization - If a label points to an unconditional jump, change the label to that jump's target
        for(Entry<String, Integer> e : labelIndexMap.entrySet()) {
            Component c = allInstructions.get(e.getValue());
            
            if(c instanceof Instruction i && i.getOpcode() == Opcode.JMP_I32) {
                String newTarget = ((ResolvableConstant) i.getSourceDescriptor().getImmediate()).getName();
                labelIndexMap.put(e.getKey(), labelIndexMap.get(newTarget));
                LOG.finest("Rerouted label " + e.getKey() + " to " + newTarget);
            }
        }
        
        // clean up component list
        // TODO
        
        LOG.fine("Placing string constants");
        // add globals and string constants to the end of the code
        for(Entry<String, String> e : stringConstants.entrySet()) {
            String name = e.getKey(),
                   val = e.getValue();
            
            // string to resolvableconstant bytes
            List<ResolvableValue> rcData = new ArrayList<>(val.length());
            
            for(char c : val.toCharArray()) {
                rcData.add(new ResolvableConstant(((int) c) & 0xFF));
            }
            
            rcData.add(new ResolvableConstant(0));
            
            labelIndexMap.put(name, allInstructions.size());
            allInstructions.add(new InitializedData(rcData, 1));
        }
        
        LOG.fine("Placing globals");
        for(Entry<String, Component> e : globalComponents.entrySet()) {
            labelIndexMap.put(e.getKey(), allInstructions.size());
            allInstructions.add(e.getValue());
        }
        
        // log final component list
        /*
        LOG.finest("FINAL COMPONENT LIST");
        for(int i = 0; i < allInstructions.size(); i++) {
            LOG.finest(allInstructions.get(i).toString());
        }
        */
        
        return new AssemblyObject(allInstructions, labelIndexMap, libraryName, libraryFilesMap, incomingReferences, outgoingReferences, incomingReferenceWidths, outgoingReferenceWidths);
    }
    
    /**
     * Compiles a function
     * 
     * @param node
     */
    private void compileFunction(ASTNode node) {
        List<ASTNode> code = node.getChildren().get(1).getChildren();
        String name = functionHeader.getName();
        
        LOG.fine("Compiling function " + name);
        
        // create context
        contextStack.pushContext();
        
        regIUsed = false;
        regJUsed = false;
        regKUsed = false;
        regLUsed = false;
        stackAllocationSize = 0;
        inConditionalContext = false;
        inLoopContext = false;
        endifLabelCounter = 0;
        loopLabelCounter = 0;
        endLoopLabelCounter = 0;
        longComparisonCounter = 0;
        functionName = name;
        
        // assign argument symbols
        List<String> argNames = functionHeader.getArgumentNames();
        List<NSTLType> argTypes = functionHeader.getArgumentTypes();
        
        for(int i = 0, offs = 8; i < argNames.size(); i++) {
            NSTLType t = argTypes.get(i);
            
            contextStack.pushSymbol(new ContextSymbol(
                argNames.get(i), t,
                new ResolvableLocationDescriptor(LocationType.MEMORY, t.getSize(), new ResolvableMemory(Register.BP, Register.NONE, 0, offs))
            ));
            
            offs += t.getSize();
        }
        
        // local code, so we can insert the prologue and epilogue with all information
        List<Component> functionComponents = new ArrayList<>();
        Map<String, Integer> functionLabelIndexMap = new HashMap<>();
        
        // compile code
        compileFunctionCode(code, functionComponents, functionLabelIndexMap);
        
        // write final code
        labelIndexMap.put(name, allInstructions.size());
        
        // create prologue
        // BP
        allInstructions.add(new Instruction(Opcode.PUSHW_BP, true));
        allInstructions.add(new Instruction(
            Opcode.MOVW_RIM,
            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.BP),
            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.SP),
            true
        ));
        
        // Allocate local variables
        if(stackAllocationSize > 0) { 
            // fits in I8 = just add
            if(stackAllocationSize < 0x80) {
                allInstructions.add(new Instruction(
                    Opcode.SUBW_SP_I8,
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 1, new ResolvableConstant(stackAllocationSize)),
                    false, true
                ));
            } else {
                // doesnt fit in I8 = sub via LEA
                allInstructions.add(new Instruction(
                    Opcode.LEA_RIM,
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.SP),
                    new ResolvableLocationDescriptor(LocationType.MEMORY, 0, new ResolvableMemory(Register.SP, Register.NONE, 0, -stackAllocationSize)),
                    false
                ));
            }
        }
        
        // Push I, J, K, L if needed
        if(regIUsed) {
            allInstructions.add(new Instruction(Opcode.PUSH_I, true));
        }
        
        if(regJUsed) {
            allInstructions.add(new Instruction(Opcode.PUSH_J, true));
        }
        
        if(regKUsed) {
            allInstructions.add(new Instruction(Opcode.PUSH_K, true));
        }
        
        if(regLUsed) {
            allInstructions.add(new Instruction(Opcode.PUSH_L, true));
        }
        
        // map local labels
        int startIndex = allInstructions.size();
        for(Entry<String, Integer> lbl : functionLabelIndexMap.entrySet()) {
            labelIndexMap.put(lbl.getKey(), lbl.getValue() + startIndex);
        }
        
        // copy code
        for(Component c : functionComponents) {
            allInstructions.add(c);
        }
        
        // create epilogue
        labelIndexMap.put(name + "%epilogue", allInstructions.size());
        
        // Pop I, J, K, L if needed
        if(regLUsed) {
            allInstructions.add(new Instruction(Opcode.POP_L, true));
        }
        
        if(regKUsed) {
            allInstructions.add(new Instruction(Opcode.POP_K, true));
        }
        
        if(regJUsed) {
            allInstructions.add(new Instruction(Opcode.POP_J, true));
        }
        
        if(regIUsed) {
            allInstructions.add(new Instruction(Opcode.POP_I, true));
        }
        
        // deallocate locals
        // same code as allocation, but with sub
        if(stackAllocationSize > 0) { 
            // fits in I8 = just add
            if(stackAllocationSize < 0x80) {
                allInstructions.add(new Instruction(
                    Opcode.ADDW_SP_I8,
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 1, new ResolvableConstant(stackAllocationSize)),
                    false, true
                ));
            } else {
                // doesnt fit in I8 = add via LEA
                allInstructions.add(new Instruction(
                    Opcode.LEA_RIM,
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.SP),
                    new ResolvableLocationDescriptor(LocationType.MEMORY, 0, new ResolvableMemory(Register.SP, Register.NONE, 0, stackAllocationSize)),
                    false
                ));
            }
        }
        
        // BP
        allInstructions.add(new Instruction(Opcode.POPW_BP, true));
        allInstructions.add(new Instruction(Opcode.RET, true));
        
        contextStack.popContext();
    }
    
    /**
     * Compiles a function header
     * 
     * @param node
     */
    private FunctionHeader compileFunctionHeader(ASTNode node) {
        List<ASTNode> children = node.getChildren();
        List<ASTNode> arguments = children.get(1).getChildren();
        
        LOG.finest("Compiling function header");
        
        String name = children.get(0).getValue();
        List<String> argumentNames = new ArrayList<>();
        List<NSTLType> argumentTypes = new ArrayList<>();
        NSTLType returnType = (children.size() == 3) ? constructType(children.get(2)) : RawType.NONE;
        
        for(int i = 0; i < arguments.size(); i++) {
            ASTNode argNode = arguments.get(i);
            List<ASTNode> ac = argNode.getChildren();
            
            // named or nameless
            if(argNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_NAMED_ARGUMENT) {
                argumentTypes.add(constructType(ac.get(0)));
                argumentNames.add(ac.get(1).getValue());
            } else {
                argumentTypes.add(constructType(ac.get(0)));
                argumentNames.add("arg" + i);
            }
        }
        
        if(name.startsWith("_")) name = name.substring(1);
        
        checkNameConflicts(name);
        
        return new FunctionHeader(name, argumentNames, argumentTypes, returnType, node);
    }
    
    /**
     * Compiles a list of function_code
     * 
     * @param code
     * @param name
     * @param header
     * @param localCode
     * @param localLabelMap
     */
    private void compileFunctionCode(List<ASTNode> code, List<Component> localCode, Map<String, Integer> localLabelMap) {
        lastWasReturn = false;
        
        for(ASTNode codeNode : code) {
            switch(codeNode.getSymbol().getID()) {
                case NstlgrammarParser.ID.VARIABLE_VALUE_CREATION:
                    compileValueCreation(codeNode, localCode, localLabelMap);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_ASSIGNMENT:
                    compileAssignment(codeNode.getChildren().get(0), codeNode.getChildren().get(1), localCode, localLabelMap);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_IF_CONSTRUCT:
                    compileIfConstruct(codeNode, localCode, localLabelMap);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_WHILE_CONSTRUCT:
                    compileWhileConstruct(codeNode, false, localCode, localLabelMap);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_UNTIL_CONSTRUCT:
                    compileWhileConstruct(codeNode, true, localCode, localLabelMap);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_FOR_CONSTRUCT:
                    compileForConstruct(codeNode, localCode, localLabelMap);
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_RETURN:
                    lastWasReturn = true;
                    List<ASTNode> children = codeNode.getChildren();
                    boolean isNone = children.size() == 0 || children.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_NONE;
                    
                    // warn on return with returns none
                    if(functionHeader.getReturnType() == RawType.NONE && !isNone) {
                        LOG.warning("Found return statement in function that returns none. It will still be computed, but likely ignored");
                    }
                    
                    if(!isNone) compileValueComputation(codeNode.getChildren().get(0), localCode, localLabelMap, "%accumulator", functionHeader.getReturnType(), false);
                    
                    localCode.add(new Instruction(
                        Opcode.JMP_I32,
                        new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(functionName + "%epilogue")),
                        false, false
                    ));
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_BREAK:
                    if(inLoopContext) {
                        List<ASTNode> c = codeNode.getChildren();
                        String target;
                        
                        if(c.size() > 0) {
                            // named break, jump to %lbl_<name>%end
                            target = "%lbl_" + c.get(0).getValue() + "%end";
                        } else {
                            // unnamed break, jump to current endloop
                            target = functionName + ".endloop" + (loopLabelCounter - 1); 
                        }
                        
                        localCode.add(new Instruction(
                            Opcode.JMP_I32,
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(target)),
                            false, false
                        ));
                    } else {
                        LOG.severe("Cannot break when outside of while, until, or for statement");
                        errorsEncountered = true;
                    }
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_CONTINUE:
                    if(inLoopContext) {
                        List<ASTNode> c = codeNode.getChildren();
                        String target;
                        
                        if(c.size() > 0) {
                            // named continue, jump to %lbl_<name>%next
                            target = "%lbl_" + c.get(0).getValue() + "%next";
                        } else {
                            // unnamed break, jump to current loop's next
                            target = functionName + ".next" + (loopLabelCounter - 1); 
                        }
                        
                        localCode.add(new Instruction(
                            Opcode.JMP_I32,
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(target)),
                            false, false
                        ));
                    } else {
                        LOG.severe("Cannot continue when outside of while, until, or for statement");
                        errorsEncountered = true;
                    }
                    break;
                
                case NstlgrammarParser.ID.VARIABLE_FUNCTION_CALL:
                    compileFunctionCall(codeNode, localCode, localLabelMap);
                    break;
                
                default:
                    LOG.severe("Unexpected node in function code: " + detailed(codeNode));
                    errorsEncountered = true;
            }
        }
    }
    
    /**
     * Compiles an IF construct
     * 
     * @param node
     * @param localCode
     */
    private void compileIfConstruct(ASTNode node, List<Component> localCode, Map<String, Integer> localLabelMap) {
        boolean wasConditionalContext = inConditionalContext;
        inConditionalContext = true;
        
        int endifLabelNumber = endifLabelCounter++;
        String endifLabel = functionName + ".endif" + endifLabelNumber,
               elseifLabelBase = functionName + ".elseif" + endifLabelNumber,
               elseLabel = functionName + ".else" + endifLabelNumber;
        
        LOG.finest("Compiling IF construct");
        
        // extract linear structure from recursive structure
        List<ASTNode> conditions = new ArrayList<>(),
                      bodies = new ArrayList<>();
        
        extractIfElse(node, conditions, bodies);
        
        boolean hasElse = bodies.size() != conditions.size();
        
        // if's and elseif's
        for(int i = 0; i < conditions.size(); i++) {
            // label elseifs
            if(i != 0) {
                localLabelMap.put(elseifLabelBase + "_" + (i - 1), localCode.size());
            }
            
            String branchTarget;
            
            // is the branch target elseif, else, or end
            if(i == conditions.size() - 1) {
                // last condition, else or end
                if(hasElse) {
                    // else
                    branchTarget = elseLabel;
                } else {
                    // no else
                    branchTarget = endifLabel;
                }
            } else {
                // more conditions left, elseif
                branchTarget = elseifLabelBase + "_" + i;
            }
            
            // compile
            compileConditionalBranch(conditions.get(i), branchTarget, false, localCode, localLabelMap);
            
            contextStack.pushContext();
            compileFunctionCode(bodies.get(i).getChildren(), localCode, localLabelMap);
            contextStack.popContext();
            
            if(!lastWasReturn && (i != conditions.size() - 1 || hasElse)) {
                localCode.add(new Instruction(
                    Opcode.JMP_I32,
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(endifLabel)),
                    false, false
                ));
            }
        }
        
        // else
        if(hasElse) {
            localLabelMap.put(elseLabel, localCode.size());
            
            contextStack.pushContext();
            compileFunctionCode(bodies.get(bodies.size() - 1).getChildren(), localCode, localLabelMap);
            contextStack.popContext();
        }
        
        localLabelMap.put(endifLabel, localCode.size());
        
        inConditionalContext = wasConditionalContext;
    }
    
    /**
     * Converts the if-elseif-else recursive structure to something convenient and linear
     * 
     * @param node
     * @param conditions
     * @param bodies
     */
    private void extractIfElse(ASTNode node, List<ASTNode> conditions, List<ASTNode> bodies) {
        List<ASTNode> children = node.getChildren();
        
        // elseif = condition body tail
        // else = body
        // end = none
        if(children.size() == 3) {
            // elseif
            conditions.add(children.get(0));
            bodies.add(children.get(1));
            extractIfElse(children.get(2), conditions, bodies);
        } else if(children.size() == 1) {
            // else
            bodies.add(children.get(0));
        }
    }
    
    /**
     * Compiles a WHILE or UNTIL construct
     * 
     * @param node
     * @param localCode
     */
    private void compileWhileConstruct(ASTNode node, boolean until, List<Component> localCode, Map<String, Integer> localLabelMap) {
        boolean wasConditionalContext = inConditionalContext,
                wasLoopContext = inLoopContext;
        inConditionalContext = true;
        inLoopContext = true;
        
        int loopLabelNumber = loopLabelCounter++;
        String loopLabel = functionName + ".loop" + loopLabelNumber,        // start of the conditional
               nextLabel = functionName + ".next" + loopLabelNumber,        // start of the conditional, for continues
               startCodeLabel = functionName + ".start" + loopLabelNumber,  // start of body code
               endloopLabel = functionName + ".endloop" + loopLabelNumber,  // end of body code
               userLabelNext = "",
               userLabelEnd = "";
        
        LOG.finest(until ? "Compiling UNTIL construct" : "Compiling WHILE construct");
        contextStack.pushContext();
        
        List<ASTNode> children = node.getChildren();
        
        // jump to after conditional for until
        if(until) {
            localCode.add(new Instruction(
                Opcode.JMP_I32,
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(startCodeLabel)),
                false, false
            ));
        }
        
        // handle labeled loops
        if(children.get(0).getChildren().size() != 0) {
            String userLabel = children.get(0).getChildren().get(0).getChildren().get(0).getValue(); // lol
            
            userLabelNext = "%lbl_" + userLabel + "%next";
            userLabelEnd = "%lbl_" + userLabel + "%end";
            
            checkNameConflicts(userLabelNext);
            checkNameConflicts(userLabelEnd);
            
            contextStack.pushSymbol(new ContextSymbol(userLabelNext, new TypedRaw(new ResolvableConstant(0), RawType.NONE)));
            contextStack.pushSymbol(new ContextSymbol(userLabelEnd, new TypedRaw(new ResolvableConstant(0), RawType.NONE)));
        }
        
        if(!userLabelEnd.equals("")) localLabelMap.put(userLabelNext, localCode.size());
        localLabelMap.put(loopLabel, localCode.size());
        localLabelMap.put(nextLabel, localCode.size());
        
        // handle conditional
        compileConditionalBranch(children.get(1), endloopLabel, until, localCode, localLabelMap);
        
        localLabelMap.put(startCodeLabel, localCode.size());
        
        // compile code
        compileFunctionCode(children.get(2).getChildren(), localCode, localLabelMap);
        
        // jump back to conditional
        localCode.add(new Instruction(
            Opcode.JMP_I32,
            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(loopLabel)),
            false, false
        ));
        
        if(!userLabelEnd.equals("")) localLabelMap.put(userLabelEnd, localCode.size());
        localLabelMap.put(endloopLabel, localCode.size());
        
        contextStack.popContext();
        
        inConditionalContext = wasConditionalContext;
        inLoopContext = wasLoopContext;
    }
    
    /**
     * Compiles a FOR construct
     * 
     * @param node
     * @param localCode
     * @param localLabelMap
     */
    private void compileForConstruct(ASTNode node, List<Component> localCode, Map<String, Integer> localLabelMap) {
       boolean wasConditionalContext = inConditionalContext,
               wasLoopContext = inLoopContext;
       inConditionalContext = true;
       inLoopContext = true;
       
       int loopLabelNumber = loopLabelCounter++;
       String loopLabel = functionName + ".loop" + loopLabelNumber,         // start of the conditional
              startCodeLabel = functionName + ".start" + loopLabelNumber,   // start of body code
              nextLabel = functionName + ".next" + loopLabelNumber,         // start of iterator code
              endloopLabel = functionName + ".endloop" + loopLabelNumber,   // after iterator code
              userLabelNext = "",
              userLabelEnd = "";
       
       LOG.finest("Compiling FOR construct");
       contextStack.pushContext();
       
       List<ASTNode> children = node.getChildren(),
                     initChildren = children.get(1).getChildren(),
                     iterChildren = children.get(3).getChildren();
       
       // compile initializer
       String iteratorName = initChildren.get(0).getValue();
       NSTLType iteratorType = constructType(initChildren.get(1));
       ContextSymbol iteratorSymbol = allocateLocalVariable(iteratorName, iteratorType);
       
       if(!iterChildren.get(0).getValue().equals(iteratorName)) {
           LOG.severe(String.format("For loop iterator does not update its iterated variable. Expected %s, got %s.", iteratorName, iterChildren.get(0).getValue()));
           errorsEncountered = true;
       }
       
       compileValueComputation(initChildren.get(2), localCode, localLabelMap, iteratorName, iteratorType, false);
       
       // handle labeled loops
       if(children.get(0).getChildren().size() != 0) {
           String userLabel = children.get(0).getChildren().get(0).getChildren().get(0).getValue(); // lol
           
           userLabelNext = "%lbl_" + userLabel + "%next";
           userLabelEnd = "%lbl_" + userLabel + "%end";
           
           checkNameConflicts(userLabelNext);
           checkNameConflicts(userLabelEnd);
           
           contextStack.pushSymbol(new ContextSymbol(userLabelNext, new TypedRaw(new ResolvableConstant(0), RawType.NONE)));
           contextStack.pushSymbol(new ContextSymbol(userLabelEnd, new TypedRaw(new ResolvableConstant(0), RawType.NONE)));
       }
       
       localLabelMap.put(loopLabel, localCode.size());
       
       // compile conditional
       compileConditionalBranch(children.get(2), endloopLabel, false, localCode, localLabelMap);
       
       localLabelMap.put(startCodeLabel, localCode.size());
       
       // compile body code
       compileFunctionCode(children.get(4).getChildren(), localCode, localLabelMap);
       
       // compile iterator code
       if(!userLabelEnd.equals("")) localLabelMap.put(userLabelNext, localCode.size());
       localLabelMap.put(nextLabel, localCode.size());
       
       compileValueComputation(iterChildren.get(1), localCode, localLabelMap, iteratorName, iteratorType, false);
       
       localCode.add(new Instruction(
            Opcode.JMP_I32,
            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(loopLabel)),
            false, false
        ));
       
       if(!userLabelEnd.equals("")) localLabelMap.put(userLabelEnd, localCode.size());
       localLabelMap.put(endloopLabel, localCode.size());
       
       contextStack.popContext();
       
       inConditionalContext = wasConditionalContext;
       inLoopContext = wasLoopContext;
    }
    
    /**
     * Compiles a conditional branch. The given label will be jumped to if the condition is false
     * 
     * @param node
     * @param falseLabel
     * @param localCode
     */
    private void compileConditionalBranch(ASTNode node, String falseLabel, boolean invert, List<Component> localCode, Map<String, Integer> localLabelMap) {
        LOG.finest("Compiling conditional branch to " + falseLabel + " with condition " + detailed(node));
        
        if(isConstant(node)) {
            // supported for stuff like while true
            TypedValue tv = computeConstant(node);
            
            if(tv instanceof TypedRaw tr) {
                if(tr.getValue().isResolved()) {
                    if((tr.getValue().value() == 0) ^ invert) {
                        // skip
                        localCode.add(new Instruction(
                            Opcode.JMP_I32,
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(falseLabel)),
                            false, false
                        ));
                    } else {
                        // don't skip (do nothing)
                    }
                } else {
                    // value isn't resolved, figure out at runtime
                    // we can't use immediates as destinations so load to accumulator
                    NSTLType t = compileValueComputation(node, localCode, localLabelMap, "%accumulator", RawType.BOOLEAN, true);
                    if(t.getSize() == 0) t = RawType.BOOLEAN; // if nothing was inferred just boolean
                    
                    generateCompareAccumulatorZero(localCode, t.getSize());
                    
                    localCode.add(new Instruction(
                        invert ? Opcode.JNZ_RIM : Opcode.JZ_RIM,
                        new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(falseLabel)),
                        false, false
                    ));
                }
            } else {
                // invalid
                LOG.severe("Expected integer for constant conditional, got " + tv);
            }
        } else {
            switch(node.getSymbol().getID()) {
                // If the value is a named reference, compare it directly to zero
                case NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE:
                    String s = node.getChildren().get(0).getValue();
                    if(contextStack.hasSymbol(s)) {
                        ContextSymbol cs = contextStack.getSymbol(s);
                        
                        // 4 byte = via accumulator
                        if(cs.getType().getSize() == 4) {
                            generateVariableMove(s, "%accumulator", 4, localCode);
                            generateCompareAccumulatorZero(localCode, 4);
                        } else if(cs.getType().getSize() <= 2) {
                            // direct
                            localCode.add(new Instruction(
                                Opcode.CMP_RIM_0,
                                cs.getVariableDescriptor(),
                                true, false
                            ));
                        } else {
                            // long
                            generateIrregularCompareZero(localCode, cs);
                        }
                        
                        localCode.add(new Instruction(
                            invert ? Opcode.JNZ_RIM : Opcode.JZ_RIM,
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(falseLabel)),
                            false, false
                        ));
                    } else {
                        LOG.severe("Symbol " + s + " as conditional is undefined or not a variable");
                        errorsEncountered = true;
                    }
                    break;
                
                // Comparisons
                case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL:
                case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL:
                case NstlgrammarLexer.ID.TERMINAL_OP_GREATER:
                case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL:
                case NstlgrammarLexer.ID.TERMINAL_OP_LESS:
                case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL:
                    List<ASTNode> children = node.getChildren();
                    ASTNode leftNode = children.get(0),
                            rightNode = children.get(1);
                    
                    boolean leftConstant = isConstant(leftNode),
                            leftDirect = false,
                            rightConstant = isConstant(rightNode),
                            rightDirect = false;
                    
                    ContextSymbol leftSymbol = null,
                                  rightSymbol = null;
                    
                    // get direct stuff if available
                    if(!leftConstant && leftNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE) {
                        String name = leftNode.getChildren().get(0).getValue();
                        leftDirect = true;
                        
                        if(contextStack.hasSymbol(name)) {
                            leftSymbol = contextStack.getSymbol(name);
                        } else {
                            LOG.severe("Unknown symbol " + name + " as left comparison argument");
                            errorsEncountered = true;
                        }
                    }
                    
                    if(!rightConstant && rightNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE) {
                        String name = rightNode.getChildren().get(0).getValue();
                        rightDirect = true;
                        
                        if(contextStack.hasSymbol(name)) {
                            rightSymbol = contextStack.getSymbol(name);
                        } else {
                            LOG.severe("Unknown symbol " + name + " as right comparison argument");
                            errorsEncountered = true;
                        }
                    }
                    
                    if(leftConstant || rightConstant) {
                        // If the value is a comparison to a constant, order matters
                        TypedValue tv;
                        NSTLType vType;
                        ResolvableLocationDescriptor rld;
                        boolean direct;
                        
                        if(leftConstant) {
                            tv = computeConstant(leftNode);
                            direct = rightDirect && rightSymbol.getType().getSize() != 4 && (rightSymbol.getVariableDescriptor().getType() == LocationType.REGISTER || rightSymbol.getType().getSize() == 1);
                            vType = direct ? rightSymbol.getType() : compileValueComputation(rightNode, localCode, localLabelMap, "%accumulator", RawType.NONE, true);
                            rld = direct ? rightSymbol.getVariableDescriptor() : null;
                        } else {
                            tv = computeConstant(rightNode);
                            direct = leftDirect && leftSymbol.getType().getSize() != 4 && (leftSymbol.getVariableDescriptor().getType() == LocationType.REGISTER || leftSymbol.getType().getSize() == 1);
                            vType = direct ? leftSymbol.getType() : compileValueComputation(leftNode, localCode, localLabelMap, "%accumulator", RawType.NONE, true);
                            rld = direct ? leftSymbol.getVariableDescriptor() : null;
                        }
                        
                        // if the value is a pointer, make it raw
                        if(vType instanceof PointerType pt) {
                            vType = RawType.PTR;
                        }
                        
                        // only raw types allowed for comparisons
                        if(vType instanceof RawType rt && tv.convertType(vType)) {
                            boolean signed = rt.isSigned();
                            
                            // transform operator cause these don't commute
                            // also handle invert
                            int transformedOperator = switch(node.getSymbol().getID()) {
                                case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL          -> invert ? NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL : NstlgrammarLexer.ID.TERMINAL_OP_EQUAL;
                                case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL      -> invert ? NstlgrammarLexer.ID.TERMINAL_OP_EQUAL : NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL;
                                case NstlgrammarLexer.ID.TERMINAL_OP_GREATER        -> (leftConstant ^ invert) ? NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL : NstlgrammarLexer.ID.TERMINAL_OP_GREATER;
                                case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL  -> (leftConstant ^ invert) ? NstlgrammarLexer.ID.TERMINAL_OP_LESS : NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL;
                                case NstlgrammarLexer.ID.TERMINAL_OP_LESS           -> (leftConstant ^ invert) ? NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL : NstlgrammarLexer.ID.TERMINAL_OP_LESS;
                                case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL     -> (leftConstant ^ invert) ? NstlgrammarLexer.ID.TERMINAL_OP_GREATER : NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL;
                                default                                             -> 0;
                            };
                            
                            if(vType.getSize() == 1) {
                                Opcode jmpOp = switch(transformedOperator) {
                                    case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL          -> Opcode.JNZ_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL      -> Opcode.JZ_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER        -> signed ? Opcode.JLE_RIM : Opcode.JBE_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL  -> signed ? Opcode.JL_RIM : Opcode.JC_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS           -> signed ? Opcode.JGE_RIM : Opcode.JNC_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL     -> signed ? Opcode.JG_RIM : Opcode.JA_RIM;
                                    default                                             -> Opcode.NOP;
                                };
                                
                                localCode.add(new Instruction(
                                    Opcode.CMP_RIM_I8,
                                    direct ? rld : new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 1, new ResolvableConstant(tv.toLong())),
                                    true
                                ));
                                
                                localCode.add(new Instruction(
                                    jmpOp,
                                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(falseLabel)),
                                    false, false
                                ));
                            } else if(vType.getSize() == 2) {
                                Opcode jmpOp = switch(transformedOperator) {
                                    case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL          -> Opcode.JNZ_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL      -> Opcode.JZ_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER        -> signed ? Opcode.JLE_RIM : Opcode.JBE_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL  -> signed ? Opcode.JL_RIM : Opcode.JC_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS           -> signed ? Opcode.JGE_RIM : Opcode.JNC_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL     -> signed ? Opcode.JG_RIM : Opcode.JA_RIM;
                                    default                                             -> Opcode.NOP;
                                };
                                
                                localCode.add(new Instruction(
                                    Opcode.CMP_RIM,
                                    direct ? rld : new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(tv.toLong())),
                                    false
                                ));
                                
                                localCode.add(new Instruction(
                                    jmpOp,
                                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(falseLabel)),
                                    false, false
                                ));
                            } else {
                                // we need multiple comparisons
                                // equals                   compare jne(false) compare jne(false)
                                // not equal                compare jne(true) compare je(false)
                                // signed greater           compare(upper) jl(false) jg(true) compare(lower) jbe(false)
                                // signed greater equal     compare(upper) jl(false) jg(true) compare(lower) jb(false)
                                // signed less              compare(upper) jg(false) jl(true) compare(lower) jae(false)
                                // signed less equal        compare(upper) jg(false) jl(true) compare(lower) ja(false)
                                // unsigned greater         compare(upper) jb(false) ja(true) compare(lower) jbe(false)
                                // unsigned greater equal   compare(upper) jb(false) ja(true) compare(lower) jb(false)
                                // unsigned less            compare(upper) ja(false) jb(true) compare(lower) jae(false)
                                // unsigned less equal      compare(upper) ja(false) jb(true) compare(lower) ja(false)
                                
                                Opcode firstJOp = switch(transformedOperator) {
                                    case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL          -> Opcode.JNZ_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL      -> null;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER        -> signed ? Opcode.JL_RIM : Opcode.JC_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL  -> signed ? Opcode.JL_RIM : Opcode.JC_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS           -> signed ? Opcode.JG_RIM : Opcode.JA_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL     -> signed ? Opcode.JG_RIM : Opcode.JA_RIM;
                                    default                                             -> Opcode.NOP;
                                };
                                
                                Opcode secondJOp = switch(transformedOperator) {
                                    case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL          -> null;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL      -> Opcode.JNZ_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER        -> signed ? Opcode.JG_RIM : Opcode.JA_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL  -> signed ? Opcode.JG_RIM : Opcode.JA_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS           -> signed ? Opcode.JL_RIM : Opcode.JC_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL     -> signed ? Opcode.JL_RIM : Opcode.JC_RIM;
                                    default                                             -> Opcode.NOP;
                                };
                                
                                Opcode thirdJOp = switch(transformedOperator) {
                                    case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL          -> Opcode.JNZ_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL      -> Opcode.JZ_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER        -> Opcode.JBE_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL  -> Opcode.JC_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS           -> Opcode.JNC_RIM;
                                    case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL     -> Opcode.JA_RIM;
                                    default                                             -> Opcode.NOP;
                                };
                                
                                String trueLabel = functionName + ".cmpt" + longComparisonCounter++;
                                
                                // compare upper
                                localCode.add(new Instruction(
                                    Opcode.CMP_RIM,
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.D),
                                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(tv.toLong() >> 16)),
                                    false
                                ));
                                
                                if(firstJOp != null) {
                                    localCode.add(new Instruction(
                                        firstJOp,
                                        new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(falseLabel)),
                                        false, false
                                    ));
                                }
                                
                                if(secondJOp != null) {
                                    localCode.add(new Instruction(
                                        secondJOp,
                                        new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(trueLabel)),
                                        false, false
                                    ));
                                }
                                
                                // compare lower
                                localCode.add(new Instruction(
                                    Opcode.CMP_RIM,
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(tv.toLong() & 0xFFFF)),
                                    false
                                ));
                                
                                localCode.add(new Instruction(
                                    thirdJOp,
                                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(falseLabel)),
                                    false, false
                                ));
                                
                                localLabelMap.put(trueLabel, localCode.size());
                            }
                        } else {
                            LOG.severe("Cannot compare non-integer types");
                            errorsEncountered = true;
                        }
                    } else {
                        // If both sides are direct references and at least one is a register, we can compare directly
                        // If one side is a direct reference, we can compute the other to %accumulator and compare
                        // If neither side is a direct reference, we compute the left to %accumulator and the right to %tmp, then compare
                        int comparisonSize = -1,
                            nodeOp = node.getSymbol().getID();
                        ResolvableLocationDescriptor leftSideDescriptor = null,
                                                     rightSideDescriptor = null;
                        boolean signed = true,
                                usesTmp = false;
                        
                        if(leftNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE || rightNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE) {
                            String refName;
                            ASTNode accNode;
                            boolean left;
                            
                            // at least one side is a direct reference
                            if(leftNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE) {
                                // left is direct
                                refName = leftNode.getChildren().get(0).getValue();
                                accNode = rightNode;
                                left = true;
                            } else {
                                // right is direct
                                refName = rightNode.getChildren().get(0).getValue();
                                accNode = leftNode;
                                left = false;
                            }
                            
                            if(contextStack.hasSymbol(refName)) {
                                ContextSymbol cs = contextStack.getSymbol(refName);
                                NSTLType accType = compileValueComputation(accNode, localCode, localLabelMap, "%accumulator", cs.getType(), true);
                                
                                if(!accType.equals(cs.getType())) { 
                                    LOG.severe("Comparison types do not match");
                                    errorsEncountered = true;
                                }
                                
                                comparisonSize = cs.getType().getSize();
                                
                                if(left) {
                                    leftSideDescriptor = cs.getVariableDescriptor();
                                    rightSideDescriptor = switch(comparisonSize) {
                                        case 1  -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL);
                                        case 2  -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A);
                                        case 4  -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA);
                                        default -> contextStack.getSymbol("%accumulator").getVariableDescriptor();
                                    };
                                } else {
                                    leftSideDescriptor = switch(comparisonSize) {
                                        case 1  -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL);
                                        case 2  -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A);
                                        case 4  -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA);
                                        default -> contextStack.getSymbol("%accumulator").getVariableDescriptor();
                                    };
                                    rightSideDescriptor = cs.getVariableDescriptor();
                                }
                            } else {
                                LOG.severe("Unknown reference " + refName + " in comparison");
                                errorsEncountered = true;
                            }
                        } else {
                            // neither side is a direct reference
                            // compute right side to accumulator to acquire type
                            NSTLType rightType = compileValueComputation(rightNode, localCode, localLabelMap, "%accumulator", RawType.NONE, true);
                            
                            // save right side
                            contextStack.pushContext();
                            usesTmp = true;
                            
                            ContextSymbol tmpSymbol = allocateLocalVariable("%tmp", rightType);
                            generateVariableMove("%accumulator", "%tmp", rightType.getSize(), localCode);
                            
                            rightSideDescriptor = tmpSymbol.getVariableDescriptor();
                            
                            // compute left side
                            NSTLType leftType = compileValueComputation(leftNode, localCode, localLabelMap, "%accumulator", RawType.NONE, true);
                            
                            // ensure things are comparable
                            if((nodeOp == NstlgrammarLexer.ID.TERMINAL_OP_EQUAL || nodeOp == NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL ||
                               (leftType instanceof RawType && rightType instanceof RawType)) &&
                               (leftType.getSize() == rightType.getSize())) {
                                comparisonSize = leftType.getSize();
                                
                                leftSideDescriptor = switch(leftType.getSize()) {
                                    case 1  -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL);
                                    case 2  -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A);
                                    case 4  -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA);
                                    default -> contextStack.getSymbol("%accumulator").getVariableDescriptor();
                                };
                            } else {
                                LOG.severe("Comparison arguments are of mismatched size or not comparable");
                                errorsEncountered = true;
                            }
                        }
                        
                        Opcode jmpOp = switch(nodeOp) {
                            case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL          -> invert ? Opcode.JZ_RIM : Opcode.JNZ_RIM;
                            case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL      -> invert ? Opcode.JNZ_RIM : Opcode.JZ_RIM;
                            case NstlgrammarLexer.ID.TERMINAL_OP_GREATER        -> signed ? (invert ? Opcode.JG_RIM : Opcode.JLE_RIM) : (invert ? Opcode.JA_RIM : Opcode.JBE_RIM);
                            case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL  -> signed ? (invert ? Opcode.JGE_RIM : Opcode.JL_RIM) : (invert ? Opcode.JNC_RIM : Opcode.JC_RIM);
                            case NstlgrammarLexer.ID.TERMINAL_OP_LESS           -> signed ? (invert ? Opcode.JL_RIM : Opcode.JGE_RIM) : (invert ? Opcode.JC_RIM : Opcode.JNC_RIM);
                            case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL     -> signed ? (invert ? Opcode.JLE_RIM : Opcode.JG_RIM) : (invert ? Opcode.JBE_RIM : Opcode.JA_RIM);
                            default                                             -> Opcode.NOP;
                        };
                        
                        if(comparisonSize == 1 || comparisonSize == 2) {
                            // compare
                            localCode.add(new Instruction(
                                Opcode.CMP_RIM,
                                leftSideDescriptor,
                                rightSideDescriptor,
                                true
                            ));
                            
                            // jump
                            localCode.add(new Instruction(
                                jmpOp,
                                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(falseLabel)),
                                false, false
                            ));
                        } else {
                            // dword variable comparisons
                            // computes boolean to AL
                            generateGenericOperation(leftSideDescriptor, rightSideDescriptor, jmpOp, true, localCode, localLabelMap);
                            
                            ResolvableLocationDescriptor leftSideLower = this.splitRLD(leftSideDescriptor).b;
                            
                            localCode.add(new Instruction(
                                Opcode.CMP_RIM_0,
                                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                                true, true
                            ));
                            
                            localCode.add(new Instruction(
                                Opcode.JNZ_RIM,
                                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(falseLabel)),
                                false, false
                            ));
                            
                            // consume accumulator temporary
                            //contextStack.popContext();
                            //contextCounter--;
                        }
                        
                        if(usesTmp) {
                            contextStack.popContext();
                        }
                    }
                    break;
                    
                default:
                    // If the value is a different expression, evaluate it and compare to zero
                    NSTLType t = compileValueComputation(node, localCode, localLabelMap, "%accumulator", RawType.NONE, true);
                    generateCompareAccumulatorZero(localCode, t.getSize());
                    
                    localCode.add(new Instruction(
                        invert ? Opcode.JNZ_RIM : Opcode.JZ_RIM,
                        new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(falseLabel)),
                        false, false
                    ));
            }
        }
    }
    
    /**
     * Compares the accumulator to zero
     * 
     * @param localCode
     * @param size
     */
    private void generateCompareAccumulatorZero(List<Component> localCode, int size) {
        LOG.finest("Comparing accumulator to zero");
        
        if(size == 1) {
            localCode.add(new Instruction(
                Opcode.CMP_RIM_0,
                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                true, true
            ));
        } else if(size == 2) {
            localCode.add(new Instruction(
                Opcode.CMP_RIM_0,
                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                true, true
            ));
        } else if(size == 4) {
            // compare A, save the flags there, compare D, AND the flags
            localCode.add(new Instruction(
                Opcode.CMP_RIM_0,
                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                true, true
            ));
            
            localCode.add(new Instruction(
                Opcode.MOV_RIM_F,
                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                true, true
            ));
            
            localCode.add(new Instruction(
                Opcode.CMP_RIM_0,
                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.D),
                true, true
            ));
            
            localCode.add(new Instruction(
                Opcode.AND_F_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                false, true
            ));
        } else {
            // if we're here there should be an accumulator symbol for it to use
            if(contextStack.hasLocalSymbol("%accumulator")) {
                generateIrregularCompareZero(localCode, contextStack.getSymbol("%accumulator"));
                
                // this consumes the irregular accumulator
                contextStack.popContext();
            } else {
                LOG.severe("No symbol for irregular accumulator");
                errorsEncountered = true;
            }
        }
    }
    
    /**
     * Compares an irregularly sized value to zero
     * 
     * @param localCode
     * @param symbol
     */
    private void generateIrregularCompareZero(List<Component> localCode, ContextSymbol symbol) {
        // TODO
        LOG.severe("UNIMPLEMENTED: IRREGULAR COMPARE ZERO");
        errorsEncountered = true;
    }
    
    /**
     * Compiles a variable assignment
     * 
     * @param target
     * @param value
     */
    private void compileAssignment(ASTNode target, ASTNode value, List<Component> localCode, Map<String, Integer> localLabelMap) {
        LOG.finest(() -> "Compiling assignment " + detailed(value) + " to " + detailed(target));
        
        // If the reference is just a name, we can write the computed value directly whether via accumulator or not
        // If the reference is complex, we'll need a register pair for the pointer. JI used by tradition.
        // If JI is unused, we don't need to do anything special and can just target it for the pointer computation.
        // To do this, push a context and assign the %pointer symbol to JI. Pop the context when done.
        // If JI is used, it needs to be saved before use
        // To do this, record the references to JI, push a context, allocate new locations for each, and move them there.
        // When done, move the values back and pop the context.
        
        if(target.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_REFERENCE) {
            List<ASTNode> tChildren = target.getChildren();
            
            // this is either AT subreference, type AT subreference, or (invalid) TO subreference
            // i.e. this is a pointer dereference
            // Compute the target pointer. If a type is not given, infer via that computation.
            // Then, compute the value and store it.
            
            if(tChildren.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_TO) {
                // invalid
                LOG.severe("Cannot assign value to TO statement");
                errorsEncountered = true;
            } else {
                boolean inferType = tChildren.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_AT;
                
                generateEvictJI(localCode);
                
                // get pointer
                NSTLType expectedTypePointer = compilePointerComputation(target, localCode, localLabelMap, "%ji"),
                         expectedType;
                
                if(expectedTypePointer instanceof PointerType pt) {
                    expectedType = pt.getPointedType();
                } else {
                    expectedType = RawType.NONE;
                    LOG.severe("Expected pointer for AT assignment target, got " + expectedTypePointer);
                    errorsEncountered = true;
                }
                
                inferType = inferType && (expectedType == RawType.NONE);
                
                // get value to accumulator
                NSTLType actualType = compileValueComputation(value, localCode, localLabelMap, "%accumulator", inferType ? RawType.NONE : expectedType, true);
                
                if(!inferType && !(actualType.equals(expectedType) || (expectedType instanceof RawType && actualType == RawType.NONE))) {
                    LOG.severe("Mismatched types: expected " + expectedType + " but found " + actualType);
                    errorsEncountered = true;
                }
                
                NSTLType t = inferType ? actualType : expectedType;
                
                // move
                if(t.getSize() <= 4 && !(t instanceof StructureType st)) {
                    generateOffsetPointerStore(0, t.getSize(), localCode);
                } else {
                    generateVariableMove("%accumulator" + contextStack.getContextCounter(), "%jipointer", t.getSize(), localCode);
                    contextStack.popContext();
                }
                
                generateRestoreJI(localCode);
            }
        } else if(target.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_SUBREFERENCE) {
            List<ASTNode> tChildren = target.getChildren();
            
            // subreference DOT name -> compute pointer and use offset
            // name DOT name -> just use offset
            // subreference INDEX variable_expression -> compute pointer but like twice
            // name INDEX variable_expression -> compute pointer
            // (INVALID) function_call (done)
            // name -> write directly (done)
            if(tChildren.size() == 1) {
                if(tChildren.get(0).getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_FUNCTION_CALL) {
                    // invalid
                    LOG.severe("Cannot assign value to function result");
                    errorsEncountered = true;
                } else {
                    // name
                    String targetName = tChildren.get(0).getValue();
                    
                    if(contextStack.hasSymbol(targetName)) {
                        // good to go, compile value computation with the given target
                        compileValueComputation(value, localCode, localLabelMap, targetName, contextStack.getSymbol(targetName).getType(), false);
                    } else {
                        // oh no
                        LOG.severe("Unknown symbol " + targetName + " as assignment target");
                        errorsEncountered = true;
                    }
                }
            } else {
                NSTLType targetType;
                
                // name DOT name doesn't need computation
                if(tChildren.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_NAME && tChildren.get(1).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_DOT) {
                    String targetName = tChildren.get(0).getValue();
                    
                    if(contextStack.hasSymbol(targetName)) {
                        ContextSymbol cs = contextStack.getSymbol(targetName);
                        
                        StructureType st = null;
                        boolean isStructPointer = false,
                                isStruct = false;
                        
                        // do we have an applicable type
                        if(!cs.getIsConstant()) {
                            if(cs.getType() instanceof StructureType st2) {
                                st = st2;
                                isStruct = true;
                            } else if(cs.getType() instanceof PointerType pt && pt.getPointedType() instanceof StructureType pst) {
                                st = pst;
                                isStructPointer = true;
                            }
                        }
                        
                        if(isStruct || isStructPointer) {
                            // member offset & type
                            String memberName = tChildren.get(2).getValue();
                            
                            if(st.getMemberNames().contains(memberName)) {
                                targetType = st.getMemberType(memberName);
                                Pair<ContextSymbol, Boolean> info = generateDirectMemberSymbol(cs, memberName, st, targetType, "%target", localCode, isStructPointer);
                                ContextSymbol tcs = info.a;
                                boolean evictedJI = info.b;
                                
                                contextStack.pushContext();
                                contextStack.pushSymbol(tcs);
                                
                                compileValueComputation(value, localCode, localLabelMap, "%target", targetType, false);
                                
                                contextStack.popContext();
                                
                                if(evictedJI) generateRestoreJI(localCode);
                            } else {
                                // member doesn't exist
                                LOG.severe("Structure type " + st + " for assignment to " + targetName + " does not have member " + memberName);
                                errorsEncountered = true;
                            }
                        } else {
                            // we don't
                            LOG.severe("Structure assignment target symbol " + targetName + " is not a structure or structure pointer");
                            errorsEncountered = true;
                        }
                    } else {
                        LOG.severe("Unknown symbol " + targetName + " as structure assignment target");
                        errorsEncountered = true;
                    }
                } else {
                    // Other stuff - compute pointer, compute value, store.
                    int structureOffset = 0;
                    
                    // evict JI for pointer
                    generateEvictJI(localCode);
                    
                    // compute pointer to JI
                    // subreference DOT name computes the subreference, others the full reference
                    if(tChildren.get(1).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_DOT) {
                        NSTLType subrefType = inferReferenceType(tChildren.get(0));
                        
                        if(subrefType instanceof PointerType pt) { 
                            // If the subreference is a structure pointer, compute the value
                            targetType = ((PointerType) compileValueComputation(tChildren.get(0), localCode, localLabelMap, "%ji", pt, true)).getPointedType();
                        } else {
                            // If the subreference isn't, compute the pointer
                            targetType = ((PointerType) compilePointerComputation(tChildren.get(0), localCode, localLabelMap, "%ji")).getPointedType();
                        }
                        
                        // we expect the name to be of a field in a structure type
                        if(targetType instanceof StructureType st) {
                            String mName = tChildren.get(2).getValue();
                            
                            if(!st.getMemberNames().contains(mName)) {
                                LOG.severe("Unknown member " + mName + " for structure type " + st);
                                errorsEncountered = true;
                            } else {
                                targetType = st.getMemberType(mName);
                                structureOffset = st.getMemberOffset(mName);
                                
                                // if the target type isn't convenient include the member offset in the pointer calculation
                                if(targetType.getSize() == 3 || targetType.getSize() > 4) {
                                    localCode.add(new Instruction(
                                        Opcode.LEA_RIM,
                                        new ResolvableLocationDescriptor(LocationType.REGISTER, Register.JI),
                                        new ResolvableLocationDescriptor(LocationType.MEMORY, 0, new ResolvableMemory(Register.JI, Register.NONE, 0, structureOffset)),
                                        false
                                    ));
                                }
                            }
                        } else {
                            LOG.severe("Expected structure type for structure member access, got " + targetType);
                            errorsEncountered = true;
                        }
                    } else {
                        targetType = ((PointerType) compilePointerComputation(target, localCode, localLabelMap, "%ji")).getPointedType();
                    }
                    
                    // if the target type is a structure, do in parts
                    if(targetType instanceof StructureType st) {
                        // allocate space for the structure
                        contextStack.pushContext();
                        
                        allocateLocalVariable("%tmp", targetType);
                        compileValueComputation(value, localCode, localLabelMap, "%tmp", targetType, false);
                        
                        generateVariableMove("%tmp", "%jipointer", targetType.getSize(), localCode);
                        
                        contextStack.popContext();
                    } else {
                        // get value to accumulator
                        compileValueComputation(value, localCode, localLabelMap, "%accumulator", targetType, false);
                        
                        // move the value
                        if(targetType.getSize() != 3 && targetType.getSize() <= 4) {
                            generateOffsetPointerStore(structureOffset, targetType.getSize(), localCode);
                        } else {
                            // except here where we already dealt with it
                            generateVariableMove("%accumulator", "%jipointer", targetType.getSize(), localCode);
                        }
                    }
                    
                    // restore JI
                    generateRestoreJI(localCode);
                }
            }
        } else {
            LOG.severe("Unexpected node as assignment target: " + detailed(target));
            errorsEncountered = true;
        }
    }
    
    /**
     * Generates a context symbol for a structure member, emitting code if necessary
     * 
     * @param structureSymbol
     * @param memberName
     * @param structureType
     * @param localCode
     * @return
     */
    private Pair<ContextSymbol, Boolean> generateDirectMemberSymbol(ContextSymbol structureSymbol, String memberName, StructureType structureType, NSTLType memberType, String symbolName, List<Component> localCode, boolean symbolIsPointer) {
        LOG.finest("Generating direct member symbol " + symbolName + " for member " + memberName + " of symbol " + structureSymbol);
        
        ContextSymbol cs = null;
        boolean evictedJI = false;
        
        int offset = structureType.getMemberOffset(memberName);
        
        ResolvableMemory targetMemory;
        
        // get and modify descriptor to access member
        // if the symbol is a pointer, move it to JI and return JI + member offset
        // otherwise, symbol will be memory
        if(symbolIsPointer) {
            generateEvictJI(localCode);
            evictedJI = true;
            
            this.generateVariableMove(structureSymbol.getName(), "%ptr", 4, localCode);
            
            targetMemory = new ResolvableMemory(Register.JI, Register.NONE, 0, offset);
        } else {
            // since only raws are allocated to registers, this should be a memory
            ResolvableMemory originalMemory = structureSymbol.getVariableDescriptor().getMemory();
            
            // if the symbol is on the stack (BP + x) we can add the offset to x to get our descriptor
            // if the symbol is a global, we don't know its location at compile time and need to use a pointer
            if(originalMemory.getBase() == Register.BP && !symbolIsPointer) {
                // local, direct
                targetMemory = new ResolvableMemory(Register.BP, Register.NONE, 0, (int)(originalMemory.getOffset().value() + offset));
            } else {
                // global, indirect
                generateEvictJI(localCode);
                evictedJI = true;
                
                localCode.add(new Instruction(
                    Opcode.MOVW_RIM,
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.JI),
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, originalMemory.getOffset().copy()),
                    false
                ));
                
                localCode.add(new Instruction(
                    Opcode.LEA_RIM,
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.JI),
                    new ResolvableLocationDescriptor(LocationType.MEMORY, 0, new ResolvableMemory(Register.JI, Register.NONE, 0, offset)),
                    true
                ));
                
                targetMemory = new ResolvableMemory(Register.JI, Register.NONE, 0, 0);
            }
        }
        
        // make symbol 
        cs = new ContextSymbol(symbolName, memberType, new ResolvableLocationDescriptor(LocationType.MEMORY, memberType.getSize(), targetMemory));
        
        return new Pair<>(cs, evictedJI);
    }
    
    /**
     * Moves a value from the accumulator to [JI + offset]
     * 
     * @param targetType
     * @param localCode
     */
    private void generateOffsetPointerStore(int offset, int size, List<Component> localCode) {
        // move with offset
        if(size == 1) {
            localCode.add(new Instruction(
                Opcode.MOV_RIM,
                new ResolvableLocationDescriptor(LocationType.MEMORY, 1, new ResolvableMemory(Register.JI, Register.NONE, 0, offset)),
                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                true
            ));
        } else if(size == 2) {
            localCode.add(new Instruction(
                Opcode.MOV_RIM,
                new ResolvableLocationDescriptor(LocationType.MEMORY, 2, new ResolvableMemory(Register.JI, Register.NONE, 0, offset)),
                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                true
            ));
        } else if(size == 4) {
            localCode.add(new Instruction(
                Opcode.MOVW_RIM,
                new ResolvableLocationDescriptor(LocationType.MEMORY, 4, new ResolvableMemory(Register.JI, Register.NONE, 0, offset)),
                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                true
            ));
        } else {
            LOG.severe("Cannot generate offset move to irregularly sized type");
            errorsEncountered = true;
        }
    }
    
    /**
     * Generates a move from one place to another.
     * If the source is %accumulator its symbol is present and local, that symbol will be used and its context popped.
     * A source of %accumulator with size 3 will use DL and A. This should only happen from 3 byte constants.
     * A source of %accumulator that isn't size 3 and lacking a symbol will cause an error.
     * If the source/destination is %jipointer or %accpointer, the pointer stored in JI or DA respectively will be used.
     * Otherwise, the symbol with the given name will be used. If the symbol is not in memory, an error will occur.
     * 
     * @param source
     * @param destination
     * @param localCode
     */
    private void generateVariableMove(String source, String destination, int size, List<Component> localCode) {
        LOG.finest("Generating variable move size " + size + " from " + source + " to " + destination);
        
        if(source.equals(destination)) return;
        
        ResolvableLocationDescriptor sourceDescriptor, destinationDescriptor, accumulatorDescriptor, accptrDescriptor, jiptrDescriptor;
        
        accumulatorDescriptor = new ResolvableLocationDescriptor(LocationType.REGISTER, switch(size) {
            case 1  -> Register.AL;
            case 2  -> Register.A;
            default -> Register.DA;
        });
        
        accptrDescriptor = new ResolvableLocationDescriptor(LocationType.MEMORY, size, new ResolvableMemory(Register.DA, Register.NONE, 0, 0));
        jiptrDescriptor = new ResolvableLocationDescriptor(LocationType.MEMORY, size, new ResolvableMemory(Register.JI, Register.NONE, 0, 0));
        
        if(contextStack.hasSymbol(source)) {
            sourceDescriptor = contextStack.getSymbol(source).getVariableDescriptor();
        } else if(source.equals("%accumulator")) {
            // accumulator
            sourceDescriptor = accumulatorDescriptor;
        } else if(source.equals("%accpointer")) {
            sourceDescriptor = accptrDescriptor;
        } else if(source.equals("%jipointer")) {
            sourceDescriptor = jiptrDescriptor;
        } else {
            sourceDescriptor = accumulatorDescriptor;
            LOG.severe("Unknown source symbol in variable move: " + source);
            errorsEncountered = true;
        }
        
        if(contextStack.hasSymbol(destination)) {
            destinationDescriptor = contextStack.getSymbol(destination).getVariableDescriptor();
        } else if(destination.equals("%accumulator") || destination.equals("%stack")) {
            // accumulator
            destinationDescriptor = accumulatorDescriptor;
        } else if(destination.equals("%accpointer")) {
            destinationDescriptor = accptrDescriptor;
        } else if(destination.equals("%jipointer")) {
            destinationDescriptor = jiptrDescriptor;
        } else {
            destinationDescriptor = accumulatorDescriptor;
            LOG.severe("Unknown destination symbol in variable move: " + destination);
            errorsEncountered = true;
        }
        
        if(size != 3 && size <= 4) {
            // stack stuff
            if(destination.equals("%stack")) {
                if(size == 4) {
                    // if the size is 4 we may need to go via accumulator
                    if(sourceDescriptor.getType() == LocationType.REGISTER) {
                        // direct
                        switch(sourceDescriptor.getRegister()) {
                            case DA:
                                localCode.add(new Instruction(Opcode.PUSH_D, true));
                                localCode.add(new Instruction(Opcode.PUSH_A, true));
                                break;
                            
                            case BC:
                                localCode.add(new Instruction(Opcode.PUSH_B, true));
                                localCode.add(new Instruction(Opcode.PUSH_C, true));
                                break;
                            
                            case JI:
                                localCode.add(new Instruction(Opcode.PUSH_J, true));
                                localCode.add(new Instruction(Opcode.PUSH_I, true));
                                break;
                                
                            case LK:
                                localCode.add(new Instruction(Opcode.PUSH_L, true));
                                localCode.add(new Instruction(Opcode.PUSH_K, true));
                                break;
                            
                            default:
                                // not possible
                                LOG.severe("Impossible case reached for push register pair");
                                errorsEncountered = true;
                        }
                    } else {
                        // accumulator
                        localCode.add(new Instruction(
                            Opcode.MOVW_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                            sourceDescriptor,
                            true
                        ));
                        
                        localCode.add(new Instruction(Opcode.PUSH_D, true));
                        localCode.add(new Instruction(Opcode.PUSH_A, true));
                    }
                } else {
                    // if the size is 1 or 2 we can always go directly
                    localCode.add(new Instruction(
                        Opcode.PUSH_RIM,
                        sourceDescriptor,
                        false, true
                    ));
                }
            } else {
                // normal values
                // if it isn't memory-memory, we can do a single move
                if(sourceDescriptor.getType() != LocationType.MEMORY || destinationDescriptor.getType() != LocationType.MEMORY) {
                    if(size == 4) {
                        localCode.add(new Instruction(
                            Opcode.MOVW_RIM,
                            destinationDescriptor,
                            sourceDescriptor,
                            true
                        ));
                    } else {
                        localCode.add(new Instruction(
                            Opcode.MOV_RIM,
                            destinationDescriptor,
                            sourceDescriptor,
                            true
                        ));
                    }
                } else {
                    // move via accumulator
                    if(destination.equals("accptrDescriptor")) {
                        // precaution, all stores should use JI
                        LOG.severe("Cannot move via accumulator to pointer stored in accumulator");
                        errorsEncountered = true;
                    }
                    
                    if(size == 4) {
                        localCode.add(new Instruction(
                            Opcode.MOVW_RIM,
                            accumulatorDescriptor,
                            sourceDescriptor,
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.MOVW_RIM,
                            destinationDescriptor,
                            accumulatorDescriptor,
                            true
                        ));
                    } else {
                        localCode.add(new Instruction(
                            Opcode.MOV_RIM,
                            accumulatorDescriptor,
                            sourceDescriptor,
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.MOV_RIM,
                            destinationDescriptor,
                            accumulatorDescriptor,
                            true
                        ));
                    }
                }
            }
        } else {
            // long/weird values
            if(sourceDescriptor.getType() != LocationType.MEMORY || destinationDescriptor.getType() != LocationType.MEMORY) {
                // this shouldn't happen
                LOG.severe("Cannot move irregular sized value between registers: " + size + " bytes from " + sourceDescriptor + " to " + destinationDescriptor);
                errorsEncountered = true;
            } else {
                // break down into move via accumulator
                int i = 0;
                ResolvableMemory sourceMem = sourceDescriptor.getMemory(),
                                 destMem = destinationDescriptor.getMemory();
                
                // 4s
                while(i + 4 <= size) {
                    localCode.add(new Instruction(
                        Opcode.MOVW_RIM,
                        new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                        new ResolvableLocationDescriptor(LocationType.MEMORY, 4, new ResolvableMemory(sourceMem.getBase(), sourceMem.getIndex(), sourceMem.getScale(), (int)(sourceMem.getOffset().value() + i))),
                        true
                    ));
                    
                    localCode.add(new Instruction(
                        Opcode.MOVW_RIM,
                        new ResolvableLocationDescriptor(LocationType.MEMORY, 4, new ResolvableMemory(destMem.getBase(), destMem.getIndex(), destMem.getScale(), (int)(destMem.getOffset().value() + i))),
                        new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                        true
                    ));
                    
                    i += 4;
                }
                
                // final 1-3 bytes
                switch(i - size) {
                    case 1:
                        localCode.add(new Instruction(
                            Opcode.MOV_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                            new ResolvableLocationDescriptor(LocationType.MEMORY, 4, new ResolvableMemory(sourceMem.getBase(), sourceMem.getIndex(), sourceMem.getScale(), (int)(sourceMem.getOffset().value() + i))),
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.MOV_RIM,
                            new ResolvableLocationDescriptor(LocationType.MEMORY, 4, new ResolvableMemory(destMem.getBase(), destMem.getIndex(), destMem.getScale(), (int)(destMem.getOffset().value() + i))),
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                            true
                        ));
                        break;
                        
                    case 2:
                        localCode.add(new Instruction(
                            Opcode.MOV_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                            new ResolvableLocationDescriptor(LocationType.MEMORY, 4, new ResolvableMemory(sourceMem.getBase(), sourceMem.getIndex(), sourceMem.getScale(), (int)(sourceMem.getOffset().value() + i))),
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.MOV_RIM,
                            new ResolvableLocationDescriptor(LocationType.MEMORY, 4, new ResolvableMemory(destMem.getBase(), destMem.getIndex(), destMem.getScale(), (int)(destMem.getOffset().value() + i))),
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                            true
                        ));
                        break;
                        
                    case 3:
                        localCode.add(new Instruction(
                            Opcode.MOV_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                            new ResolvableLocationDescriptor(LocationType.MEMORY, 4, new ResolvableMemory(sourceMem.getBase(), sourceMem.getIndex(), sourceMem.getScale(), (int)(sourceMem.getOffset().value() + i))),
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.MOV_RIM,
                            new ResolvableLocationDescriptor(LocationType.MEMORY, 4, new ResolvableMemory(destMem.getBase(), destMem.getIndex(), destMem.getScale(), (int)(destMem.getOffset().value() + i))),
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.MOV_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                            new ResolvableLocationDescriptor(LocationType.MEMORY, 4, new ResolvableMemory(sourceMem.getBase(), sourceMem.getIndex(), sourceMem.getScale(), (int)(sourceMem.getOffset().value() + i + 2))),
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.MOV_RIM,
                            new ResolvableLocationDescriptor(LocationType.MEMORY, 4, new ResolvableMemory(destMem.getBase(), destMem.getIndex(), destMem.getScale(), (int)(destMem.getOffset().value() + i + 2))),
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                            true
                        ));
                        break;
                        
                    default:
                }
            }
        }
    }
    
    /**
     * Handles context, allocation, and code generation for evicting any values in JI to another location
     * 
     * @param localCode
     */
    private void generateEvictJI(List<Component> localCode) {
        LOG.finest("Evicting JI");
        
        contextStack.pushContext();
        
        regJUsed = true;
        regIUsed = true;
        
        // check for JI allocations
        AllocatedContextMarker acm = (AllocatedContextMarker) contextStack.getLocalMarker();
        
        // do we even need to do anything
        if(!acm.registerAvailable(Register.JI)) {
            boolean hadJAllocated = acm.registerAllocations.containsKey(Register.J),
                    hadIAllocated = acm.registerAllocations.containsKey(Register.I),
                    hadJIAllocated = acm.registerAllocations.containsKey(Register.JI);
            
            // grab these before we overwrite
            String jiName = hadJIAllocated ? acm.registerAllocations.get(Register.JI): null,
                   jName = hadJAllocated ? acm.registerAllocations.get(Register.J): null,
                   iName = hadIAllocated ? acm.registerAllocations.get(Register.I): null;
            
            // make sure we don't relocate from JI to JI (e.g. only I allocated)
            acm.registerAllocations.put(Register.JI, "%jiused");
            contextStack.pushSymbol(new ContextSymbol("%jiused", RawType.PTR, new ResolvableLocationDescriptor(LocationType.REGISTER, Register.JI)));
            
            // yes we do
            if(hadJIAllocated) { 
                // single value, single allocation, single move
                NSTLType jiType = contextStack.getSymbol(jiName).getType();
                
                ContextSymbol cs = allocateLocalVariable(jiName, jiType);
                contextStack.pushSymbol(new ContextSymbol("%jitmp", RawType.PTR, cs.getVariableDescriptor()));
                
                localCode.add(new Instruction(
                    Opcode.MOVW_RIM,
                    cs.getVariableDescriptor(),
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.JI),
                    true
                ));
            } else {
                if(hadJAllocated) {
                    // same as JI but just J
                    NSTLType jType = contextStack.getSymbol(jName).getType();
                    
                    ContextSymbol cs = allocateLocalVariable(jName, jType);
                    contextStack.pushSymbol(new ContextSymbol("%jtmp", RawType.U16, cs.getVariableDescriptor()));
                    
                    localCode.add(new Instruction(
                        Opcode.MOV_RIM,
                        cs.getVariableDescriptor(),
                        new ResolvableLocationDescriptor(LocationType.REGISTER, Register.J),
                        true
                    ));
                }
                
                if(hadIAllocated) {
                    // same as JI but just I
                    NSTLType iType = contextStack.getSymbol(iName).getType();
                    
                    ContextSymbol cs = allocateLocalVariable(iName, iType);
                    contextStack.pushSymbol(new ContextSymbol("%itmp", RawType.U16, cs.getVariableDescriptor()));
                    
                    localCode.add(new Instruction(
                        Opcode.MOV_RIM,
                        cs.getVariableDescriptor(),
                        new ResolvableLocationDescriptor(LocationType.REGISTER, Register.I),
                        true
                    ));
                }
            }
        }
        
        // put the temporary pointer symbol in
        acm.registerAllocations.put(Register.JI, "%ptr");
        contextStack.pushSymbol(new ContextSymbol("%ptr", RawType.PTR, new ResolvableLocationDescriptor(LocationType.REGISTER, Register.JI)));
    }
    
    /**
     * Handles context, allocation, and code generation for restoring values to JI from a temporary location
     * @param localCode
     */
    private void generateRestoreJI(List<Component> localCode) {
        LOG.finest("Restoring JI");
        
        // the %<reg>tmp tells what got moved where
        // prior allocations of the names are recovered via popping the context
        
        // figure out what we need to restore
        ContextSymbol jitmpSymbol = (contextStack.hasLocalSymbol("%jitmp")) ? contextStack.getSymbol("%jitmp") : null,
                      jtmpSymbol = (contextStack.hasLocalSymbol("%jtmp")) ? contextStack.getSymbol("%jtmp") : null,
                      itmpSymbol = (contextStack.hasLocalSymbol("%itmp")) ? contextStack.getSymbol("%itmp") : null;
        
        contextStack.popContext();
        
        if(jitmpSymbol != null) {
            // had a symbol in JI
            localCode.add(new Instruction(
                Opcode.MOVW_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.JI),
                jitmpSymbol.getVariableDescriptor(),
                true
            ));
        } else {
            if(jtmpSymbol != null) {
                // had a symbol in J
                localCode.add(new Instruction(
                    Opcode.MOV_RIM,
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.J),
                    jtmpSymbol.getVariableDescriptor(),
                    true
                ));
            }
            
            if(itmpSymbol != null) {
                // had a symbol in I
                localCode.add(new Instruction(
                    Opcode.MOV_RIM,
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.I),
                    itmpSymbol.getVariableDescriptor(),
                    true
                ));
            }
        }
    }
    
    /**
     * Compiles the computation of a value
     * 
     * @param node variable_expression
     * @param localCode
     * @param target Destination symbol, %accumulator, %stack, or %ji
     * @param type destination type. RawType.NONE will attempt to infer the type - If the target has a type, that will become the target type. If the target is %stack and a type cannot be inferred from the node, an exception will be thrown
     * @param returnInferred If true, the returned type is the inferred type rather than specified type
     * @return the type of the value generated, or RawType.NONE. used for inference
     */
    private NSTLType compileValueComputation(ASTNode node, List<Component> localCode, Map<String, Integer> localLabelMap, String target, NSTLType type, boolean returnInferred) {
        LOG.finest("Compiling value computation " + target + " <= " + type + " " + detailed(node));
        NSTLType inferredType = RawType.NONE;
        
        // NOTE: Pointers to values stored in registers
        // 1. Allocate stack memory for the value
        // 2. Copy the value to the new location
        // 3. If the symbol is local or the context is unconditional, deallocate old register
        // 4. Otherwise, emit a warning
        // 5. Give pointer to new location
        
        // if we have a constant, ez
        if(isConstant(node)) {
            TypedValue constantValue = computeConstant(node);
            inferredType = constantValue.getType();
            
            // we need an expected type for this
            if(constantValue.getType() == RawType.NONE && type == RawType.NONE) {
                LOG.severe("Could not infer type in computation: " + detailed(node));
                errorsEncountered = true;
                return RawType.NONE; 
            } else if(constantValue.convertType(type)) {
                // keep things in order
                type = constantValue.getType();
                
                // we have a properly typed value, move it where it needs to go
                if(target.equals("%accumulator")) {
                    // %accumulator is DA for a value <= 4 bytes or a temporarily allocated stack location otherwise
                    if(type.getSize() == 1) {
                        // AL
                        localCode.add(new Instruction(
                            Opcode.MOVS_A_I8, // AH doesn't matter and this saves a byte
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(constantValue.toLong())),
                            false, false
                        ));
                    } else if(type.getSize() == 2) { 
                        // A
                        localCode.add(new Instruction(
                            Opcode.MOV_A_I16,
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(constantValue.toLong())),
                            false, false
                        ));
                    } else if(type.getSize() <= 4) {
                        // DA
                        localCode.add(new Instruction(
                            Opcode.MOVW_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(constantValue.toLong())),
                            false
                        ));
                    } else {
                        // stack allocation of %accumulator<counter>
                        // whatever is asking for this to be in the accumulator will pop the context
                        contextStack.pushContext();
                        
                        ContextSymbol accSymbol = allocateLocalVariable("%accumulator" + contextStack.getContextCounter(), type);
                        generateLongConstantMove(accSymbol, constantValue, localCode, localLabelMap);
                    }
                } else if(target.equals("%stack")) {
                    // %stack is the stack
                    // defer to compilePushLocalVariable via temporary constant
                    contextStack.pushContext();
                    
                    contextStack.pushSymbol(new ContextSymbol("%tmp", constantValue));
                    generatePushLocalVariable("%tmp", localCode);
                    
                    contextStack.popContext();
                } else if(contextStack.hasSymbol(target)) {
                    // symbol target
                    ContextSymbol cs = contextStack.getSymbol(target);
                    if(type == RawType.NONE) {
                        type = cs.getType();
                        inferredType = cs.getType();
                    }
                    
                    if(cs.getIsConstant()) {
                        LOG.severe("Cannot use constant " + target + " as computation target");
                        errorsEncountered = true;
                    } else {
                        boolean wide = cs.getVariableDescriptor().getSize() == 4,
                                thin = cs.getVariableDescriptor().getSize() == 1;
                        
                        if(cs.getVariableDescriptor().getType() == LocationType.REGISTER) {
                            // register target, write directly
                            localCode.add(new Instruction(
                                wide ? Opcode.MOVW_RIM : Opcode.MOV_RIM,
                                cs.getVariableDescriptor(),
                                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(constantValue.toLong())),
                                false
                            ));
                        } else {
                            // memory target, go by accumulator
                            localCode.add(new Instruction(
                                wide ? Opcode.MOVW_RIM : Opcode.MOV_RIM,
                                new ResolvableLocationDescriptor(LocationType.REGISTER, wide ? Register.DA : Register.A),
                                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(constantValue.toLong())),
                                false
                            ));
                            
                            localCode.add(new Instruction(
                                wide ? Opcode.MOVW_RIM : Opcode.MOV_RIM,
                                cs.getVariableDescriptor(),
                                new ResolvableLocationDescriptor(LocationType.REGISTER, wide ? Register.DA : (thin ? Register.AL : Register.A)),
                                false
                            ));
                        }
                    }
                } else {
                    LOG.severe("Unknown symbol " + target + " as computation target");
                    errorsEncountered = true;
                }
                
                return returnInferred ? inferredType : type;
            } else {
                LOG.severe("Incorrect or unconvertable type in computation: expected " + type + " but got " + constantValue);
                errorsEncountered = true;
                return RawType.NONE;
            }
        }
        
        // not constant
        List<ASTNode> children = node.getChildren();
        
        // what do we got
        switch(node.getSymbol().getID()) {
            // Parser IDs
            case NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE:
                // since this is detected as non-constant, this is a variable name
                String n = children.get(0).getValue();
                
                if(contextStack.hasSymbol(n)) {
                    ContextSymbol cs = contextStack.getSymbol(n);
                    
                    // PTR promotes
                    if(type.equals(RawType.PTR) && !cs.getType().equals(RawType.PTR)) {
                        checkType(RawType.NONE, cs.getType(), "from name reference");
                        compilePointerPromotion(cs, localCode);
                        
                        if(!target.equals("%accumulator")) {
                            generateVariableMove("%accumulator", target, 4, localCode);
                        }
                        
                        return RawType.PTR;
                    } else {
                        // check types
                        type = checkType(type, cs.getType(), "from name reference");
                        
                        generateVariableMove(n, target, cs.getType().getSize(), localCode);
                        return cs.getType();
                    }
                } else {
                    LOG.severe("Unknown symbol " + n + " in variable value computation");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_NAME:
                // copied from constant_value
                // since this is detected as non-constant, this is a variable name
                n = node.getValue();
                
                if(contextStack.hasSymbol(n)) {
                    ContextSymbol cs = contextStack.getSymbol(n);
                    
                    // PTR promotes
                    if(type.equals(RawType.PTR) && !cs.getType().equals(RawType.PTR)) {
                        checkType(RawType.NONE, cs.getType(), "from name reference");
                        compilePointerPromotion(cs, localCode);
                        
                        if(!target.equals("%accumulator")) {
                            generateVariableMove("%accumulator", target, 4, localCode);
                        }
                        
                        return RawType.PTR;
                    } else {
                        // check types
                        type = checkType(type, cs.getType(), "from name reference");
                        
                        generateVariableMove(n, target, cs.getType().getSize(), localCode);
                        return cs.getType();
                    }
                } else {
                    LOG.severe("Unknown symbol " + n + " in variable value computation");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarParser.ID.VARIABLE_REFERENCE:
                if(children.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_TO) {
                    // pointer to reference
                    NSTLType inferred = compilePointerComputation(children.get(1), localCode, localLabelMap, target);
                    
                    // allow pointers of unknown type
                    if(!inferred.equals(RawType.NONE))
                        type = checkType(type, inferred, "from TO");
                    
                    return returnInferred ? inferred : type;
                } else {
                    // AT
                    // dereference via accumulator
                    NSTLType inferred = ((PointerType) compilePointerComputation(node, localCode, localLabelMap, "%accumulator")).getPointedType();
                    if(type.equals(RawType.NONE)) type = inferred;
                    else type = checkType(type, inferred, "from AT");
                    
                    generateVariableMove("%accpointer", target, type.getSize(), localCode);
                    
                    return returnInferred ? inferred : type;
                }
            
            case NstlgrammarParser.ID.VARIABLE_SUBREFERENCE:
                if(children.size() == 1) {
                    if(children.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_NAME) {
                        // just a name
                        n = children.get(0).getValue();
                        
                        if(contextStack.hasSymbol(n)) {
                            ContextSymbol cs = contextStack.getSymbol(n);
                            
                            generateVariableMove(n, target, cs.getType().getSize(), localCode);
                            return cs.getType();
                        } else {
                            LOG.severe("Unknown symbol " + n + " in variable value computation");
                            errorsEncountered = true;
                        }
                    } else {
                        // function call
                        // generate it
                        NSTLType returnType = compileFunctionCall(children.get(0), localCode, localLabelMap);
                        
                        checkType(type, returnType, "from function call");
                        
                        // move to target if needed
                        if(!target.equals("%accumulator")) {
                            this.generateVariableMove("%accumulator", target, returnType.getSize(), localCode);
                        }
                        
                        return returnInferred ? returnType : type;
                    }
                } else {
                    if(children.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_NAME && children.get(1).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_DOT) {
                        // NAME DOT NAME is nice and direct
                        String structureName = children.get(0).getValue();
                        
                        if(contextStack.hasSymbol(structureName)) {
                            ContextSymbol cs = contextStack.getSymbol(structureName);
                            
                            StructureType st = null;
                            
                            boolean isStruct = false,
                                    isStructPointer = false;
                            
                            if(cs.getType() instanceof StructureType st2) {
                                st = st2;
                                isStruct = true;
                            } else if(cs.getType() instanceof PointerType pt && pt.getPointedType() instanceof StructureType pst) {
                                st = pst;
                                isStructPointer = true;
                            }
                            
                            // do we have the right type
                            if(isStruct || isStructPointer) {
                                if(cs.getIsConstant()) {
                                    LOG.severe("Invalid state: found constant structure symbol for variable");
                                    errorsEncountered = true;
                                } else {
                                    // member offset & type
                                    String memberName = children.get(2).getValue();
                                    
                                    if(st.getMemberNames().contains(memberName)) {
                                        NSTLType memberType = st.getMemberType(memberName);
                                        
                                        checkType(type, memberType, "from structure member " + memberName + " of " + st);
                                        
                                        Pair<ContextSymbol, Boolean> info = generateDirectMemberSymbol(cs, memberName, st, memberType, "%member", localCode, isStructPointer);
                                        ContextSymbol tcs = info.a;
                                        boolean evictedJI = info.b;
                                        
                                        // if the target involves JI, we need to move to a temporary and restore JI before moving to the target
                                        if(evictedJI && target.contains("%ji")) {
                                            contextStack.pushContext();
                                            contextStack.pushSymbol(tcs);
                                            
                                            // do we need a memory acc
                                            if(memberType.getSize() > 4) {                                                
                                                ContextSymbol accSymbol = allocateLocalVariable("%accumulator" + contextStack.getContextCounter(), memberType);
                                                generateVariableMove("%member", accSymbol.getName(), memberType.getSize(), localCode);
                                                
                                                contextStack.popContext();
                                                generateRestoreJI(localCode);
                                                
                                                contextStack.pushContext();
                                                contextStack.pushSymbol(accSymbol);
                                                
                                                generateVariableMove(accSymbol.getName(), target, memberType.getSize(), localCode);
                                                contextStack.popContext();
                                            } else {
                                                // simple
                                                generateVariableMove("%member", "%accumulator", memberType.getSize(), localCode);
                                                
                                                contextStack.popContext();
                                                generateRestoreJI(localCode);
                                                
                                                generateVariableMove("%accumulator", target, memberType.getSize(), localCode);
                                            }
                                        } else {
                                            // direct move
                                            contextStack.pushContext();
                                            contextStack.pushSymbol(tcs);
                                            
                                            generateVariableMove("%member", target, memberType.getSize(), localCode);
                                            
                                            contextStack.popContext();
                                            if(evictedJI) generateRestoreJI(localCode);
                                        }
                                        
                                        return returnInferred ? memberType : type;
                                    } else {
                                        LOG.severe("Structure type " + st + " does not have member " + memberName);
                                        errorsEncountered = true;
                                    }
                                }
                            } else {
                                // we don't
                                LOG.severe("Structure reference symbol " + structureName + " is not a structure or structure pointer");
                                errorsEncountered = true;
                            }
                        } else {
                            LOG.severe("Unknown symbol " + structureName + " as structure reference");
                            errorsEncountered = true;
                        }
                    } else {
                        // otherwise just compute pointer and load
                        if(!target.equals("%ji")) generateEvictJI(localCode);
                        NSTLType t = ((PointerType) compilePointerComputation(node, localCode, localLabelMap, "%ji")).getPointedType();
                        
                        String contextMessage = children.get(1).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_INDEX ? "from index" : "from structure";
                        
                        type = checkType(type, t, contextMessage);
                        
                        // target might have been JI or [JI]
                        if(target.equals("%ji")) {
                            // target is JI. do not restore
                            generateVariableMove("%jipointer", target, type.getSize(), localCode);
                        } else if(symbolUsesJI(target)) {
                            // target needs JI. Move to accumulator, restore, then move
                            generateVariableMove("%jipointer", "%accumulator", type.getSize(), localCode);
                            generateRestoreJI(localCode);
                            generateVariableMove("%accumulator", target, type.getSize(), localCode);
                        } else {
                            // JI not needed for target, move and restore
                            generateVariableMove("%jipointer", target, type.getSize(), localCode);
                            generateRestoreJI(localCode);
                        }
                        
                        return returnInferred ? t : type;
                    }
                }
                break;
            
            case NstlgrammarParser.ID.VARIABLE_VARIABLE_STRUCTURE:
                // get and check type
                String typeName = children.get(0).getValue();
                
                if(!typeDefinitions.containsKey(typeName)) {
                    LOG.severe("Unknown structure type: " + typeName);
                    errorsEncountered = true;
                }
                
                NSTLType definedType = checkType(type, typeDefinitions.get(typeName).getRealType(), "from structure");
                StructureType structType;
                
                if(definedType instanceof StructureType st) {
                    structType = st;
                } else {
                    structType = null;
                    LOG.severe("Not a structure type: " + definedType);
                    errorsEncountered = true;
                }
                
                // find members
                List<String> memberNames = structType.getMemberNames();
                Map<String, ASTNode> memberNodeMap = new HashMap<>();
                
                findLoop:
                for(String name : memberNames) {
                    for(ASTNode memberNode : children.get(1).getChildren()) {
                        List<ASTNode> memberChildren = memberNode.getChildren();
                        
                        if(memberChildren.get(0).getValue().equals(name)) {
                            memberNodeMap.put(name, memberChildren.get(1));
                            continue findLoop;
                        }
                    }
                    
                    // member not found
                    LOG.severe("Could not find structure member " + name + " for structure " + structType);
                    errorsEncountered = true;
                }
                
                // check names
                for(ASTNode memberNode : children.get(1).getChildren()) {
                    String memberName = memberNode.getChildren().get(0).getValue();
                    
                    if(!structType.getMemberNames().contains(memberName)) { 
                        LOG.severe("Unknown structure member " + memberName + " for structure " + structType);
                        errorsEncountered = true;
                    }
                }
                
                // since we can't do operations on structures, we should only have memory locations to put them in
                if(target.equals("%accumulator") && !contextStack.hasLocalSymbol("%accumulator")) {
                    // no memory accumulator available, make one
                    contextStack.pushContext();
                    
                    ContextSymbol accSymbol = allocateLocalVariable("%accumulator" + contextStack.getContextCounter(), type);
                    compileValueComputation(node, localCode, localLabelMap, accSymbol.getName(), type, returnInferred); // defer to below
                } else if(target.equals("%stack")) {
                    // push in reverse member order
                    for(int i = memberNames.size() - 1; i >= 0; i--) {
                        String name = memberNames.get(i);
                        ASTNode mNode = memberNodeMap.get(name);
                        
                        compileValueComputation(mNode, localCode, localLabelMap, "%stack", structType.getMemberType(name), false);
                    }
                } else if(contextStack.hasSymbol(target)) {
                    // should be a memory symbol
                    ContextSymbol cs = contextStack.getSymbol(target);
                    
                    checkType(definedType, cs.getType(), "from target symbol " + cs + " of structure");
                    
                    // evaluate in member order
                    // get pointer to target in JI, use offsets from it
                    generateEvictJI(localCode);
                    
                    localCode.add(new Instruction(
                        Opcode.LEA_RIM,
                        new ResolvableLocationDescriptor(LocationType.REGISTER, Register.JI),
                        cs.getVariableDescriptor(),
                        true
                    ));
                    
                    for(int i = 0; i < memberNames.size(); i++) {
                        String name = memberNames.get(i);
                        ASTNode mNode = memberNodeMap.get(name);
                        NSTLType mType = structType.getMemberType(name);
                        
                        contextStack.pushSymbol(new ContextSymbol("%jioffs", mType, new ResolvableLocationDescriptor(LocationType.MEMORY, mType.getSize(), new ResolvableMemory(Register.JI, Register.NONE, 0, structType.getMemberOffset(name)))));
                        
                        compileValueComputation(mNode, localCode, localLabelMap, "%jioffs", structType.getMemberType(name), false);
                    }
                    
                    generateRestoreJI(localCode);
                } else {
                    LOG.severe("Unknown symbol " + target + " as computation target");
                    errorsEncountered = true;
                }
                
                return returnInferred ? definedType : type;
            
            case NstlgrammarParser.ID.VARIABLE_VARIABLE_ARRAY:
                //TODO asdjkjlkasdjlkasdlkjs
                LOG.severe("UNIMPLEMENTED: VALUE COMPUTATION OF VARIABLE ARRAY");
                errorsEncountered = true;
                break;
            
            // Lexer IDs
            case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.JZ_RIM, true, localCode, localLabelMap);
                } else {
                    // valid
                    // TODO
                    LOG.severe("UNIMPLEMENTED: NON-RAW COMPARE EQUAL");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.JNZ_RIM, true, localCode, localLabelMap);
                } else {
                    // valid
                    // TODO
                    LOG.severe("UNIMPLEMENTED: NON-RAW COMPARE NOT EQUAL");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_GREATER:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.JG_RIM, false, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot compare non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.JGE_RIM, false, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot compare non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_LESS:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.JL_RIM, false, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot compare non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.JLE_RIM, false, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot compare non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_AND:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.AND_RIM, true, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot AND non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_OR:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.OR_RIM, true, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot OR non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_XOR:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.XOR_RIM, true, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot XOR non-raw types");
                    errorsEncountered = true;
                }
                break;
                
            case NstlgrammarLexer.ID.TERMINAL_OP_SHIFT_LEFT:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.SHL_RIM, false, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot shift non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_ARITH_SHIFT_RIGHT:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.SAR_RIM, false, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot shift non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_LOGIC_SHIFT_RIGHT:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.SHR_RIM, false, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot shift non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_ROL:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.ROL_RIM, false, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot rotate non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_ROR:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.ROR_RIM, false, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot rotate  non-raw types");
                    errorsEncountered = true;
                }
                break;
                
            case NstlgrammarLexer.ID.TERMINAL_OP_ADD:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.ADD_RIM, true, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot add non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_SUBTRACT:
                if(type instanceof RawType || type instanceof PointerType) {
                    if(children.size() == 2) {
                        // subtract
                        return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.SUB_RIM, false, localCode, localLabelMap);
                    } else {
                        // negate
                        // TODO
                        LOG.severe("UNIMLPEMENTED: NEGATION (UNARY GENERIC OPERATION)");
                    }
                } else {
                    LOG.severe("Cannot subtract non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_MULTIPLY:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.MUL_RIM, true, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot multiply non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_DIVIDE:
                if(type instanceof RawType || type instanceof PointerType) {
                    return compileGenericOperation(target, children.get(0), children.get(1), type, Opcode.DIV_RIM, false, localCode, localLabelMap);
                } else {
                    LOG.severe("Cannot divide non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_REMAINDER:
                if(type instanceof RawType rt && rt.getSize() < 4) {
                    // divide into accumulator
                    inferredType = compileGenericOperation("%accumulator", children.get(0), children.get(1), type, type.isSigned() ? Opcode.DIVMS_RIM : Opcode.DIVM_RIM, false, localCode, localLabelMap);
                    
                    // Move from accumulator upper to lower
                    if(rt.getSize() == 1) {
                        localCode.add(new Instruction(
                            Opcode.MOV_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AH),
                            true
                        ));
                    } else {
                        localCode.add(new Instruction(
                            Opcode.MOV_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.D),
                            true
                        ));
                    }
                    
                    return inferredType;
                } else {
                    LOG.severe("Cannot take remainder of non-raw or wide types");
                    errorsEncountered = true;
                }
                
                // TODO
                LOG.severe("UNIMPLEMENTED: REMAINDER");
                errorsEncountered = true;
                break;
                
            case NstlgrammarLexer.ID.TERMINAL_KW_NOT:
                if(type instanceof RawType || type instanceof PointerType) {
                    ASTNode valueNode = children.get(0);
                    
                    if(valueNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE) {
                        String name = valueNode.getChildren().get(0).getValue(); 
                        
                        if(!contextStack.hasSymbol(name)) {
                            LOG.severe("Unknown symbol " + name + " in NOT");
                            errorsEncountered = true;
                        } else if(target.equals(name)) {
                            // target direct = do directly
                            ContextSymbol cs = contextStack.getSymbol(name);
                            ResolvableLocationDescriptor rld = cs.getVariableDescriptor();
                            if(type.equals(RawType.NONE)) type = cs.getType();
                            else checkType(type, cs.getType(), "from symbol " + cs);
                            
                            if(type.getSize() == 1 || type.getSize() == 2) {
                                localCode.add(new Instruction(
                                    Opcode.NOT_RIM,
                                    rld,
                                    true, true
                                ));
                            } else if(type.getSize() == 4) {
                                Pair<ResolvableLocationDescriptor, ResolvableLocationDescriptor> pair = splitRLD(rld);
                                ResolvableLocationDescriptor high = pair.a, low = pair.b;
                                
                                localCode.add(new Instruction(
                                    Opcode.NOT_RIM,
                                    low,
                                    true, true
                                ));
                                
                                localCode.add(new Instruction(
                                    Opcode.NOT_RIM,
                                    high,
                                    true, true
                                ));
                            } else {
                                LOG.severe("Invalid type for NOT: " + type);
                                errorsEncountered = true;
                            }
                            
                            return type;
                        }
                    }
                    
                    // indirect = do via accumulator
                    NSTLType t = compileValueComputation(valueNode, localCode, localLabelMap, "%accumulator", type, returnInferred);
                    
                    if(type.equals(RawType.NONE)) type = t;
                    else checkType(type, t, " for NOT");
                    
                    if(type.getSize() == 1 || type.getSize() == 2) {
                        localCode.add(new Instruction(
                            Opcode.NOT_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, (type.getSize() == 1) ? Register.AL : Register.A),
                            true, true
                        ));
                    } else if(type.getSize() == 4) {
                        localCode.add(new Instruction(
                            Opcode.NOT_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                            true, true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.NOT_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.D),
                            true, true
                        ));
                    } else {
                        LOG.severe("Invalid type for NOT: " + type);
                        errorsEncountered = true;
                    }
                    
                    return returnInferred ? t : type;
                } else {
                    LOG.severe("Cannot NOT non-raw types");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_AS:
                // type casting
                NSTLType castedType = constructType(children.get(1));
                
                // compute to accumulator, perform cast if needed, send to target
                NSTLType originalType = compileValueComputation(children.get(0), localCode, localLabelMap, "%accumulator", RawType.NONE, true);
                
                if((originalType instanceof RawType || originalType instanceof PointerType) && (castedType instanceof RawType || castedType instanceof PointerType)) {
                    // can extend/truncate as needed
                    int os = originalType.getSize(),
                        cs = castedType.getSize();
                    
                    if(os < cs) {
                        // extend based on original type
                        ResolvableLocationDescriptor dst = switch(cs) {
                            case 1  -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL);
                            case 2  -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A);
                            default -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA);
                        };
                        
                        ResolvableLocationDescriptor src = switch(os) {
                            case 1  -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL);
                            case 2  -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A);
                            default -> new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA);
                        };
                        
                        if(originalType instanceof RawType rt && rt.isSigned()) {
                            // signed
                            if(cs - os < 3) {
                                // 1 -> 2 or 2 -> 4
                                localCode.add(new Instruction(
                                    Opcode.MOVS_RIM,
                                    dst,
                                    src,
                                    true
                                ));
                            } else {
                                // 1 -> 4
                                LOG.finest("SIGNED " + os + " TO " + cs + " -> MOVS 2X");
                                localCode.add(new Instruction(
                                    Opcode.MOVS_RIM,
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                    src,
                                    true
                                ));
                                
                                localCode.add(new Instruction(
                                    Opcode.MOVS_RIM,
                                    dst,
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                    true
                                ));
                            }
                        } else {
                            // unsigned
                            if(cs - os < 3) {
                                // 1 -> 2 or 2 -> 4
                                localCode.add(new Instruction(
                                    Opcode.MOVZ_RIM,
                                    dst,
                                    src,
                                    true
                                ));
                            } else {
                                // 1 -> 4
                                localCode.add(new Instruction(
                                    Opcode.MOVZ_RIM,
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                    src,
                                    true
                                ));
                                
                                localCode.add(new Instruction(
                                    Opcode.MOVZ_RIM,
                                    dst,
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                    true
                                ));
                            }
                        }
                    } // same/truncated = no action
                    
                    this.generateVariableMove("%accumulator", target, cs, localCode);
                    
                    return castedType;
                } else if(type.getSize() == castedType.getSize()) {
                    // alias. no action
                    this.generateVariableMove("%accumulator", target, castedType.getSize(), localCode);
                    return castedType;
                } else {
                    LOG.severe("Cannot cast between non-raw types of different sizes: " + type + " to " + castedType + " from " + detailed(node));
                    errorsEncountered = true;
                }
                break;
            
            default:
                LOG.severe("Unexpected symbol in variable_expression " + detailed(node));
                errorsEncountered = true;
        }
        
        return RawType.NONE;
    }
    
    /**
     * Compiles generic 2-argument operations on raw types, like add/sub
     * 
     * @param target
     * @param left
     * @param right
     * @param targetType
     * @param op
     * @param commutative
     * @param localCode
     * @return
     */
    private NSTLType compileGenericOperation(String target, ASTNode left, ASTNode right, NSTLType targetType, Opcode op, boolean commutative, List<Component> localCode, Map<String, Integer> localLabelMap) {
        LOG.finest("Compiling generic operation " + detailed(left) + " " + op + " " + detailed(right) + " to " + targetType + " " + target);
        
        /*
         * If one of the arguments is the target and either the operation is commutative (a gets b + a) or the target is the left side (a gets a / b):
         *  the non-target value will be computed to the accumulator, and the operation OP <target>, acc performed
         *  For dword values in memory, the offset must be resolved.
         * Otherwise, the computation recurses with the target as the accumulator, then the accumulator is moved to the target.
         * 
         * When computing to the accumulator:
         *  If one of the arguments is constant:
         *      If the operation is commutative or the constant is on the right side:
         *          The non-constant value will be computed to the accumulator, and the operation OP acc, constant performed
         *  
         *  If one of the arguments is a direct reference:
         *      If the operation is commutative or the reference is on the right side:
         *          The other value is computed to the accumulator, and the operation OP acc, reference is performed
         *  
         *  Otherwise
         *      The right side is computed to the stack, the left side is computed to the accumulator, and the operation OP acc, [SP] is performed
         */
        
        boolean isComparison = op.toString().contains("J") && targetType.equals(RawType.BOOLEAN),
                inferType = targetType.equals(RawType.NONE) || isComparison,
                targetBoolean = isComparison && target.equals("%accumulator");
        
        // descriptors for each size accumulator
        ResolvableLocationDescriptor accumulatorDescriptor1 = new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                                     accumulatorDescriptor2 = new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                     accumulatorDescriptor4 = new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA);
        
        // deteremine whether the arguments are constant
        // only one argument will be constant if they are passed to this function
        boolean leftIsConstant = isConstant(left),
                rightIsConstant = isConstant(right);
        
        // determine whether the nodes are calculated or not
        // a more sophisticated system could detect this for complex references, but we aren't passing this information and it would be a huge pain
        boolean leftIsDirectReference = false,
                rightIsDirectReference = false,
                leftIsTarget = false,
                rightIsTarget = false,
                leftSymbolExists = false,
                rightSymbolExists = false;
        String leftReferenceName = null,
               rightReferenceName = null;
        
        if(!leftIsConstant && left.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE) {
            leftIsDirectReference = true;
            leftReferenceName = left.getChildren().get(0).getValue();
            
            leftIsTarget = leftReferenceName.equals(target);
            leftSymbolExists = contextStack.hasSymbol(leftReferenceName);
        }
        
        if(!rightIsConstant && right.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE) {
            rightIsDirectReference = true;
            rightReferenceName = right.getChildren().get(0).getValue();
            
            rightIsTarget = rightReferenceName.equals(target);
            rightSymbolExists = contextStack.hasSymbol(rightReferenceName);
        }
        
        if(target.equals("%accumulator")) {
            if(rightIsConstant || (leftIsConstant && commutative)) {
                // A value is constant and we can use it directly. Compute the other to the accumulator then apply the oepration.
                // get type
                NSTLType accType = compileValueComputation(rightIsConstant ? left : right, localCode, localLabelMap, "%accumulator", isComparison ? RawType.NONE : targetType, true);
                
                if(inferType) {
                    if(accType instanceof RawType rt) {
                        targetType = rt;
                    } else {
                        LOG.severe("Generic operations must use raw types, found " + accType);
                        errorsEncountered = true;
                    }
                } else {
                    checkType(targetType, accType, "from computation " + detailed(rightIsConstant ? left : right) + " in generic operation " + op);
                }
                
                // get constant
                TypedValue tv = computeConstant(rightIsConstant ? right : left);
                tv.convertType(targetType);
                checkType(targetType, tv.getType(), "from constant " + tv + " in generic operation " + op);
                
                ResolvableLocationDescriptor accDescriptor;
                if(op == Opcode.DIVM_RIM || op == Opcode.DIVMS_RIM) {
                    // Special case for modulo
                    accDescriptor = switch(targetType.getSize()) {
                        case 1  -> accumulatorDescriptor2;
                        default -> accumulatorDescriptor4;
                    };
                } else {
                    accDescriptor = switch(targetType.getSize()) {
                        case 1  -> accumulatorDescriptor1;
                        case 2  -> accumulatorDescriptor2;
                        default -> accumulatorDescriptor4;
                    };
                }
                
                ResolvableLocationDescriptor constantDescriptor = new ResolvableLocationDescriptor(LocationType.IMMEDIATE, targetType.getSize(), ((TypedRaw) tv).getValue());
                
                generateGenericOperation(accDescriptor, constantDescriptor, op, targetBoolean, localCode, localLabelMap);
                return isComparison ? RawType.BOOLEAN : targetType;
            } else if(rightIsDirectReference || (leftIsDirectReference && commutative)) {
                String refName = rightIsDirectReference ? rightReferenceName : leftReferenceName;
                
                // A value is a direct reference we can use. Compute the other to the accumulator then apply the operation
                // get ref
                if(!(rightIsDirectReference ? rightSymbolExists : leftSymbolExists)) {
                    LOG.severe("Unknown symbol " + (refName) + " in generic operation " + op);
                    errorsEncountered = true;
                }
                
                ContextSymbol cs = contextStack.getSymbol(refName);
                
                if(inferType) {
                    if(cs.getType() instanceof RawType rt) {
                        targetType = rt;
                    } else {
                        LOG.severe("Generic operations must use raw types, found " + cs.getType());
                        errorsEncountered = true;
                    }
                } else {
                    checkType(targetType, cs.getType(), "from reference " + cs + " in generic operation " + op);
                }
                
                // compute
                NSTLType accType = compileValueComputation(rightIsDirectReference ? left : right, localCode, localLabelMap, "%accumulator", isComparison ? RawType.NONE : targetType, false);
                checkType(targetType, accType, "in generic operation " + op);
                
                ResolvableLocationDescriptor accDescriptor;
                if(op == Opcode.DIVM_RIM || op == Opcode.DIVMS_RIM) {
                    // Special case for modulo
                    accDescriptor = switch(targetType.getSize()) {
                        case 1  -> accumulatorDescriptor2;
                        default -> accumulatorDescriptor4;
                    };
                } else {
                    accDescriptor = switch(targetType.getSize()) {
                        case 1  -> accumulatorDescriptor1;
                        case 2  -> accumulatorDescriptor2;
                        default -> accumulatorDescriptor4;
                    };
                }
                
                generateGenericOperation(accDescriptor, cs.getVariableDescriptor(), op, targetBoolean, localCode, localLabelMap);
                return isComparison ? RawType.BOOLEAN : targetType;
            } else {
                // The long way. Compute the right side to the stack, the left side to the accumulator, then apply the operation and clean up.
                // compute and infer type
                NSTLType rightType = compileValueComputation(right, localCode, localLabelMap, "%stack", isComparison ? RawType.NONE : targetType, true);
                
                if(inferType) {
                    if(rightType instanceof RawType rt) {
                        targetType = rt;
                    } else {
                        LOG.severe("Generic operations must use raw types, found " + rightType);
                        errorsEncountered = true;
                    }
                } else {
                    checkType(targetType, rightType, "from computation " + detailed(right) + " in generic operation " + op);
                }
                
                NSTLType leftType = compileValueComputation(left, localCode, localLabelMap, "%accumulator", isComparison ? RawType.NONE : targetType, false);
                checkType(targetType, leftType, "from computation " + detailed(left) + " in generic operation " + op);
                
                ResolvableLocationDescriptor accDescriptor;
                if(op == Opcode.DIVM_RIM || op == Opcode.DIVMS_RIM) {
                    // Special case for modulo
                    accDescriptor = switch(targetType.getSize()) {
                        case 1  -> accumulatorDescriptor2;
                        default -> accumulatorDescriptor4;
                    };
                } else {
                    accDescriptor = switch(targetType.getSize()) {
                        case 1  -> accumulatorDescriptor1;
                        case 2  -> accumulatorDescriptor2;
                        default -> accumulatorDescriptor4;
                    };
                }
                
                ResolvableLocationDescriptor stackDescriptor = new ResolvableLocationDescriptor(LocationType.MEMORY, targetType.getSize(), new ResolvableMemory(Register.SP, Register.NONE, 0, 0));
                
                generateGenericOperation(accDescriptor, stackDescriptor, op, targetBoolean, localCode, localLabelMap);
                
                // clean up stack
                localCode.add(new Instruction(
                    Opcode.ADDW_SP_I8,
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(targetType.getSize())),
                    false, false
                ));
                
                return isComparison ? RawType.BOOLEAN : targetType;
            }
        } else {
            // setup values so we don't have to duplicate code
            boolean targetIsRegister = contextStack.hasSymbol(target) && contextStack.getSymbol(target).getVariableDescriptor().getType() == LocationType.REGISTER,
                    rightSideDirect = leftIsTarget ? rightIsDirectReference : leftIsDirectReference;
            
            // The target is not the accumulator. We might be able to use it as an argument 
            // when possible, compute using the target in the instruction
            if((leftIsTarget && !rightIsConstant) || (rightIsTarget && commutative && !leftIsConstant)) {
                ASTNode rightSideNode = leftIsTarget ? right : left;
                
                if(!(leftIsTarget ? leftSymbolExists : rightSymbolExists)) {
                    LOG.severe("Unknown symbol " + (leftIsTarget ? leftReferenceName : rightReferenceName) + " in generic operation " + op);
                    errorsEncountered = true;
                }
                
                ContextSymbol targetSymbol = contextStack.getSymbol(leftIsTarget ? leftReferenceName : rightReferenceName);
                
                // infer target type if needed
                if(inferType) {
                    if(targetSymbol.getType() instanceof RawType rt) {
                        targetType = rt;
                    } else {
                        LOG.severe("Target of generic operation is not a raw type: " + targetSymbol);
                        errorsEncountered = true;
                    }
                } else {
                    checkType(targetType, targetSymbol.getType(), "from " + targetSymbol + " in generic operation " + op);
                }
                
                if(rightSideDirect) {
                    // left is target, right is direct
                    // if one is a register compute directly
                    // otherwise go via accumulator
                    if(!(leftIsTarget ? rightSymbolExists : leftSymbolExists)) {
                        LOG.severe("Unknown symbol " + (leftIsTarget ? rightReferenceName : leftReferenceName) + " in generic operation " + op);
                        errorsEncountered = true;
                    }
                    
                    ContextSymbol rightSideSymbol = contextStack.getSymbol(leftIsTarget ? rightReferenceName : leftReferenceName);
                    checkType(targetType, rightSideSymbol.getType(), "from " + rightSideSymbol + " in generic operation " + op);
                    
                    if(targetSymbol.getVariableDescriptor().getType() == LocationType.REGISTER || rightSideSymbol.getVariableDescriptor().getType() == LocationType.REGISTER) {
                        // can go directly
                        generateGenericOperation(targetSymbol.getVariableDescriptor(), rightSideSymbol.getVariableDescriptor(), op, targetBoolean, localCode, localLabelMap);
                        return isComparison ? RawType.BOOLEAN : targetType;
                    } else {
                        // via accumulator
                        NSTLType t = compileValueComputation(rightSideNode, localCode, localLabelMap, "%accumulator", isComparison ? RawType.NONE : targetType, false);
                        checkType(targetType, t, "from computation " + detailed(rightSideNode) + " in generic operation " + op);
                        
                        ResolvableLocationDescriptor rightDescriptor = switch(targetType.getSize()) {
                            case 1  -> accumulatorDescriptor1;
                            case 2  -> accumulatorDescriptor2;
                            default -> accumulatorDescriptor4;
                        };
                        
                        generateGenericOperation(targetSymbol.getVariableDescriptor(), rightDescriptor, op, targetBoolean, localCode, localLabelMap);
                        return isComparison ? RawType.BOOLEAN : targetType;
                    }
                } else {
                    // left is target, right is indirect
                    // compute right to accumulator then apply operation
                    NSTLType rightType = compileValueComputation(rightSideNode, localCode, localLabelMap, "%accumulator", isComparison ? RawType.NONE : targetType, true);
                    checkType(targetType, rightType, "from computation " + detailed(rightSideNode) + " in generic operation " + op);
                    
                    ResolvableLocationDescriptor rightDescriptor = switch(targetType.getSize()) {
                        case 1  -> accumulatorDescriptor1;
                        case 2  -> accumulatorDescriptor2;
                        default -> accumulatorDescriptor4;
                    };
                    
                    generateGenericOperation(targetSymbol.getVariableDescriptor(), rightDescriptor, op, targetBoolean, localCode, localLabelMap);
                    return isComparison ? RawType.BOOLEAN : targetType;
                }
            } else if((leftIsTarget && rightIsConstant) || (rightIsTarget && commutative && leftIsConstant)) {
                if(!(leftIsTarget ? leftSymbolExists : rightSymbolExists)) {
                    LOG.severe("Unknown symbol " + (leftIsTarget ? leftReferenceName : rightReferenceName) + " in generic operation " + op);
                    errorsEncountered = true;
                }
                
                ContextSymbol targetSymbol = contextStack.getSymbol(leftIsTarget ? leftReferenceName : rightReferenceName);
                
                if(inferType) {
                    targetType = targetSymbol.getType();
                } else {
                    checkType(targetType, targetSymbol.getType(), "from target symbol " + targetSymbol);
                }
                
                TypedValue tv = computeConstant(leftIsTarget ? right : left);
                tv.convertType(targetType);
                checkType(targetType, tv.getType(), "from constant " + tv + " in generic operation " + op);
                
                if(targetIsRegister || ((op == Opcode.ADD_RIM || op == Opcode.SUB_RIM) && ((TypedRaw) tv).getValue().value() == 1)) {
                    // The target is a register and the other value is a constant, or the value is 1. We can compute this
                    ResolvableLocationDescriptor constantDescriptor = new ResolvableLocationDescriptor(LocationType.IMMEDIATE, targetType.getSize(), ((TypedRaw) tv).getValue());
                    generateGenericOperation(contextStack.getSymbol(target).getVariableDescriptor(), constantDescriptor, op, targetBoolean, localCode, localLabelMap);
                    return isComparison ? RawType.BOOLEAN : targetType;
                }
                
                // otherwise defer below
            }
            
            // the long way - compute via accumulator
            NSTLType type = compileGenericOperation("%accumulator", left, right, targetType, op, commutative, localCode, localLabelMap);
            
            if(inferType) {
                targetType = (RawType) type;
            } else {
                checkType(targetType, type, "from generic operation " + op);
            }
            
            generateVariableMove("%accumulator", target, targetType.getSize(), localCode);
            return isComparison ? RawType.BOOLEAN : targetType;
        }
        
        //return RawType.NONE;
    }
    
    /**
     * Generates instructions for the given operation, particularly dword operations
     * 
     * @param left
     * @param right
     * @param op
     * @param localCode
     */
    private void generateGenericOperation(ResolvableLocationDescriptor left, ResolvableLocationDescriptor right, Opcode op, boolean targetBoolean, List<Component> localCode, Map<String, Integer> localLabelMap) {
        LOG.finest("Generating generic operation " + op + ", " + left + ", " + right);
        // needed in a few places
        // split operands
        ResolvableLocationDescriptor leftHigh = null, leftLow = null, rightHigh = null, rightLow = null;
        boolean wide = left.getSize() == 4,
                incdec = (op == Opcode.ADD_RIM || op == Opcode.SUB_RIM) && right.getType() == LocationType.IMMEDIATE && right.getImmediate().value() == 1;
        
        if(wide) {
            Pair<ResolvableLocationDescriptor, ResolvableLocationDescriptor> leftPair = splitRLD(left),
                                                                             rightPair = splitRLD(right);
            
            leftHigh = leftPair.a;
            leftLow = leftPair.b;
            
            rightHigh = rightPair.a;
            rightLow = rightPair.b;
        }
        
        // conditionals
        if(op.getType() == Operation.JCC) {
            Opcode oppositeJmp = switch(op) {
                case JC_RIM     -> Opcode.JNC_RIM;
                case JNC_RIM    -> Opcode.JC_RIM;
                case JS_RIM     -> Opcode.JNS_RIM;
                case JNS_RIM    -> Opcode.JS_RIM;
                case JO_RIM     -> Opcode.JNO_RIM;
                case JNO_RIM    -> Opcode.JO_RIM;
                case JZ_RIM     -> Opcode.JNZ_RIM;
                case JNZ_RIM    -> Opcode.JZ_RIM;
                case JA_RIM     -> Opcode.JBE_RIM;
                case JBE_RIM    -> Opcode.JA_RIM;
                case JG_RIM     -> Opcode.JLE_RIM;
                case JGE_RIM    -> Opcode.JL_RIM;
                case JL_RIM     -> Opcode.JGE_RIM;
                case JLE_RIM    -> Opcode.JG_RIM;
                default -> op;
            };
            
            Opcode nonEqualJmp = switch(op) {
                case JNC_RIM    -> Opcode.JA_RIM;   // JAE
                case JBE_RIM    -> Opcode.JC_RIM;
                case JGE_RIM    -> Opcode.JG_RIM;
                case JLE_RIM    -> Opcode.JL_RIM;
                default         -> op;
            };
            
            Opcode oppositeNonEqualJmp = switch(oppositeJmp) {
                case JNC_RIM    -> Opcode.JA_RIM;   // JAE
                case JBE_RIM    -> Opcode.JC_RIM;
                case JGE_RIM    -> Opcode.JG_RIM;
                case JLE_RIM    -> Opcode.JL_RIM;
                default         -> oppositeJmp;
            };
            
            // evaluate conditional
            if(left.getSize() == 1 || left.getSize() == 2) {
                localCode.add(new Instruction(
                    Opcode.CMP_RIM,
                    left,
                    right,
                    false
                ));
            } else {
                switch(op) {
                    case JZ_RIM:
                    case JNZ_RIM:
                        // make both comparisons, OR the flags
                        localCode.add(new Instruction(
                            Opcode.CMP_RIM,
                            leftHigh,
                            rightHigh,
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.PUSH_F,
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.CMP_RIM,
                            leftLow,
                            rightLow,
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.POP_A,
                            true
                        ));
                        
                        localCode.add(new Instruction(
                                Opcode.AND_F_RIM,
                                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                false, true
                            ));
                        break;
                        
                    case JG_RIM:
                    case JGE_RIM:
                    case JL_RIM:
                    case JLE_RIM:
                    case JA_RIM:
                    case JNC_RIM: // JAE
                    case JC_RIM: // JB
                    case JBE_RIM:
                        String determinedLabel = functionName + ".cmpd" + longComparisonCounter++; 
                        
                        // compare upper. if determinate, jump to the cmov
                        localCode.add(new Instruction(
                            Opcode.CMP_RIM,
                            leftHigh,
                            rightHigh,
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            nonEqualJmp,
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(determinedLabel)),
                            false, false
                        ));
                        
                        localCode.add(new Instruction(
                            oppositeNonEqualJmp,
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(determinedLabel)),
                            false, false
                        ));
                        
                        // compare lower
                        localCode.add(new Instruction(
                            Opcode.CMP_RIM,
                            leftLow,
                            rightLow,
                            true
                        ));
                        
                        localLabelMap.put(determinedLabel, localCode.size());
                        break;
                    
                    default:
                        LOG.severe("Unsupported generic dword compare: " + op);
                        errorsEncountered = true;
                }
            }
            
            // if we're getting a boolean from this which goes in ACC
            if(targetBoolean) {
                left = new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL);
                leftLow = left;
            }
            
            // cmov constant 0/1
            localCode.add(new Instruction(
                Opcode.CMOVCC_RIM,
                wide ? leftLow : left,
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(1)),
                op.getOp(),
                false
            ));
            
            localCode.add(new Instruction(
                Opcode.CMOVCC_RIM,
                wide ? leftLow : left,
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(0)),
                oppositeJmp.getOp(),
                false
            ));
            
            // upper bytes get 0
            if(left.getSize() == 4) {
                localCode.add(new Instruction(
                    Opcode.MOVZ_RIM,
                    leftHigh,
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(0)),
                    false
                ));
            }
        } else {
            // byte/word operations can be done directly
            if(left.getSize() == 1 || left.getSize() == 2 || op == Opcode.DIVM_RIM || op == Opcode.DIVMS_RIM) {
                // hacky bad practice
                if(op == Opcode.DIVM_RIM || op == Opcode.DIVMS_RIM) {
                    if(left.getSize() == 2) {
                        localCode.add(new Instruction(
                            op == Opcode.DIVMS_RIM ? Opcode.MOVS_RIM : Opcode.MOVZ_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                            true
                        ));
                    } else {
                        localCode.add(new Instruction(
                            op == Opcode.DIVMS_RIM ? Opcode.MOVS_RIM : Opcode.MOVZ_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                            true
                        ));
                    }
                }
                
                if(incdec) {
                    localCode.add(new Instruction(
                        op == Opcode.ADD_RIM ? Opcode.INC_RIM : Opcode.DEC_RIM,
                        left,
                        true, true
                    ));
                } else {
                    localCode.add(new Instruction(
                        op,
                        left,
                        right,
                        false
                    ));
                }
            } else {
                // dword operations require more thought
                switch(op) {
                    case ADD_RIM:
                        if(incdec) {
                            localCode.add(new Instruction(
                                Opcode.INC_RIM,
                                leftLow,
                                true, true
                            ));
                            
                            localCode.add(new Instruction(
                                Opcode.ICC_RIM,
                                leftHigh,
                                true, true
                            ));
                        } else {
                            localCode.add(new Instruction(
                                Opcode.ADD_RIM,
                                leftLow,
                                rightLow,
                                false
                            ));
                            
                            localCode.add(new Instruction(
                                Opcode.ADC_RIM,
                                leftHigh,
                                rightHigh,
                                false
                            ));
                        }
                        break;
                    
                    case SUB_RIM:
                        if(incdec) {
                            localCode.add(new Instruction(
                                Opcode.DEC_RIM,
                                leftLow,
                                true, true
                            ));
                            
                            localCode.add(new Instruction(
                                Opcode.DCC_RIM,
                                leftHigh,
                                true, true
                            ));
                        } else {
                            localCode.add(new Instruction(
                                Opcode.SUB_RIM,
                                leftLow,
                                rightLow,
                                false
                            ));
                            
                            localCode.add(new Instruction(
                                Opcode.SBB_RIM,
                                leftHigh,
                                rightHigh,
                                false
                            ));
                        }
                        break;
                    
                    case AND_RIM:
                    case OR_RIM:
                    case XOR_RIM:
                        localCode.add(new Instruction(
                            op,
                            leftLow, rightLow,
                            false
                        ));
                        
                        localCode.add(new Instruction(
                            op,
                            leftHigh, rightHigh,
                            false
                        ));
                        break;
                    
                    case SHL_RIM:
                        if(right.getType() == LocationType.IMMEDIATE && right.getImmediate().isResolved()) {
                            if(right.getImmediate().value() == 16) {
                                localCode.add(new Instruction(
                                    Opcode.MOV_RIM,
                                    leftHigh, leftLow,
                                    false
                                ));
                                localCode.add(new Instruction(
                                    Opcode.MOV_RIM,
                                    leftLow, new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(0)),
                                    false
                                ));
                            } else if(right.getImmediate().value() == 1) {
                                localCode.add(new Instruction(
                                    Opcode.SHL_RIM,
                                    leftLow,
                                    right,
                                    false
                                ));
                                localCode.add(new Instruction(
                                    Opcode.RCL_RIM,
                                    leftHigh,
                                    right,
                                    false
                                ));
                            } else {
                                LOG.severe("Unsupported dword operation: " + op + " with arbitrary value");
                                errorsEncountered = true;
                            }
                        } else {
                            LOG.severe("Unsupported dword operation: " + op + " with arbitrary value");
                            errorsEncountered = true;
                        }
                        break;
                        
                    case SHR_RIM:
                        if(right.getType() == LocationType.IMMEDIATE && right.getImmediate().isResolved()) {
                            if(right.getImmediate().value() == 16) {
                                localCode.add(new Instruction(
                                    Opcode.MOVZ_RIM,
                                    left, leftHigh,
                                    false
                                ));
                            } else if(right.getImmediate().value() == 1) {
                                localCode.add(new Instruction(
                                    Opcode.SHR_RIM,
                                    leftHigh,
                                    right,
                                    false
                                ));
                                localCode.add(new Instruction(
                                    Opcode.RCR_RIM,
                                    leftLow,
                                    right,
                                    false
                                ));
                            } else {
                                LOG.severe("Unsupported dword operation: " + op + " with arbitrary value");
                                errorsEncountered = true;
                            }
                        } else {
                            LOG.severe("Unsupported dword operation: " + op + " with arbitrary value");
                            errorsEncountered = true;
                        }
                        break;
                    
                    // TODO more operations
                    
                    default:
                        LOG.severe("Unsupported dword operation: " + op);
                        errorsEncountered = true;
                }
            }
        }
    }
    
    /**
     * Splits a location descriptor
     * 
     * @param rld
     * @return a = high, b = low
     */
    private Pair<ResolvableLocationDescriptor, ResolvableLocationDescriptor> splitRLD(ResolvableLocationDescriptor rld) {
        ResolvableLocationDescriptor high, low;
        
        if(rld.getType() == LocationType.REGISTER) {
            Pair<Register, Register> halves = splitRegister(rld.getRegister());
            high = new ResolvableLocationDescriptor(LocationType.REGISTER, halves.a);
            low = new ResolvableLocationDescriptor(LocationType.REGISTER, halves.b);
        } else if(rld.getType() == LocationType.MEMORY) {
            ResolvableMemory rm = rld.getMemory();
            high = new ResolvableLocationDescriptor(LocationType.MEMORY, 4, new ResolvableMemory(rm.getBase(), rm.getIndex(), rm.getScale(), new ResolvableConstant(rm.getOffset().value() + 2)));
            low = new ResolvableLocationDescriptor(LocationType.MEMORY, 4, new ResolvableMemory(rm.getBase(), rm.getIndex(), rm.getScale(), rm.getOffset()));
        } else {
            long v = rld.getImmediate().value();
            high = new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 2, new ResolvableConstant((v >> 16) & 0xFFFF));
            low = new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 2, new ResolvableConstant(v & 0xFFFF));
        }
        
        return new Pair<>(high, low);
    }
    
    /**
     * Splits a register pair
     * 
     * @param r
     * @return
     */
    private Pair<Register, Register> splitRegister(Register r) {
        switch(r) {
            case DA:
                return new Pair<>(Register.D, Register.A);
                
            case BC:
                return new Pair<>(Register.B, Register.C);
                
            case JI:
                return new Pair<>(Register.J, Register.I);
                
            case LK:
                return new Pair<>(Register.L, Register.K);
            
            default:
                return new Pair<>(Register.NONE, Register.NONE);
        }
    }
    
    /**
     * Returns true if a symbol needs JI to be accessed
     * 
     * @param name
     * @return
     */
    private boolean symbolUsesJI(String name) {
        if(name.equals("%ji") || name.equals("%jipointer")) return true;
        
        if(contextStack.hasSymbol(name)) {
            ResolvableLocationDescriptor rld = contextStack.getSymbol(name).getVariableDescriptor();
            
            if(rld.getType() == LocationType.REGISTER) {
                return rld.getRegister() == Register.JI ||
                       rld.getRegister() == Register.J ||
                       rld.getRegister() == Register.I;
            } else {
                return rld.getMemory().getBase() == Register.JI;
            }
        }
        
        return false;
    }
    
    /**
     * Compiles the computation of a pointer
     * 
     * @param node reference
     * @param localCode
     * @param target Destination symbol, %accumulator, %stack, or %ji
     * @return a PointerType to the type pointed to
     */
    private NSTLType compilePointerComputation(ASTNode node, List<Component> localCode, Map<String, Integer> localLabelMap, String target) {
        LOG.finest("Compiling pointer computation " + target + " <= pointer to " + detailed(node));
        
        ContextSymbol targetSymbol = (contextStack.hasSymbol(target) ? contextStack.getSymbol(target) : null);
        boolean targetIsRegister = target.equals("%accumulator") || target.equals("%ji") ||
                (targetSymbol != null && !targetSymbol.getIsConstant() && targetSymbol.getVariableDescriptor().getType() == LocationType.REGISTER);
        
        /*
         * Reference
         *  AT subreference         subreference pointer
         *  type AT subreference    subreference pointer
         *  TO subreference         invalid
         * Subreference
         *  subreference DOT NAME   (subreference pointer) + member offset
         *  NAME DOT NAME           (name pointer) + member offset 
         *  subreference INDEX exp  (subreference pointer) + (exp * member type size)
         *  NAME INDEX exp          (name pointer) + (exp * member type size)
         *  function_call           valid if it returns a relevant pointer
         *  NAME                    name pointer
         */
        List<ASTNode> children = node.getChildren();
        
        if(node.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_REFERENCE) {
            // Reference
            if(children.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_TO) {
                // TO subref, invalid here
                LOG.severe("Cannot generate pointer to intermediate value TO " + children.get(1));
                errorsEncountered = true;
            } else {
                // AT subreference = subreference
                if(children.get(1).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_AT) {
                    // untyped or overridden
                    compileValueComputation(children.get(2), localCode, localLabelMap, target, RawType.PTR, true);
                    
                    return new PointerType(constructType(children.get(0)));
                } else {
                    // must be a typed pointer
                    NSTLType inferredType = compileValueComputation(children.get(1), localCode, localLabelMap, target, RawType.PTR, true);
                    
                    if(inferredType instanceof PointerType pt) {
                        return pt;
                    } else {
                        LOG.severe("Cannot dereference without pointed type: " + detailed(children.get(1)) + " (" + inferredType + ") is not a typed pointer");
                        errorsEncountered = true;
                    }
                }
            }
        } else if(children.size() > 1) {
            // Subreference
            if(children.get(1).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_DOT) {
                // Structure member
                // what are we getting a member from
                NSTLType structType, inferredType = inferReferenceType(children.get(0));
                
                if(inferredType instanceof PointerType pt) {
                    structType = ((PointerType) inferredType).getPointedType();
                    compileValueComputation(children.get(0), localCode, localLabelMap, "%accumulator", inferredType, true);
                } else {
                    structType = inferredType;
                    compilePointerComputation(children.get(0), localCode, localLabelMap, "%accumulator");
                }
                
                if(structType instanceof StructureType st) {
                    String mName = children.get(2).getValue();
                    
                    // good to go, add member offset
                    if(st.getMemberNames().contains(mName)) {
                        int offset = st.getMemberOffset(mName);
                        
                        if(offset != 0) {
                            localCode.add(new Instruction(
                                Opcode.ADD_RIM,
                                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(offset & 0xFFFF)),
                                false
                            ));
                            
                            if(offset < 0x1_0000) {
                                localCode.add(new Instruction(
                                    Opcode.ICC_RIM,
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.D),
                                    true, false
                                ));
                            } else {
                                // this is unlikely but I'm thorough
                                localCode.add(new Instruction(
                                    Opcode.ADC_RIM,
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.D),
                                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant((offset >> 16) & 0xFFFF)),
                                    false
                                ));
                            }
                        }
                        
                        // move to target
                        if(!target.equals("%accumulator")) {
                            this.generateVariableMove("%accumulator", target, 4, localCode);
                        }
                        
                        return new PointerType(st.getMemberType(mName));
                    } else {
                        LOG.severe("Unknown member " + mName + " for structure type " + st);
                        errorsEncountered = true;
                    }
                } else {
                    // we need a structure
                    LOG.severe("Could not infer structure type for structure access: " + structType + " from " + detailed(children.get(0)));
                    errorsEncountered = true;
                }
            } else {
                // Array access
                // compute pointer to first part, then compute the offset and add it to the pointer.
                
                // handle constants nicely
                boolean indexConstant = isConstant(children.get(2)),
                        toJI = target.equals("%ji"), // compute reference to JI, compute offset to acc, add to JI
                        toAcc = target.equals("%accumulator"); // compute reference to acc, save it, compute offset, add saved. also applies to other targets
                
                NSTLType referenceType = inferReferenceType(children.get(0)),
                         elementType;
                //NSTLType pointedType = ((PointerType) compilePointerComputation(children.get(0), localCode, toJI ? "%ji" : "%accumulator")).getPointedType(),
                
                if(referenceType instanceof ArrayType at) {
                    elementType = at.getMemberType();
                    
                    // if the indexed reference is an array, the final pointer is (pointer to indexed reference) + offset
                    compilePointerComputation(children.get(0), localCode, localLabelMap, toJI ? "%ji" : "%accumulator");
                } else if(referenceType instanceof PointerType pt) {
                    elementType = pt.getPointedType();
                    
                    // if the indexed reference is a typed pointer, the final pointer is (value of indexed reference) + offset
                    compileValueComputation(children.get(0), localCode, localLabelMap, toJI ? "%ji" : "%accumulator", pt, false);
                } else {
                    elementType = null;
                    LOG.severe("Indexed reference is not an array or typed pointer: " + referenceType);
                    errorsEncountered = true;
                }
                
                // compute offset
                if(indexConstant) {
                    // constant = nice
                    TypedValue constantIndex = computeConstant(children.get(2));
                    
                    if(constantIndex instanceof TypedRaw tr) {
                        int offset = (int) tr.getValue().value() * elementType.getSize();
                        
                        localCode.add(new Instruction(
                            Opcode.LEA_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, toJI ? Register.JI : Register.DA),
                            new ResolvableLocationDescriptor(LocationType.MEMORY, 0, new ResolvableMemory(toJI ? Register.JI : Register.DA, Register.NONE, 0, offset)),
                            true
                        ));
                    } else {
                        LOG.severe("Indexes must be integers, found " + constantIndex);
                        errorsEncountered = true;
                    }
                    
                    if(!(toAcc || toJI)) {
                        generateVariableMove("%accumulator", target, 4, localCode);
                    }
                } else {
                    // not constant
                    // save accumulator if needed
                    if(!toJI) {
                        localCode.add(new Instruction(Opcode.PUSH_D, true));
                        localCode.add(new Instruction(Opcode.PUSH_A, true));
                    }
                    
                    // compute offset
                    NSTLType offsetType = compileValueComputation(children.get(2), localCode, localLabelMap, "%accumulator", RawType.NONE, true);
                    
                    // multiply & extend to ptr
                    if(offsetType instanceof RawType rt) {
                        //LOG.finest("" + rt);
                        if(rt.getSize() == 1) {
                            if(elementType.getSize() == 1) {
                                localCode.add(new Instruction(
                                    rt.isSigned() ? Opcode.MOVS_RIM : Opcode.MOVZ_RIM,
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                                    true
                                ));
                            } else {
                                localCode.add(new Instruction(
                                    rt.isSigned() ? Opcode.MULSH_RIM : Opcode.MULH_RIM,
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(elementType.getSize())),
                                    true
                                ));
                            }
                            
                            localCode.add(new Instruction(
                                rt.isSigned() ? Opcode.MOVS_RIM : Opcode.MOVZ_RIM,
                                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                true
                            ));
                        } else if(rt.getSize() == 2) {
                            if(elementType.getSize() == 1) {
                                localCode.add(new Instruction(
                                    rt.isSigned() ? Opcode.MOVS_RIM : Opcode.MOVZ_RIM,
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                    true
                                ));
                            } else {
                                localCode.add(new Instruction(
                                    rt.isSigned() ? Opcode.MULSH_RIM : Opcode.MULH_RIM,
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(elementType.getSize())),
                                    true
                                ));
                            }
                        } else {
                            generateWideConstantMultiply(Register.DA, elementType.getSize(), false, localCode);
                        }
                    } else {
                        LOG.severe("Indexes must be raw types. Found: " + offsetType);
                        errorsEncountered = true;
                    }
                    
                    // compute final pointer
                    if(toJI) {
                        // direct
                        localCode.add(new Instruction(
                            Opcode.ADD_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.I),
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.ADC_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.J),
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.D),
                            true
                        ));
                    } else {
                        // compute and move
                        localCode.add(new Instruction(
                            Opcode.ADD_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                            new ResolvableLocationDescriptor(LocationType.MEMORY, 2, new ResolvableMemory(Register.SP, Register.NONE, 0, 0)),
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.ADC_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.D),
                            new ResolvableLocationDescriptor(LocationType.MEMORY, 2, new ResolvableMemory(Register.SP, Register.NONE, 0, 2)),
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.ADDW_SP_I8,
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(4)),
                            false, false
                        ));
                        
                        if(!toAcc) {
                            this.generateVariableMove("%accumulator", target, 4, localCode);
                        }
                    }
                }
                
                return new PointerType(elementType);
            }
        } else {
            if(children.size() > 0 && children.get(0).getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_FUNCTION_CALL) {
                // function_call
                // is this even valid?
                LOG.severe("Found function call as pointer target");
                errorsEncountered = true;
            } else {
                // NAME
                String name = children.size() > 0 ? children.get(0).getValue() : node.getValue();
                
                if(contextStack.hasSymbol(name)) {
                    ContextSymbol pointedSymbol = contextStack.getSymbol(name);
                    
                    // string constants can be pointed to
                    if(pointedSymbol.getIsConstant()) {
                        if(pointedSymbol.getType() instanceof StringType st) {
                            if(targetIsRegister) {
                                // register = put directly
                                localCode.add(new Instruction(
                                    Opcode.MOVW_RIM,
                                    target.equals("%accumulator") ? new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA) : targetSymbol.getVariableDescriptor(),
                                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 4, new ResolvableConstant(name)),
                                    true
                                ));
                            } else if(target.equals("%stack")) {
                                // we can push constants straight to the stack
                                localCode.add(new Instruction(
                                    Opcode.PUSHW_RIM,
                                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 4, new ResolvableConstant(name)),
                                    false, true
                                ));
                            } else {
                                // otherwise move to accumulator then to destination
                                localCode.add(new Instruction(
                                    Opcode.MOVW_RIM,
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 4, new ResolvableConstant(name)),
                                    true
                                ));
                                
                                localCode.add(new Instruction(
                                    Opcode.MOVW_RIM,
                                    targetSymbol.getVariableDescriptor(),
                                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                                    true
                                ));
                            }
                            
                            return new PointerType(pointedSymbol.getType());
                        } else {
                            // any other constants are invalid
                            LOG.severe("Cannot create a pointer to constant " + name);
                            errorsEncountered = true;
                        }
                    } else if(pointedSymbol.getVariableDescriptor().getType() == LocationType.REGISTER) {
                        // TODO: make this possible
                        LOG.severe("Cannot create pointer to local register value " + name + ". Please add this feature.");
                        errorsEncountered = true;
                    }
                    
                    // locals & globals
                    if(targetIsRegister) {
                        // register = put directly
                        localCode.add(new Instruction(
                            Opcode.LEA_RIM,
                            target.equals("%accumulator") ? new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA) : targetSymbol.getVariableDescriptor(),
                            pointedSymbol.getVariableDescriptor(),
                            true
                        ));
                    } else {
                        // memory = via accumulator
                        localCode.add(new Instruction(
                            Opcode.LEA_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                            pointedSymbol.getVariableDescriptor(),
                            true
                        ));
                        
                        // stack is also handled here
                        if(target.equals("%stack")) {
                            localCode.add(new Instruction(Opcode.PUSH_D, true));
                            localCode.add(new Instruction(Opcode.PUSH_A, true));
                        } else {
                            localCode.add(new Instruction(
                                Opcode.MOVW_RIM,
                                targetSymbol.getVariableDescriptor(),
                                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                                true
                            ));
                        }
                    }
                    
                    return new PointerType(pointedSymbol.getType());
                } else if(functionDefinitions.containsKey(name)) {
                    // function pointers are a thing
                    if(targetIsRegister) {
                        localCode.add(new Instruction(
                            Opcode.MOVW_RIM,
                            target.equals("%accumulator") ? new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA) : targetSymbol.getVariableDescriptor(),
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 4, new ResolvableConstant(name)),
                            true
                        ));
                    } else if(target.equals("%stack")) {
                        // we can push constants straight to the stack
                        localCode.add(new Instruction(
                            Opcode.PUSHW_RIM,
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 4, new ResolvableConstant(name)),
                            false, true
                        ));
                    } else {
                        // move to accumulator then to destination
                        localCode.add(new Instruction(
                            Opcode.MOVW_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 4, new ResolvableConstant(name)),
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.MOVW_RIM,
                            targetSymbol.getVariableDescriptor(),
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                            true
                        ));
                    }
                    
                    return new PointerType(RawType.NONE);
                } else if(name.startsWith("_")) {
                    name = name.substring(1);
                    
                    // library members can be pointed to
                    if(targetIsRegister) {
                        localCode.add(new Instruction(
                            Opcode.MOVW_RIM,
                            target.equals("%accumulator") ? new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA) : targetSymbol.getVariableDescriptor(),
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 4, new ResolvableConstant(name)),
                            true
                        ));
                    } else if(target.equals("%stack")) {
                        // we can push constants straight to the stack
                        localCode.add(new Instruction(
                            Opcode.PUSHW_RIM,
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 4, new ResolvableConstant(name)),
                            false, true
                        ));
                    } else {
                        // move to accumulator then to destination
                        localCode.add(new Instruction(
                            Opcode.MOVW_RIM,
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 4, new ResolvableConstant(name)),
                            true
                        ));
                        
                        localCode.add(new Instruction(
                            Opcode.MOVW_RIM,
                            targetSymbol.getVariableDescriptor(),
                            new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                            true
                        ));
                    }
                    
                    return new PointerType(RawType.NONE);
                } else {
                    LOG.severe("Name " + name + " is not a variable or function; cannot create pointer");
                    errorsEncountered = true;
                }
            }
        }
        
        return RawType.NONE; 
    }
    
    /**
     * Multiplies the given register pair by a constant
     * 
     * @param high
     * @param low
     * @param value
     */
    private void generateWideConstantMultiply(Register reg, int value, boolean signed, List<Component> localCode) {
        LOG.finest("Generating wide constant " + (signed ? "signed" : "unsigned") + " multiply: " + reg + " *= " + value);
        
        if(value == 1) return;
        
        Register high = switch(reg) {
            case DA -> Register.D;
            case BC -> Register.B;
            case JI -> Register.J;
            case LK -> Register.L;
            default -> null;
        };
        
        Register low = switch(reg) {
            case DA -> Register.A;
            case BC -> Register.C;
            case JI -> Register.I;
            case LK -> Register.K;
            default -> null;
        };
        
        // shifts are faster, but also more compact as multiply by a 16 bit constant is 13 bytes
        if(value == 2) {
            // single shift (6 bytes)
            localCode.add(new Instruction(
                Opcode.SHL_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, low),
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(1)),
                false
            ));
            
            localCode.add(new Instruction(
                Opcode.RCL_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, high),
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(1)),
                false
            ));
        } else if(value == 4) {
            // double shift (12 bytes)
            localCode.add(new Instruction(
                Opcode.SHL_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, low),
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(1)),
                false
            ));
            
            localCode.add(new Instruction(
                Opcode.RCL_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, high),
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(1)),
                false
            ));
            
            localCode.add(new Instruction(
                Opcode.SHL_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, low),
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(1)),
                false
            ));
            
            localCode.add(new Instruction(
                Opcode.RCL_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, high),
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(1)),
                false
            ));
        } else if(signed ? ((value & 0xFFFF_8000) == 0xFFFF_8000 || (value & 0xFFFF_8000) == 0) : ((value & 0xFFFF_0000) == 0)) {
            // 16 bit: 
            // low = low * constant
            // high = upper(low * constant) + high * constant
            // MUL  high, value
            // PUSH high
            // MULH high:low, value
            // ADD  high, [SP]
            // ADD  SP, 2
            localCode.add(new Instruction(
                Opcode.MUL_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, high),
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(value & 0xFFFF)),
                false
            ));
            
            localCode.add(new Instruction(
                Opcode.PUSH_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, high),
                false, true
            ));
            
            localCode.add(new Instruction(
                signed ? Opcode.MULSH_RIM : Opcode.MULH_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, reg),
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(value & 0xFFFF)),
                false
            ));
            
            localCode.add(new Instruction(
                Opcode.ADD_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, high),
                new ResolvableLocationDescriptor(LocationType.MEMORY, 2, new ResolvableMemory(Register.SP, Register.NONE, 0, 0)),
                false
            ));
            
            localCode.add(new Instruction(
                Opcode.ADDW_SP_I8,
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(2)),
                false, false
            ));
        } else {
            // 32 bit:
            // low = low * constant_low
            // high = upper(low * constant_low) + lower(low * constant_high) + lower(high * constant_low)
            // MUL  high, value_low         ; [SP + 2] = high * constant_low
            // PUSH high
            // PUSH low                     ; [SP] = low * constant_high
            // MUL  [SP], value_high
            // MULH high:low, value_low     ; low = lower(low * constant_low), high = upper(low * constant_low)
            // ADD  high, [SP]              ; high = ... + lower(low * constant_high)
            // ADD  high, [SP + 2]          ; high = ... + lower(high * constant_low)
            // ADD  SP, 4
            localCode.add(new Instruction(
                Opcode.MUL_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, high),
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(value & 0xFFFF)),
                false
            ));
            
            localCode.add(new Instruction(
                Opcode.PUSH_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, high),
                false, true
            ));
            
            localCode.add(new Instruction(
                Opcode.PUSH_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, low),
                false, true
            ));
            
            localCode.add(new Instruction(
                Opcode.MUL_RIM,
                new ResolvableLocationDescriptor(LocationType.MEMORY, 2, new ResolvableMemory(Register.SP, Register.NONE, 0, 0)),
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant((value >> 16) & 0xFFFF)),
                false
            ));
            
            localCode.add(new Instruction(
                signed ? Opcode.MULSH_RIM : Opcode.MULH_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, reg),
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(value & 0xFFFF)),
                false
            ));
            
            localCode.add(new Instruction(
                Opcode.ADD_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, high),
                new ResolvableLocationDescriptor(LocationType.MEMORY, 2, new ResolvableMemory(Register.SP, Register.NONE, 0, 0)),
                false
            ));
            
            localCode.add(new Instruction(
                Opcode.ADD_RIM,
                new ResolvableLocationDescriptor(LocationType.REGISTER, high),
                new ResolvableLocationDescriptor(LocationType.MEMORY, 2, new ResolvableMemory(Register.SP, Register.NONE, 0, 2)),
                false
            ));
            
            localCode.add(new Instruction(
                Opcode.ADDW_SP_I8,
                new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(4)),
                false, false
            ));
        }
    }
    
    /**
     * Infer the type of a node
     * 
     * @param node
     * @return
     */
    private NSTLType inferReferenceType(ASTNode node) {
        // handle names so we don't need to check this in multiple places
        if(node.getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_NAME) {
            String n = node.getValue();
            
            if(contextStack.hasSymbol(n)) {
                return contextStack.getSymbol(n).getType();
            } else {
                LOG.severe("Unknown symbol: " + n);
                errorsEncountered = true;
                return RawType.NONE;
            }
        }
        
        List<ASTNode> children = node.getChildren();
        
        if(node.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_REFERENCE) {
            if(children.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_TO) {
                return new PointerType(inferReferenceType(children.get(1)));
            } else if(children.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_AT) {
                return ((PointerType) inferReferenceType(children.get(1))).getPointedType();
            } else {
                return constructType(children.get(0));
            }
        } else if(node.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_SUBREFERENCE) {
            if(children.size() == 1) {
                return inferReferenceType(children.get(0));
            } else if(children.get(1).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_DOT) {
                // <subref>.name
                String memberName = children.get(2).getValue();
                NSTLType t = inferReferenceType(children.get(0)),
                         t2 = t;
                
                // allow access to structure pointers
                if(t instanceof PointerType pt) {
                    t = pt.getPointedType();
                }
                
                if(t instanceof StructureType st) {
                    if(st.getMemberNames().contains(memberName)) {
                        return st.getMemberType(memberName);
                    } else {
                        LOG.severe("Structure type " + st + " does not have member " + memberName + " for reference " + detailed(children.get(0)));
                        errorsEncountered = true;
                        return RawType.NONE;
                    }
                } else {
                    LOG.severe("Reference for structure access is not a structure or structure pointer: " + detailed(children.get(0)) + " inferred as " + t2);
                    errorsEncountered = true;
                    return RawType.NONE;
                }
            } else {
                // index
                NSTLType t = inferReferenceType(children.get(0));
                
                if(t instanceof ArrayType at) {
                    return at.getMemberType();
                } else if(t instanceof PointerType pt) {
                    return pt.getPointedType();
                } else {
                    LOG.severe("Indexed reference is not pointer or array: " + detailed(children.get(0)));
                    errorsEncountered = true;
                    return RawType.NONE;
                }
            }
        } else if(node.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_FUNCTION_CALL) {
            // idk
            // TODO
            LOG.severe("UNIMPLEMENTED: INFER TYPE FROM FUNCTION CALL");
            return RawType.NONE;
        } else {
            LOG.severe("Not a reference: " + detailed(node));
            errorsEncountered = true;
            return RawType.NONE;
        }
    }
    
    /**
     * Compiles a function call
     * 
     * @param node
     */
    private NSTLType compileFunctionCall(ASTNode node, List<Component> localCode, Map<String, Integer> localLabelMap) {
        LOG.finest("Compiling function call");
        
        List<ASTNode> children = node.getChildren();
        
        // if we have a function reference, the header determines the expected arguments
        // if we have an indirect call, the argument types must be inferable. inferred types (i.e. expressions with a typed value somewhere) should emit warnings
        int totalArgumentsSize = 0;
        
        // do we have a name or an indirect
        boolean isName = false,
                isDirect = false,
                isLibrary = false;
        
        ASTNode callTargetNode = children.get(0),
                argumentList = children.get(1); // list or KW_NONE
        
        String targetName = "";
        
        FunctionHeader header = null;
        
        switch(callTargetNode.getSymbol().getID()) {
            case NstlgrammarParser.ID.VARIABLE_SUBREFERENCE:
                // single value = direct, multiple = indirect (defer to computation)
                List<ASTNode> subreferenceNodes = callTargetNode.getChildren();
                
                if(subreferenceNodes.size() == 1) {
                    // just a name, direct reference if its a defined function
                    targetName = subreferenceNodes.get(0).getValue();
                    isName = true;
                    
                    if(targetName.startsWith("_")) {
                        isLibrary = true;
                        targetName = renameLibrary(targetName);
                    }
                    
                    // check specials
                    if(specialFunctionNames.contains(targetName)) {
                        return compileSpecialFunction(targetName, argumentList.getChildren(), localCode, localLabelMap);
                    }
                    
                    if(functionDefinitions.containsKey(targetName)) {
                        isDirect = true;
                        header = functionDefinitions.get(targetName);
                    } else if(!contextStack.hasSymbol(targetName)) {
                        // unknown names = error
                        LOG.severe("Unknown symbol " + targetName + " as function call");
                        errorsEncountered = true;
                        throw new IllegalArgumentException();
                    }
                }
                break;
                
            case NstlgrammarParser.ID.VARIABLE_REFERENCE:
                // pointer reference or dereference
                // stuff is already set correctly
                break;
                
            default:
                LOG.severe("Unexpected node as function reference: " + callTargetNode);
                errorsEncountered = true;
                throw new IllegalArgumentException();
        }
        
        // push B and C if needed
        AllocatedContextMarker acm = (AllocatedContextMarker) contextStack.getLocalMarker();
        
        if(!acm.registerAvailable(Register.B)) {
            localCode.add(new Instruction(Opcode.PUSH_B, true));
        }
        
        if(!acm.registerAvailable(Register.C)) {
            localCode.add(new Instruction(Opcode.PUSH_C, true));
        }
        
        // push arguments
        List<ASTNode> argumentNodes = argumentList.getChildren();
        
        // verify we have the expected number of arguments
        if(header != null && header.getArgumentNames().size() != argumentNodes.size()) {
            LOG.severe("Incorrect number of arguments for " + header + ": " + argumentNodes.size());
            errorsEncountered = true;
        }
        
        for(int i = argumentNodes.size() - 1; i >= 0; i--) {
            ASTNode argNode = argumentNodes.get(i);
            NSTLType argType;
            
            if(header == null) {
                // infer
                argType = RawType.NONE;
            } else {
                argType = header.getArgumentTypes().get(i);
            }
            
            argType = compileValueComputation(argNode, localCode, localLabelMap, "%stack", argType, false);
            totalArgumentsSize += argType.getSize();
        }
        
        // call function
        // direct call to local function = CALL <name>
        // direct call to library function = CALLA <name>
        // indirect call = CALLA <value>
        if(isName && isDirect) {
            // CALL or CALLA
            if(isLibrary) {
                localCode.add(new Instruction(
                    Opcode.CALLA_I32,
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 4, new ResolvableConstant(targetName)),
                    false, true
                ));
                
                LOG.finer("Emitted direct call to library function " + targetName);
            } else {
                localCode.add(new Instruction(
                    Opcode.CALL_I32,
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(targetName)),
                    false, false
                ));
                
                LOG.finer("Emitted direct function call to " + targetName);
            }
        } else {
            if(isLibrary) {
                // this is invalid
                LOG.severe("Unexpected library value as function reference (may be missing definition): " + targetName);
                errorsEncountered = true;
            } else if(isName) {
                // name = call the value
                ContextSymbol cs = contextStack.getSymbol(targetName);
                
                // Constant or variable
                if(cs.getIsConstant()) {
                    if(cs.getType() instanceof RawType rt) {
                        // constant raw = just call it
                        localCode.add(new Instruction(
                            Opcode.CALLA_I32,
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 4, ((TypedRaw) cs.getConstantValue()).getValue()),
                            false, true
                        ));
                        
                        LOG.finer("Emitted indirect function call to constant value of " + targetName);
                    } else if(cs.getType().getSize() == 4) {
                        // 4 bytes = call value
                        long v = cs.getConstantValue().toLong();
                        
                        localCode.add(new Instruction(
                            Opcode.CALLA_I32,
                            new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 4, new ResolvableConstant(v)),
                            false, true
                        ));
                        
                        LOG.finer("Emitted indirect function call to 4-byte constant value of " + targetName);
                    } else {
                        // not 4 bytes = error
                        LOG.severe("Cannot call a complex type other than 4 bytes");
                        errorsEncountered = true;
                    }
                } else {
                    // rawtype = promote if needed & call
                    if(cs.getType() instanceof RawType rt) {
                        if(rt.getSize() == 4) {
                            localCode.add(new Instruction(
                                Opcode.CALLA_RIM32,
                                cs.getVariableDescriptor(),
                                false, true
                            ));
                        } else {
                            compilePointerPromotion(cs, localCode); // puts the value in DA as a u32
                            
                            localCode.add(new Instruction(
                                Opcode.CALLA_RIM32,
                                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                                false, true
                            ));
                        }
                        
                        LOG.finer("Emitted indirect function call to value of " + targetName);
                    } else if(cs.getType().getSize() == 4) {
                        // 4 bytes = call value
                        localCode.add(new Instruction(
                            Opcode.CALLA_RIM32,
                            cs.getVariableDescriptor(),
                            false, true
                        ));
                        
                        LOG.finer("Emitted indirect function call to 4-byte value of " + targetName);
                    } else {
                        // not 4 bytes = error
                        LOG.severe("Cannot call a complex type other than 4 bytes");
                        errorsEncountered = true;
                    }
                }
            } else {
                // not a direct reference - compute it and call that
                compileValueComputation(callTargetNode, localCode, localLabelMap, "%accumulator", RawType.PTR, false);
                
                localCode.add(new Instruction(
                    Opcode.CALLA_RIM32,
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                    false, true
                ));
                
                LOG.finer("Emitted computed indirect function call");
            }
        }
        
        // deallocate arguments
        if(totalArgumentsSize > 0) {
            // ADD_SP_I8 or LEA
            if(totalArgumentsSize < 0x80) {
                localCode.add(new Instruction(
                    Opcode.ADDW_SP_I8,
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 1, new ResolvableConstant(totalArgumentsSize)),
                    false, true
                ));
            } else {
                localCode.add(new Instruction(
                    Opcode.LEA_RIM,
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.SP),
                    new ResolvableLocationDescriptor(LocationType.MEMORY, 0, new ResolvableMemory(Register.SP, Register.NONE, 0, totalArgumentsSize)),
                    false
                ));
            }
        }
        
        // pop B and C if needed
        if(!acm.registerAvailable(Register.C)) {
            localCode.add(new Instruction(Opcode.POP_C, true));
        }
        
        if(!acm.registerAvailable(Register.B)) {
            localCode.add(new Instruction(Opcode.POP_B, true));
        }
        
        return (header == null) ? RawType.NONE : header.getReturnType();
    }
    
    /**
     * Compile a special function, such as type casts
     * 
     * @param name
     * @param arguments
     * @param localCode
     */
    private NSTLType compileSpecialFunction(String name, List<ASTNode> arguments, List<Component> localCode, Map<String, Integer> localLabelMap) {
        LOG.finest("Compiling special function " + name);
        
        // TODO
        /*
        specialFunctionNames.add("padd4");
        specialFunctionNames.add("padd8");
        specialFunctionNames.add("psub4");
        specialFunctionNames.add("psub8");
        specialFunctionNames.add("pinc4");
        specialFunctionNames.add("pinc8");
        specialFunctionNames.add("pdec4");
        specialFunctionNames.add("pdec8");
        
        specialFunctionNames.add("pmul4");
        specialFunctionNames.add("pmul8");
        specialFunctionNames.add("pmulh4");
        specialFunctionNames.add("pmulh8");
        specialFunctionNames.add("pmulsh4");
        specialFunctionNames.add("pmulsh8");
        
        specialFunctionNames.add("pdiv4");
        specialFunctionNames.add("pdiv8");
        specialFunctionNames.add("pdivm4");
        specialFunctionNames.add("pdivm8");
        specialFunctionNames.add("pdivs4");
        specialFunctionNames.add("pdivs8");
        specialFunctionNames.add("pdivms4");
        specialFunctionNames.add("pdivms8"); 
         */
        
        NSTLType initialType, initialType2;
        
        boolean signed = switch(name) {
            case "sxi8", "zxi8", "sxi16", "zxi16", "sxi32", "zxi32", "mulsh" -> true;
            default -> false;
        };
        
        boolean signExtend = switch(name) {
            case "sxu8", "sxi8", "sxu16", "sxi16", "sxu32", "sxi32", "sxptr" -> true;
            default -> false;
        };
        
        switch(name) {
            case "std.halt":
                localCode.add(new Instruction(Opcode.HLT, true));
                break;
            
            case "mulh":
            case "mulsh":
                // compute to acc/[sp]
                initialType2 = compileValueComputation(arguments.get(1), localCode, localLabelMap, "%accumulator", RawType.NONE, true);
                
                if(!(initialType2 instanceof RawType) || initialType2.getSize() > 2) {
                    LOG.severe("Invalid type for high multiply; only U/I 8/16 are allowed: " + initialType2);
                    errorsEncountered = true;
                }
                
                localCode.add(new Instruction(Opcode.PUSH_A, true));    
                
                initialType = compileValueComputation(arguments.get(0), localCode, localLabelMap, "%accumulator", RawType.NONE, true);
                
                if(!(initialType instanceof RawType) || initialType.getSize() > 2) {
                    LOG.severe("Invalid type for high multiply; only U/I 8/16 are allowed: " + initialType);
                    errorsEncountered = true;
                } else if(!initialType.equals(initialType2)) {
                    LOG.severe("Types for high multiply must match: Found " + initialType + " and " + initialType2);
                    errorsEncountered = true;
                }
                
                // multiply
                if(initialType.getSize() == 1) {
                    localCode.add(new Instruction(
                        signed ? Opcode.MULSH_RIM : Opcode.MULH_RIM,
                        new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                        new ResolvableLocationDescriptor(LocationType.MEMORY, 1, new ResolvableMemory(Register.SP, Register.NONE, 0, 0)),
                        true
                    ));
                } else {
                    localCode.add(new Instruction(
                        signed ? Opcode.MULSH_RIM : Opcode.MULH_RIM,
                        new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                        new ResolvableLocationDescriptor(LocationType.MEMORY, 2, new ResolvableMemory(Register.SP, Register.NONE, 0, 0)),
                        true
                    ));
                }
                
                // clean stack
                localCode.add(new Instruction(
                    Opcode.ADDW_SP_I8,
                    new ResolvableLocationDescriptor(LocationType.IMMEDIATE, -1, new ResolvableConstant(2)),
                    false, true
                ));
                
                // return based on left side type
                RawType rt = (RawType) initialType;
                if(rt.getSize() == 1) {
                    return rt.isSigned() ? RawType.I16 : RawType.U16;
                } else {
                    return rt.isSigned() ? RawType.I32 : RawType.U32;
                }
            
            // sign & zero extensions
            case "sxu8":
            case "zxu8":
            case "sxi8":
            case "zxi8":
            case "sxu16":
            case "zxu16":
            case "sxi16":
            case "zxi16":
            case "sxu32":
            case "zxu32":
            case "sxi32":
            case "zxi32":
            case "sxptr":
            case "zxptr":
                initialType = compileValueComputation(arguments.get(0), localCode, localLabelMap, "%accumulator", RawType.NONE, true);
                
                if(!(initialType instanceof RawType)) {
                    LOG.severe("Invalid type for sign/zero extend; only raw types are allowed: " + initialType);
                    errorsEncountered = true;
                }
                
                switch(name) {
                    case "sxu8":
                    case "zxu8":
                    case "sxi8":
                    case "zxi8":
                        return signed ? RawType.I8 : RawType.U8;
                    
                    case "sxu16":
                    case "zxu16":
                    case "sxi16":
                    case "zxi16":
                        if(initialType.getSize() == 1) {
                            localCode.add(new Instruction(
                                signExtend ? Opcode.MOVS_RIM : Opcode.MOVZ_RIM,
                                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                                true
                            ));
                        }
                        
                        return signed ? RawType.I16 : RawType.U16;
                        
                    case "sxu32":
                    case "zxu32":
                    case "sxi32":
                    case "zxi32":
                    case "sxptr":
                    case "zxptr":
                        if(initialType.getSize() == 1) {
                            localCode.add(new Instruction(
                                signExtend ? Opcode.MOVS_RIM : Opcode.MOVZ_RIM,
                                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.AL),
                                true
                            ));
                        }
                        
                        if(initialType.getSize() < 4) {
                            localCode.add(new Instruction(
                                signExtend ? Opcode.MOVS_RIM : Opcode.MOVZ_RIM,
                                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                                new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                                true
                            ));
                        }
                        
                        return name.endsWith("ptr") ? RawType.PTR : (signed ? RawType.I32 : RawType.U32);
                }
                break;
        }
        
        return RawType.NONE;
    }
    
    /**
     * Compile getting a 32 bit value from a raw variable symbol
     * 
     * @param cs
     */
    private void compilePointerPromotion(ContextSymbol cs, List<Component> localCode) {
        LOG.finest("Promoting " + cs + " to ptr");
        
        if(cs.getType() instanceof RawType rt) {
            if(rt.getSize() == 1) {
                // 1 -> 4 = double MOVS/MOVZ
                localCode.add(new Instruction(
                    rt.isSigned() ? Opcode.MOVS_RIM : Opcode.MOVZ_RIM,
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                    cs.getVariableDescriptor(),
                    true
                ));
                
                localCode.add(new Instruction(
                    rt.isSigned() ? Opcode.MOVS_RIM : Opcode.MOVZ_RIM,
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.A),
                    true
                ));
            } else {
                // 2 -> 4 = single MOVS/MOVZ
                localCode.add(new Instruction(
                    rt.isSigned() ? Opcode.MOVS_RIM : Opcode.MOVZ_RIM,
                    new ResolvableLocationDescriptor(LocationType.REGISTER, Register.DA),
                    cs.getVariableDescriptor(),
                    true
                ));
            }
        } else {
            // uuuuuh error
            // TODO
            LOG.severe("Cannot treat " + cs + " as a ptr");
            errorsEncountered = true;
        }
    }
    
    /**
     * Push a local variable
     */
    private void generatePushLocalVariable(String name, List<Component> localCode) {
        ContextSymbol cs = contextStack.getSymbol(name);
        
        LOG.finest("Pushing local variable " + cs);
        
        if(cs.getIsConstant()) {
            // Constant = push the value
            byte[] value = cs.getConstantValue().getBytes();
            
            // high bytes to low bytes, as many at a time as we can
            for(int i = value.length; i > 0;) {
                long val;
                
                if(i >= 4) {
                    // PUSH I32
                    i -= 4;
                    
                    val = cs.getConstantValue().toLong();
                    
                    localCode.add(new Instruction(
                        Opcode.PUSHW_RIM,
                        new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 4, new ResolvableConstant(val)),
                        false, true
                    ));
                } else if(i >= 2) {
                    // PUSH_RIM word
                    i -= 2;
                    
                    val = ((value[i + 1] & 0xFF) << 8) | (value[i] & 0xFF);
                    
                    localCode.add(new Instruction(
                        Opcode.PUSH_RIM,
                        new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 2, new ResolvableConstant(val)),
                        false, true
                    ));
                } else {
                    // PUSH_RIM byte
                    i -= 1;
                    
                    val = value[i] & 0xFF;
                    
                    localCode.add(new Instruction(
                        Opcode.PUSH_RIM,
                        new ResolvableLocationDescriptor(LocationType.IMMEDIATE, 1, new ResolvableConstant(val)),
                        false, true
                    ));
                }
            }
        } else {
            ResolvableLocationDescriptor rld = cs.getVariableDescriptor();
            
            // if the location is a register or byte/word we can push directly
            int size = rld.getSize();
            if(cs.getType().getSize() == size && (size == 1 || size == 2)) {
                localCode.add(new Instruction(
                    Opcode.PUSH_RIM,
                    rld,
                    false, false
                ));
            } else if(cs.getType().getSize() == size && size == 4 && rld.getType() == LocationType.REGISTER) {
                // push pair
                localCode.add(new Instruction(
                    switch(rld.getRegister()) {
                        case BC -> Opcode.PUSH_B;
                        case JI -> Opcode.PUSH_J;
                        case LK -> Opcode.PUSH_L;
                        default -> throw new IllegalStateException("Invalid local variable dword register " + rld);
                    },
                    true
                ));
                
                localCode.add(new Instruction(
                    switch(rld.getRegister()) {
                        case BC -> Opcode.PUSH_C;
                        case JI -> Opcode.PUSH_I;
                        case LK -> Opcode.PUSH_K;
                        default -> throw new IllegalStateException("Invalid local variable dword register " + rld);
                    },
                    true
                ));
            } else if(rld.getType() == LocationType.MEMORY) {
                // extract location
                long offset = rld.getMemory().getOffset().value();
                
                // push
                for(int i = size; i > 0;) {
                    if(i >= 2) {
                        i -= 2;
                        
                        localCode.add(new Instruction(
                            Opcode.PUSH_RIM,
                            new ResolvableLocationDescriptor(LocationType.MEMORY, 2, new ResolvableMemory(Register.BP, Register.NONE, 0, (int) (offset + i))),
                            false, true
                        ));
                    } else {
                        i -= 1;
                        
                        localCode.add(new Instruction(
                            Opcode.PUSH_RIM,
                            new ResolvableLocationDescriptor(LocationType.MEMORY, 1, new ResolvableMemory(Register.BP, Register.NONE, 0, (int) (offset + i))),
                            false, true
                        ));
                    }
                }
            } else {
                throw new IllegalArgumentException("Invalid local variable register: " + cs);
            }
        }
    }
    
    /**
     * Generates code to move a long constant value (5+ bytes) to the given symbol 
     * 
     * @param symbol
     * @param constantValue
     * @param localCode
     */
    private void generateLongConstantMove(ContextSymbol symbol, TypedValue constantValue, List<Component> localCode, Map<String, Integer> localLabelMap) {
        LOG.finest("Generating long constant move " + constantValue + " to " + symbol);
        
        // TODO
        LOG.severe("UNIMPLEMENTED: LONG CONSTANT MOVE");
        errorsEncountered = true;
    }
    
    /**
     * Create a value
     * 
     * @param node
     */
    private void compileValueCreation(ASTNode node, List<Component> localCode, Map<String, Integer> localLabelMap) {
        List<ASTNode> children = node.getChildren();
        
        String name = children.get(1).getValue();
        checkNameConflicts(name);
        
        LOG.finest("Creating value " + name);
        
        NSTLType type = constructType(children.get(2));
        
        // if it's a constant, determine the value
        if(children.get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_CONSTANT) {
            // normal constant or structure constant
            ASTNode valueNode = children.get(3);
            TypedValue tv;
            
            if(valueNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_CONSTANT_STRUCTURE || valueNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_VARIABLE_STRUCTURE) {
                if(type instanceof StructureType st) {
                    tv = computeConstantStructure(st, valueNode);
                } else {
                    LOG.severe("Not a structure type: " + type);
                    throw new IllegalArgumentException();
                }
            } else {
                tv = computeConstant(valueNode);
            }
            
            if(!tv.convertType(type)) {
                LOG.severe("Mismatched types: " + type + ", " + tv.getType());
            }
            
            // strings need their constants allocated
            if(tv instanceof StringType st) {
                stringConstants.put(name, st.getValue());
            }
            
            LOG.finer("Assigned local constant " + tv.getType().getName() + " " + name + " = " + tv);
            contextStack.pushSymbol(new ContextSymbol(name, tv));
        } else if(contextStack.getContextCounter() == 0) {
            // global variables
            // allocate
            contextStack.pushSymbol(new ContextSymbol(name, type, new ResolvableLocationDescriptor(LocationType.MEMORY, type.getSize(), new ResolvableMemory(Register.NONE, Register.NONE, 0, new ResolvableConstant(name)))));
            
            if(children.size() == 4) {
                // if it's a global variable, only constant assignments are allowed
                // compute (copied from constant case)
                ASTNode valueNode = children.get(3);
                TypedValue tv;
                
                if(valueNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_CONSTANT_STRUCTURE || valueNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_VARIABLE_STRUCTURE) {
                    if(type instanceof StructureType st) {
                        tv = computeConstantStructure(st, valueNode);
                    } else {
                        LOG.severe("Not a structure type: " + type);
                        throw new IllegalArgumentException();
                    }
                } else if(valueNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_VARIABLE_ARRAY) {
                    if(type instanceof ArrayType at) {
                        tv = computeConstantArray(at, valueNode);
                    } else {
                        LOG.severe("Not an array type: " + type);
                        throw new IllegalArgumentException();
                    }
                } else {
                    tv = computeConstant(valueNode);
                }
                
                if(!tv.convertType(type)) {
                    LOG.severe("Mismatched types for global variable " + name + ": expected " + type + ", got " + tv.getType());
                }
                
                // constant as byte array
                List<ResolvableValue> data = new ArrayList<>();
                
                for(byte b : tv.getBytes()) {
                    data.add(new ResolvableConstant(b));
                }
                
                globalComponents.put(name, new InitializedData(data, 1));
            } else {
                // no value, uninitialized data
                globalComponents.put(name, new UninitializedData(1, type.getSize()));
            }
            
            LOG.finer("Allocated global variable " + type + " " + name);
        } else {
            // it's a variable, create value and generate assignment code if needed
            allocateLocalVariable(name, type);
            
            // check for value
            if(children.size() == 4) {
                // generate assignment code
                compileValueComputation(children.get(3), localCode, localLabelMap, name, type, false);
            }
        }
    }
    
    /**
     * Allocates a local variable, returning its symbol
     * 
     * @param name
     * @param type
     * @return
     */
    private ContextSymbol allocateLocalVariable(String name, NSTLType type) {
        AllocatedContextMarker localContext = (AllocatedContextMarker) contextStack.getLocalMarker();
        ContextSymbol cs = null;
        int size = type.getSize();
        
        LOG.finest("Allocating local variable " + type + " " + name);
        
        // if we can put it in a register, try that
        Register nextRegister = localContext.getNextUnallocatedRegister(size);
        
        if((type instanceof RawType || type instanceof PointerType) && nextRegister != Register.NONE) {
            // I, J, K, L use needs to be tracked for entire functions
            switch(nextRegister) {
                case JI:
                    regIUsed = true;
                    regJUsed = true;
                    break;
                    
                case LK:
                    regKUsed = true;
                    regLUsed = true;
                    break;
                    
                case I:
                    regIUsed = true;
                    break;
                
                case J:
                    regJUsed = true;
                    break;
                
                case K:
                    regKUsed = true;
                    break;
                
                case L:
                    regLUsed = true;
                    break;
                    
                default:
            }
            
            // there's a register available
            cs = new ContextSymbol(name, type, new ResolvableLocationDescriptor(LocationType.REGISTER, nextRegister));
            localContext.registerAllocations.put(nextRegister, name);
        } else {
            // there isn't a register available, use stack
            localContext.stackAllocationSize += size;
            int offset = -localContext.stackAllocationSize;
            
            cs = new ContextSymbol(name, type, new ResolvableLocationDescriptor(LocationType.MEMORY, size, new ResolvableMemory(Register.BP, Register.NONE, 0, offset)));
            
            // record maximum allocation
            if(localContext.stackAllocationSize > stackAllocationSize) stackAllocationSize = localContext.stackAllocationSize;
        }
        
        contextStack.pushSymbol(cs);
        
        LOG.finer("Allocated local variable " + cs);
        return cs;
    }
    
    /**
     * Constructs a full NSTLType of a type node
     * 
     * @param node
     * @return
     */
    private NSTLType constructType(ASTNode node) {
        if(node.getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_NONE) return RawType.NONE; 
        
        List<ASTNode> children = node.getChildren();
        
        if(children.size() == 1) {
            // named type
            String name = children.get(0).getValue();
            LOG.finest("Constructing type " + name);
            
            // if it's defined, return it
            if(typeDefinitions.containsKey(name)) {
                return typeDefinitions.get(name).getRealType();
            } else {
                // check string constants and arrays
                if(contextStack.hasSymbol(name)) {
                    ContextSymbol cs = contextStack.getSymbol(name);
                    
                    if(cs.getIsConstant()) {
                        NSTLType t = cs.getConstantValue().getType().getRealType();
                        
                        if(t instanceof StringType st) return t;
                    } else if(cs.getType() instanceof ArrayType at) {
                        return at;
                    }
                }
                
                // if it isn't, error
                LOG.severe("Unknown type: " + name);
                errorsEncountered = true;
            }
        } else {
            NSTLType t = constructType(children.get(0)).getRealType();
            
            // pointer or expression
            if(children.get(1).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_POINTER) {
                LOG.finest("Constructing pointer to " + t);
                return new PointerType(t);
            } else {
                // can't have arrays of strings
                if(t instanceof StringType st) {
                    LOG.severe("Cannot create array of strings");
                    errorsEncountered = true;
                    throw new IllegalArgumentException();
                }
                
                // array size expression
                TypedValue v = computeConstant(children.get(1));
                
                if(v instanceof TypedRaw tr) {
                    // got an integer, good
                    LOG.finest("Constructing array of " + tr.getValue() + " " + t);
                    return new ArrayType(t, (int) tr.getValue().value());
                } else {
                    // not an integer, bad
                    LOG.severe("Invalid array size: " + v);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Handles the members of a structure definition
     * 
     * @param node
     * @return
     */
    private NSTLType compileStructureDefinition(String name, ASTNode node) {
        List<ASTNode> members = node.getChildren();
        List<String> memberNames = new ArrayList<>();
        List<NSTLType> memberTypes = new ArrayList<>();
        
        LOG.finest("Compiling structure definition " + name);
        
        for(ASTNode member : members) {
            List<ASTNode> children = member.getChildren();
            
            String mn = children.get(0).getValue();
            NSTLType type = constructType(children.get(1));
            
            if(type instanceof StringType st) {
                LOG.severe("Structures cannot have string members.");
                errorsEncountered = true;
                throw new IllegalArgumentException();
            }
            
            LOG.finest("Structure member " + mn + " = " + type);
            
            memberNames.add(mn);
            memberTypes.add(type);
        }
        
        return new StructureType(name, memberNames, memberTypes);
    }
    
    /**
     * Checks that types match and returns the expected or inferred type. 
     * 
     * @param expected
     * @param actual
     * @param contextMessage describes error context
     * @return
     */
    private NSTLType checkType(NSTLType expected, NSTLType actual, String contextMessage) {
        boolean infer = expected.equals(RawType.NONE);
        
        if(infer) {
            if(actual.equals(RawType.NONE)) {
                LOG.severe("Could not infer type " + contextMessage);
                errorsEncountered = true;
            }
            
            return actual;
        } else {
            if(!actual.equals(expected)) {
                LOG.severe("Mismatched types: expected " + expected + " got " + actual + " " + contextMessage);
                errorsEncountered = true;
            }
            
            return expected;
        }
    }
    
    /**
     * Computes the value of a constant_expression
     * 
     * @param node
     * @return
     */
    private TypedValue computeConstant(ASTNode node) {
        List<ASTNode> children = node.getChildren();
        
        ASTNode c0, c1;
        String s1;
        ContextSymbol cs1;
        NSTLType t1;
        TypedValue v1, v2;
        
        c0 = children.get(0);
        
        switch(node.getSymbol().getID()) {
            case NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE:
                switch(c0.getSymbol().getID()) {
                    case NstlgrammarParser.ID.VARIABLE_TYPE:
                        // typed integer
                        t1 = constructType(c0);
                        c1 = children.get(1);
                        
                        if(t1 instanceof RawType rt) {
                            return new TypedRaw(new ResolvableConstant(parseInteger(c1.getValue(), rt.getSize(), rt.isSigned())), rt);
                        } else {
                            LOG.severe("Non-integer type " + t1 + " for integer " + c1);
                            errorsEncountered = true;
                        }
                        break;
                    
                    case NstlgrammarLexer.ID.TERMINAL_KW_SIZEOF:
                        // type size
                        t1 = constructType(children.get(1));
                        return new TypedRaw(new ResolvableConstant(t1.getSize()), RawType.NONE);
                    
                    case NstlgrammarLexer.ID.TERMINAL_INTEGER:
                        // integer
                        return new TypedRaw(new ResolvableConstant(parseInteger(c0.getValue(), 0, true)), RawType.NONE);
                    
                    case NstlgrammarLexer.ID.TERMINAL_STRING:
                        // string
                        s1 = c0.getValue();
                        
                        if(s1.startsWith("\"") && s1.endsWith("\"")) s1 = s1.substring(1, s1.length() - 1);
                        
                        return new StringType(s1);
                    
                    case NstlgrammarLexer.ID.TERMINAL_NAME:
                        // reference
                        s1 = c0.getValue();
                        
                        // check definitions
                        if(compilerDefinitions.containsKey(s1)) {
                            return compilerDefinitions.get(s1);
                        } else if((cs1 = contextStack.getSymbol(s1)) != null && cs1.getIsConstant()) {
                            return cs1.getConstantValue();
                        } else {
                            LOG.severe("Unknown or non-constant symbol " + s1 + " in constant expression");
                            errorsEncountered = true;
                        }
                        break;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_AS:
                v1 = computeConstant(c0);
                t1 = constructType(children.get(1));
                
                if(v1.convertType(t1)) {
                    return v1;
                } else {
                    LOG.severe("Could not convert constant value " + v1 + " to type " + t1);
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1.equals(v2)) {
                    return new TypedRaw(new ResolvableConstant(1), RawType.BOOLEAN);
                } else {
                    return new TypedRaw(new ResolvableConstant(0), RawType.BOOLEAN);
                }
            
            case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(!v1.equals(v2)) {
                    return new TypedRaw(new ResolvableConstant(1), RawType.BOOLEAN);
                } else {
                    return new TypedRaw(new ResolvableConstant(0), RawType.BOOLEAN);
                }
            
            case NstlgrammarLexer.ID.TERMINAL_OP_GREATER:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    if(tr1.getValue().value() > tr2.getValue().value()) {
                        return new TypedRaw(new ResolvableConstant(1), RawType.BOOLEAN);
                    } else {
                        return new TypedRaw(new ResolvableConstant(0), RawType.BOOLEAN);
                    }
                } else {
                    LOG.severe("Cannot compare non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    if(tr1.getValue().value() >= tr2.getValue().value()) {
                        return new TypedRaw(new ResolvableConstant(1), RawType.BOOLEAN);
                    } else {
                        return new TypedRaw(new ResolvableConstant(0), RawType.BOOLEAN);
                    }
                } else {
                    LOG.severe("Cannot compare non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_LESS:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    if(tr1.getValue().value() < tr2.getValue().value()) {
                        return new TypedRaw(new ResolvableConstant(1), RawType.BOOLEAN);
                    } else {
                        return new TypedRaw(new ResolvableConstant(0), RawType.BOOLEAN);
                    }
                } else {
                    LOG.severe("Cannot compare non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    if(tr1.getValue().value() <= tr2.getValue().value()) {
                        return new TypedRaw(new ResolvableConstant(1), RawType.BOOLEAN);
                    } else {
                        return new TypedRaw(new ResolvableConstant(0), RawType.BOOLEAN);
                    }
                } else {
                    LOG.severe("Cannot compare non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_AND:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    // get larger int type
                    RawType rt = tr1.getRawType().promote(tr2. getType());
                    long v = tr1.getValue().value() & tr2.getValue().value();
                    
                    return new TypedRaw(new ResolvableConstant(v), rt);
                } else {
                    LOG.severe("Cannot AND non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_OR:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    // get larger int type
                    RawType rt = tr1.getRawType().promote(tr2. getType());
                    long v = tr1.getValue().value() | tr2.getValue().value();
                    
                    return new TypedRaw(new ResolvableConstant(v), rt);
                } else {
                    LOG.severe("Cannot OR non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_XOR:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    // get larger int type
                    RawType rt = tr1.getRawType().promote(tr2. getType());
                    long v = tr1.getValue().value() ^ tr2.getValue().value();
                    
                    return new TypedRaw(new ResolvableConstant(v), rt);
                } else {
                    LOG.severe("Cannot XOR non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_SHIFT_LEFT:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    // get larger int type
                    RawType rt = tr1.getRawType().promote(tr2. getType());
                    long v = tr1.getValue().value() << tr2.getValue().value();
                    
                    return new TypedRaw(new ResolvableConstant(v), rt);
                } else {
                    LOG.severe("Cannot shift non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_ARITH_SHIFT_RIGHT:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    // get larger int type
                    RawType rt = tr1.getRawType().promote(tr2. getType());
                    long v = tr1.getValue().value() >> tr2.getValue().value();
                    
                    return new TypedRaw(new ResolvableConstant(v), rt);
                } else {
                    LOG.severe("Cannot shift non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_LOGIC_SHIFT_RIGHT:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    // get larger int type
                    RawType rt = tr1.getRawType().promote(tr2. getType());
                    long v = tr1.getValue().value() >>> tr2.getValue().value();
                    
                    return new TypedRaw(new ResolvableConstant(v), rt);
                } else {
                    LOG.severe("Cannot shift non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_ROL:
                // nah
                LOG.severe("Rotated constants are not supported");
                errorsEncountered = true;
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_ROR:
                // nah
                LOG.severe("Rotated constants are not supported");
                errorsEncountered = true;
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_ADD:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    // get larger int type
                    RawType rt = tr1.getRawType().promote(tr2. getType());
                    long v = tr1.getValue().value() + tr2.getValue().value();
                    
                    return new TypedRaw(new ResolvableConstant(v), rt);
                } else {
                    LOG.severe("Cannot add non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_SUBTRACT: // also negate
                v1 = computeConstant(c0);
                
                // negate?
                if(children.size() == 1) {
                    if(v1 instanceof TypedRaw tr) {
                        RawType rt = tr.getRawType();
                        long v = tr.getValue().value();
                        
                        return new TypedRaw(new ResolvableConstant(-v), rt);
                    } else {
                        LOG.severe("Cannot negate non-integer constants");
                        errorsEncountered = true;
                    }
                } else {
                    v2 = computeConstant(children.get(1));
                    
                    if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                        // get larger int type
                        RawType rt = tr1.getRawType().promote(tr2. getType());
                        long v = tr1.getValue().value() - tr2.getValue().value();
                        
                        return new TypedRaw(new ResolvableConstant(v), rt);
                    } else {
                        LOG.severe("Cannot subtract non-integer constants");
                        errorsEncountered = true;
                    }
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_MULTIPLY:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    // get larger int type
                    RawType rt = tr1.getRawType().promote(tr2. getType());
                    long v = tr1.getValue().value() * tr2.getValue().value();
                    
                    return new TypedRaw(new ResolvableConstant(v), rt);
                } else {
                    LOG.severe("Cannot multiply non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_DIVIDE:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    // get larger int type
                    RawType rt = tr1.getRawType().promote(tr2. getType());
                    long v = tr1.getValue().value() / tr2.getValue().value();
                    
                    return new TypedRaw(new ResolvableConstant(v), rt);
                } else {
                    LOG.severe("Cannot divide non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_OP_REMAINDER:
                v1 = computeConstant(c0);
                v2 = computeConstant(children.get(1));
                
                if(v1 instanceof TypedRaw tr1 && v2 instanceof TypedRaw tr2) {
                    // get larger int type
                    RawType rt = tr1.getRawType().promote(tr2. getType());
                    long v = tr1.getValue().value() % tr2.getValue().value();
                    
                    return new TypedRaw(new ResolvableConstant(v), rt);
                } else {
                    LOG.severe("Cannot take remainder of non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarLexer.ID.TERMINAL_KW_NOT:
                v1 = computeConstant(c0);
                
                if(v1 instanceof TypedRaw rt) {
                    return new TypedRaw(new ResolvableConstant(~rt.getValue().value()), rt.getRawType());
                } else {
                    LOG.severe("Cannot NOT non-integer constants");
                    errorsEncountered = true;
                }
                break;
            
            case NstlgrammarParser.ID.VARIABLE_REFERENCE:
                c1 = children.get(1);
                
                if(c0.getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_KW_TO && c1.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_SUBREFERENCE && c1.getChildren().get(0).getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_NAME) {
                    String pointedName = c1.getChildren().get(0).getValue();
                    
                    if(contextStack.hasSymbol(pointedName)) {
                        return new TypedRaw(new ResolvableConstant(pointedName), RawType.PTR);
                    } else {
                        LOG.severe("Unknown symbol in constant TO: " + pointedName);
                        errorsEncountered = true;
                    }
                } else {
                    LOG.severe("Unexpected node in constant parse: " + detailed(node));
                    errorsEncountered = true;                    
                }
                break;
            
            default:
                LOG.severe("Unexpected node in constant parse: " + detailed(node));
                errorsEncountered = true;
        }
        
        return new TypedRaw(new ResolvableConstant(0), RawType.NONE);
    }
    
    /**
     * Computes the value of a constant structure
     * 
     * @param node
     * @return
     */
    private TypedValue computeConstantStructure(StructureType type, ASTNode node) {
        Map<String, TypedValue> members = new HashMap<>();
        List<ASTNode> children = node.getChildren();
        List<ASTNode> memberNodes = children.get(1).getChildren();
        List<String> expectedNames = type.getMemberNames();
        List<NSTLType> expectedTypes = type.getMemberTypes();
        
        LOG.finest("Computing constant structure " + type);
        
        // check type
        if(!children.get(0).getValue().equals(type.getName())) {
            LOG.severe("Incorrect structure type: " + children.get(0).getValue() + ", expected " + type.getName());
            errorsEncountered = true;
            return null;
        }
        
        // construct
        for(ASTNode mn : memberNodes) {
            List<ASTNode> memberChildren = mn.getChildren();
            
            String memberName = memberChildren.get(0).getValue();
            ASTNode valueNode = memberChildren.get(1);
            TypedValue memberValue;
            
            // check member exists and get its type
            if(expectedNames.contains(memberName)) {
                NSTLType expectedType = expectedTypes.get(expectedNames.indexOf(memberName));
                
                // structures can contain structures
                if(valueNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_CONSTANT_STRUCTURE) {
                    if(expectedType instanceof StructureType st) {
                        memberValue = computeConstantStructure(st, valueNode);
                    } else {
                        LOG.severe("Found structure for non-structure type " + expectedType + " of " + memberName + " in " + type);
                        errorsEncountered = true;
                        
                        throw new IllegalArgumentException();
                    }
                } else if(valueNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_VARIABLE_ARRAY) {
                    if(expectedType instanceof ArrayType at) {
                        memberValue = computeConstantArray(at, valueNode);
                    } else {
                        LOG.severe("Found array for non-array type " + expectedType + " of " + memberName + " in " + type);
                        errorsEncountered = true;
                        
                        throw new IllegalArgumentException();
                    }
                } else {
                    memberValue = computeConstant(valueNode);
                }
                
                // check type & convert raws
                memberValue.convertType(expectedType);
                
                // add
                members.put(memberName, memberValue);
            } else {
                LOG.severe("Unknown structure member " + memberName + " of " + type);
                errorsEncountered = true;
            }
        }
        
        // verify all members exist
        for(int i = 0; i < expectedNames.size(); i++) {
            String eName = expectedNames.get(i);
            NSTLType eType = expectedTypes.get(i);
            
            if(!members.containsKey(eName) || !members.get(eName).getType().equals(eType)) {
                LOG.severe("Missing or incorrect structure member " + eName + " of " + type);
                errorsEncountered = true;
                
                throw new IllegalArgumentException();
            }
        }
        
        return new TypedStructure(members, type);
    }
    
    /**
     * Computes the value of a constant array
     * 
     * @param at
     * @param node
     * @return
     */
    private TypedValue computeConstantArray(ArrayType type, ASTNode node) {
        int length = type.getLength();
        List<ASTNode> children = node.getChildren();
        List<ASTNode> members = children.get(1).getChildren();
        List<TypedValue> values = new ArrayList<>();
        NSTLType expectedType = type.getMemberType();
        
        // check type
        checkType(expectedType, constructType(children.get(0)), "for constant array member type");
        
        // get members
        for(ASTNode memberNode : members) {
            TypedValue memberValue;
            
            if(memberNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_CONSTANT_STRUCTURE) {
                if(expectedType instanceof StructureType st) {
                    memberValue = computeConstantStructure(st, memberNode);
                } else {
                    LOG.severe("Found structure for non-structure type " + expectedType + " in " + type);
                    errorsEncountered = true;
                    
                    throw new IllegalArgumentException();
                }
            } else if(memberNode.getSymbol().getID() == NstlgrammarParser.ID.VARIABLE_VARIABLE_ARRAY) {
                if(expectedType instanceof ArrayType at) {
                    memberValue = computeConstantArray(at, memberNode);
                } else {
                    LOG.severe("Found array for non-array type " + expectedType + " in " + type);
                    errorsEncountered = true;
                    
                    throw new IllegalArgumentException();
                }
            } else {
                memberValue = computeConstant(memberNode);
            }
            
            // check type & convert raws
            memberValue.convertType(expectedType);
            
            // add
            values.add(memberValue);
        }
        
        // check size
        if(members.size() != length) {
            LOG.severe("Array literal does not match expected size " + length + "; got " + values.size());
        }
        
        return new TypedArray(values, type);
    }
    
    /**
     * Handles a compiler definition node
     * 
     * @param node
     */
    private void compileDefinition(ASTNode node) {
        List<ASTNode> children = node.getChildren();
        
        String defName = children.get(0).getValue();
        checkNameConflicts(defName);
        TypedValue value = computeConstant(children.get(1));
        
        LOG.finer("Defining " + defName +" = " + value);
        
        compilerDefinitions.put(defName, value);
    }
    
    /**
     * Handles a library inclusion node
     * 
     * @param node
     * @throws NoSuchFileException 
     */
    private ASTNode compileLibraryInclusion(ASTNode node, FileLocator locator) throws NoSuchFileException {
        List<ASTNode> children = node.getChildren();
        
        // first child is always LNAME, might be local name
        boolean hasLocalName = false,
                hasFile = false;
        
        String libname = children.get(0).getValue().substring(1),
               localLibname = libname,
               filename = "";
        
        LOG.finest("Including library " + libname);
        
        // second LNAME = canonical name, STRING = file
        if(children.size() == 2) {
            ASTNode n = children.get(1);
            
            if(n.getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_LNAME) {
                libname = n.getValue().substring(1);
                hasLocalName = true;
            } else {
                filename = n.getValue();
                filename = filename.substring(1, filename.length() - 1);
                hasFile = true;
            }
        } else if(children.size() == 3) {
            ASTNode n = children.get(1);
            ASTNode m = children.get(2);
            
            if(n.getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_LNAME) {
                libname = n.getValue().substring(1);
                filename = m.getValue();
                filename = filename.substring(1, filename.length() - 1);
            } else {
                libname = m.getValue().substring(1);
                filename = n.getValue();
                filename = filename.substring(1, filename.length() - 1);
            }
            
            hasLocalName = true;
            hasFile = true;
        }
        
        if(knownLibraryNames.containsValue(libname)) {
            LOG.finest("Included library " + libname + " as " + localLibname + " from existing library");
            
            knownLibraryNames.put(localLibname, libname);
            return null;
        }
        
        knownLibraryNames.put(localLibname, libname);
        
        Path givenPath = hasFile ? Paths.get(filename) : Paths.get(libname);
        if(!locator.addFile(givenPath)) {
            LOG.severe("Could not find file for library " + libname);
            errorsEncountered = true;
            return null;
        }
        
        Path truePath = locator.getSourceFile(givenPath);
        filename = truePath.toString();
        libraryFilesMap.put(truePath.toFile(), localLibname);
        
        LOG.finest("Included library " + libname + " as " + localLibname + " from " + filename);
        
        // if we have a header file, insert it into the tree
        return getHeaderContents(givenPath, libname, locator);
    }
    
    /**
     * Gets the contents of a header file
     * @param headerPath
     * @return
     * @throws NoSuchFileException 
     */
    private ASTNode getHeaderContents(Path givenPath, String libname, FileLocator locator) throws NoSuchFileException {
        Path headerPath = locator.getHeaderFile(givenPath);
        
        if(headerPath == null) {
            LOG.finer("No header file for " + libname);
            return null;
        }
        
        LOG.finer("Including header contents for " + libname + " from " + headerPath);
        
        try {
            NstlgrammarLexer lexer = new NstlgrammarLexer(new InputStreamReader(Files.newInputStream(headerPath)));
            NstlgrammarParser parser = new NstlgrammarParser(lexer);
            
            ParseResult result = parser.parse();
            
            if(result.getErrors().size() != 0) {
                LOG.severe("Encountered errors parsing " + headerPath);
                for(ParseError pe : result.getErrors()) {
                    LOG.severe("ParseError: " + pe);
                }
                
                errorsEncountered = true;
            } else {
                ASTNode headerRoot = result.getRoot();
                return headerRoot;
            }
        } catch(IOException e) {
            LOG.severe("IOException parsing header file: " + headerPath);
            e.printStackTrace();
            errorsEncountered = true;
        } catch(InitializationException e) {
            LOG.severe("InitializationException parsing header file: " + headerPath);
            e.printStackTrace();
            errorsEncountered = true;
        }
        
        return null;
    }
    
    /**
     * Returns true if the node contains a constant expression
     * 
     * @param node
     * @return
     */
    private boolean isConstant(ASTNode node) {
        switch(node.getSymbol().getID()) {
            case NstlgrammarLexer.ID.TERMINAL_OP_EQUAL:
            case NstlgrammarLexer.ID.TERMINAL_OP_NOT_EQUAL:
            case NstlgrammarLexer.ID.TERMINAL_OP_GREATER:
            case NstlgrammarLexer.ID.TERMINAL_OP_GREATER_EQUAL:
            case NstlgrammarLexer.ID.TERMINAL_OP_LESS:
            case NstlgrammarLexer.ID.TERMINAL_OP_LESS_EQUAL:
            case NstlgrammarLexer.ID.TERMINAL_KW_AND:
            case NstlgrammarLexer.ID.TERMINAL_KW_OR:
            case NstlgrammarLexer.ID.TERMINAL_KW_XOR:
            case NstlgrammarLexer.ID.TERMINAL_OP_SHIFT_LEFT:
            case NstlgrammarLexer.ID.TERMINAL_OP_ARITH_SHIFT_RIGHT:
            case NstlgrammarLexer.ID.TERMINAL_OP_LOGIC_SHIFT_RIGHT:
            case NstlgrammarLexer.ID.TERMINAL_KW_ROL:
            case NstlgrammarLexer.ID.TERMINAL_KW_ROR:
            case NstlgrammarLexer.ID.TERMINAL_OP_ADD:
            case NstlgrammarLexer.ID.TERMINAL_OP_SUBTRACT:
            case NstlgrammarLexer.ID.TERMINAL_OP_MULTIPLY:
            case NstlgrammarLexer.ID.TERMINAL_OP_DIVIDE:
            case NstlgrammarLexer.ID.TERMINAL_OP_REMAINDER:
            case NstlgrammarLexer.ID.TERMINAL_KW_NOT:
                return isConstant(node.getChildren());
            
            case NstlgrammarLexer.ID.TERMINAL_KW_AS:
                return isConstant(node.getChildren().get(0));
                
            case NstlgrammarParser.ID.VARIABLE_CONSTANT_VALUE:
                List<ASTNode> children = node.getChildren();
                
                if(children.size() == 1) {
                    ASTNode n = children.get(0);
                    return n.getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_INTEGER ||
                           n.getSymbol().getID() == NstlgrammarLexer.ID.TERMINAL_STRING ||
                           compilerDefinitions.containsKey(n.getValue()) ||
                           (contextStack.hasSymbol(n.getValue()) && contextStack.getSymbol(n.getValue()).getIsConstant());
                } else {
                    return true;
                }
                
            case NstlgrammarParser.ID.VARIABLE_CONSTANT_STRUCTURE:
                return true;
            
            default:
                return false;
        }
    }
    
    /**
     * Returns true if all nodes in the list are constant expressions
     * 
     * @param nodes
     * @return
     */
    private boolean isConstant(List<ASTNode> nodes) {
        for(ASTNode n : nodes) {
            if(!isConstant(n)) return false;
        }
        
        return true;
    }
    
    /**
     * Generates the default map of type names
     * 
     * @return
     */
    private HashMap<String, NSTLType> generateDefaultTypemap() {
        HashMap<String, NSTLType> map = new HashMap<>();
        
        map.put("u8", RawType.U8);
        map.put("u16", RawType.U16);
        map.put("u32", RawType.U32);
        map.put("i8", RawType.I8);
        map.put("i16", RawType.I16);
        map.put("i32", RawType.I32);
        map.put("char", RawType.U8);
        map.put("boolean", RawType.BOOLEAN);
        map.put("string", new StringType(""));
        map.put("ptr", RawType.U32);
        
        return map;
    }
    
    /**
     * Parse integer literals
     * 
     * @param s
     * @return
     */
    private long parseInteger(String s, int bytes, boolean signed) {
        long v;
        
        s = s.replace("_", "");
        
        if(s.startsWith("0x")) {
            v = Integer.parseUnsignedInt(s.substring(2), 16);
        } else if(s.startsWith("0o")) {
            v = Integer.parseUnsignedInt(s.substring(2), 8);
        } else if(s.startsWith("0d")) {
            v = Integer.parseUnsignedInt(s.substring(2));
        } else if(s.startsWith("0b")) {
            v = Integer.parseUnsignedInt(s.substring(2), 2);
        } else {
            v = Integer.parseUnsignedInt(s);
        }
        
        if(signed && bytes != 0) {
            v = (v << (64 - bytes*8)) >> (64 - bytes*8);
        }
        
        return v;
    }
    
    /**
     * Checks the context stack and definitions for a name conflict
     * 
     * @param name
     * @return
     */
    private void checkNameConflicts(String name) {
        if(typeDefinitions.containsKey(name) || contextStack.hasLocalSymbol(name) || typeDefinitions.containsKey(name) || functionDefinitions.containsKey(name) || specialFunctionNames.contains(name)) {
            LOG.severe("Duplicate name: " + name);
            errorsEncountered = true;
        }
    }
    
    /**
     * Correct the library name of a symbol
     * 
     * @param symbol
     * @return
     */
    private String renameLibrary(String symbol) {
        int i = symbol.indexOf('.');
        String lname = symbol.substring(1, i),
               s = symbol.substring(i);
        
        if(!knownLibraryNames.containsKey(lname)) {
            LOG.severe("Unknown library: " + lname + " in " + symbol);
            errorsEncountered = true;
        }
        
        return knownLibraryNames.getOrDefault(lname, "%unknown") + s;
    }
    
    /**
     * toString for an ASTNode with slightly more detail
     * 
     * @param node
     * @return
     */
    private String detailed(ASTNode node) {
        if(node.getSymbolType() == SymbolType.Terminal) {
            return node.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            
            sb.append(node);
            sb.append(" {");
            
            for(ASTNode n : node.getChildren()) {
                sb.append(n.toString() + ", ");
            }
            
            sb.delete(sb.length() - 2, sb.length());
            sb.append("}");
            
            return sb.toString();
        }
    }
}
