package notsotiny.lang.ir;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.view.Viewer;

/**
 * Renders control-flow graphs
 */
public class IRCFGRenderer {
    
    private static Logger LOG = Logger.getLogger(IRCFGRenderer.class.getName());
    
    static {
        System.setProperty("org.graphstream.ui", "swing");
    }
    
    /**
     * Renders the CFG of a function
     * @param function
     */
    public static void renderCFG(IRFunction function) {
        String name = function.getID().toString();
        LOG.info("Rendering CFG of " + name);
        
        Graph cfg = new SingleGraph(name);
        
        cfg.setAttribute("ui.stylesheet",
            "node{\n" +
            "fill-color: #f0f0f0;\n" +
            "size: 25px, 25px;\n" +
            "text-mode:normal;\n" +
            "text-background-mode: plain;\n" +
            "text-background-color: rgba(255, 255, 255, 150);\n" +
            "text-size: 12px;\n" +
            "}"
        );
        cfg.setAttribute("ui.title", name);
        
        addToGraph(function.getBasicBlockList().get(0), cfg, new HashSet<>());
        
        /*Viewer view =*/ cfg.display();
    }
    
    /**
     * Add a bb and its successors to the graph
     * @param irbb
     * @param g
     * @param inGraph
     */
    private static void addToGraph(IRBasicBlock irbb, Graph g, Set<IRIdentifier> inGraph) {
        IRIdentifier bbID = irbb.getID();
        String bbn = bbID.toString();
        
        if(inGraph.contains(bbID)) {
            return;
        }
        
        LOG.finest(bbn);
        
        inGraph.add(bbID);
        
        g.addNode(bbn).setAttribute("ui.label", bbn);
        
        IRBranchInstruction exit = irbb.getExitInstruction();
        
        IRIdentifier tsID = exit.getTrueTargetBlock(),
                     fsID = exit.getFalseTargetBlock();
        
        if(tsID != null) {
            String tsn = tsID.toString();
            IRBasicBlock tsBB = irbb.getParentFunction().getBasicBlock(tsID);
            
            addToGraph(tsBB, g, inGraph);
            
            g.addEdge(bbn + tsn, bbn, tsn, true);
        }
        
        if(fsID != null) {
            String fsn = fsID.toString();
            IRBasicBlock fsBB = irbb.getParentFunction().getBasicBlock(fsID);
            
            addToGraph(fsBB, g, inGraph);
            
            g.addEdge(bbn + fsn, bbn, fsn, true);
        }
    }
}