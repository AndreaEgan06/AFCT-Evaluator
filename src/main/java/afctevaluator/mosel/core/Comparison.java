package afctevaluator.mosel.core;

/** The CMP of MOSEL's {@code |S| CMP n}, {@code |S| % m CMP n} and {@code P CMP P}, written as in the JSON form. */
public enum Comparison {
    LT("<"), LE("<="), EQ("="), NE("!="), GE(">="), GT(">");

    private final String symbol;

    Comparison(String symbol) {
        this.symbol = symbol;
    }

    public static Comparison fromSymbol(String symbol) {
        for (Comparison comparison : values()) {
            if (comparison.symbol.equals(symbol)) {
                return comparison;
            }
        }
        throw new IllegalArgumentException("'" + symbol + "' is not a comparison (<, <=, =, !=, >=, >)");
    }

    public boolean test(int left, int right) {
        return switch (this) {
            case LT -> left < right;
            case LE -> left <= right;
            case EQ -> left == right;
            case NE -> left != right;
            case GE -> left >= right;
            case GT -> left > right;
        };
    }

    @Override
    public String toString() {
        return symbol;
    }
}
