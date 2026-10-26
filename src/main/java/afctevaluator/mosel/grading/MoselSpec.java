package afctevaluator.mosel.grading;

import afctevaluator.mosel.core.FactTrackingSystem;

import java.util.Set;

/**
 * A parsed MOSEL description: the language of the strings over {@code alphabet} that satisfy the condition.
 *
 * @param alphabet the symbols of the problem, sorted
 * @param system the condition's fact-tracking system
 */
public record MoselSpec(Set<Character> alphabet, FactTrackingSystem<Object> system) {
}
