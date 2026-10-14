package afctevaluator.mosel.concrete_fact_systems;

import afctevaluator.mosel.fact_system_tree_structure.Pair;

/** |indOf w| &gt;= k. */
public final class CountAtLeastFactSystem extends AbstractCountFactSystem {
    private final int k;

    public CountAtLeastFactSystem(String w, int k) {
        super(w, k);
        this.k = k;
    }

    @Override
    public boolean isAccepting(Pair<Integer, Integer> fact) {
        return count(fact) >= k;
    }
}
