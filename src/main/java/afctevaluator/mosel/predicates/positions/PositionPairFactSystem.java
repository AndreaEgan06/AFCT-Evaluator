package afctevaluator.mosel.predicates.positions;

import afctevaluator.mosel.core.Comparison;
import afctevaluator.mosel.core.FactTrackingSystem;

/**
 * Shared machinery for conditions that order two positions t1 = j1 + c1 and t2 = j2 + c2. Since j = |s| - age,
 * t1 - t2 = D - K with D = age2 - age1 and K = c2 - c1. While neither anchor moves, both ages grow together and D
 * stays put, so a fact keeps D itself, clamped to [K - 1, K + 1] (enough to know the sign of t1 - t2), and works it
 * out again from the ages only when an anchor (re)fires. The ages are capped just high enough for that to stay exact.
 */
public abstract class PositionPairFactSystem implements FactTrackingSystem<PositionPairFactSystem.Fact> {
    /** @param diff D = age2 - age1 clamped to [K - 1, K + 1]; null until both anchors are found */
    public record Fact(AnchorTracker.Fact first, AnchorTracker.Fact second, Integer diff) {
    }

    final AnchorTracker first;
    final AnchorTracker second;
    private final int k;

    PositionPairFactSystem(AnchorTracker first, AnchorTracker second) {
        this.k = second.offset() - first.offset();
        // When the first anchor fires, D = age2 - (length1 - 1) must clamp correctly from a capped age2, and
        // symmetrically for the second.
        this.first = first.withAge(new Lasso(Math.max(second.patternLength() - k, 0), 1));
        this.second = second.withAge(new Lasso(Math.max(k + first.patternLength(), 0), 1));
    }

    @Override
    public Fact initialFact() {
        return new Fact(first.initial(), second.initial(), null);
    }

    @Override
    public Fact transfer(Fact fact, char symbol) {
        AnchorTracker.Step one = first.advance(fact.first(), symbol);
        AnchorTracker.Step two = second.advance(fact.second(), symbol);
        Integer diff;
        if (!one.fact().found() || !two.fact().found()) {
            diff = null;
        } else if (one.fired() || two.fired() || fact.diff() == null) {
            diff = Math.max(k - 1, Math.min(k + 1, two.fact().age() - one.fact().age()));
        } else {
            diff = fact.diff();
        }
        return new Fact(one.fact(), two.fact(), diff);
    }

    /** t1 CMP t2, given that both positions are found. */
    final boolean ordered(Fact fact, Comparison cmp) {
        return cmp.test(fact.diff() - k, 0);
    }
}
