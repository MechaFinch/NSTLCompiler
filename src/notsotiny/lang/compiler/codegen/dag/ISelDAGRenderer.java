package notsotiny.lang.compiler.codegen.dag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;

/**
 * Renders instruction selection DAGs
 */
public class ISelDAGRenderer {
    
    private static Logger LOG = Logger.getLogger(ISelDAGRenderer.class.getName());
    
    static {
        System.setProperty("org.graphstream.ui", "swing");
    }
    
    private static class Unique {
        
        private int number = 0;
        
        public int get() { return this.number++; }
        
    }
    
    /**
     * Renders a DAG
     * @param dag
     * @param name
     */
    public static void renderDAG(ISelDAG dag, String name) {
        LOG.info("Rendering DAG of " + name);
        
        Graph dagGraph = new SingleGraph(name);
        
        dagGraph.setAttribute("ui.stylesheet",
            "node{\n" +
            "fill-color: #404040;\n" +
            "size: 25px, 25px;\n" +
            "text-mode:normal;\n" +
            "text-background-mode: plain;\n" +
            "text-background-color: rgba(255, 255, 255, 150);\n" +
            "text-size: 12px;\n" +
            "}"
        );
        dagGraph.setAttribute("ui.title", name);
        
        Map<ISelDAGNode, String> inGraph = new HashMap<>();
        Unique ugen = new Unique();
        
        // Fill graph
        for(ISelDAGNode terminator : dag.getTerminators()) {
            addToGraph(terminator, dagGraph, inGraph, ugen);
        }
        
        dagGraph.display();
    }
    
    private static void addToGraph(ISelDAGNode node, Graph g, Map<ISelDAGNode, String> inGraph, Unique ugen) {
        if(inGraph.containsKey(node)) {
            return;
        }
        
        // Generate name & label
        String nodeName = "node" + ugen.get(),
               nodeLabel = "",
               nodeStyle = "";
        
        if(node instanceof ISelDAGProducerNode pn) {
            nodeLabel = pn.getProducedType() + " " + pn.getOperation() + " " + pn.getProducedValue();
            nodeStyle = "shape: diamond;";
            
            // TODO: Additional information according to operation
            switch(pn.getOperation()) {
                case IN, VALUE:
                    nodeStyle = "shape: circle;";
                    break;
                
                default:
            }
        } else if(node instanceof ISelDAGTerminatorNode tn) {
            nodeLabel = tn.getOperation() + "";
            nodeStyle = "shape: box;";
            
            // TODO: Additional information according to operation
            switch(tn.getOperation()) {
                case JMP:
                    nodeLabel += " " + tn.getTrueTargetBlock();
                    break;
                    
                case JCC:
                    nodeLabel += " " + tn.getCondition() + " " + tn.getTrueTargetBlock() + " " + tn.getFalseTargetBlock();
                    break;
                
                case OUT:
                    nodeLabel += " " + tn.getTarget();
                    break;
                
                default:
            }
        }
        
        //LOG.finest(nodeName + ": " + nodeLabel);
        
        if(node == null) {
            return;
        }
        
        // Add node to graph
        inGraph.put(node, nodeName);
        Node gNode = g.addNode(nodeName);
        gNode.setAttribute("ui.label", nodeLabel);
        gNode.setAttribute("ui.style", nodeStyle);
        
        // Process inputs
        List<ISelDAGProducerNode> inputs = node.getInputNodes();
        
        for(int i = 0; i < inputs.size(); i++) {
            // Get input
            ISelDAGNode inputNode = inputs.get(i);
            
            // Add to graph if not present, get name in graph
            addToGraph(inputNode, g, inGraph, ugen);
            String inputName = inGraph.get(inputNode);
            
            // TODO: label according to role (argument mappings in particular) 
            String edgeLabel = "" + i;
            
            // Create edge
            try {
                g.addEdge(inputName + nodeName, inputName, nodeName, true).setAttribute("ui.label", edgeLabel);
            } catch(Exception e) {
                LOG.finest(e.getMessage());
            }
        }
        
        // Process chain
        if(node.getChain() != null) {
            ISelDAGNode chainNode = node.getChain();
            
            // Add if not present
            addToGraph(chainNode, g, inGraph, ugen);
            String chainName = inGraph.get(chainNode);
            
            // Create edge
            try {
                g.addEdge(chainName + nodeName + "chain", chainName, nodeName, true)
                 .setAttribute("ui.style", "stroke-mode: dots;\nfill-mode: none;\nsize: 0px;\nstroke-width: 2px;");
            } catch(Exception e) {
                LOG.finest(e.getMessage());
            }
        }
    }
    
}
