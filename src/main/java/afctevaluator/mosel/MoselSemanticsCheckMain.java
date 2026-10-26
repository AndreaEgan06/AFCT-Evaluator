package afctevaluator.mosel;

import afctevaluator.mosel.core.FactTrackingSystem;
import afctevaluator.mosel.grading.MoselSpecParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Standalone check, like {@link MoselVerifierTestMain}, that every leaf kind of the JSON form means what the paper's
 * semantics (Fig. 3) says it means: each fact system built by {@link MoselSpecParser} is run on every string up to a
 * length bound and compared with a direct evaluation of the condition on that string. The alphabet each check runs
 * over is the description's alphabet, and is wider than the condition needs, so each condition is also run on strings
 * with a symbol it never mentions ('c', or a letter among digits).
 * Not wired into {@code Main}.
 */
public final class MoselSemanticsCheckMain {
    private static final int MAX_LENGTH = 9;
    /** Shorter, for the three- and four-symbol alphabets. */
    private static final int MAX_LENGTH_WIDE = 6;

    public static void main(String[] args) {
        List<String> conditions = new ArrayList<>();
        String[] cmps = {"<", "<=", "=", "!=", ">=", ">"};
        List<String> positions = new ArrayList<>();
        for (int offset = -3; offset <= 3; offset++) {
            positions.add(pos("fst", null, offset));
            positions.add(pos("last", null, offset));
            for (String w : new String[]{"a", "ab", "aba", "bb"}) {
                positions.add(pos("fstOc", w, offset));
                positions.add(pos("lastOc", w, offset));
            }
        }
        for (String cmp : cmps) {
            for (int n = 0; n <= 3; n++) {
                for (String w : new String[]{"a", "ab", "aa", "aba"}) {
                    conditions.add(leaf("count", "\"w\":\"" + w + "\",\"cmp\":\"" + cmp + "\",\"n\":" + n));
                }
                conditions.add(leaf("length", "\"cmp\":\"" + cmp + "\",\"n\":" + n));
            }
            for (int m = 1; m <= 3; m++) {
                for (int r = 0; r < m; r++) {
                    conditions.add(leaf("countMod", "\"w\":\"ab\",\"m\":" + m + ",\"cmp\":\"" + cmp + "\",\"r\":" + r));
                    conditions.add(leaf("lengthMod", "\"m\":" + m + ",\"cmp\":\"" + cmp + "\",\"r\":" + r));
                }
            }
        }
        conditions.add(leaf("true", ""));
        conditions.add(leaf("false", ""));
        conditions.add(leaf("isEmpty", ""));
        for (String w : new String[]{"a", "ab", "aba"}) {
            conditions.add(leaf("begWt", "\"w\":\"" + w + "\""));
            conditions.add(leaf("endWt", "\"w\":\"" + w + "\""));
        }
        for (String words : new String[]{"\"ab\"", "\"\"", "\"\",\"a\",\"ab\",\"ba\"", "\"aba\",\"ab\",\"b\",\"bbab\""}) {
            conditions.add(leaf("oneOf", "\"words\":[" + words + "]"));
        }
        for (int k = 0; k <= 4; k++) {
            conditions.add(leaf("symbolFromStart", "\"a\":\"a\",\"k\":" + k));
            conditions.add(leaf("symbolFromEnd", "\"a\":\"b\",\"k\":" + k));
        }
        for (int m = 1; m <= 3; m++) {
            for (int r = 0; r < m; r++) {
                conditions.add(leaf("periodicSymbol", "\"a\":\"a\",\"m\":" + m + ",\"r\":" + r));
            }
        }
        String[] ranges = {"psLt", "psLe", "psGt", "psGe"};
        for (String p : positions) {
            for (String range : ranges) {
                conditions.add(leaf("symbolOnRange", "\"a\":\"a\",\"range\":\"" + range + "\",\"pos\":" + p));
                conditions.add(leaf("rangeCount", "\"range\":\"" + range + "\",\"pos\":" + p + ",\"cmp\":\"<=\",\"n\":2"));
                conditions.add(leaf("rangeCount", "\"range\":\"" + range + "\",\"pos\":" + p + ",\"cmp\":\"=\",\"n\":1"));
                conditions.add(leaf("rangeCountMod", "\"range\":\"" + range + "\",\"pos\":" + p + ",\"m\":3,\"cmp\":\"=\",\"r\":1"));
                conditions.add(leaf("rangeCountMod", "\"range\":\"" + range + "\",\"pos\":" + p + ",\"m\":2,\"cmp\":\"!=\",\"r\":0"));
            }
        }
        for (String left : positions) {
            for (String right : new String[]{pos("fstOc", "b", 0), pos("lastOc", "ab", -1), pos("last", null, -2),
                    pos("fst", null, 2), pos("fstOc", "aa", 1)}) {
                for (String cmp : cmps) {
                    conditions.add(leaf("positionCompare", "\"left\":" + left + ",\"cmp\":\"" + cmp + "\",\"right\":" + right));
                }
            }
        }
        conditions.add("{\"op\":\"implies\",\"args\":[" + leaf("begWt", "\"w\":\"a\"") + "," + leaf("endWt", "\"w\":\"b\"") + "]}");
        conditions.add("{\"op\":\"iff\",\"args\":[" + leaf("begWt", "\"w\":\"a\"") + "," + leaf("endWt", "\"w\":\"b\"") + "]}");

        int failures = 0;
        for (String condition : conditions) {
            failures += check(List.of('a', 'b'), MAX_LENGTH, condition);
            failures += check(List.of('a', 'b', 'c'), MAX_LENGTH_WIDE, condition);
        }
        for (String cmp : cmps) {
            failures += check(List.of('0', '1', '2', 'a'), MAX_LENGTH_WIDE, leaf("sumMod", "\"m\":3,\"cmp\":\"" + cmp + "\",\"r\":1"));
            failures += check(List.of('0', '1', '2', 'a'), MAX_LENGTH_WIDE,
                    leaf("valueMod", "\"base\":2,\"m\":3,\"cmp\":\"" + cmp + "\",\"r\":1"));
            failures += check(List.of('0', '7', 'b', 'F', 'x'), MAX_LENGTH_WIDE,
                    leaf("valueMod", "\"base\":16,\"m\":7,\"cmp\":\"" + cmp + "\",\"r\":4"));
        }
        System.out.println(conditions.size() + 3 * cmps.length + " conditions checked on every string up to length "
                + MAX_LENGTH + " over {a, b} and " + MAX_LENGTH_WIDE + " over {a, b, c} (sumMod: {0, 1, 2, a}; valueMod: "
                + "{0, 1, 2, a} in base 2 and {0, 7, b, F, x} in base 16): "
                + (failures == 0 ? "PASS" : failures + " FAILED"));
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static String leaf(String kind, String fields) {
        return "{\"kind\":\"" + kind + "\"" + (fields.isEmpty() ? "" : "," + fields) + "}";
    }

    private static String pos(String anchor, String w, int offset) {
        return "{\"pos\":\"" + anchor + "\"" + (w == null ? "" : ",\"w\":\"" + w + "\"") + ",\"offset\":" + offset + "}";
    }

    @SuppressWarnings("unchecked")
    private static int check(List<Character> alphabet, int maxLength, String conditionJson) {
        String alphabetJson = alphabet.stream().map(symbol -> "\"" + symbol + "\"").collect(Collectors.joining(","));
        JsonObject spec = JsonParser.parseString("{\"alphabet\":[" + alphabetJson + "],\"condition\":" + conditionJson + "}")
                .getAsJsonObject();
        FactTrackingSystem<Object> system = MoselSpecParser.parse(spec).system();
        Set<Object> facts = new java.util.HashSet<>();
        List<String> strings = new ArrayList<>();
        strings.add("");
        for (int i = 0; i < strings.size(); i++) {
            String s = strings.get(i);
            Object fact = system.initialFact();
            for (char symbol : s.toCharArray()) {
                fact = system.transfer(fact, symbol);
            }
            facts.add(fact);
            boolean expected = holds(spec.get("condition").getAsJsonObject(), s);
            if (system.isAccepting(fact) != expected) {
                System.out.println("FAIL " + conditionJson + " on '" + s + "': expected " + expected);
                return 1;
            }
            if (s.length() < maxLength) {
                for (char symbol : alphabet) {
                    strings.add(s + symbol);
                }
            }
        }
        return 0;
    }

    /** The paper's semantics, evaluated directly on s. */
    private static boolean holds(JsonObject node, String s) {
        if (node.has("op")) {
            JsonArray args = node.getAsJsonArray("args");
            List<Boolean> values = new ArrayList<>();
            for (JsonElement arg : args) {
                values.add(holds(arg.getAsJsonObject(), s));
            }
            return switch (node.get("op").getAsString()) {
                case "and" -> !values.contains(false);
                case "or" -> values.contains(true);
                case "not" -> !values.get(0);
                case "implies" -> !values.get(0) || values.get(1);
                case "iff" -> values.get(0).equals(values.get(1));
                default -> throw new IllegalStateException();
            };
        }
        int n = s.length();
        return switch (node.get("kind").getAsString()) {
            case "true" -> true;
            case "false" -> false;
            case "isEmpty" -> n == 0;
            case "begWt" -> s.startsWith(str(node, "w"));
            case "endWt" -> s.endsWith(str(node, "w"));
            case "oneOf" -> {
                boolean listed = false;
                for (JsonElement w : node.getAsJsonArray("words")) {
                    listed |= w.getAsString().equals(s);
                }
                yield listed;
            }
            case "count" -> cmp(node, occurrences(s, str(node, "w")).size(), num(node, "n"));
            case "countMod" -> cmp(node, occurrences(s, str(node, "w")).size() % num(node, "m"), num(node, "r"));
            case "length" -> cmp(node, n, num(node, "n"));
            case "lengthMod" -> cmp(node, n % num(node, "m"), num(node, "r"));
            case "valueMod" -> {
                // An independent oracle: keep the base-b digits and let BigInteger read the number.
                int base = num(node, "base");
                StringBuilder digits = new StringBuilder("0");
                s.chars().filter(c -> c < 128 && Character.digit(c, base) >= 0).forEach(c -> digits.append((char) c));
                yield cmp(node, new BigInteger(digits.toString(), base).mod(BigInteger.valueOf(num(node, "m"))).intValue(), num(node, "r"));
            }
            case "sumMod" -> cmp(node, s.chars().filter(c -> '0' <= c && c <= '9').map(c -> c - '0').sum() % num(node, "m"), num(node, "r"));
            case "symbolFromStart" -> symbolAt(s, 1 + num(node, "k"), str(node, "a").charAt(0));
            case "symbolFromEnd" -> symbolAt(s, n - num(node, "k"), str(node, "a").charAt(0));
            case "periodicSymbol" -> {
                boolean all = true;
                for (int j = 1; j <= n; j++) {
                    all &= j % num(node, "m") != num(node, "r") || s.charAt(j - 1) == str(node, "a").charAt(0);
                }
                yield all;
            }
            case "positionCompare" -> {
                Integer left = position(node.getAsJsonObject("left"), s);
                Integer right = position(node.getAsJsonObject("right"), s);
                yield left != null && right != null && cmp(node, left, right);
            }
            case "symbolOnRange" -> {
                Set<Integer> range = range(node, s);
                yield range != null && range.stream().allMatch(j -> s.charAt(j - 1) == str(node, "a").charAt(0));
            }
            case "rangeCount" -> {
                Set<Integer> range = range(node, s);
                yield range != null && cmp(node, range.size(), num(node, "n"));
            }
            case "rangeCountMod" -> {
                Set<Integer> range = range(node, s);
                yield range != null && cmp(node, range.size() % num(node, "m"), num(node, "r"));
            }
            default -> throw new IllegalStateException(node.toString());
        };
    }

    private static boolean symbolAt(String s, int j, char a) {
        return j >= 1 && j <= s.length() && s.charAt(j - 1) == a;
    }

    private static TreeSet<Integer> occurrences(String s, String w) {
        TreeSet<Integer> starts = new TreeSet<>();
        for (int j = 1; j + w.length() - 1 <= s.length(); j++) {
            if (s.startsWith(w, j - 1)) {
                starts.add(j);
            }
        }
        return starts;
    }

    /** P's value, or null where the paper leaves it undefined. */
    private static Integer position(JsonObject node, String s) {
        int n = s.length();
        Integer j = switch (str(node, "pos")) {
            case "fst" -> n > 0 ? 1 : null;
            case "last" -> n > 0 ? n : null;
            case "fstOc" -> occurrences(s, str(node, "w")).isEmpty() ? null : occurrences(s, str(node, "w")).first();
            case "lastOc" -> occurrences(s, str(node, "w")).isEmpty() ? null : occurrences(s, str(node, "w")).last();
            default -> throw new IllegalStateException();
        };
        if (j == null) {
            return null;
        }
        int t = j + num(node, "offset");
        return t >= 1 && t <= n ? t : null;
    }

    private static Set<Integer> range(JsonObject node, String s) {
        Integer t = position(node.getAsJsonObject("pos"), s);
        if (t == null) {
            return null;
        }
        int from;
        int to;
        switch (str(node, "range")) {
            case "psLt" -> { from = 1; to = t - 1; }
            case "psLe" -> { from = 1; to = t; }
            case "psGt" -> { from = t + 1; to = s.length(); }
            case "psGe" -> { from = t; to = s.length(); }
            default -> throw new IllegalStateException();
        }
        Set<Integer> range = new TreeSet<>();
        for (int j = from; j <= to; j++) {
            range.add(j);
        }
        return range;
    }

    private static boolean cmp(JsonObject node, int left, int right) {
        return switch (str(node, "cmp")) {
            case "<" -> left < right;
            case "<=" -> left <= right;
            case "=" -> left == right;
            case "!=" -> left != right;
            case ">=" -> left >= right;
            case ">" -> left > right;
            default -> throw new IllegalStateException();
        };
    }

    private static String str(JsonObject node, String key) {
        return node.get(key).getAsString();
    }

    private static int num(JsonObject node, String key) {
        return node.get(key).getAsInt();
    }
}
