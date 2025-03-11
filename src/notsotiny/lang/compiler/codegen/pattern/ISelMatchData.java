package notsotiny.lang.compiler.codegen.pattern;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import notsotiny.lang.compiler.codegen.dag.ISelDAGNode;

/**
 * Holds data about an instruction selection map
 * @param pattern Pattern being matched
 * @param matchRoot Root of the subtree matched by the pattern
 * @param matchMap Map from pattern-local identifier to matched DAG node
 * @param subpatternMap Map from pattern-local identifier to subpattern match data
 * @param coveredNodes Nodes covered by this match (does not include subpatterns)
 */
public record ISelMatchData(ISelPattern pattern, ISelDAGNode matchRoot, Map<String, ISelDAGNode> matchMap, Map<String, List<ISelMatchData>> subpatternMap, List<ISelDAGNode> coveredNodes) {
    
    /**
     * Returns coveredNodes as a set
     * @return
     */
    public Set<ISelDAGNode> coveredSet() {
        return new HashSet<>(this.coveredNodes);
    }
    
    /*
     * TODO figure this shit out
     * - Some kind of conversion result w/ List<AASMPart> result & List<ISelDAGNode> covered nodes
     * - Conversion process needs to which subpattern match is in use
     * - how do we deal with nesting with multiple matches on different levels
     * 
     * subpatternMap tells what subpatterns exist and how many matches per subpattern
     * If we get the conversion results of each subpattern match, we can choose combinations
     * Map<String, List<ISelMatchData>> -> Map<String, List<List<List<AASMPart>>>>
     *  Outer list in map values corresponds to outer list in subpatternMap
     *  Map of subpattern identifier -> List<List<AASMPart> for each match in group
     *  List<List<AASMPart>> = list of conversion results for a given pattern
     * 
     * Given
     * sub1:
     *  (whatever) -> "result 1" |
     *  (whatever) -> "result 2" ;
     * 
     * sub2:
     *  (whatever) -> "result 3" |
     *  (whatever) -> "result 4" ;
     * 
     * sub3:
     *  (<x> sub1) -> "<x>" |
     *  (<x> sub2) -> "<x>" ;
     * 
     * pat:
     *  (whatever (<x> sub3) (<y> sub3)) -> "<x>; <y>; <x>" ;
     * 
     * <x> can be any of result 1-4
     * <y> can be any of result 1-4
     * 
     * final can be of 16 combinations
     * There are 48 invalid combinations where first and second <x> use different results
     * 
     * pat 0 matchdata {
     *  subpat map {
     *      "<x>" -> [
     *          sub3 0 matchddata {
     *              subpat map {
     *                  "<x>" -> [
     *                      sub1 0 matchdata {
     *                          results [
     *                              ["result 1"]
     *                          ]
     *                      },
     *                      sub1 1 matchdata {
     *                          results [
     *                              ["result 2"]
     *                          ]
     *                      }
     *                  ]
     *              }
     *              subpat results {
     *                  "<x>" -> [
     *                      [
     *                          ["result 1"]
     *                      ],
     *                      [
     *                          ["result 2"]
     *                      ]
     *                  ]
     *              }
     *              results [
     *                  ["result 1"],
     *                  ["result 2"]
     *              ]
     *          },
     *          sub3 1 matchdata {
     *              subpat map {
     *                  "<x>" -> [
     *                      sub2 0 matchdata {
     *                          results [
     *                              ["result 3"]
     *                          ]
     *                      },
     *                      sub2 1 matchdata {
     *                          results [
     *                              ["result 4"]
     *                          ]
     *                      }
     *                  ]
     *              }
     *              subpat results {
     *                  "<x>" -> [
     *                      [
     *                          ["result 3"]
     *                      ],
     *                      [
     *                          ["result 4"]
     *                      ]
     *                  ]
     *              }
     *              results [
     *                  ["result 3"],
     *                  ["result 4"]
     *              ]
     *          }
     *      ],
     *      "<y>" -> [
     *          sub3 0 matchddata {
     *              subpat map {
     *                  "<x>" -> [
     *                      sub1 0 matchdata {
     *                          results [
     *                              ["result 1"]
     *                          ]
     *                      },
     *                      sub1 1 matchdata {
     *                          results [
     *                              ["result 2"]
     *                          ]
     *                      }
     *                  ]
     *              }
     *              subpat results {
     *                  "<x>" -> [
     *                      [
     *                          ["result 1"]
     *                      ],
     *                      [
     *                          ["result 2"]
     *                      ]
     *                  ]
     *              }
     *              results [
     *                  ["result 1"],
     *                  ["result 2"]
     *              ]
     *          },
     *          sub3 1 matchdata {
     *              subpat map {
     *                  "<x>" -> [
     *                      sub2 0 matchdata {
     *                          results [
     *                              ["result 3"]
     *                          ]
     *                      },
     *                      sub2 1 matchdata {
     *                          results [
     *                              ["result 4"]
     *                          ]
     *                      }
     *                  ]
     *              }
     *              subpat results {
     *                  "<x>" -> [
     *                      [
     *                          ["result 3"]
     *                      ],
     *                      [
     *                          ["result 4"]
     *                      ]
     *                  ]
     *              }
     *              results [
     *                  ["result 3"],
     *                  ["result 4"]
     *              ]
     *          }
     *      ]
     *  }
     *  subpat results {
     *      "<x>" -> [
     *          [
     *              ["result 1"],
     *              ["result 2"]
     *          ],
     *          [
     *              ["result 3"],
     *              ["result 4"]
     *          ]
     *      ],
     *      "<y>" -> [
     *          [
     *              ["result 1"],
     *              ["result 2"]
     *          ],
     *          [
     *              ["result 3"],
     *              ["result 4"]
     *          ]
     *      ]
     *  }
     *  results [
     *      x0y0 <- choose matchresult indices
     *          ["result 1; result 1; result 1], <- choose conversion indices
     *          ["result 1; result 2; result 1],
     *          ["result 2; result 1; result 2],
     *          ["result 2; result 2; result 2],
     *      x0y1
     *          ["result 1; result 3; result 1],
     *          ["result 1; result 4; result 1],
     *          ["result 2; result 3; result 2],
     *          ["result 2; result 4; result 2],
     *      x1y0
     *          ["result 3; result 1; result 3],
     *          ["result 3; result 2; result 3],
     *          ["result 4; result 1; result 4],
     *          ["result 4; result 2; result 4],
     *      x1y1
     *          ["result 3; result 3; result 3],
     *          ["result 3; result 4; result 3],
     *          ["result 4; result 3; result 4],
     *          ["result 4; result 4; result 4],
     *  ]
     * }
     * 
     * 
     */
    
}
