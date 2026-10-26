package afctevaluator.mosel.grading;

import afctevaluator.mosel.core.Comparison;
import afctevaluator.mosel.core.FactTrackingSystem;
import afctevaluator.mosel.logic.BooleanOperator;
import afctevaluator.mosel.logic.ConstantFactSystem;
import afctevaluator.mosel.logic.NegationFactSystem;
import afctevaluator.mosel.logic.ProductFactSystem;
import afctevaluator.mosel.predicates.counts.CountCompareFactSystem;
import afctevaluator.mosel.predicates.counts.IsEmptyFactSystem;
import afctevaluator.mosel.predicates.counts.LengthCompareFactSystem;
import afctevaluator.mosel.predicates.counts.LengthModFactSystem;
import afctevaluator.mosel.predicates.counts.ModularCountFactSystem;
import afctevaluator.mosel.predicates.counts.SumModFactSystem;
import afctevaluator.mosel.predicates.counts.ValueModFactSystem;
import afctevaluator.mosel.predicates.positions.Position;
import afctevaluator.mosel.predicates.positions.PositionCompareFactSystem;
import afctevaluator.mosel.predicates.positions.PositionRange;
import afctevaluator.mosel.predicates.positions.RangeCountFactSystem;
import afctevaluator.mosel.predicates.positions.SymbolOnRangeFactSystem;
import afctevaluator.mosel.predicates.symbols.PeriodicSymbolFactSystem;
import afctevaluator.mosel.predicates.symbols.SymbolFromEndFactSystem;
import afctevaluator.mosel.predicates.symbols.SymbolFromStartFactSystem;
import afctevaluator.mosel.predicates.words.OneOfFactSystem;
import afctevaluator.mosel.predicates.words.PrefixFactSystem;
import afctevaluator.mosel.predicates.words.SuffixFactSystem;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Parses the JSON form of a MOSEL description into a fact-tracking system:
 * <pre>
 * { "alphabet": ["a", "b"],
 *   "condition": { "op": "and", "args": [ { "kind": "begWt", "w": "ab" },
 *                                         { "kind": "count", "w": "b", "cmp": ">=", "n": 2 } ] } }
 * </pre>
 * The alphabet is part of the problem: the description stands for the strings over it that satisfy the condition,
 * so it can replace an answer file. It holds 1 to {@value #MAX_ALPHABET} distinct single-character symbols, none of
 * them whitespace, and every word and symbol in the condition must be written over it. A symbol a condition does not
 * mention is read like any other where the condition depends on it (it is a position, it counts towards the length,
 * it breaks a match of w), and leaves the facts unchanged where it does not (a non-digit in the digit sum or in the
 * number in base b).
 * The leaves, with the MOSEL they stand for ({@code cmp} is one of {@code < <= = != >= >}; {@code pos} is a position,
 * {@code { "pos": "fst"|"last"|"fstOc"|"lastOc", "w": ..., "offset": k }} with {@code w} only for fstOc/lastOc;
 * {@code range} is one of {@code psLt psLe psGt psGe}):
 * <ul>
 *   <li>{@code true}, {@code false}, {@code isEmpty};</li>
 *   <li>{@code begWt(w)}, {@code endWt(w)};</li>
 *   <li>{@code oneOf(words)}: the string is one of the listed words, which may include the empty word (our
 *   extension);</li>
 *   <li>{@code count(w, cmp, n)}: |indOf w| CMP n; {@code countMod(w, m, cmp, r)}: |indOf w| % m CMP r;</li>
 *   <li>{@code length(cmp, n)}: |all| CMP n; {@code lengthMod(m, cmp, r)}: |all| % m CMP r;</li>
 *   <li>{@code sumMod(m, cmp, r)}: the digit sum % m CMP r (our extension);</li>
 *   <li>{@code valueMod(base, m, cmp, r)}: the string as a number in base b (2-36), % m CMP r (our extension);</li>
 *   <li>{@code symbolFromStart(a, k)}: a@(fst + k); {@code symbolFromEnd(a, k)}: a@(last - k);</li>
 *   <li>{@code periodicSymbol(a, m, r)}: a@posMod(m, r) (our extension; m = 1 is a@all);</li>
 *   <li>{@code positionCompare(left, cmp, right)}: P CMP P;</li>
 *   <li>{@code symbolOnRange(a, range, pos)}: a@range P;</li>
 *   <li>{@code rangeCount(range, pos, cmp, n)}: |range P| CMP n; {@code rangeCountMod(range, pos, m, cmp, r)}:
 *   |range P| % m CMP r.</li>
 * </ul>
 * {@code and}/{@code or} nodes take two or more {@code args} and fold into nested {@link ProductFactSystem}s,
 * {@code implies}/{@code iff} take exactly two, and a {@code not} node takes exactly one {@code args} entry and wraps
 * it in a {@link NegationFactSystem}. The web app validates the same shape (src/schemas/mosel.ts in AFCT); keep the
 * two in step.
 */
public final class MoselSpecParser {
    /** The largest position offset, and so the furthest a@(last - k) looks back; MOSEL_LIMITS.maxOffset in AFCT. */
    static final int MAX_OFFSET = 10;
    /** The largest alphabet; MOSEL_LIMITS.maxAlphabet in AFCT. */
    static final int MAX_ALPHABET = 36;

    private static final Map<String, BooleanOperator> OPERATORS = Map.of(
            "and", BooleanOperator.AND, "or", BooleanOperator.OR, "not", BooleanOperator.NOT,
            "implies", BooleanOperator.IMPLIES, "iff", BooleanOperator.IFF);

    private final Set<Character> alphabet;

    private MoselSpecParser(Set<Character> alphabet) {
        this.alphabet = alphabet;
    }

    /** Parses a description into its alphabet and its condition's fact system. */
    public static MoselSpec parse(JsonElement json) {
        JsonObject root = object(json, "the MOSEL description");
        for (String key : root.keySet()) {
            if (!key.equals("alphabet") && !key.equals("condition")) {
                throw new IllegalArgumentException("the MOSEL description has an unexpected key '" + key + "'");
            }
        }
        Set<Character> alphabet = alphabet(root.get("alphabet"));
        return new MoselSpec(alphabet, new MoselSpecParser(alphabet).parseCondition(root.get("condition"), "condition"));
    }

    private static Set<Character> alphabet(JsonElement json) {
        if (json == null || !json.isJsonArray() || json.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException("alphabet must be a non-empty array of symbols");
        }
        JsonArray symbols = json.getAsJsonArray();
        if (symbols.size() > MAX_ALPHABET) {
            throw new IllegalArgumentException("alphabet must have at most " + MAX_ALPHABET + " symbols");
        }
        Set<Character> alphabet = new TreeSet<>();
        for (int i = 0; i < symbols.size(); i++) {
            String symbol = string(symbols.get(i), "alphabet[" + i + "]");
            if (symbol.length() != 1 || Character.isWhitespace(symbol.charAt(0))) {
                throw new IllegalArgumentException("alphabet[" + i + "] must be exactly one symbol, not whitespace");
            }
            if (!alphabet.add(symbol.charAt(0))) {
                throw new IllegalArgumentException("alphabet lists '" + symbol + "' twice");
            }
        }
        return Collections.unmodifiableSet(alphabet);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private FactTrackingSystem<Object> parseCondition(JsonElement json, String path) {
        JsonObject node = object(json, path);
        if (node.has("op")) {
            String op = string(node.get("op"), path + ".op");
            BooleanOperator operator = OPERATORS.get(op);
            if (operator == null) {
                throw new IllegalArgumentException(
                        path + ".op must be 'and', 'or', 'not', 'implies' or 'iff', got '" + op + "'");
            }
            JsonElement argsJson = node.get("args");
            if (operator == BooleanOperator.NOT) {
                if (argsJson == null || !argsJson.isJsonArray() || argsJson.getAsJsonArray().size() != 1) {
                    throw new IllegalArgumentException(path + ".args must be an array of exactly one condition for 'not'");
                }
                return new NegationFactSystem<>(parseCondition(argsJson.getAsJsonArray().get(0), path + ".args[0]"));
            }
            boolean binary = operator == BooleanOperator.IMPLIES || operator == BooleanOperator.IFF;
            if (binary && (argsJson == null || !argsJson.isJsonArray() || argsJson.getAsJsonArray().size() != 2)) {
                throw new IllegalArgumentException(path + ".args must be an array of exactly two conditions for '" + op + "'");
            }
            if (argsJson == null || !argsJson.isJsonArray() || argsJson.getAsJsonArray().size() < 2) {
                throw new IllegalArgumentException(path + ".args must be an array of at least two conditions");
            }
            JsonArray args = argsJson.getAsJsonArray();
            FactTrackingSystem<Object> folded = parseCondition(args.get(0), path + ".args[0]");
            for (int i = 1; i < args.size(); i++) {
                FactTrackingSystem<Object> next = parseCondition(args.get(i), path + ".args[" + i + "]");
                folded = (FactTrackingSystem) new ProductFactSystem<>(folded, next, operator);
            }
            return folded;
        }

        String kind = string(node.get("kind"), path + ".kind");
        FactTrackingSystem<?> leaf = switch (kind) {
            case "true" -> new ConstantFactSystem(true);
            case "false" -> new ConstantFactSystem(false);
            case "isEmpty" -> new IsEmptyFactSystem();
            case "begWt" -> new PrefixFactSystem(word(node, path));
            case "endWt" -> new SuffixFactSystem(word(node, path));
            case "oneOf" -> new OneOfFactSystem(words(node, path));
            case "count" -> new CountCompareFactSystem(word(node, path), cmp(node, path), nonNegative(node, "n", path));
            case "countMod" -> {
                String w = word(node, path);
                int m = modulus(node, path);
                yield new ModularCountFactSystem(w, m, cmp(node, path), remainder(node, m, path));
            }
            case "length" -> new LengthCompareFactSystem(cmp(node, path), nonNegative(node, "n", path));
            case "lengthMod" -> {
                int m = modulus(node, path);
                yield new LengthModFactSystem(m, cmp(node, path), remainder(node, m, path));
            }
            case "sumMod" -> {
                int m = modulus(node, path);
                yield new SumModFactSystem(m, cmp(node, path), remainder(node, m, path));
            }
            case "valueMod" -> {
                int base = integer(node.get("base"), path + ".base");
                if (base < 2 || base > ValueModFactSystem.MAX_BASE) {
                    throw new IllegalArgumentException(path + ".base must be between 2 and " + ValueModFactSystem.MAX_BASE);
                }
                int m = modulus(node, path);
                yield new ValueModFactSystem(base, m, cmp(node, path), remainder(node, m, path));
            }
            case "symbolFromStart" -> new SymbolFromStartFactSystem(symbol(node, path), offset(node, "k", path));
            case "symbolFromEnd" -> new SymbolFromEndFactSystem(symbol(node, path), offset(node, "k", path));
            case "periodicSymbol" -> {
                char a = symbol(node, path);
                int m = modulus(node, path);
                yield new PeriodicSymbolFactSystem(a, m, remainder(node, m, path));
            }
            case "positionCompare" -> new PositionCompareFactSystem(
                    position(node.get("left"), path + ".left"), cmp(node, path), position(node.get("right"), path + ".right"));
            case "symbolOnRange" -> new SymbolOnRangeFactSystem(symbol(node, path), range(node, path),
                    position(node.get("pos"), path + ".pos"));
            case "rangeCount" -> new RangeCountFactSystem(range(node, path), position(node.get("pos"), path + ".pos"),
                    null, cmp(node, path), nonNegative(node, "n", path));
            case "rangeCountMod" -> {
                PositionRange range = range(node, path);
                Position position = position(node.get("pos"), path + ".pos");
                int m = modulus(node, path);
                yield new RangeCountFactSystem(range, position, m, cmp(node, path), remainder(node, m, path));
            }
            default -> throw new IllegalArgumentException(path + ".kind '" + kind + "' is not a known MOSEL condition");
        };
        return (FactTrackingSystem<Object>) leaf;
    }

    private Position position(JsonElement json, String path) {
        JsonObject node = object(json, path);
        String name = string(node.get("pos"), path + ".pos");
        Position.Anchor anchor = switch (name) {
            case "fst" -> Position.Anchor.FST;
            case "last" -> Position.Anchor.LAST;
            case "fstOc" -> Position.Anchor.FST_OC;
            case "lastOc" -> Position.Anchor.LAST_OC;
            default -> throw new IllegalArgumentException(path + ".pos must be 'fst', 'last', 'fstOc' or 'lastOc', got '" + name + "'");
        };
        String w = null;
        if (anchor.isOccurrence()) {
            w = word(node, path);
        } else if (node.has("w")) {
            throw new IllegalArgumentException(path + ".w is only for fstOc and lastOc");
        }
        int offset = integer(node.get("offset"), path + ".offset");
        if (Math.abs(offset) > MAX_OFFSET) {
            throw new IllegalArgumentException(path + ".offset must be between -" + MAX_OFFSET + " and " + MAX_OFFSET);
        }
        return new Position(anchor, w, offset);
    }

    private static PositionRange range(JsonObject node, String path) {
        String name = string(node.get("range"), path + ".range");
        try {
            return PositionRange.fromName(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(path + ".range: " + e.getMessage());
        }
    }

    private static Comparison cmp(JsonObject node, String path) {
        String symbol = string(node.get("cmp"), path + ".cmp");
        try {
            return Comparison.fromSymbol(symbol);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(path + ".cmp: " + e.getMessage());
        }
    }

    private char symbol(JsonObject node, String path) {
        String a = string(node.get("a"), path + ".a");
        if (a.length() != 1) {
            throw new IllegalArgumentException(path + ".a must be exactly one symbol");
        }
        checkOverAlphabet(a, path + ".a");
        return a.charAt(0);
    }

    private static int offset(JsonObject node, String key, String path) {
        int value = nonNegative(node, key, path);
        if (value > MAX_OFFSET) {
            throw new IllegalArgumentException(path + "." + key + " must be at most " + MAX_OFFSET);
        }
        return value;
    }

    private String word(JsonObject node, String path) {
        String w = string(node.get("w"), path + ".w");
        if (w.isEmpty()) {
            throw new IllegalArgumentException(path + ".w must not be empty");
        }
        checkOverAlphabet(w, path + ".w");
        return w;
    }

    /** The list of a oneOf: at least one word, each written over the alphabet, the empty word allowed, none twice. */
    private List<String> words(JsonObject node, String path) {
        JsonElement json = node.get("words");
        if (json == null || !json.isJsonArray() || json.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException(path + ".words must be a non-empty array of words");
        }
        JsonArray array = json.getAsJsonArray();
        List<String> words = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            String w = string(array.get(i), path + ".words[" + i + "]");
            checkOverAlphabet(w, path + ".words[" + i + "]");
            if (!seen.add(w)) {
                throw new IllegalArgumentException(path + ".words lists '" + w + "' twice");
            }
            words.add(w);
        }
        return words;
    }

    /** A symbol outside the alphabet never occurs, so a condition naming one is almost certainly a typo. */
    private void checkOverAlphabet(String text, String path) {
        for (char symbol : text.toCharArray()) {
            if (!alphabet.contains(symbol)) {
                throw new IllegalArgumentException(path + " uses '" + symbol + "', which is not in the alphabet");
            }
        }
    }

    private static int modulus(JsonObject node, String path) {
        int m = integer(node.get("m"), path + ".m");
        if (m < 1) {
            throw new IllegalArgumentException(path + ".m must be at least 1");
        }
        return m;
    }

    private static int remainder(JsonObject node, int m, String path) {
        int r = integer(node.get("r"), path + ".r");
        if (r < 0 || r >= m) {
            throw new IllegalArgumentException(path + ".r must be between 0 and m - 1");
        }
        return r;
    }

    private static int nonNegative(JsonObject node, String key, String path) {
        int value = integer(node.get(key), path + "." + key);
        if (value < 0) {
            throw new IllegalArgumentException(path + "." + key + " must not be negative");
        }
        return value;
    }

    private static JsonObject object(JsonElement json, String what) {
        if (json == null || !json.isJsonObject()) {
            throw new IllegalArgumentException(what + " must be a JSON object");
        }
        return json.getAsJsonObject();
    }

    private static String string(JsonElement json, String what) {
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(what + " must be a string");
        }
        return json.getAsString();
    }

    private static int integer(JsonElement json, String what) {
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(what + " must be an integer");
        }
        double value = json.getAsDouble();
        if (value != Math.rint(value) || Math.abs(value) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(what + " must be an integer");
        }
        return (int) value;
    }
}
