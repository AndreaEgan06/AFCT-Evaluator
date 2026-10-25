package afctevaluator.mosel.predicates.symbols;

import afctevaluator.mosel.core.FactTrackingSystem;

/**
 * a@(last - k): the string has at least k+1 symbols and the (k+1)-th from the end is a. The fact is a window over
 * the last k+1 symbols read, remembering for each only whether it was a, so there are at most 2^(k+2) facts
 * whatever the alphabet.
 */
public final class SymbolFromEndFactSystem implements FactTrackingSystem<SymbolFromEndFactSystem.Window> {
    /**
     * The last {@code length} symbols read (at most k+1); bit i of {@code marks} says whether the symbol i places
     * before the newest one was a.
     */
    public record Window(char a, int length, int marks) {
        @Override
        public String toString() {
            StringBuilder text = new StringBuilder();
            for (int i = length - 1; i >= 0; i--) {
                text.append((marks >> i & 1) == 1 ? a : '_');
            }
            return length == 0 ? "nothing read" : "last " + length + ": " + text;
        }
    }

    private final char a;
    private final int k;

    public SymbolFromEndFactSystem(char a, int k) {
        this.a = a;
        this.k = k;
    }

    @Override
    public Window initialFact() {
        return new Window(a, 0, 0);
    }

    @Override
    public Window transfer(Window fact, char symbol) {
        int marks = (fact.marks() << 1 | (symbol == a ? 1 : 0)) & ((1 << (k + 1)) - 1);
        return new Window(a, Math.min(fact.length() + 1, k + 1), marks);
    }

    @Override
    public boolean isAccepting(Window fact) {
        return fact.length() == k + 1 && (fact.marks() >> k & 1) == 1;
    }
}
