package notsotiny.lang.compiler.irgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.compiler.types.NSTLType;
import notsotiny.lang.ir.IRArgumentList;
import notsotiny.lang.ir.IRArgumentMapping;
import notsotiny.lang.ir.IRBasicBlock;
import notsotiny.lang.ir.IRBranchInstruction;
import notsotiny.lang.ir.IRFunction;
import notsotiny.lang.ir.IRIdentifier;
import notsotiny.lang.ir.IRIdentifierClass;
import notsotiny.lang.ir.IRType;
import notsotiny.lang.ir.IRUtil;
import notsotiny.lang.ir.IRValue;
import notsotiny.lang.util.MapUtil;
import notsotiny.lang.util.Pair;

/**
 * Handles functions & data related to SSA construction
 * https://c9x.me/compile/bib/braun13cc.pdf
 */
public class SSAManager {
    
    private static Logger LOG = Logger.getLogger(SSAManager.class.getName());
    
    // Function being compiled to
    private IRFunction targetFunction;
    
    //Maps BB identifiers to maps from local variable names to their definition identifiers
    private Map<IRIdentifier, Map<String, IRValue>> latestVariableDefintions;
    
    // Set of BBs which are sealed, and thus will have no predecessors added
    private Set<IRIdentifier> sealedBlocks;
    
    // Maps BB identifiers to maps from BB argument ID to variable name
    // Holds arguments that need to be mapped in the block's predecessors
    private Map<IRIdentifier, Map<IRIdentifier, String>> unmappedBBArguments;
    
    // Maps BB argument IDs to their parent BB
    private Map<IRIdentifier, IRIdentifier> argumentSourceMap;
    
    // Records the IRType of a variable
    private Map<String, IRType> variableTypeMap;
    
    // Function-unique ID counter
    private int fuid;
    
    /**
     * Create an empty SSA manager
     */
    public SSAManager(IRFunction targetFunction) {
        this.targetFunction = targetFunction;
        
        this.latestVariableDefintions = new HashMap<>();
        this.sealedBlocks = new HashSet<>();
        this.unmappedBBArguments = new HashMap<>();
        this.argumentSourceMap = new HashMap<>();
        this.variableTypeMap= new HashMap<>();
        this.fuid = 0;
        
        // Write arguments in entry
        IRIdentifier entryID = new IRIdentifier("entry", IRIdentifierClass.BLOCK);
        
        List<IRIdentifier> argNames = targetFunction.getArguments().getNameList();
        List<IRType> argTypes = targetFunction.getArguments().getTypeList();
        
        for(int i = 0; i < argNames.size(); i++) {
            writeVariable(argNames.get(i).getName(), entryID, argNames.get(i), argTypes.get(i));
        }
    }
    
    /**
     * Get a unique local name prefixed by name
     * @param name
     * @return
     */
    public IRIdentifier getUniqueLocalID(String name) {
        int id = fuid++;
        String uname;
        
        if(name.equals("")) {
            uname = "" + id;
        } else if(name.contains("%")) {
            uname = name.substring(0, name.indexOf("%")) + "%" + id;
        } else {
            uname = name + "%" + id;
        }
        
        return new IRIdentifier(uname, IRIdentifierClass.LOCAL);
    }
    
    /**
     * Defines name to be localID in block blockID
     * @param name
     * @param blockID
     * @param localID
     */
    public void writeVariable(String name, IRIdentifier blockID, IRValue localVal, IRType type) {
        LOG.finest("Writing " + localVal + " to " + name + " in " + blockID);
        
        if(type == null) {
            LOG.finest("Attempted to write " + localVal + " to " + name + " in " + blockID + " with null type");
            throw new NullPointerException();
        }
        
        // Update map entry
        MapUtil.getOrCreateMap(this.latestVariableDefintions, blockID).put(name, localVal);
        this.variableTypeMap.put(name, type);
    }
    
    /**
     * Get the definition of name in BB blockID
     * @param name
     * @param blockID
     * @return
     * @throws CompilationException 
     */
    public IRValue readVariable(String name, IRIdentifier blockID) throws CompilationException {
        LOG.finest("Reading variable " + name + " from " + blockID);
        
        Map<String, IRValue> map = MapUtil.getOrCreateMap(this.latestVariableDefintions, blockID);
        if(map.containsKey(name)) {
            // Variable is defined in block. Return it
            return map.get(name);
        } else {
            // Variable is not defined in block. Get it from predecessors, creating arguments as necessary
            return readVariableRecursive(name, blockID);
        }
    }
    
