package afctevaluator.mosel.predicates.counts;

import afctevaluator.mosel.core.Comparison;
import afctevaluator.mosel.core.FactTrackingSystem;

/** |s| mod m CMP r. Every transition advances the fact by one, regardless of the symbol read. */
public final class LengthModFactSystem implements FactTrackingSystem<Integer> {
    private final int m;
    private final Comparison cmp;
    private final int r;

    public LengthModFactSystem(int m, Comparison cmp, int r) {
        this.m = m;
        this.cmp = cmp;
        this.r = r;
    }

    @Override
    public Integer initialFact() {
        return 0;
    }

    @Override
    public Integer transfer(Integer fact, char symbol) {
        return (fact + 1) % m;
    }

    @Override
    public boolean isAccepting(Integer fact) {
        return cmp.test(fact, r);
    }
}
