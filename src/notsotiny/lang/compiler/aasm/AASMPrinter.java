package notsotiny.lang.compiler.aasm;

import java.util.List;

/**
 * Prints AbstractAssembly in a readable format
 */
public class AASMPrinter {
    
    /**
     * @param aasm
     */
    public static String getAASMString(List<AASMPart> aasm) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        boolean first = true;
        for(AASMPart part : aasm) {
            if(!first) {
                sb.append("; ");
            }
            
            first = false;
            sb.append(part);
        }
        
        sb.append("}");
        return sb.toString();
    }
    
}
