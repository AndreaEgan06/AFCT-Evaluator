package afctevaluator.mosel.concrete_fact_systems;

/**
 * The Knuth-Morris-Pratt failure function and automaton transition for a fixed pattern,
 * shared by {@link SuffixFactSystem} and the occurrence-count fact systems so both track
 * "progress toward the next match of w" the same way.
 */
final class KmpTable {
    private final String pattern;
    private final int[] border;

    KmpTable(String pattern) {
        this.pattern = pattern;
        this.border = new int[pattern.length()];
        int b = 0;
        for (int i = 1; i < pattern.length(); i++) {
            while (b > 0 && pattern.charAt(i) != pattern.charAt(b)) {
                b = border[b - 1];
            }
            if (pattern.charAt(i) == pattern.charAt(b)) {
                b++;
            }
            border[i] = b;
        }
    }

    int length() {
        return pattern.length();
    }

    int step(int state, char symbol) {
        int k = state;
        while (k > 0 && (k == pattern.length() || pattern.charAt(k) != symbol)) {
            k = border[k - 1];
        }
        if (k < pattern.length() && pattern.charAt(k) == symbol) {
            k++;
        }
        return k;
    }

    /** Where to resume after a full match so overlapping occurrences are still counted. */
    int borderOfFullMatch() {
        return pattern.isEmpty() ? 0 : border[pattern.length() - 1];
    }
}
