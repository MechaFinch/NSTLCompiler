package notsotiny.lang.compiler.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A structure
 * 
 * @author Mechafinch
 */
public class StructureType implements NSTLType {
    
    private String name;
    private List<String> memberNames;
    private List<NSTLType> memberTypes;
    private Map<String, Integer> memberOffsetMap;
    private Map<String, NSTLType> memberTypeMap;
    private int size;
    
    /**
     * full constructor
     * 
     * @param name
     * @param memberNames
     * @param memberTypes
     */
    public StructureType(String name, List<String> memberNames, List<NSTLType> memberTypes) {
        this.name = name;
        this.memberNames = memberNames;
        this.memberTypes = memberTypes;
        
        this.memberOffsetMap = new HashMap<>();
        this.memberTypeMap = new HashMap<>();
        
        // find size & offsets
        int s = 0;
        for(int i = 0; i < memberNames.size(); i++) {
            String member = memberNames.get(i);
            
            this.memberOffsetMap.put(member, s);
            this.memberTypeMap.put(member, memberTypes.get(i));
            s += memberTypes.get(i).getSize();
        }
        
        this.size = s;
    }
    
    /**
     * placeholder constructor
     * 
     * @param name
     */
    public StructureType(String name) {
        this.name = name;
        this.memberNames = null;
        this.memberTypes = null;
        this.memberOffsetMap = null;
        this.memberTypeMap = null;
        this.size = 0;
    }
    
    @Override
    public boolean updateSize(List<String> updatedNames) {
        if(updatedNames.contains(this.name)) return false;
        updatedNames.add(this.name);
        
        boolean changed = false;
        int s = 0;
        for(int i = 0; i < memberNames.size(); i++) {
            String member = memberNames.get(i);
            NSTLType memberType = memberTypes.get(i).getRealType();
            memberTypes.set(i, memberType);
            
            changed |= memberType.updateSize(updatedNames);
            
            this.memberOffsetMap.put(member,  s);
            s += memberType.getSize();
        }
        
        changed |= this.size != s;
        this.size = s;
        return changed;
    }
    
    /**
     * Adds members to a placeholder structure
     * 
     * @param memberNames
     * @param memberTypes
     */
    public void addMembers(List<String> memberNames, List<NSTLType> memberTypes) {
        this.memberNames = memberNames;
        this.memberTypes = memberTypes;
        
        this.memberOffsetMap = new HashMap<>();
        this.memberTypeMap = new HashMap<>();
        
        // find size & offsets
        int s = 0;
        for(int i = 0; i < memberNames.size(); i++) {
            String member = memberNames.get(i);
            
            this.memberOffsetMap.put(member, s);
            this.memberTypeMap.put(member, memberTypes.get(i).getRealType());
            s += memberTypes.get(i).getSize();
        }
        
        this.size = s;
    }
    
    /**
     * Checks for recursion in structures.
     * Recursion can occur if a structure has a member which is or contains an instance of itself, either as a structure, array, or instance.
     * 
     * @param disallowedTypes
     * @return true if recursion is detected
     */
    public boolean checkRecursion(List<String> disallowedTypes) {
        if(disallowedTypes.contains(this.name)) return true;
        int index = disallowedTypes.size();
        disallowedTypes.add(this.name);
        
        for(NSTLType t : this.memberTypes) {
            if(checkRecursion(disallowedTypes, t)) return true;
        }
        
        disallowedTypes.remove(index);
        return false;
    }
    
    /**
     * Checks recursion on a specific type
     * 
     * @param disallowedTypes
     * @param t
     * @return
     */
    private boolean checkRecursion(List<String> disallowedTypes, NSTLType t) {
        if(t instanceof StructureType st) {
            return st.checkRecursion(disallowedTypes);
        } else if(t instanceof ArrayType at) {
            return checkRecursion(disallowedTypes, at.getMemberType());
        } else {
            return false;
        }
    }

    @Override
    public int getSize() {
        return this.size;
    }
    
    public List<String> getMemberNames() { return this.memberNames; }
    public List<NSTLType> getMemberTypes() { return this.memberTypes; }
    public int getMemberOffset(String name) { return this.memberOffsetMap.get(name); }
    public NSTLType getMemberType(String name) { return this.memberTypeMap.get(name).getRealType(); }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(this.name + ": (");
        
        if(this.memberNames == null) {
            sb.append("incomplete structure");
        } else {
            for(int i = 0; i < this.memberNames.size(); i++) {
                sb.append(this.memberNames.get(i));
                sb.append(" ");
                sb.append(this.memberTypes.get(i).getName());
                sb.append(", ");
            }
            
            sb.delete(sb.length() - 2, sb.length());
        }
        
        sb.append(")");
        
        return sb.toString();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public boolean equals(NSTLType t) {
        t = t.getRealType();
        
        if(t instanceof StructureType st) {
            return st.name.equals(this.name);
        }
        
        return false;
    }
}
