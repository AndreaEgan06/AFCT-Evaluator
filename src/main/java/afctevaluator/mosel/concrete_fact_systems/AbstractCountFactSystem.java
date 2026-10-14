package afctevaluator.mosel.concrete_fact_systems;

import afctevaluator.mosel.fact_system_tree_structure.FactTrackingSystem;
import afctevaluator.mosel.fact_system_tree_structure.Pair;

/**
 * Shared machinery for |indOf w| {@literal >=/=/<=} k: a fact is (occurrence count, KMP
 * progress toward the next occurrence of w). The count saturates at {@code saturationCeiling}
 * once it can no longer affect the accept/reject classification, which is where the three
 * count conditions differ (see {@link CountAtLeastFactSystem}, {@link CountExactlyFactSystem},
 * {@link CountAtMostFactSystem}).
 */
abstract class AbstractCountFactSystem implements FactTrackingSystem<Pair<Integer, Integer>> {
    private final KmpTable kmp;
    private final int saturationCeiling;

    AbstractCountFactSystem(String w, int saturationCeiling) {
        this.kmp = new KmpTable(w);
        this.saturationCeiling = saturationCeiling;
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
            return new Pair<>(Math.min(count + 1, saturationCeiling), kmp.borderOfFullMatch());
        }
        return new Pair<>(count, nextKmpState);
    }

    final int count(Pair<Integer, Integer> fact) {
        return fact.first();
    }
}
