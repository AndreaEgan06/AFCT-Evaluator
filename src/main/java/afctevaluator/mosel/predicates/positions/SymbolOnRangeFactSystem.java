package afctevaluator.mosel.predicates.positions;

import afctevaluator.mosel.core.Comparison;

/**
 * a@S for S = psLt/psLe/psGt/psGe P: P is defined and every position in the range holds a (an empty range holds
 * vacuously), e.g. {@code b@psGt fstOc 'b'} ("after the first b, every symbol is b").
 * <p>
 * A range from the start holds when the first symbol other than a comes after it, and a range to the end when the
 * last symbol other than a comes before it, so this orders two positions like {@link PositionCompareFactSystem}: the
 * first (or last) non-a symbol, and P.
 */
public final class SymbolOnRangeFactSystem extends PositionPairFactSystem {
    private final Comparison order;

    public SymbolOnRangeFactSystem(char a, PositionRange range, Position position) {
        super(AnchorTracker.ofSymbol(range.fromStart(), symbol -> symbol != a, new Lasso(0, 1)),
                AnchorTracker.of(position, new Lasso(0, 1)));
        // How the other symbol must lie relative to P = t for the range to be all a's.
        this.order = switch (range) {
            case PS_LT -> Comparison.GE;
            case PS_LE -> Comparison.GT;
            case PS_GT -> Comparison.LE;
            case PS_GE -> Comparison.LT;
        };
    }

    @Override
    public boolean isAccepting(Fact fact) {
        return second.defined(fact.second()) && (!fact.first().found() || ordered(fact, order));
    }
}
