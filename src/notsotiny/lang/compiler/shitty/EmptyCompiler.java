package notsotiny.lang.compiler.shitty;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import asmlib.util.FileLocator;
import fr.cenotelie.hime.redist.ASTNode;
import notsotiny.asm.Assembler.AssemblyObject;
import notsotiny.asm.components.Component;
import notsotiny.lang.compiler.NSTCompiler;

/**
 * Compiler template
 * 
 * @author Mechafinch
 */
public class EmptyCompiler implements NSTCompiler {
    
    private static Logger LOG = Logger.getLogger(EmptyCompiler.class.getName());

    public EmptyCompiler() {
        
    }
    
    @Override
    public AssemblyObject compile(ASTNode astRoot, String defaultLibName, FileLocator locator) {
        
        // AssemblyObject parts
        List<Component> allInstructions = new ArrayList<>();
        Map<String, Integer> labelIndexMap = new HashMap<>();
        String libraryName = defaultLibName;
        HashMap<File, String> libraryNamesMap = new HashMap<>();
        HashMap<String, List<Integer>> incomingReferences = new HashMap<>();
        HashMap<String, Integer> outgoingReferences = new HashMap<>(),
                                 incomingReferenceWidths = new HashMap<>(),
                                 outgoingReferenceWidths = new HashMap<>();
        
        return new AssemblyObject(allInstructions, labelIndexMap, libraryName, libraryNamesMap, incomingReferences, outgoingReferences, incomingReferenceWidths, outgoingReferenceWidths);
    }
    
}
