package notsotiny.lang.compiler.codegen.dag;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import notsotiny.lang.compiler.aasm.AASMPart;
import notsotiny.lang.compiler.codegen.pattern.ISelMatchData;

/**
 * A tile that covers the instruction selection DAG
 * @param rootNode node matching the root
 * @param coveredNodes Set of nodes covered by the tile
 * @param inputNodes Set of nodes used as inputs by the tile
 * @param chainNode chain
 * @param aasm Abstract assembly produced by the match
 * @param sourceMatch Match which produced the tile
 */
public record ISelDAGTile(ISelDAGNode rootNode, Set<ISelDAGNode> coveredNodes, Set<ISelDAGNode> inputNodes, ISelDAGNode chainNode, List<AASMPart> aasm, ISelMatchData sourceMatch) {
    
    public ISelDAGTile {
        // Infer chain if not present
        if(chainNode == null) {
            // Only one outgoing chain allowed, so take the first outgoing chain as the tile's chain
            // If there're multiple, the tile will be rejected later, so no exception thrown
            for(ISelDAGNode node : coveredNodes) {
                if(node.getChain() != null && !coveredNodes.contains(node.getChain())) {
                    chainNode = node.getChain();
                    break;
                }
            }
        }
        
        // coveredNodes must be modifiable
        try {
            coveredNodes.remove(rootNode);
            coveredNodes.add(rootNode);
        } catch(UnsupportedOperationException e) {
            coveredNodes = new HashSet<>(coveredNodes);
            coveredNodes.add(rootNode); // in case add failed but not remove
        }
    }
    
    public ISelDAGTile(ISelDAGNode rootNode, Set<ISelDAGNode> coveredNodes, Set<ISelDAGNode> inputNodes, List<AASMPart> aasm, ISelMatchData sourceMatch) {
        this(rootNode, coveredNodes, inputNodes, null, aasm, null);
    }
    
    /**
     * No-source constructor
     * @param rootNode
     * @param coveredNodes
     * @param inputNodes
     * @param chainNode
     * @param aasm
     */
    public ISelDAGTile(ISelDAGNode rootNode, Set<ISelDAGNode> coveredNodes, Set<ISelDAGNode> inputNodes, List<AASMPart> aasm) {
        this(rootNode, coveredNodes, inputNodes, aasm, null);
    }
    
}
