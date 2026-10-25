package afctevaluator.mosel.logic;

import afctevaluator.mosel.core.FactTrackingSystem;
import afctevaluator.mosel.core.Pair;

/**
 * Combines two fact systems into one tracking the tuple of both facts, for a compound
 * condition C1 AND/OR/IMPLIES/IFF C2. Implements the same interface it composes, so arbitrary
 * trees of these are built by nesting ProductFactSystem instances. NOT is unary and lives in
 * {@link NegationFactSystem}.
 */
public final class ProductFactSystem<D1, D2> implements FactTrackingSystem<Pair<D1, D2>> {
    private final FactTrackingSystem<D1> left;
    private final FactTrackingSystem<D2> right;
    private final BooleanOperator operator;

    public ProductFactSystem(FactTrackingSystem<D1> left, FactTrackingSystem<D2> right, BooleanOperator operator) {
        if (operator == BooleanOperator.NOT) {
            throw new IllegalArgumentException("NOT is unary; use NegationFactSystem");
        }
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    @Override
    public Pair<D1, D2> initialFact() {
        return new Pair<>(left.initialFact(), right.initialFact());
    }

    @Override
    public Pair<D1, D2> transfer(Pair<D1, D2> fact, char symbol) {
        return new Pair<>(left.transfer(fact.first(), symbol), right.transfer(fact.second(), symbol));
    }

    @Override
    public boolean isAccepting(Pair<D1, D2> fact) {
        return operator.apply(left.isAccepting(fact.first()), right.isAccepting(fact.second()));
    }
}
