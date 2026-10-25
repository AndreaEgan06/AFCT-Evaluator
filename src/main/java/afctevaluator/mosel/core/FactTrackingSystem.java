package afctevaluator.mosel.core;

/**
 * A fact-tracking system for a single condition C: a finite
 * domain of facts, the fact that holds for the empty string, a transfer function describing
 * how reading a character updates the fact, and a classification of which facts satisfy C.
 * <p>
 * New primitives (or custom conditions outside the original MOSEL grammar) are added by
 * implementing this interface directly; nothing in {@code analysis} needs to change. Atomic
 * conditions live in the {@code predicates} subpackages, grouped by what they talk about (whole
 * words, counts, symbols at fixed places, positions and ranges); the connectives that combine
 * them live in {@code logic}; and this package holds what they share.
 *
 * @param <D> the fact domain; must have value-based equals()/hashCode() since facts are
 *            tracked in sets during verification
 */
public interface FactTrackingSystem<D> {
    D initialFact();

    D transfer(D fact, char symbol);

    boolean isAccepting(D fact);
}
