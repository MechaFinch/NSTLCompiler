package notsotiny.lang.compiler.codegen.alloc;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import notsotiny.asm.Register;
import notsotiny.lang.compiler.aasm.AASMMachineRegister;
import notsotiny.lang.ir.parts.IRIdentifier;

/**
 * Helper class with data about machine registers
 */
public class MachineRegisters {
    
    public static final IRIdentifier ID_BP = new AASMMachineRegister(Register.BP).id(),
                                     ID_SP = new AASMMachineRegister(Register.SP).id();
    
    private static Map<Register, Set<Register>> registerAliasSets;
    private static Map<RARegisterClass, Set<Register>> classAliasSets;
    
    private static Map<RARegisterClass, RAVertex> vertexMap;
    
    private static Map<RARegisterClass, Map<RAVertex, Integer>> boundMap;
    
    private static Map<RARegisterClass, Map<RARegisterClass, Integer>> worstMap;
    
    static {
        // Populate alias sets
        registerAliasSets = new HashMap<>();
        
        registerAliasSets.put(Register.DA, EnumSet.of(Register.DA, Register.A, Register.D, Register.AH, Register.AL, Register.DH, Register.DL));
        registerAliasSets.put(Register.BC, EnumSet.of(Register.BC, Register.B, Register.C, Register.BH, Register.BL, Register.CH, Register.CL));
        registerAliasSets.put(Register.JI, EnumSet.of(Register.JI, Register.I, Register.J));
        registerAliasSets.put(Register.LK, EnumSet.of(Register.LK, Register.L, Register.K));
        registerAliasSets.put(Register.XP, EnumSet.of(Register.XP));
        registerAliasSets.put(Register.YP, EnumSet.of(Register.YP));
        registerAliasSets.put(Register.BP, EnumSet.of(Register.BP));
        registerAliasSets.put(Register.SP, EnumSet.of(Register.SP));
        
        registerAliasSets.put(Register.A, EnumSet.of(Register.DA, Register.A, Register.AH, Register.AL));
        registerAliasSets.put(Register.B, EnumSet.of(Register.BC, Register.B, Register.BH, Register.BL));
        registerAliasSets.put(Register.C, EnumSet.of(Register.BC, Register.C, Register.CH, Register.CL));
        registerAliasSets.put(Register.D, EnumSet.of(Register.DA, Register.D, Register.DH, Register.DL));
        registerAliasSets.put(Register.I, EnumSet.of(Register.JI, Register.I));
        registerAliasSets.put(Register.J, EnumSet.of(Register.JI, Register.J));
        registerAliasSets.put(Register.K, EnumSet.of(Register.LK, Register.K));
        registerAliasSets.put(Register.L, EnumSet.of(Register.LK, Register.L));
        
        registerAliasSets.put(Register.AH, EnumSet.of(Register.DA, Register.A, Register.AH));
        registerAliasSets.put(Register.AL, EnumSet.of(Register.DA, Register.A, Register.AL));
        registerAliasSets.put(Register.BH, EnumSet.of(Register.BC, Register.B, Register.BH));
        registerAliasSets.put(Register.BL, EnumSet.of(Register.BC, Register.B, Register.BL));
        registerAliasSets.put(Register.CH, EnumSet.of(Register.BC, Register.C, Register.CH));
        registerAliasSets.put(Register.CL, EnumSet.of(Register.BC, Register.C, Register.CL));
        registerAliasSets.put(Register.DH, EnumSet.of(Register.DA, Register.D, Register.DH));
        registerAliasSets.put(Register.DL, EnumSet.of(Register.DA, Register.D, Register.DL));
        
        classAliasSets = new HashMap<>();
        
        for(RARegisterClass rClass : RARegisterClass.values()) {
            Set<Register> aliases = EnumSet.noneOf(Register.class);
            
            for(Register r : rClass.registers()) {
                aliases.addAll(registerAliasSets.get(r));
            }
            
            classAliasSets.put(rClass, aliases);
        }
        
        // Populate class tree
        vertexMap = new HashMap<>();
        
        // Classes: I32, I32_HALF, I16, I16_HALF, I8
        // I16_HALF ~ I8
        // I32_HALF ~ I16
        // I16_HALF/I8 [ I32_HALF/I16
        // I32_HALF/I16 [ I32
        
        RAVertex vertLarge = new RAVertex(EnumSet.of(RARegisterClass.I32), null);
        vertexMap.put(RARegisterClass.I32, vertLarge);
        
        RAVertex vertMedium = new RAVertex(EnumSet.of(RARegisterClass.I32_HALF, RARegisterClass.I16), vertLarge);
        vertexMap.put(RARegisterClass.I32_HALF, vertMedium);
        vertexMap.put(RARegisterClass.I16, vertMedium);
        
        RAVertex vertSmall = new RAVertex(EnumSet.of(RARegisterClass.I16_HALF, RARegisterClass.I8), vertMedium);
        vertexMap.put(RARegisterClass.I16_HALF, vertSmall);
        vertexMap.put(RARegisterClass.I8, vertSmall);
        
        // Populate bound map
        boundMap = new EnumMap<>(RARegisterClass.class);
        
        for(RARegisterClass rClass : vertexMap.keySet()) {
            Map<RAVertex, Integer> submap = new HashMap<>();
            
            for(RAVertex v : vertexMap.values()) {
                // Given that classes in the same vertex are alias-equivalent,
                // bound(N, CC) = size(intersection of N and any C in CC)
                Set<Register> vAlias = EnumSet.copyOf(classAliasSets.get(v.classes().iterator().next()));
                vAlias.retainAll(rClass.registers());
                
                submap.put(v, vAlias.size());
            }
            
            boundMap.put(rClass, submap);
        }
        
        // Populate worst1 map
        worstMap = new EnumMap<>(RARegisterClass.class);
        
        for(RARegisterClass nClass : vertexMap.keySet()) {
            Map<RARegisterClass, Integer> submap = new EnumMap<>(RARegisterClass.class);
            
            for(RARegisterClass cClass : vertexMap.keySet()) {
                // worst1(N, C) = given register S in C max size(intersection of N and alias(S))
                int worst = 0;
                
                for(Register s : cClass.registers()) {
                    Set<Register> sAlias = EnumSet.copyOf(registerAliasSets.get(s));
                    sAlias.retainAll(nClass.registers());
                    
                    int disp = sAlias.size();
                    
                    if(disp > worst) {
                        worst = disp;
                    }
                }
                
                submap.put(cClass, worst);
            }
            
            worstMap.put(nClass, submap);
        }
    }
    
