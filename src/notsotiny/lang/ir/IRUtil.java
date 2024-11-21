package notsotiny.lang.ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import notsotiny.lang.util.MapUtil;

/**
 * Utility functions for the IR
 */
public class IRUtil {
    
    private static Logger LOG = Logger.getLogger(IRUtil.class.getName());
    
    /**
     * Constructs a list of IRDefinitions found in the function
     * @param function
     * @return
     */
    public static List<IRDefinition> getDefinitionList(IRFunction function) {
        List<IRDefinition> defList = new ArrayList<>();
        
        // Get global constants/variables
        IRModule module = function.getModule();
        
        for(IRIdentifier id : module.getGlobals().keySet()) {
            defList.add(new IRDefinition(id));
        }
        
        for(IRIdentifier id : module.getFunctions().keySet()) {
            defList.add(new IRDefinition(id));
        }
        
        // Get function args
        for(IRIdentifier id : function.getArguments().getNameList()) {
            defList.add(new IRDefinition(id, function));
        }
        
        // For each BB
        for(IRBasicBlock bb : function.getBasicBlockList()) {
            // Get bb args
            for(IRIdentifier id : bb.getArgumentList().getNameList()) {
                defList.add(new IRDefinition(id, bb));
            }
            
            // Get instruction defines
            for(IRLinearInstruction li : bb.getInstructions()) {
                IRIdentifier id = li.getDestinationID();
                if(id != null) {
                    defList.add(new IRDefinition(id, li));
                }
            }
            
            defList.add(new IRDefinition(bb.getID(), bb.getExitInstruction()));
        }
        
        return defList;
    }
    
    /**
     * Constructs a map from identifiers to their definitions
     * @param defList
     * @return
     */
    public static Map<IRIdentifier, IRDefinition> getDefinitionMap(List<IRDefinition> defList) {
        Map<IRIdentifier, IRDefinition> defMap = new HashMap<>();
        
        for(IRDefinition def : defList) {
            defMap.put(def.getID(), def);
        }
        
        return defMap;
    }
    
    /**
     * Constructs a map from identifiers to their definitions
     * Calls getDefinitionList
     * @param function
     * @return
     */
    public static Map<IRIdentifier, IRDefinition> getDefinitionMap(IRFunction function) {
        return getDefinitionMap(getDefinitionList(function));
    }
    
    /**
     * Constructs a map from identifiers to defines that use them
     * @param function
     * @param defineMap
     * @return
     */
    public static Map<IRIdentifier, List<IRDefinition>> getUseMap(IRFunction function, List<IRDefinition> defList) {
        Map<IRIdentifier, List<IRDefinition>> useMap = new HashMap<>();
        
        for(IRDefinition def : defList) {
            IRIdentifier definedID = def.getID();
            
            switch(def.getType()) {
                case BBARG: {
                    // This def is a use for any value mapped to this argument for the BB
                    IRBasicBlock bb = def.getBB();
                    
                    for(IRIdentifier predID : bb.getPredecessorBlocks()) {
                        IRBranchInstruction predExit = function.getBasicBlock(predID).getExitInstruction();
                        
                        if(bb.getID().equals(predExit.getTrueTargetBlock())) {
                            if(predExit.getTrueArgumentMapping().getMapping(definedID) instanceof IRIdentifier mappedID) {
                                MapUtil.getOrCreateList(useMap, mappedID).add(def);
                            }
                        }
                        
                        if(bb.getID().equals(predExit.getFalseTargetBlock())) {
                            if(predExit.getFalseArgumentMapping().getMapping(definedID) instanceof IRIdentifier mappedID) {
                                MapUtil.getOrCreateList(useMap, mappedID).add(def);
                            }
                        }
                    }
                    break;
                }
                    
                case LINEAR: {
                    // This def is a use for any value in the arguments of the LI
                    IRLinearInstruction li = def.getLinearInstruction();
                    
                    if(li.getLeftSourceValue() instanceof IRIdentifier argID) {
                        MapUtil.getOrCreateList(useMap, argID).add(def);
                    }
                    
                    if(li.getRightSourceValue() instanceof IRIdentifier argID) {
                        MapUtil.getOrCreateList(useMap, argID).add(def);
                    }
                    
                    if(li.getLeftComparisonValue() instanceof IRIdentifier argID) {
                        MapUtil.getOrCreateList(useMap, argID).add(def);
                    }
                    
                    if(li.getRightComparisonValue() instanceof IRIdentifier argID) {
                        MapUtil.getOrCreateList(useMap, argID).add(def);
                    }
                    
                    if(li.getOp() == IRLinearOperation.CALLR) {
                        for(IRValue mappedVal : li.getCallArgumentMapping().getMap().values()) {
                            if(mappedVal instanceof IRIdentifier mappedID) {
                                MapUtil.getOrCreateList(useMap, mappedID).add(def);
                            }
                        }
                    }
                    break;
                }
                
                case BRANCH: {
                    // This def is a use for any value in the arguments of the BI. Mappings are handled by BBArg defs
                    IRBranchInstruction bi = def.getBranchInstruction();
                    
                    if(bi.getCondition() != IRCondition.NONE) {
                        if(bi.getCompareLeft() instanceof IRIdentifier argID) {
                            MapUtil.getOrCreateList(useMap, argID).add(def);
                        }
                        
                        if(bi.getCompareRight() instanceof IRIdentifier argID) {
                            MapUtil.getOrCreateList(useMap, argID).add(def);
                        }
                    }
                    break;
                }
                    
                default:
                    // GLOBAL and FUNARG don't have any use's within the function
                    break;
            }
        }
        
        return useMap;
    }
    
