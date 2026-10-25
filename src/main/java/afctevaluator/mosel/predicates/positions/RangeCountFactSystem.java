package afctevaluator.mosel.predicates.positions;

import afctevaluator.mosel.core.Comparison;
import afctevaluator.mosel.core.FactTrackingSystem;

/**
 * |S| CMP n and |S| % m CMP n for S = psLt/psLe/psGt/psGe P, e.g. {@code |psLe fstOc 'b'| % 2 = 1} ("the first b is
 * at an odd position"). False when P is undefined.
 * <p>
 * With P = t = j + c, the size is t or t - 1 for a range from the start, which is fixed when the anchor fires, and
 * |s| - t or |s| - t + 1 for a range to the end, which is the anchor's age plus a constant. So a fact keeps the
 * anchor, the length read (for the first kind) and the size fixed at the last firing, all as {@link Lasso} values:
 * capped just above n for a count, or mod m.
 */
public final class RangeCountFactSystem implements FactTrackingSystem<RangeCountFactSystem.Fact> {
    /**
     * @param length symbols read, as a lasso value (only kept for a range from the start)
     * @param size   the range's size for the anchor's last firing, as a lasso value (only for a range from the start)
     */
    public record Fact(AnchorTracker.Fact anchor, int length, int size) {
    }

    private final boolean fromStart;
    private final AnchorTracker anchor;
    private final Lasso length;
    private final Integer m;
    private final Comparison cmp;
    private final int n;
    /** size = j + shift from the start, or age + shift to the end. */
    private final int shift;

    /** @param m the modulus, or null to compare the size itself */
    public RangeCountFactSystem(PositionRange range, Position position, Integer m, Comparison cmp, int n) {
        this.fromStart = range.fromStart();
        this.m = m;
        this.cmp = cmp;
        this.n = n;
        int c = position.offset();
        this.shift = switch (range) {
            case PS_LT -> c - 1;
            case PS_LE -> c;
            case PS_GT -> -c;
            case PS_GE -> 1 - c;
        };
        int patternLength = position.w() == null ? 1 : position.w().length();
        // For a count, every lasso is exact far enough that a saturated value still means size >= n + 1.
        if (fromStart) {
            this.length = m == null ? new Lasso(Math.max(n + patternLength - shift, 0), 1) : new Lasso(0, m);
            this.anchor = AnchorTracker.of(position, new Lasso(0, 1));
        } else {
            this.length = new Lasso(0, 1);
            this.anchor = AnchorTracker.of(position, m == null ? new Lasso(n + 1 - shift, 1) : new Lasso(0, m));
        }
    }

    @Override
    public Fact initialFact() {
        return new Fact(anchor.initial(), 0, 0);
    }

    @Override
    public Fact transfer(Fact fact, char symbol) {
        AnchorTracker.Step step = anchor.advance(fact.anchor(), symbol);
        if (!fromStart) {
            return new Fact(step.fact(), 0, 0);
        }
        int read = length.next(fact.length());
        int size = fact.size();
        if (step.fired()) {
            // j = read - patternLength + 1 for the occurrence that ends here.
            size = measure(read - anchor.patternLength() + 1 + shift);
        }
        return new Fact(step.fact(), read, size);
    }

    @Override
    public boolean isAccepting(Fact fact) {
        if (!anchor.defined(fact.anchor())) {
            return false;
        }
        int size = fromStart ? fact.size() : measure(fact.anchor().age() + shift);
        return cmp.test(size, n);
    }

    private int measure(int size) {
        return m == null ? Math.min(size, n + 1) : Math.floorMod(size, m);
    }
}
