package notsotiny.lang.ir.util;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import notsotiny.lang.ir.parts.IRArgumentList;
import notsotiny.lang.ir.parts.IRArgumentMapping;
import notsotiny.lang.ir.parts.IRBasicBlock;
import notsotiny.lang.ir.parts.IRBranchInstruction;
import notsotiny.lang.ir.parts.IRConstant;
import notsotiny.lang.ir.parts.IRFunction;
import notsotiny.lang.ir.parts.IRGlobal;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRLinearInstruction;
import notsotiny.lang.ir.parts.IRModule;
import notsotiny.lang.ir.parts.IRType;
import notsotiny.lang.ir.parts.IRValue;
import notsotiny.lang.util.Printer;

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
     * @throws IOException 
     */
    public static void printModule(Printer printer, IRModule module, int depth) throws IOException { 
        String prefix = getPrefix(depth);
        printer.println(prefix + "Module " + module.getName() + " {");
        
        // Order:
        // Constants
        // Variables
        // External functions
        // Internal functions
        
        // Constants
        boolean hasConstants = false;
        for(IRGlobal g : module.getGlobals().values()) {
            if(g.isConstant()) {
                hasConstants = true;
                printGlobal(printer, g, depth + 1);
            }
        }
        
        if(hasConstants) {
            printer.println("");
        }
        
        // Globals
        boolean hasVariables = false;
        for(IRGlobal g : module.getGlobals().values()) {
            if(!g.isConstant()) {
                hasVariables = true;
                printGlobal(printer, g, depth + 1);
            }
        }
        
        if(hasVariables) {
            printer.println("");
        }
        
        // External Functions
        boolean hasExternals = false;
        
        for(IRFunction f : module.getFunctions().values()) {
            if(f.isExternal()) {
                hasExternals = true;
                printFunction(printer, f, depth + 1);
            }
        }
        
        if(hasExternals) {
            printer.println("");
        }
        
        // Internal functions
        boolean first = true;
        
        for(IRFunction f : module.getFunctions().values()) {
            if(f.isExternal()) {
                continue;
            }
            
            if(first) {
                first = false;
            } else {
                printer.println("");
            }
            
            printFunction(printer, f, depth + 1);
        }
        
        printer.println(prefix + "}");
    }
    
    /**
     * Print a global
     * @param global
     * @param depth
     * @throws IOException 
     */
    public static void printGlobal(Printer printer, IRGlobal global, int depth) throws IOException {
        IRValue v = global.getContents().get(0);
        IRType firstType = (v instanceof IRConstant c ? c.getType() : IRType.I32);
        
        String prefix = getPrefix(depth) + (global.isConstant() ? "const " : "");
        
        if(global.getContents().size() == 1) {
            printer.println(prefix + firstType + " " + global.getID() + ": " + (v instanceof IRConstant c ? c.getValue() : v));
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
                StringBuilder sb = new StringBuilder(prefix + firstType + "[] " + global.getID() + ": \"");
                for(IRValue v2 : global.getContents()) {
                    sb.append((char)(((IRConstant) v2).getValue()));
                }
                printer.println(sb.toString() + "\"");
            } else if(array) {
                // All same type, not printable
                StringBuilder sb = new StringBuilder(prefix + firstType + "[] " + global.getID() + ": ");
                
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
                
                printer.println(sb.toString());
            } else {
                // Mixed types
                String pf = getPrefix(depth + 1);
                printer.println(prefix + global.getID() + ":");
                
                for(IRValue v2 : global.getContents()) {
                    if(v2 instanceof IRConstant c) {
                        printer.println(pf + c.getType() + " " + c.getValue());
                    } else {
                        printer.println(pf + IRType.I32 + " " + v2);
                    }
                }
            }
        }
    }
    
    /**
     * Print a function
     * @param function
     * @param depth
     * @throws IOException 
     */
    public static void printFunction(Printer printer, IRFunction function, int depth) throws IOException {
        String pf = getPrefix(depth);
        
        if(function.isExternal()) {
            printer.println(pf + "external func " + function.getReturnType() + " " + function.getID() + getArgumentList(function.getArguments()));
            return;
        }
        
        printer.println(pf + "func " + function.getReturnType() + " " + function.getID() + getArgumentList(function.getArguments()) + " {");
                    
        boolean first = true;
        
        for(IRBasicBlock bb : function.getBasicBlockList()) {
            if(first) {
                first = false;
            } else {
                printer.println("");
            }
            
            printBasicBlock(printer, bb, depth + 1);
        }
        
        printer.println(pf + "}");
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
     * @throws IOException 
     */
    public static void printBasicBlock(Printer printer, IRBasicBlock bb, int depth) throws IOException {
        printer.println(getPrefix(depth) + bb.getID() + getArgumentList(bb.getArgumentList()) + ":");
        
        for(IRLinearInstruction li : bb.getInstructions()) {
            printLinearInstruction(printer, li, depth + 1);
        }
        
        printBranchInstruction(printer, bb.getExitInstruction(), depth + 1);
    }
    
    /**
     * Print a linear instruction
     * @param inst
     * @param depth
     * @throws IOException 
     */
    public static void printLinearInstruction(Printer printer, IRLinearInstruction inst, int depth) throws IOException {
        String pf = getPrefix(depth);
        
        switch(inst.getOp()) {
            // 1 arg w/ destination
            case TRUNC, SX, ZX, LOAD, STACK, NOT, NEG:
                printer.println(pf + getDestination(inst) + inst.getOp() + " " + inst.getLeftSourceValue());
                break;
            
            // 2 arg
            case STORE:
                printer.println(pf + inst.getOp() + " " + inst.getLeftSourceValue() + " -> " + inst.getRightSourceValue());
                break;
            
            // 2 arg w/ destination
            case ADD, SUB, MULU, MULS, DIVU, DIVS, REMU, REMS, SHL, SHR, SAR, ROL, ROR, AND, OR, XOR:
                printer.println(pf + getDestination(inst) + inst.getOp() + " " + inst.getLeftSourceValue() + ", " + inst.getRightSourceValue());
                break;
        
            // 4 arg w/ destination & condition
            case SELECT:
                printer.println(pf + getDestination(inst) + inst.getOp() + " " + inst.getLeftComparisonValue() + " " + inst.getSelectCondition() + " " + inst.getRightComparisonValue() + ", " + inst.getLeftSourceValue() + ", " + inst.getRightSourceValue());
                break;
                
            // 1 arg w/ destination & argument map
            case CALLR:
                printer.println(pf + getDestination(inst) + inst.getOp() + " " + inst.getLeftSourceValue() + getArgumentMapping(inst.getCallArgumentMapping()));
                break;
            
            // 1 arg w/ argument map
            case CALLN:
                printer.println(pf + inst.getOp() + " " + inst.getLeftSourceValue() + getArgumentMapping(inst.getCallArgumentMapping()));
                break;
            
            default:
                printer.println("");
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
     * @throws IOException 
     */
    public static void printBranchInstruction(Printer printer, IRBranchInstruction inst, int depth) throws IOException {
        if(inst == null) {
            return;
        }
        
        String pf = getPrefix(depth) + inst.getOp() + " ";
        
        switch(inst.getOp()) {
            case JMP:
                printer.println(pf + inst.getTrueTargetBlock() + getArgumentMapping(inst.getTrueArgumentMapping()));
                break;
                
            case JCC:
                printer.println(pf + inst.getCompareLeft() + " " + inst.getCondition() + " " + inst.getCompareRight() + ", " + inst.getTrueTargetBlock() + getArgumentMapping(inst.getTrueArgumentMapping()) + ", " + inst.getFalseTargetBlock() + getArgumentMapping(inst.getFalseArgumentMapping()));
                break;
                
            case RET:
                printer.println(pf + inst.getReturnValue());
                break;
                
            default:
                printer.println("");
        }
    }
    
    /**
     * Print an argument mapping
     * @param map
     */
    private static String getArgumentMapping(IRArgumentMapping map) {
        if(map == null) {
            return "(null)";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        
        int i = 0;
        for(IRIdentifier id : map.getMap().keySet()) {
            if(i != 0) {
                sb.append(", ");
            }
            
            sb.append(id.toString());
            sb.append(" = ");
            sb.append("" + map.getMapping(id));
            
            i++;
        }
        
        sb.append(")");
        return sb.toString();
    }
}