    /**
     * Reads a variable from a block's predecessors, creating BB arguments as necessary 
     * @param name
     * @param blockID
     * @return
     * @throws CompilationException 
     */
    private IRValue readVariableRecursive(String name, IRIdentifier blockID) throws CompilationException {
        LOG.finest("Reading variable " + name + " from predecessors of " + blockID);
        IRValue localVal;
        
        IRBasicBlock bb = this.targetFunction.getBasicBlock(blockID);
        
        if(this.sealedBlocks.contains(blockID)) {
            // Block can't get additional predecessors, look up from predecessors
            List<IRIdentifier> predecessors = bb.getPredecessorBlocks();
            
            if(predecessors.size() == 1) {
                // Only one predecessor, no argument needed
                localVal = readVariable(name, predecessors.get(0));
            } else {
                // Multiple predecessors, create argument
                IRIdentifier argID = getUniqueLocalID(name);
                bb.addArgument(argID, this.variableTypeMap.get(name));
                this.argumentSourceMap.put(argID, blockID);
                
                writeVariable(name, blockID, argID, this.variableTypeMap.get(name));
                localVal = mapArguments(name, argID, blockID);                
            }
        } else {
            // Block might have predecessors added. Create BB argument and put it in the unmapped map
            IRIdentifier argID = getUniqueLocalID(name);
            bb.addArgument(argID, this.variableTypeMap.get(name));
            this.argumentSourceMap.put(argID, blockID);
            
            MapUtil.getOrCreateMap(this.unmappedBBArguments, blockID).put(argID, name);
            
            localVal = argID;
        }
        
        writeVariable(name, blockID, localVal, this.variableTypeMap.get(name));
        return localVal;
    }
    
    /**
     * Maps the given argument in the predecessors of the given block. 
     * @param name
     * @param argID
     * @param blockID
     * @return
     * @throws CompilationException 
     */
    private IRValue mapArguments(String name, IRIdentifier argID, IRIdentifier blockID) throws CompilationException {
        LOG.finest("Mapping " + name + " to " + argID + " in " + blockID);
        IRBasicBlock bb = this.targetFunction.getBasicBlock(blockID);
        
        // For each predecessor
        for(IRIdentifier predID : bb.getPredecessorBlocks()) {
            IRBranchInstruction predBranch = this.targetFunction.getBasicBlock(predID).getExitInstruction();
            
            IRValue definition = readVariable(name, predID);
            
            // Figure out if we're true or false
            if(predBranch.getTrueTargetBlock().equals(blockID)) {
                // we're the true block
                predBranch.getTrueArgumentMapping().addMapping(argID, definition);
            } else {
                // we're the false block
                predBranch.getFalseArgumentMapping().addMapping(argID, definition);
            }
        }
        
        // Remove trivial arguments if possible
        return tryRemoveArgument(argID, blockID);
    }
    
