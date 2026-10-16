package afctevaluator.mosel.abstract_fact_system;

/**
 * Combines two fact systems into one tracking the tuple of both facts, for a compound
 * condition C1 AND/OR C2. Implements the same interface it composes, so arbitrary trees of
 * AND/OR are built by nesting ProductFactSystem instances.
 */
public final class ProductFactSystem<D1, D2> implements FactTrackingSystem<Pair<D1, D2>> {
    private final FactTrackingSystem<D1> left;
    private final FactTrackingSystem<D2> right;
    private final BooleanOperator operator;

    public ProductFactSystem(FactTrackingSystem<D1> left, FactTrackingSystem<D2> right, BooleanOperator operator) {
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
