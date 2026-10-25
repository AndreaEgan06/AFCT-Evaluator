package afctevaluator.mosel.predicates.positions;

/**
 * A closed MOSEL position term: {@code fst}, {@code last}, {@code fstOc 'w'} or {@code lastOc 'w'}, moved by
 * {@code offset}. Positions count from 1, as in the paper. The paper's {@code P + 1} and {@code P - 1} are folded
 * into one offset, and {@code P + c} is defined when the anchor is and 1 &lt;= anchor + c &lt;= |s|.
 *
 * @param w the word for fstOc/lastOc; null for fst and last
 */
public record Position(Anchor anchor, String w, int offset) {
    public enum Anchor {
        FST("fst"),         // The first symbol in the string
        LAST("last"),       // The last symbol in the string
        FST_OC("fstOc"),    // The first occurrence of the word in the string
        LAST_OC("lastOc");  // The last occurrence of the word in the string

        private final String name;

        Anchor(String name) {
            this.name = name;
        }

        public boolean isFstOrFstOc() {
            return this == FST || this == FST_OC;
        }

        public boolean isOccurrence() {
            return this == FST_OC || this == LAST_OC;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public Position {
        if (anchor.isOccurrence() != (w != null)) {
            throw new IllegalArgumentException(anchor + (anchor.isOccurrence() ? " needs a word" : " takes no word"));
        }
        if (w != null && w.isEmpty()) {
            throw new IllegalArgumentException("the word of " + anchor + " must not be empty");
        }
    }

    @Override
    public String toString() {
        String base = w == null ? anchor.toString() : anchor + " '" + w + "'";
        if (offset == 0) {
            return base;
        }
        return base + (offset > 0 ? " + " + offset : " - " + -offset);
    }
}
