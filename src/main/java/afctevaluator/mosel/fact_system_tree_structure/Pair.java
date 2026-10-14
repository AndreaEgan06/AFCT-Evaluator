package afctevaluator.mosel.fact_system_tree_structure;

/** The product domain used to combine two fact systems, e.g. count and KMP progress, or two compound conditions. */
public record Pair<A, B>(A first, B second) {
}
