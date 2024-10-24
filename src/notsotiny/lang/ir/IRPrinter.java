package notsotiny.lang.ir;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prints IR in a readable format
 * @author Mechafinch
 */
public class IRPrinter {
    
    /**
     * Get a prefix for indentation
     * @param depth
     * @return
     */
    private static String getPrefix(int depth) {
        StringBuilder sb = new StringBuilder();
        
        for(int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        
        return sb.toString();
    }
    
    /**
     * Print a module
     * @param module
     * @param depth
     */
    public static void printModule(Logger printer, IRModule module, int depth) {
        if(printer.isLoggable(Level.FINEST)) { 
            String prefix = getPrefix(depth);
            printer.finest(prefix + "Module " + module.getName());
            
            for(IRGlobal g : module.getGlobalsList()) {
                printGlobal(printer, g, depth + 1);
            }
            
            if(module.getGlobalsList().size() != 0) {
                printer.finest("");
            }
            
            boolean first = true;
            
            for(IRFunction f : module.getFunctionsList()) {
                if(first) {
                    first = false;
                } else {
                    printer.finest("");
                }
                
                printFunction(printer, f, depth + 1);
            }
        }
    }
    
    /**
     * Print a global
     * @param global
     * @param depth
     */
    public static void printGlobal(Logger printer, IRGlobal global, int depth) {
        if(printer.isLoggable(Level.FINEST)) { 
            IRValue v = global.getContents().get(0);
            IRType firstType = (v instanceof IRConstant c ? c.getType() : IRType.I32);
            
            if(global.getContents().size() == 1) {
                printer.finest(getPrefix(depth) + firstType + " " + global.getID() + ": " + (v instanceof IRConstant c ? c.getValue() : v));
            } else {
                boolean array = true, string = true;
                
                for(IRValue v2 : global.getContents()) {
                    if(v2 instanceof IRConstant c) {
                        if(c.getType() != firstType) {
                            string = false;
                            array = false;
                        } else if(c.getValue() < 0x20 || c.getValue() > 0x7E) {
                            string = false;
                        }
                    } else {
                        string = false;
                        if(firstType != IRType.I32) {
                            array = false;
                        }
                    }
                }
                
                if(string) {
                    // All same type, printable
                    StringBuilder sb = new StringBuilder(getPrefix(depth) + firstType + "[] " + global.getID() + ": \"");
                    for(IRValue v2 : global.getContents()) {
                        sb.append((char)(((IRConstant) v2).getValue()));
                    }
                    printer.finest(sb.toString() + "\"");
                } else if(array) {
                    // All same type, not printable
                    StringBuilder sb = new StringBuilder(getPrefix(depth) + firstType + "[] " + global.getID() + ": ");
                    
                    boolean first = true;
                    for(IRValue v2 : global.getContents()) {
                        if(first) {
                            first = false;
                        } else {
                            sb.append(", ");
                        }
                        
                        if(v2 instanceof IRConstant c) {
                            sb.append(c.getValue());
                        } else {
                            sb.append(v2);
                        }
                    }
                    
                    printer.finest(sb.toString());
                } else {
                    // Mixed types
                    String pf = getPrefix(depth + 1);
                    printer.finest(getPrefix(depth) + global.getID() + ":");
                    
                    for(IRValue v2 : global.getContents()) {
                        if(v2 instanceof IRConstant c) {
                            printer.finest(pf + c.getType() + " " + c.getValue());
                        } else {
                            printer.finest(pf + IRType.I32 + " " + v2);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Print a function
     * @param function
     * @param depth
     */
    public static void printFunction(Logger printer, IRFunction function, int depth) {
        if(printer.isLoggable(Level.FINEST)) {
            String pf = getPrefix(depth);
            printer.finest(pf + (function.isExternal() ? "external " : "") + "func " + function.getReturnType() + " " + function.getID() + getArgumentList(function.getArguments()) + " {");
                        
            boolean first = true;
            
            for(IRBasicBlock bb : function.getBasicBlockList()) {
                if(first) {
                    first = false;
                } else {
                    printer.finest("");
                }
                
                printBasicBlock(printer, bb, depth + 1);
            }
            
            printer.finest(pf + "}");
        }
    }
    
    /**
     * Print an argument list
     * @param alist
     */
    private static String getArgumentList(IRArgumentList alist) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        
        for(int i = 0; i < alist.getArgumentCount(); i++) {
            if(i != 0) {
                sb.append(", ");
            }
            sb.append(alist.getType(i) + " " + alist.getName(i));
        }
        
        sb.append(")");
        return sb.toString();
    }
    
    /**
     * Print a basic block
     * @param bb
     * @param depth
     */
    public static void printBasicBlock(Logger printer, IRBasicBlock bb, int depth) {
        if(printer.isLoggable(Level.FINEST)) {
            printer.finest(getPrefix(depth) + bb.getID() + getArgumentList(bb.getArgumentList()) + ":");
            
            for(IRLinearInstruction li : bb.getInstructions()) {
                printLinearInstruction(printer, li, depth + 1);
            }
            
            printBranchInstruction(printer, bb.getExitInstruction(), depth + 1);
        }
    }
    
    /**
     * Print a linear instruction
     * @param inst
     * @param depth
     */
    public static void printLinearInstruction(Logger printer, IRLinearInstruction inst, int depth) {
        String pf = getPrefix(depth);
        
        switch(inst.getOp()) {
            // 1 arg w/ destination
            case TRUNC, SX, ZX, LOAD, NOT, NEG:
                printer.finest(pf + getDestination(inst) + inst.getOp() + " " + inst.getLeftSourceValue());
                break;
            
            // 2 arg
            case STORE:
                printer.finest(pf + inst.getOp() + " " + inst.getLeftSourceValue() + " -> " + inst.getRightSourceValue());
                break;
            
            // 2 arg w/ destination
            case ADD, SUB, MULU, MULS, DIVU, DIVS, REMU, REMS, SHL, SHR, SAR, ROL, ROR, AND, OR, XOR:
                printer.finest(pf + getDestination(inst) + inst.getOp() + " " + inst.getLeftSourceValue() + ", " + inst.getRightSourceValue());
                break;
        
            // 4 arg w/ destination & condition
            case SELECT:
                printer.finest(pf + getDestination(inst) + inst.getOp() + " " + inst.getLeftComparisonValue() + " " + inst.getSelectCondition() + " " + inst.getRightComparisonValue() + ", " + inst.getLeftSourceValue() + ", " + inst.getRightSourceValue());
                break;
                
            // 1 arg w/ destination & argument map
            case CALLR:
                printer.finest(pf + getDestination(inst) + inst.getOp() + " " + inst.getLeftSourceValue() + getArgumentMapping(inst.getCallArgumentMapping()));
                break;
            
            // 1 arg w/ argument map
            case CALLN:
                printer.finest(pf + inst.getOp() + " " + inst.getLeftSourceValue() + getArgumentMapping(inst.getCallArgumentMapping()));
                break;
            
            default:
                printer.finest("");
        }
    }
    
    /**
     * Print the destination assignment of a linear instruction
     * @param inst
     */
    private static String getDestination(IRLinearInstruction inst) {
        return inst.getDestinationType() + " " + inst.getDestinationID() + " = ";
    }
    
    /**
     * Print a branch instruction
     * @param inst
     * @param depth
     */
    public static void printBranchInstruction(Logger printer, IRBranchInstruction inst, int depth) {
        String pf = getPrefix(depth) + inst.getOp() + " ";
        
        switch(inst.getOp()) {
            case JMP:
                printer.finest(pf + inst.getTrueTargetBlock() + getArgumentMapping(inst.getTrueArgumentMapping()));
                break;
                
            case JCC:
                printer.finest(pf + inst.getCompareLeft() + " " + inst.getCondition() + " " + inst.getCompareRight() + ", " + inst.getTrueTargetBlock() + getArgumentMapping(inst.getTrueArgumentMapping()) + ", " + inst.getFalseTargetBlock() + getArgumentMapping(inst.getFalseArgumentMapping()));
                break;
                
            case RET:
                printer.finest(pf + inst.getReturnValue());
                break;
                
            default:
                printer.finest("");
        }
    }
    
    /**
     * Print an argument mapping
     * @param map
     */
    private static String getArgumentMapping(IRArgumentMapping map) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        
        for(int i = 0; i < map.getMappingList().size(); i++) {
            if(i != 0) {
                sb.append(", ");
            }
            
            sb.append(map.getMapping(i).toString());
        }
        
        sb.append(")");
        return sb.toString();
    }
}
