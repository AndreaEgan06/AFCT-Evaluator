package afctevaluator.mosel.concrete_fact_systems;

import afctevaluator.mosel.fact_system_tree_structure.FactTrackingSystem;

/** the sum of the digits in the string is congruent to r mod m */
public final class SumModFactSystem implements FactTrackingSystem<Integer> {
    private final Integer remainder;
    private final Integer modulus;

    public SumModFactSystem(Integer remainder, Integer modulus) {
        this.remainder = remainder;
        this.modulus = modulus;
    }

    @Override
    public Integer initialFact() {
        return 0;
    }

    @Override
    public Integer transfer(Integer fact, char symbol) {
        return (fact + Character.getNumericValue(symbol)) % this.modulus;
    }

    @Override
    public boolean isAccepting(Integer fact) {
        return fact % this.modulus == this.remainder;
    }
}
