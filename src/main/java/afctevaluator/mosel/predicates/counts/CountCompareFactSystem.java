package afctevaluator.mosel.predicates.counts;

import afctevaluator.mosel.core.Comparison;
import afctevaluator.mosel.core.FactTrackingSystem;
import afctevaluator.mosel.core.KmpTable;
import afctevaluator.mosel.core.Pair;

/**
 * |indOf w| CMP n. A fact is (occurrence count, KMP progress toward the next occurrence of w). The count saturates
 * at n + 1, past which no comparison with n changes.
 */
public final class CountCompareFactSystem implements FactTrackingSystem<Pair<Integer, Integer>> {
    private final KmpTable kmp;
    private final Comparison cmp;
    private final int n;

    public CountCompareFactSystem(String w, Comparison cmp, int n) {
        this.kmp = new KmpTable(w);
        this.cmp = cmp;
        this.n = n;
    }

    @Override
    public Pair<Integer, Integer> initialFact() {
        return new Pair<>(0, 0);
    }

    @Override
    public Pair<Integer, Integer> transfer(Pair<Integer, Integer> fact, char symbol) {
        int count = fact.first();
        int nextKmpState = kmp.step(fact.second(), symbol);
        if (nextKmpState == kmp.length()) {
            return new Pair<>(Math.min(count + 1, n + 1), kmp.borderOfFullMatch());
        }
        return new Pair<>(count, nextKmpState);
    }

    @Override
    public boolean isAccepting(Pair<Integer, Integer> fact) {
        return cmp.test(fact.first(), n);
    }
}
