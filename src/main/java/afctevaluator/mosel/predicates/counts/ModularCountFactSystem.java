package afctevaluator.mosel.predicates.counts;

import afctevaluator.mosel.core.Comparison;
import afctevaluator.mosel.core.FactTrackingSystem;
import afctevaluator.mosel.core.KmpTable;
import afctevaluator.mosel.core.Pair;

/** |indOf w| mod m CMP r. Fact is (occurrence count mod m, KMP progress toward the next occurrence of w). */
public final class ModularCountFactSystem implements FactTrackingSystem<Pair<Integer, Integer>> {
    private final KmpTable kmp;
    private final int m;
    private final Comparison cmp;
    private final int r;

    public ModularCountFactSystem(String w, int m, Comparison cmp, int r) {
        this.kmp = new KmpTable(w);
        this.m = m;
        this.cmp = cmp;
        this.r = r;
    }

    @Override
    public Pair<Integer, Integer> initialFact() {
        return new Pair<>(0, 0);
    }

    @Override
    public Pair<Integer, Integer> transfer(Pair<Integer, Integer> fact, char symbol) {
        int count = fact.first();
        int kmpState = fact.second();
        int nextKmpState = kmp.step(kmpState, symbol);
        if (nextKmpState == kmp.length()) {
            return new Pair<>((count + 1) % m, kmp.borderOfFullMatch());
        }
        return new Pair<>(count, nextKmpState);
    }

    @Override
    public boolean isAccepting(Pair<Integer, Integer> fact) {
        return cmp.test(fact.first(), r);
    }
}
