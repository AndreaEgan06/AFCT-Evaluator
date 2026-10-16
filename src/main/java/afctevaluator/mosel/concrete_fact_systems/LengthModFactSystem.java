package afctevaluator.mosel.concrete_fact_systems;

import afctevaluator.mosel.abstract_fact_system.FactTrackingSystem;

/** |s| mod m = r. Every transition advances the fact by one, regardless of the symbol read. */
public final class LengthModFactSystem implements FactTrackingSystem<Integer> {
    private final int m;
    private final int r;

    public LengthModFactSystem(int m, int r) {
        this.m = m;
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
        return fact == r;
    }
}