    /**
     * Attempt to remove arguments that map to 1 value other than themself
     * @param argID
     * @param blockID
     * @return
     */
    private IRValue tryRemoveArgument(IRIdentifier argID, IRIdentifier blockID) {
        LOG.finest("Attempting to remove argument " + argID + " from " + blockID);
        IRBasicBlock bb = this.targetFunction.getBasicBlock(blockID);
        
        IRValue val = null;
        
        // For each predecessor
        for(IRIdentifier predID : bb.getPredecessorBlocks()) {
            // Get the mapping of this argument
            IRBranchInstruction predBranch = this.targetFunction.getBasicBlock(predID).getExitInstruction();
            IRValue mappedValue;
            
            // Figure out if we're true or false
            if(predBranch.getTrueTargetBlock().equals(blockID)) {
                // we're the true block
                mappedValue = predBranch.getTrueArgumentMapping().getMapping(argID);
            } else {
                // we're the false block
                mappedValue = predBranch.getFalseArgumentMapping().getMapping(argID);
            }
            
            if(mappedValue.equals(val) || mappedValue.equals(argID)) {
                // Unique value or self-reference. Trivial.
                continue;
            } else if(val != null) {
                // At least two values are involved. Not trivial
                LOG.finest(argID + " is not trivial");
                return argID;
            }
            
            // Found unique value
            val = mappedValue;
        }
        
        // If we're here, the argument is trivial
        LOG.finest(argID + " is trivial");
        
        // Construct list of BB arguments that get mapped to the value to be removed
        // List<{arg ID, bb ID}>
        Set<Pair<IRIdentifier, IRIdentifier>> trivialArgumentCandidates = new HashSet<>();
        findCandidateArguments(trivialArgumentCandidates, argID, blockID, new HashSet<>());
        
        // Substitute argument with value
        // In the IR
        IRUtil.replaceInFunction(bb.getParentFunction(), argID, val);
        
        // And in our data
        this.argumentSourceMap.remove(argID);
        for(Map<String, IRValue> variableMap : this.latestVariableDefintions.values()) {
            for(Entry<String, IRValue> entry : variableMap.entrySet()) {
                if(entry.getValue().equals(argID)) {
                    entry.setValue(val);
                }
            }
        }
        
        // Check arguments that may be trivial now
        for(Pair<IRIdentifier, IRIdentifier> pair : trivialArgumentCandidates) {
            // Pair{argID, bbID}
            if(pair.b.equals(blockID)) {
                continue;
            }
            
            tryRemoveArgument(pair.a, pair.b);
        }
        
        return val;
    }
    
    /**
     * Finds BB arguments that get mapped to the given value
     * @param list
     * @param val
     * @param bb
     */
    private void findCandidateArguments(Set<Pair<IRIdentifier, IRIdentifier>> list, IRIdentifier val, IRIdentifier blockID, Set<IRIdentifier> visited) {
        // Only visit once
        if(visited.contains(blockID)) {
            return;
        }
        
        visited.add(blockID);
        
        LOG.finest("Finding argument removal candidates from " + blockID);
        IRBasicBlock bb = this.targetFunction.getBasicBlock(blockID);
        
        // bb hasn't been processed
        if(bb == null) {
            return;
        }
        
        // Check mappings in this block
        IRBranchInstruction exitInst = bb.getExitInstruction();
        if(exitInst == null) {
            // No successors yet
            return;
        }
        
        IRArgumentMapping trueMapping = exitInst.getTrueArgumentMapping();
        IRArgumentMapping falseMapping = exitInst.getFalseArgumentMapping();
        
        // If an arg is mapped to val, it's a candidate
        if(trueMapping != null) {
            for(Entry<IRIdentifier, IRValue> entry : trueMapping.getMap().entrySet()) {
                if(entry.getValue().equals(val)) {
                    list.add(new Pair<>(entry.getKey(), exitInst.getTrueTargetBlock()));
                }
            }
        }
        
        if(falseMapping != null) {
            for(Entry<IRIdentifier, IRValue> entry : trueMapping.getMap().entrySet()) {
                if(entry.getValue().equals(val)) {
                    list.add(new Pair<>(entry.getKey(), exitInst.getFalseTargetBlock()));
                }
            }
        }
        
        // Check successors
        IRBranchInstruction branch = bb.getExitInstruction();
        IRIdentifier trueID = branch.getTrueTargetBlock();
        IRIdentifier falseID = branch.getFalseTargetBlock();
        
        if(trueID != null) {
            findCandidateArguments(list, val, trueID, visited);
        }
        
        if(falseID != null) {
            findCandidateArguments(list, val, falseID, visited);
        }
    }
    
    /**
     * Seals a block
     * Predecessors must not be added to a sealed block.
     * @param blockID
     * @throws CompilationException 
     */
    public void sealBlock(IRIdentifier blockID) throws CompilationException {
        LOG.finest("Sealing " + blockID);
        Map<IRIdentifier, String> unmappedArgMap = MapUtil.getOrCreateMap(this.unmappedBBArguments, blockID);
        
        for(IRIdentifier argID : unmappedArgMap.keySet()) {
            String variableName = unmappedArgMap.get(argID);
            
            mapArguments(variableName, argID, blockID);
        }
        
        this.sealedBlocks.add(blockID);
    }
    
    /**
     * Returns true if a block is sealed
     * @param bockID
     * @return
     */
    public boolean isSealed(IRIdentifier blockID) {
        return this.sealedBlocks.contains(blockID);
    }
}
