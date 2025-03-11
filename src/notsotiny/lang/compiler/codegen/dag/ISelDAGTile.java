package notsotiny.lang.compiler.codegen.dag;

import java.util.List;
import java.util.Set;

import notsotiny.lang.compiler.aasm.AASMPart;
import notsotiny.lang.compiler.codegen.pattern.ISelMatchData;

/**
 * A tile that covers the instruction selection DAG
 * @param rootNode node matching the root
 * @param coveredNodes List of nodes covered by the tile
 * @param aasm Abstract assembly produced by the match
 * @param sourceMatch Match which produced the tile
 */
public record ISelDAGTile(ISelDAGNode rootNode, Set<ISelDAGNode> coveredNodes, List<AASMPart> aasm, ISelMatchData sourceMatch) {
    
    /**
     * No-source constructor
     * @param rootNode
     * @param coveredNodes
     * @param aasm
     */
    public ISelDAGTile(ISelDAGNode rootNode, Set<ISelDAGNode> coveredNodes, List<AASMPart> aasm) {
        this(rootNode, coveredNodes, aasm, null);
    }
    
}
