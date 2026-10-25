package afctevaluator.mosel.predicates.positions;

/**
 * A counter abstraction: values below {@code cap} are kept exactly, and from {@code cap} on only their residue mod
 * {@code period} is kept (so a period of 1 is a plain saturating cap, and a cap of 0 is plain counting mod period).
 * Every value it hands back is congruent to the real value mod period, and is at least cap exactly when the real
 * value is.
 */
record Lasso(int cap, int period) {
    int of(int value) {
        return value < cap ? value : cap + (value - cap) % period;
    }

    int next(int value) {
        return of(value + 1);
    }
}
