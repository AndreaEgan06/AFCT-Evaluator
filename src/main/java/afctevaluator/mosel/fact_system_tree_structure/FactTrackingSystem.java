package afctevaluator.mosel.fact_system_tree_structure;

/**
 * A fact-tracking system for a single condition C: a finite
 * domain of facts, the fact that holds for the empty string, a transfer function describing
 * how reading a character updates the fact, and a classification of which facts satisfy C.
 * <p>
 * New primitives (or custom conditions outside the original MOSEL grammar) are added by
 * implementing this interface directly; nothing else in this package needs to change.
 *
 * @param <D> the fact domain; must have value-based equals()/hashCode() since facts are
 *            tracked in sets during verification
 */
public interface FactTrackingSystem<D> {
    D initialFact();

    D transfer(D fact, char symbol);

    boolean isAccepting(D fact);
}
