package afctevaluator.mosel.predicates.positions;

/** The paper's position sets built from one position P = j: psLt P = [1..j-1], psLe P = [1..j], psGt P = [j+1..|s|], psGe P = [j..|s|]. */
public enum PositionRange {
    PS_LT("psLt"), PS_LE("psLe"), PS_GT("psGt"), PS_GE("psGe");

    private final String name;

    PositionRange(String name) {
        this.name = name;
    }

    public static PositionRange fromName(String name) {
        for (PositionRange range : values()) {
            if (range.name.equals(name)) {
                return range;
            }
        }
        throw new IllegalArgumentException("'" + name + "' is not a range (psLt, psLe, psGt, psGe)");
    }

    /** Whether the range runs from the start of the string (psLt, psLe) rather than to its end. */
    public boolean fromStart() {
        return this == PS_LT || this == PS_LE;
    }

    @Override
    public String toString() {
        return name;
    }
}
