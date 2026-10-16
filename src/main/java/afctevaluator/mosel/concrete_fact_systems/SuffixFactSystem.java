package afctevaluator.mosel.concrete_fact_systems;

import afctevaluator.mosel.abstract_fact_system.FactTrackingSystem;

/** endWt w: the string ends with w. Fact is the KMP overlap value in [0, |w|]. */
public final class SuffixFactSystem implements FactTrackingSystem<Integer> {
    private final KmpTable kmp;

    public SuffixFactSystem(String w) {
        this.kmp = new KmpTable(w);
    }

    @Override
    public Integer initialFact() {
        return 0;
    }

    @Override
    public Integer transfer(Integer fact, char symbol) {
        return kmp.step(fact, symbol);
    }

    @Override
    public boolean isAccepting(Integer fact) {
        return fact == kmp.length();
    }
}
