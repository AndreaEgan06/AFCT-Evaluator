package afctevaluator.mosel;

/** |indOf w| &lt;= k. */
public final class CountAtMostFactSystem extends AbstractCountFactSystem {
    private final int k;

    public CountAtMostFactSystem(String w, int k) {
        super(w, k + 1);
        this.k = k;
    }

    @Override
    public boolean isAccepting(Pair<Integer, Integer> fact) {
        return count(fact) <= k;
    }
}
