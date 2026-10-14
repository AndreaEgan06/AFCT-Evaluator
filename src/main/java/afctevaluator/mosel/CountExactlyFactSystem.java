package afctevaluator.mosel;

/** |indOf w| = k. */
public final class CountExactlyFactSystem extends AbstractCountFactSystem {
    private final int k;

    public CountExactlyFactSystem(String w, int k) {
        super(w, k + 1);
        this.k = k;
    }

    @Override
    public boolean isAccepting(Pair<Integer, Integer> fact) {
        return count(fact) == k;
    }
}
