package afctevaluator.mosel.predicates.words;

import afctevaluator.mosel.core.FactTrackingSystem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * oneOf(w_1, ..., w_n): the string is one of the listed words (our extension; not in the original MOSEL), for problems
 * that list a finite language outright. The empty word may be listed.
 * <p>
 * The progress toward w_i is the number of its symbols read so far, or FAIL once the string has left it (by deviating,
 * or by running past its end). A string s that is still a prefix of w_i has made progress |s| toward it, so every
 * counter that has not failed holds the same number, and which counters those are is decided by s: the vector of
 * counters and s itself determine each other. A fact is therefore s while it is a prefix of some w_i, and
 * {@link #FAIL} once it is a prefix of none. It accepts when some counter has reached the end of its word, that is
 * when s is one of the words.
 */
public final class OneOfFactSystem implements FactTrackingSystem<OneOfFactSystem.Progress> {
    /**
     * The string read so far, or null once it is a prefix of no word.
     */
    public record Progress(String read) {
        @Override
        public String toString() {
            return read == null ? "FAIL" : "'" + read + "'";
        }
    }

    public static final Progress FAIL = new Progress(null);

    private final Set<String> words;
    private final Set<String> prefixes = new HashSet<>();

    public OneOfFactSystem(List<String> words) {
        if (words.isEmpty()) {
            throw new IllegalArgumentException("oneOf needs at least one word");
        }
        this.words = Set.copyOf(words);
        for (String w : words) {
            for (int i = 0; i <= w.length(); i++) {
                prefixes.add(w.substring(0, i));
            }
        }
    }

    @Override
    public Progress initialFact() {
        return new Progress("");
    }

    @Override
    public Progress transfer(Progress fact, char symbol) {
        if (fact.read() == null) {
            return FAIL;
        }
        String read = fact.read() + symbol;
        return prefixes.contains(read) ? new Progress(read) : FAIL;
    }

    @Override
    public boolean isAccepting(Progress fact) {
        return fact.read() != null && words.contains(fact.read());
    }
}