    /**
     * Constructs a map from identifiers to defines that use them
     * Calls getDefinitionMap
     * @param function
     * @return
     */
    public static Map<IRIdentifier, List<IRDefinition>> getUseMap(IRFunction function) {
        return getUseMap(function, getDefinitionList(function));
    }
    
    /**
     * kajdghsdkfjghskdjfgdsfg
     * @param source
     * @param target
     */
    public static void tryMergeBasicBlocks(IRBasicBlock source, IRBasicBlock target) {
        // TODO
    }
    
    /**
     * Merges source into target. Source is assumed to have no body code and an unconditional exit to target. 
     * @param source
     * @param target
     */
    public static void mergeBasicBlocks(IRBasicBlock source, IRBasicBlock target) {
        // TODO
    }
    
    /**
     * Replaces uses of from with to in module. Removes statement which defines from
     * @param module
     * @param from
     * @param to
     */
    public static void replaceInModule(IRModule module, IRValue from, IRValue to) {
        // Replace in functions
        for(IRFunction function : module.getFunctions().values()) {
            replaceInFunction(function, from, to);
        }
        
        // Find defining global if it exists
        // TODO
    }
    
    /**
     * Replaces uses of from with to in function. Removes statement which defines from if found
     * @param function
     * @param from
     * @param to
     */
    public static void replaceInFunction(IRFunction function, IRValue from, IRValue to) {
        // Replace in basic blocks
        for(IRBasicBlock block : function.getBasicBlockList()) {
            replaceInBlock(block, from, to);
        }
    }
    
    /**
     * Replaces uses of from with to in bb. Removes statement which defines from if found
     * @param bb
     * @param from
     * @param to
     */
    public static void replaceInBlock(IRBasicBlock bb, IRValue from, IRValue to) {
        boolean definedByInstruction = false;
        
        // Replace in body code, find defining instruction if it exists\
        List<IRLinearInstruction> lis = bb.getInstructions();
        for(int i = 0; i < lis.size(); i++) {
            IRLinearInstruction li = lis.get(i);
            
            if(from.equals(li.getDestinationID())) {
                // LI defines from. Remove it.
                definedByInstruction = true;
                lis.remove(i--);
            } else {
                if(from.equals(li.getLeftSourceValue())) {
                    // Left source is from. replace.
                    li.setLeftSourceValue(to);
                }
                
                if(from.equals(li.getRightSourceValue())) {
                    // Right source is from. replace.
                    li.setRightSourceValue(to);
                }
                
                if(from.equals(li.getLeftComparisonValue())) {
                    // Left comparison value is from. replace.
                    li.setLeftComparisonValue(to);
                }
                
                if(from.equals(li.getRightComparisonValue())) {
                    // Right comparison value is from. replace.
                    li.setRightComparisonValue(to);
                }
            }
        }
        
        // Replace in exit code
        IRBranchInstruction branch = bb.getExitInstruction();
        if(branch != null) {
            if(from.equals(branch.getCompareLeft())) {
                branch.setCompareLeft(to);
            }
            
            if(from.equals(branch.getCompareRight())) {
                branch.setCompareRight(to);
            }
            
            if(from.equals(branch.getReturnValue())) {
                branch.setReturnValue(to);
            }
            
            IRArgumentMapping trueMap = branch.getTrueArgumentMapping();
            IRArgumentMapping falseMap = branch.getFalseArgumentMapping();
            
            if(trueMap != null) {
                for(Entry<IRIdentifier, IRValue> entry : trueMap.getMap().entrySet()) {
                    if(entry.getValue().equals(from)) {
                        entry.setValue(to);
                    }
                }
            }
            
            if(falseMap != null) {
                for(Entry<IRIdentifier, IRValue> entry : falseMap.getMap().entrySet()) {
                    if(entry.getValue().equals(from)) {
                        entry.setValue(to);
                    }
                }
            }
        }
        
        // Find defining argument if it exists
        IRArgumentList args = bb.getArgumentList();
        List<IRIdentifier> argNames = args.getNameList();
        List<IRType> argTypes = args.getTypeList();
        
        for(int i = 0; i < argNames.size(); i++) {
            if(argNames.get(i).equals(from)) {
                // Defined by an argument. Remove it
                IRIdentifier argName = argNames.remove(i);
                argTypes.remove(i);
                i--;
                
                // Remove from predecessors' mappings
                for(IRIdentifier predID : bb.getPredecessorBlocks()) {
                    IRBasicBlock predBB = bb.getParentFunction().getBasicBlock(predID);
                    IRBranchInstruction predExit = predBB.getExitInstruction();
                    IRIdentifier predTrueSuccessor = predExit.getTrueTargetBlock();
                    IRIdentifier predFalseSuccessor = predExit.getFalseTargetBlock();
                    
                    if(bb.getID().equals(predTrueSuccessor)) {
                        // We're the true successor
                        predExit.getTrueArgumentMapping().getMap().remove(argName);
                    }
                    
                    if(bb.getID().equals(predFalseSuccessor)) {
                        // We're the false successor
                        predExit.getFalseArgumentMapping().getMap().remove(argName);
                    }
                }
            }
        }
    }
}
