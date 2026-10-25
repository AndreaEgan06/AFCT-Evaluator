package afctevaluator.mosel.predicates.positions;

import java.util.function.Predicate;

import afctevaluator.mosel.core.KmpTable;

/**
 * Follows a position while a string is read: where its anchor j is (the start of the first or the last occurrence of
 * a word, or of a single symbol from a class; fst and last are the first and last occurrence of any symbol), and
 * whether j + offset lands inside the string. Shared by the systems that take a {@link Position}.
 * <p>
 * Nothing is remembered about j itself; a fact keeps the age |s| - j instead, which is what the systems compare. The
 * age is a {@link Lasso} counter so a system can choose how much of it to keep.
 */
public final class AnchorTracker {
    /**
     * @param match  KMP progress toward the next occurrence of the word (0 once a first-occurrence anchor is found)
     * @param length symbols read, saturating once it no longer decides {@code low}
     * @param found  whether the anchor has been seen
     * @param low    whether j + offset &gt;= 1 for the current anchor
     * @param age    |s| - j for the current anchor, as a lasso value (0 until found)
     */
    public record Fact(int match, int length, boolean found, boolean low, int age) {
    }

    record Step(Fact fact, boolean fired) {
    }

    private final boolean first;
    private final KmpTable kmp;
    private final Predicate<Character> symbols;
    private final int patternLength;
    private final int offset;
    private final int lengthCap;
    private final Lasso age;

    private AnchorTracker(boolean first, KmpTable kmp, Predicate<Character> symbols, int patternLength, int offset,
                          Lasso age) {
        this.first = first;
        this.kmp = kmp;
        this.symbols = symbols;
        this.patternLength = patternLength;
        this.offset = offset;
        this.lengthCap = Math.max(patternLength - offset, 0);
        // Exact up to the anchor's starting age (patternLength - 1) and to the offset, so firing and "j + offset <= |s|"
        // read correctly whatever cap the caller asked for.
        this.age = new Lasso(Math.max(age.cap(), Math.max(patternLength, offset)), age.period());
    }

    /** Tracks {@code position}, keeping its age as {@code age} (raised as needed). */
    static AnchorTracker of(Position position, Lasso age) {
        String w = position.w();
        return w == null
                ? new AnchorTracker(position.anchor().isFstOrFstOc(), null, symbol -> true, 1, position.offset(), age)
                : new AnchorTracker(position.anchor().isFstOrFstOc(), new KmpTable(w), null, w.length(), position.offset(), age);
    }

    /** Tracks the first ({@code first}) or last position whose symbol satisfies {@code symbols}, with no offset. */
    static AnchorTracker ofSymbol(boolean first, Predicate<Character> symbols, Lasso age) {
        return new AnchorTracker(first, null, symbols, 1, 0, age);
    }

    /** The same tracker keeping its age as {@code age} instead (raised as needed). */
    AnchorTracker withAge(Lasso age) {
        return new AnchorTracker(first, kmp, symbols, patternLength, offset, age);
    }

    int patternLength() {
        return patternLength;
    }

    int offset() {
        return offset;
    }

    Fact initial() {
        return new Fact(0, 0, false, false, 0);
    }

    Step advance(Fact fact, char symbol) {
        int length = Math.min(fact.length() + 1, lengthCap);
        if (first && fact.found()) {
            return new Step(new Fact(0, lengthCap, true, fact.low(), age.next(fact.age())), false);
        }
        int match = 0;
        boolean fires;
        if (kmp == null) {
            fires = symbols.test(symbol);
        } else {
            int next = kmp.step(fact.match(), symbol);
            fires = next == kmp.length();
            match = fires ? kmp.borderOfFullMatch() : next;
        }
        if (fires) {
            // The occurrence ends here, so j = length - patternLength + 1, and j + offset >= 1 iff length >= lengthCap.
            int restLength = first ? lengthCap : length;
            return new Step(new Fact(match, restLength, true, length >= lengthCap, age.of(patternLength - 1)), true);
        }
        int nextAge = fact.found() ? age.next(fact.age()) : 0;
        return new Step(new Fact(match, length, fact.found(), fact.low(), nextAge), false);
    }

    /** Whether the position is defined for the string read so far: the anchor exists and j + offset is in [1..|s|]. */
    boolean defined(Fact fact) {
        return fact.found() && fact.low() && fact.age() >= offset;
    }
}
