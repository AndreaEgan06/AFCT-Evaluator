package afctevaluator.mosel.predicates.positions;

import afctevaluator.mosel.core.Comparison;

/**
 * P1 CMP P2, e.g. {@code fstOc 'a' < fstOc 'b'} ("the first a comes before the first b"). False when either position
 * is undefined, as in the paper. See {@link PositionPairFactSystem} for the facts.
 */
public final class PositionCompareFactSystem extends PositionPairFactSystem {
    private final Comparison cmp;

    public PositionCompareFactSystem(Position left, Comparison cmp, Position right) {
        super(AnchorTracker.of(left, new Lasso(0, 1)), AnchorTracker.of(right, new Lasso(0, 1)));
        this.cmp = cmp;
    }

    @Override
    public boolean isAccepting(Fact fact) {
        return first.defined(fact.first()) && second.defined(fact.second()) && ordered(fact, cmp);
    }
}
