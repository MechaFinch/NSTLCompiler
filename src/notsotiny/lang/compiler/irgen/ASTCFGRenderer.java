package notsotiny.lang.compiler.irgen;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.view.Viewer;

/**
 * Renders control-flow graphs
 */
public class ASTCFGRenderer {
    
    private static Logger LOG = Logger.getLogger(ASTCFGRenderer.class.getName());
    //private static ASTLogger ALOG = new ASTLogger(LOG);
    
    static {
        System.setProperty("org.graphstream.ui", "swing");
    }
    
    /**
     * Renders the CFG of a function
     * @param function
     */
    public static void renderCFG(ASTFunction function, String suffix) {
        String name = function.getHeader().getName() + suffix;
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
        
        //cfg.setStrict(false);
        //cfg.setAutoCreate(true);
        
        addToGraph(function.getBasicBlocks().get(0), cfg, new HashSet<>());
        
        /*Viewer view =*/ cfg.display();
    }
    
    /**
     * Add a bb and its successors to the graph
     * @param abb
     * @param g
     */
    private static void addToGraph(ASTBasicBlock abb, Graph g, Set<String> inGraph) {
        String abbn = abb.getName();
        
        if(inGraph.contains(abbn)) {
            return;
        }
        
        LOG.finest(abbn);
        
        inGraph.add(abbn);
        
        g.addNode(abbn).setAttribute("ui.label", abbn);
        
        ASTBasicBlock ts = abb.getTrueSuccessor(),
                      fs = abb.getFalseSuccessor();
        
        // Add successors to graph
        if(ts != null) {
            String tsn = ts.getName();
            
            //LOG.finest("true: " + tsn);
            addToGraph(ts, g, inGraph);
            
            g.addEdge(abbn + tsn, abbn, tsn, true);
        }
        
        if(fs != null) {
            String fsn = fs.getName();
            
            //LOG.finest("false: " + fsn);
            addToGraph(fs, g, inGraph);
            
            g.addEdge(abbn + fsn, abbn, fsn, true);
        }
        
    }
}
