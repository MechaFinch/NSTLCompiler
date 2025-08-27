package notsotiny.lang.compiler.codegen.alloc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.graphstream.graph.Edge;
import org.graphstream.graph.ElementNotFoundException;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;

import notsotiny.sim.Register;
import notsotiny.lang.ir.parts.IRIdentifierClass;

/**
 * Renders register allocation interference graphs
 */
public class RAIGRenderer {
    
    private static Logger LOG = Logger.getLogger(RAIGRenderer.class.getName());
    
    private static Map<Register, String> registerColorMap;
    
    static {
        System.setProperty("org.graphstream.ui", "swing");
        
        registerColorMap = new HashMap<>();
        registerColorMap.put(Register.NONE, "black");
        registerColorMap.put(Register.A, "#008000");
        registerColorMap.put(Register.AH, "#2f4f4f");
        registerColorMap.put(Register.AL, "#556b2f");
        registerColorMap.put(Register.B, "#000080");
        registerColorMap.put(Register.BH, "#a52a2a");
        registerColorMap.put(Register.BL, "#483d8b");
        registerColorMap.put(Register.C, "#00fa9a");
        registerColorMap.put(Register.CH, "#9acd32");
        registerColorMap.put(Register.CL, "#66cdaa");
        registerColorMap.put(Register.D, "#1e90ff");
        registerColorMap.put(Register.DH, "#00bfff");
        registerColorMap.put(Register.DL, "#ee82ee");
        registerColorMap.put(Register.I, "#db7093");
        registerColorMap.put(Register.J, "#eee8aa");
        registerColorMap.put(Register.K, "#ff1493");
        registerColorMap.put(Register.L, "#ffa07a");
        registerColorMap.put(Register.DA, "#ff0000");
        registerColorMap.put(Register.BC, "#ffff00");
        registerColorMap.put(Register.JI, "#0000ff");
        registerColorMap.put(Register.LK, "#ff00ff");
        registerColorMap.put(Register.XP, "#ff8c00");
        registerColorMap.put(Register.YP, "#7cfc00");
        
    }
    
    /**
     * Renders a register allocation interference graph
     * @param graph
     * @param name
     */
    public static void renderRAIG(RAInterferenceGraph graph, String name) {
        LOG.info("Rendering interference graph " + name);
        
        Graph raigGraph = new SingleGraph(name);
        
        raigGraph.setAttribute("ui.stylesheet",
            "node{\n" +
            "fill-color: #303030;\n" +
            "size: 25px, 25px;\n" +
            "text-mode:normal;\n" +
            "text-background-mode: plain;\n" +
            "text-background-color: rgba(255, 255, 255, 150);\n" +
            "text-size: 12px;\n" +
            "}\n" +
            "graph{\n" + 
            "fill-color: #A0A0A0;\n" +
            "}"
        );
        raigGraph.setAttribute("ui.title", name);
        
        Map<RAIGNode, String> inGraph = new HashMap<>();
        
        // Fill graph
        for(RAIGNode node : graph.getAllNodes()) {
            addToGraph(node, raigGraph, inGraph);
        }
        
        raigGraph.display();
    }
    
    private static void addToGraph(RAIGNode origNode, Graph g, Map<RAIGNode, String> inGraph) {
        if(origNode == null) {
            return;
        }
        
        RAIGNode node = origNode.getAlias();
        
        if(inGraph.containsKey(node)) {
            inGraph.put(origNode, inGraph.get(node));
            return;
        }
        
        // machine registers with no interference are ignored
        if(node.getIdentifier().getIDClass() == IRIdentifierClass.SPECIAL && node.getInterferingNodes().size() == 0 && node.getMoves().size() == 0) {
            //LOG.finest("Ignored: " + node.getIdentifier());
            return;
        }
        
        // Generate name & label
        String nodeName = node.getIdentifier() + "",
               nodeLabel = "",
               nodeStyle = "fill-color: " + registerColorMap.get(node.getColoring()) + ";";
        
        if(node.getColoring() == Register.NONE) {
            nodeLabel = node.getIdentifier() + "";
        } else {
            nodeLabel = node.getIdentifier() + " - " + node.getColoring();
        }
        
        inGraph.put(node, nodeName);
        Node gNode = g.addNode(nodeName);
        gNode.setAttribute("ui.label", nodeLabel);
        gNode.setAttribute("ui.style", nodeStyle);
        
        // Add interference
        for(RAIGNode interferer : node.getInterferingNodes()) {
            addToGraph(interferer, g, inGraph);
            String intName = inGraph.get(interferer);
            
            try {
                g.addEdge(intName + nodeName, intName, nodeName, false)
                .setAttribute("ui.style", "size: 2px;");
            } catch(Exception e) {
                //LOG.finest(e.getMessage());
            }
        }
        
        // Add moves
        for(RAMove move : node.getMoves()) {
            if(move.source().getAlias() == node) {
                RAIGNode dest = move.destination().getAlias();
                addToGraph(dest, g, inGraph);
                
                String dstName = inGraph.get(dest);
                
                //LOG.info(nodeName + " -> " + dstName);
                
                try {
                    // Move-only edge
                    Edge e = g.addEdge(nodeName + "->" + dstName, nodeName, dstName, true);
                    e.setAttribute("ui.style", "stroke-mode: dots;\nfill-mode: none;\nsize: 0px;\nstroke-width: 2px;");
                    e.setAttribute("layout.weight", 1.5);
                } catch(ElementNotFoundException e) {
                    LOG.finest("Not found: " + move.destination().getIdentifier());
                } catch(Exception e) {
                    LOG.finest(e + "");
                    
                    // Edge already existed. Move-interfere edge
                    g.removeEdge(nodeName, dstName);
                    g.addEdge(nodeName + "-->" + dstName, nodeName, dstName, true)
                    .setAttribute("ui.style", "size: 2px;");
                }
            }
        }
    }
    
}