    public static Collection<RAVertex> vertices() {
        return vertexMap.values();
    }
    
    /**
     * Returns worst1(N, C)
     * @param nClass
     * @param cClass
     * @return
     */
    public static int worst1(RARegisterClass nClass, RARegisterClass cClass) {
        return worstMap.get(nClass).get(cClass);
    }
    
    /**
     * Returns the maximum contribution to squeeze* to n by classes in v
     * @param nClass
     * @param v
     * @return
     */
    public static int bound(RARegisterClass nClass, RAVertex v) {
        return boundMap.get(nClass).get(v);
    }
    
    /**
     * Returns the class tree vertex of R
     * @param r
     * @return
     */
    public static RAVertex vertex(RARegisterClass rClass) {
        return vertexMap.get(rClass);
    }
    
    /**
     * Returns the alias set of R
     * @param r
     * @return
     */
    public static Set<Register> aliasSet(Register r) {
        return registerAliasSets.get(r);
    }
    
    /**
     * Returns the alias set of register class R
     * @param rClass
     * @return
     */
    public static Set<Register> aliasSet(RARegisterClass rClass) {
        return classAliasSets.get(rClass);
    }
    
    /**
     * Returns the given half of r
     * @param r
     * @param upper
     * @return
     */
    public static Register half(Register r, boolean upper) {
        if(upper) {
            return upperHalf(r);
        } else {
            return lowerHalf(r);
        }
    }
    
    /**
     * Returns the upper half of r
     * @param r
     * @return
     */
    public static Register upperHalf(Register r) {
        return switch(r) {
            case DA -> Register.D;
            case BC -> Register.B;
            case JI -> Register.J;
            case LK -> Register.L;
            case A  -> Register.AH;
            case B  -> Register.BH;
            case C  -> Register.CH;
            case D  -> Register.DH;
            default -> throw new IllegalArgumentException("Cannot take half of " + r);
        };
    }
    
    /**
     * Returns the lower half of r
     * @param r
     * @return
     */
    public static Register lowerHalf(Register r) {
        return switch(r) {
            case DA -> Register.A;
            case BC -> Register.C;
            case JI -> Register.I;
            case LK -> Register.K;
            case A  -> Register.AL;
            case B  -> Register.BL;
            case C  -> Register.CL;
            case D  -> Register.DL;
            default -> throw new IllegalArgumentException("Cannot take half of " + r);
        };
    }
    
}
