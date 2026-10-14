package afctevaluator.mosel.concrete_fact_systems;

import afctevaluator.mosel.fact_system_tree_structure.FactTrackingSystem;
import afctevaluator.mosel.fact_system_tree_structure.Pair;

/** |indOf w| mod m = r. Fact is (occurrence count mod m, KMP progress toward the next occurrence of w). */
public final class ModularCountFactSystem implements FactTrackingSystem<Pair<Integer, Integer>> {
    private final KmpTable kmp;
    private final int m;
    private final int r;

    public ModularCountFactSystem(String w, int m, int r) {
        this.kmp = new KmpTable(w);
        this.m = m;
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
        return fact.first() == r;
    }
}
