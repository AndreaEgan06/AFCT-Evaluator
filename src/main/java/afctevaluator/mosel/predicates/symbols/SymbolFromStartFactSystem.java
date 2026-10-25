package afctevaluator.mosel.predicates.symbols;

import afctevaluator.mosel.core.FactTrackingSystem;

/**
 * a@(fst + k): the string has a (k+1)-th symbol and it is a. The fact is the number of symbols read so far
 * (0 to k), then {@link #MATCH} or {@link #FAIL} once the (k+1)-th symbol has been read.
 */
public final class SymbolFromStartFactSystem implements FactTrackingSystem<Integer> {
    public static final int FAIL = -1;
    public static final int MATCH = -2;

    private final char a;
    private final int k;

    public SymbolFromStartFactSystem(char a, int k) {
        this.a = a;
        this.k = k;
    }

    @Override
    public Integer initialFact() {
        return 0;
    }

    @Override
    public Integer transfer(Integer fact, char symbol) {
        if (fact == FAIL || fact == MATCH) {
            return fact;
        }
        if (fact == k) {
            return symbol == a ? MATCH : FAIL;
        }
        return fact + 1;
    }

    @Override
    public boolean isAccepting(Integer fact) {
        return fact == MATCH;
    }
}
