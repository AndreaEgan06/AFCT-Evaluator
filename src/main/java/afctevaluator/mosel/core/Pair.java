package afctevaluator.mosel.core;

/** The product domain used to combine two fact systems, e.g. count and KMP progress, or two compound conditions. */
public record Pair<A, B>(A first, B second) {
}
