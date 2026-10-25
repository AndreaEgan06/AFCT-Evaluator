package afctevaluator.mosel.predicates.counts;

import afctevaluator.mosel.core.Comparison;
import afctevaluator.mosel.core.FactTrackingSystem;

/** |all| CMP n: the length compared with n. The fact is the length, saturating at n + 1. */
public final class LengthCompareFactSystem implements FactTrackingSystem<Integer> {
    private final Comparison cmp;
    private final int n;

    public LengthCompareFactSystem(Comparison cmp, int n) {
        this.cmp = cmp;
        this.n = n;
    }

    @Override
    public Integer initialFact() {
        return 0;
    }

    @Override
    public Integer transfer(Integer fact, char symbol) {
        return Math.min(fact + 1, n + 1);
    }

    @Override
    public boolean isAccepting(Integer fact) {
        return cmp.test(fact, n);
    }
}
