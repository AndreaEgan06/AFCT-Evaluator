package afctevaluator.mosel.predicates.counts;

import afctevaluator.mosel.core.Comparison;
import afctevaluator.mosel.core.FactTrackingSystem;

/** the sum of the digits in the string, mod m, compared with r (our extension; not in the original MOSEL) */
public final class SumModFactSystem implements FactTrackingSystem<Integer> {
    private final Integer modulus;
    private final Comparison cmp;
    private final Integer remainder;

    public SumModFactSystem(Integer modulus, Comparison cmp, Integer remainder) {
        this.modulus = modulus;
        this.cmp = cmp;
        this.remainder = remainder;
    }

    @Override
    public Integer initialFact() {
        return 0;
    }

    @Override
    public Integer transfer(Integer fact, char symbol) {
        if ('0' <= symbol && symbol <= '9') {
            return (fact + (symbol - '0')) % this.modulus;
        }
        return fact;
    }

    @Override
    public boolean isAccepting(Integer fact) {
        return cmp.test(fact % this.modulus, this.remainder);
    }
}
