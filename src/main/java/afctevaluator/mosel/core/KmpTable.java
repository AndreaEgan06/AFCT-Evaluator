package afctevaluator.mosel.core;

/**
 * The Knuth-Morris-Pratt failure function and automaton transition for a fixed pattern,
 * shared by endWt, the occurrence counts and the occurrence-based positions so they all track
 * "progress toward the next match of w" the same way.
 */
public final class KmpTable {
    private final String pattern;
    private final int[] border;

    public KmpTable(String pattern) {
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

    public int length() {
        return pattern.length();
    }

    public int step(int state, char symbol) {
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
    public int borderOfFullMatch() {
        return pattern.isEmpty() ? 0 : border[pattern.length() - 1];
    }
}
