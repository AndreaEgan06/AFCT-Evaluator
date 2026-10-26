package afctevaluator.mosel.predicates.counts;

import afctevaluator.mosel.core.Comparison;
import afctevaluator.mosel.core.FactTrackingSystem;

/**
 * the string read as a number in base b, mod m, compared with r (our extension; not in the original MOSEL). The
 * digits are 0-9 and then a-z (either case) for 10-35, and only those worth less than b count: any other symbol
 * leaves the number unchanged, as a non-digit does in {@link SumModFactSystem}. The empty string is 0.
 */
public final class ValueModFactSystem implements FactTrackingSystem<Integer> {
    public static final int MAX_BASE = 36;

    private final int base;
    private final int modulus;
    private final Comparison cmp;
    private final int remainder;

    public ValueModFactSystem(int base, int modulus, Comparison cmp, int remainder) {
        if (base < 2 || base > MAX_BASE) {
            throw new IllegalArgumentException("the base must be between 2 and " + MAX_BASE);
        }
        this.base = base;
        this.modulus = modulus;
        this.cmp = cmp;
        this.remainder = remainder;
    }

    /** The value of {@code symbol} as a digit in base {@code base}, or -1 if it is not one. */
    public static int digit(char symbol, int base) {
        int value;
        if ('0' <= symbol && symbol <= '9') {
            value = symbol - '0';
        } else if ('a' <= symbol && symbol <= 'z') {
            value = symbol - 'a' + 10;
        } else if ('A' <= symbol && symbol <= 'Z') {
            value = symbol - 'A' + 10;
        } else {
            return -1;
        }
        return value < base ? value : -1;
    }

    @Override
    public Integer initialFact() {
        return 0;
    }

    @Override
    public Integer transfer(Integer fact, char symbol) {
        int digit = digit(symbol, base);
        if (digit < 0) {
            return fact;
        }
        return (fact * base + digit) % modulus;
    }

    @Override
    public boolean isAccepting(Integer fact) {
        return cmp.test(fact, remainder);
    }
}
