package afctevaluator.mosel.concrete_fact_systems;

import afctevaluator.mosel.fact_system_tree_structure.FactTrackingSystem;

/** begWt w: the string begins with w. Fact is PRE_i (0 &lt;= i &lt;= |w|), or FAIL on deviation. */
public final class PrefixFactSystem implements FactTrackingSystem<Integer> {
    public static final int FAIL = -1;

    private final String w;

    public PrefixFactSystem(String w) {
        this.w = w;
    }

    @Override
    public Integer initialFact() {
        return 0;
    }

    @Override
    public Integer transfer(Integer fact, char symbol) {
        if (fact == FAIL) {
            return FAIL;
        }
        if (fact == w.length()) {
            return fact;
        }
        if (symbol == w.charAt(fact)) {
            return fact + 1;
        }
        return FAIL;
    }

    @Override
    public boolean isAccepting(Integer fact) {
        return fact == w.length();
    }
}
