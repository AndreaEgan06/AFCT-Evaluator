package afctevaluator.mosel.logic;

import afctevaluator.mosel.core.FactTrackingSystem;

/**
 * The negation NOT C of a condition C. Tracks exactly the same facts as C's system, since a string's
 * fact under C determines whether it satisfies C, and therefore whether it satisfies NOT C; only the
 * accept/reject classification flips. Like {@link ProductFactSystem}, it implements the interface it
 * wraps, so it nests anywhere in an AND/OR/NOT tree.
 */
public final class NegationFactSystem<D> implements FactTrackingSystem<D> {
    private final FactTrackingSystem<D> inner;

    public NegationFactSystem(FactTrackingSystem<D> inner) {
        this.inner = inner;
    }

    @Override
    public D initialFact() {
        return inner.initialFact();
    }

    @Override
    public D transfer(D fact, char symbol) {
        return inner.transfer(fact, symbol);
    }

    @Override
    public boolean isAccepting(D fact) {
        return BooleanOperator.NOT.apply(inner.isAccepting(fact));
    }
}
