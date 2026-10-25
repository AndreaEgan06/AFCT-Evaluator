package afctevaluator.mosel.predicates.symbols;

import afctevaluator.mosel.core.FactTrackingSystem;
import afctevaluator.mosel.core.Pair;

/**
 * a@posMod(m, r) (our extension, a closed stand-in for the paper's set-builder): every position j with j mod m = r
 * holds a, counting positions from 1 as the paper does. So a@posMod(2, 1) is the paper's L3 ("every odd position is
 * a") and a@posMod(1, 0) is a@all. The fact is (length mod m, whether some such position held another symbol).
 */
public final class PeriodicSymbolFactSystem implements FactTrackingSystem<Pair<Integer, Boolean>> {
    private final char a;
    private final int m;
    private final int r;

    public PeriodicSymbolFactSystem(char a, int m, int r) {
        this.a = a;
        this.m = m;
        this.r = r;
    }

    @Override
    public Pair<Integer, Boolean> initialFact() {
        return new Pair<>(0, false);
    }

    @Override
    public Pair<Integer, Boolean> transfer(Pair<Integer, Boolean> fact, char symbol) {
        int position = (fact.first() + 1) % m;
        boolean failed = fact.second() || (position == r && symbol != a);
        return new Pair<>(position, failed);
    }

    @Override
    public boolean isAccepting(Pair<Integer, Boolean> fact) {
        return !fact.second();
    }
}
