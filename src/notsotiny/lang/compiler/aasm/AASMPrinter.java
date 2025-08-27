package notsotiny.lang.compiler.aasm;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lib.printing.Printer;

/**
 * Prints AbstractAssembly in a readable format
 */
public class AASMPrinter {
    
    /**
     * Print a module
     * @param printer
     * @param code
     * @throws IOException
     */
    public static void printModule(Printer printer, Map<IRIdentifier, List<List<AASMPart>>> code) throws IOException {
        boolean firstFunction = true;
        
        // Print each function
        for(Entry<IRIdentifier, List<List<AASMPart>>> entry : code.entrySet()) {
            // Separating line
            if(firstFunction) {
                firstFunction = false;
            } else {
                printer.println("");
            }
            
            // Label
            printer.println(entry.getKey() + ":");
            
            boolean firstGroup = true;
            
            // Print each group
            for(List<AASMPart> group : entry.getValue()) {
                if(firstGroup) {
                    // Skip $entry label
                    firstGroup = false;
                } else if(group.get(0) instanceof AASMLabel){
                    printer.println("");
                } else {
                    printer.print("-");
                }
                
                // Print each part
                for(AASMPart part : group) {
                    if(part instanceof AASMLabel lbl) {
                        printer.println(lbl + "");
                    } else {
                        printer.println("\t" + part);
                    }
                }
            }
        }
    }
    
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
