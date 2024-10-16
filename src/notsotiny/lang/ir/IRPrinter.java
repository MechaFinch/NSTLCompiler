package notsotiny.lang.ir;

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
    public static void printModule(IRModule module, int depth) {
        String prefix = getPrefix(depth);
        System.out.println(prefix + "Module " + module.getName());
        
        for(IRGlobal g : module.getGlobalsList()) {
            printGlobal(g, depth + 1);
        }
        
        if(module.getGlobalsList().size() != 0) {
            System.out.println();
        }
        
        boolean first = true;
        
        for(IRFunction f : module.getFunctionsList()) {
            if(first) {
                first = false;
            } else {
                System.out.println();
            }
            
            printFunction(f, depth + 1);
        }
    }
    
    /**
     * Print a global
     * @param global
     * @param depth
     */
    public static void printGlobal(IRGlobal global, int depth) {
        IRValue v = global.getContents().get(0);
        IRType firstType = (v instanceof IRConstant c ? c.getType() : IRType.I32);
        
        if(global.getContents().size() == 1) {
            System.out.println(getPrefix(depth) + firstType + " " + global.getID() + ": " + (v instanceof IRConstant c ? c.getValue() : v));
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
                System.out.print(getPrefix(depth) + firstType + "[] " + global.getID() + ": \"");
                for(IRValue v2 : global.getContents()) {
                    System.out.print((char)(((IRConstant) v2).getValue()));
                }
                System.out.println("\"");
            } else if(array) {
                // All same type, not printable
                System.out.print(getPrefix(depth) + firstType + "[] " + global.getID() + ": ");
                
                boolean first = true;
                for(IRValue v2 : global.getContents()) {
                    if(first) {
                        first = false;
                    } else {
                        System.out.print(", ");
                    }
                    
                    if(v2 instanceof IRConstant c) {
                        System.out.print(c.getValue());
                    } else {
                        System.out.print(v2);
                    }
                }
                
                System.out.println();
            } else {
                // Mixed types
                String pf = getPrefix(depth + 1);
                System.out.println(getPrefix(depth) + global.getID() + ":");
                
                for(IRValue v2 : global.getContents()) {
                    if(v2 instanceof IRConstant c) {
                        System.out.println(pf + c.getType() + " " + c.getValue());
                    } else {
                        System.out.println(pf + IRType.I32 + " " + v2);
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
    public static void printFunction(IRFunction function, int depth) {
        String pf = getPrefix(depth);
        System.out.print(pf + (function.isExternal() ? "external " : "") + "func " + function.getReturnType() + " " + function.getID());
        printArgumentList(function.getArguments());
        System.out.println(" {");
        
        boolean first = true;
        
        for(IRBasicBlock bb : function.getBasicBlockList()) {
            if(first) {
                first = false;
            } else {
                System.out.println();
            }
            
            printBasicBlock(bb, depth + 1);
        }
        
        System.out.println(pf + "}");
    }
    
    /**
     * Print an argument list
     * @param alist
     */
    private static void printArgumentList(IRArgumentList alist) {
        System.out.print("(");
        
        for(int i = 0; i < alist.getArgumentCount(); i++) {
            if(i != 0) {
                System.out.print(", ");
            }
            System.out.print(alist.getType(i) + " " + alist.getName(i));
        }
        
        System.out.print(")");
    }
    
    /**
     * Print a basic block
     * @param bb
     * @param depth
     */
    public static void printBasicBlock(IRBasicBlock bb, int depth) {
        System.out.print(getPrefix(depth) + bb.getID());
        printArgumentList(bb.getArgumentList());
        System.out.println(":");
        
        for(IRLinearInstruction li : bb.getInstructions()) {
            printLinearInstruction(li, depth + 1);
        }
        
        printBranchInstruction(bb.getExitInstruction(), depth + 1);
    }
    
    /**
     * Print a linear instruction
     * @param inst
     * @param depth
     */
    public static void printLinearInstruction(IRLinearInstruction inst, int depth) {
        System.out.print(getPrefix(depth));
        
        switch(inst.getOp()) {
            // 1 arg w/ destination
            case TRUNC, SX, ZX, LOAD, NOT, NEG:
                printDestination(inst);
                System.out.println(inst.getOp() + " " + inst.getLeftSourceValue());
                break;
            
            // 2 arg
            case STORE:
                System.out.println(inst.getOp() + " " + inst.getLeftSourceValue() + " -> " + inst.getRightSourceValue());
                break;
            
            // 2 arg w/ destination
            case ADD, SUB, MULU, MULS, DIVU, DIVS, REMU, REMS, SHL, SHR, SAR, ROL, ROR, AND, OR, XOR:
                printDestination(inst);
                System.out.println(inst.getOp() + " " + inst.getLeftSourceValue() + ", " + inst.getRightSourceValue());
                break;
        
            // 4 arg w/ destination & condition
            case SELECT:
                printDestination(inst);
                System.out.println(inst.getOp() + " " + inst.getLeftComparisonValue() + " " + inst.getSelectCondition() + " " + inst.getRightComparisonValue() + ", " + inst.getLeftSourceValue() + ", " + inst.getRightSourceValue());
                break;
                
            // 1 arg w/ destination & argument map
            case CALLR:
                printDestination(inst);
                System.out.print(inst.getOp() + " " + inst.getLeftSourceValue());
                printArgumentMapping(inst.getCallArgumentMapping());
                System.out.println();
                break;
            
            // 1 arg w/ argument map
            case CALLN:
                System.out.print(inst.getOp() + " " + inst.getLeftSourceValue());
                printArgumentMapping(inst.getCallArgumentMapping());
                System.out.println();
                break;
            
            default:
                System.out.println();
        }
    }
    
    /**
     * Print the destination assignment of a linear instruction
     * @param inst
     */
    private static void printDestination(IRLinearInstruction inst) {
        System.out.print(inst.getDestinationType() + " " + inst.getDestinationID() + " = ");
    }
    
    /**
     * Print a branch instruction
     * @param inst
     * @param depth
     */
    public static void printBranchInstruction(IRBranchInstruction inst, int depth) {
        System.out.print(getPrefix(depth) + inst.getOp() + " ");
        
        switch(inst.getOp()) {
            case JMP:
                System.out.print(inst.getTrueTargetBlock());
                printArgumentMapping(inst.getTrueArgumentMapping());
                System.out.println();
                break;
                
            case JCC:
                System.out.print(inst.getCompareLeft() + " " + inst.getCondition() + " " + inst.getCompareRight() + ", " + inst.getTrueTargetBlock());
                printArgumentMapping(inst.getTrueArgumentMapping());
                System.out.print(", " + inst.getFalseTargetBlock());
                printArgumentMapping(inst.getFalseArgumentMapping());
                System.out.println();
                break;
                
            case RET:
                System.out.println(inst.getReturnValue());
                break;
                
            default:
                System.out.println();
        }
    }
    
    /**
     * Print an argument mapping
     * @param map
     */
    private static void printArgumentMapping(IRArgumentMapping map) {
        System.out.print("(");
        
        for(int i = 0; i < map.getMappingList().size(); i++) {
            if(i != 0) {
                System.out.print(", ");
            }
            
            System.out.print(map.getMapping(i));
        }
        
        System.out.print(")");
    }
}
