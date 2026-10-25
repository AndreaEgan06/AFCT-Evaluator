package afctevaluator.mosel;

import afctevaluator.mosel.core.Comparison;
import afctevaluator.mosel.core.FactTrackingSystem;
import afctevaluator.mosel.logic.BooleanOperator;
import afctevaluator.mosel.logic.NegationFactSystem;
import afctevaluator.mosel.logic.ProductFactSystem;
import afctevaluator.mosel.predicates.counts.CountCompareFactSystem;
import afctevaluator.mosel.predicates.counts.LengthCompareFactSystem;
import afctevaluator.mosel.predicates.counts.LengthModFactSystem;
import afctevaluator.mosel.predicates.counts.ModularCountFactSystem;
import afctevaluator.mosel.predicates.counts.SumModFactSystem;
import afctevaluator.mosel.predicates.positions.Position;
import afctevaluator.mosel.predicates.positions.PositionCompareFactSystem;
import afctevaluator.mosel.predicates.positions.PositionRange;
import afctevaluator.mosel.predicates.positions.RangeCountFactSystem;
import afctevaluator.mosel.predicates.positions.SymbolOnRangeFactSystem;
import afctevaluator.mosel.predicates.symbols.PeriodicSymbolFactSystem;
import afctevaluator.mosel.predicates.symbols.SymbolFromEndFactSystem;
import afctevaluator.mosel.predicates.symbols.SymbolFromStartFactSystem;
import afctevaluator.mosel.predicates.words.PrefixFactSystem;
import afctevaluator.mosel.predicates.words.SuffixFactSystem;
import afctevaluator.mosel.analysis.DfaFactConstructor;
import afctevaluator.mosel.analysis.FactVerifier;
import afctevaluator.mosel.analysis.StateDiagnosis;
import afctevaluator.mosel.analysis.VerificationReport;
import automata.State;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;

import java.awt.Point;
import java.util.Map;
import java.util.Set;

/**
 * Standalone smoke test for the fact-tracking verifier, in the same spirit as {@code TestMain}:
 * hand-built DFAs and NFAs with known mistakes, run through the verifier, with actual vs. expected
 * diagnoses printed for inspection. Not wired into {@code Main} or the golden-case suite.
 */
public final class MoselVerifierTestMain {
    public static void main(String[] args) {
        runCase("Prefix 'ab': correct DFA is fully consistent",
                correctPrefixDfa(), new PrefixFactSystem("ab"), Map.of());

        runCase("Prefix 'ab': missing acceptance when the fully-matched state isn't final",
                prefixDfaMissingAcceptance(), new PrefixFactSystem("ab"),
                Map.of("q2", StateDiagnosis.MISSING_ACCEPTANCE));

        runCase("Prefix 'ab': spurious acceptance when the FAIL state is marked final",
                prefixDfaSpuriousAcceptance(), new PrefixFactSystem("ab"),
                Map.of("fail", StateDiagnosis.SPURIOUS_ACCEPTANCE));

        runCase("Count exactly 1 'a': treating 'k-th a reached' as terminal conflates k and GT",
                countExactlyConflatedDfa(), new CountCompareFactSystem("a", Comparison.EQ, 1),
                Map.of("q1", StateDiagnosis.CONFLICTED));

        runCase("Compound: begWt 'a' AND |indOf 'b'| >= 1, correct DFA",
                compoundDfa(),
                new ProductFactSystem<>(new PrefixFactSystem("a"), new CountCompareFactSystem("b", Comparison.GE, 1), BooleanOperator.AND),
                Map.of());

        runCase("NOT begWt 'ab': the prefix DFA with its accepting states flipped is fully consistent",
                complementedPrefixDfa(), new NegationFactSystem<>(new PrefixFactSystem("ab")), Map.of());

        runCase("NOT begWt 'ab': the un-negated prefix DFA gets every state's acceptance backwards",
                correctPrefixDfa(), new NegationFactSystem<>(new PrefixFactSystem("ab")),
                Map.of("q0", StateDiagnosis.MISSING_ACCEPTANCE,
                        "q1", StateDiagnosis.MISSING_ACCEPTANCE,
                        "q2", StateDiagnosis.SPURIOUS_ACCEPTANCE,
                        "fail", StateDiagnosis.MISSING_ACCEPTANCE));

        runCase("NOT |indOf 'a'| = 1: negation keeps a structural conflict a conflict",
                countExactlyConflatedDfa(), new NegationFactSystem<>(new CountCompareFactSystem("a", Comparison.EQ, 1)),
                Map.of("q0", StateDiagnosis.MISSING_ACCEPTANCE, "q1", StateDiagnosis.CONFLICTED));

        runCase("Sum of the digits is divisible by 4: correct DFA and fully consistent",
            sumModDfa(),
            new SumModFactSystem(4, Comparison.EQ, 0),
            Map.of()
        );

        runCase("Sum of the digits is divisible by 4: a non-digit symbol looping on every state changes nothing",
            withSelfLoops(sumModDfa(), "x"),
            new SumModFactSystem(4, Comparison.EQ, 0),
            Map.of()
        );

        runCase("Sum of the digits is divisible by 4: incorrect DFA and fully conflicted",
            badSumModDfa(),
            new SumModFactSystem(4, Comparison.EQ, 0),
            Map.of("q0", StateDiagnosis.CONFLICTED,
                "q1", StateDiagnosis.CONFLICTED,
                "q2", StateDiagnosis.CONFLICTED,
                "q3", StateDiagnosis.CONFLICTED
            )
        );

        runCase("NFA, begWt 'ab': a multi-symbol label and a λ-transition carry facts, fully consistent",
                prefixLambdaNfa(false), new PrefixFactSystem("ab"), Map.of());

        runCase("NFA, begWt 'ab': a λ-transition that skips the prefix carries the initial fact into an accepting state",
                prefixLambdaNfa(true), new PrefixFactSystem("ab"),
                Map.of("q2", StateDiagnosis.CONFLICTED));

        runCase("NFA, endWt 'ab': the guessing state also sees strings in the language, so it is conflicted",
                suffixGuessingNfa(), new SuffixFactSystem("ab"),
                Map.of("q0", StateDiagnosis.CONFLICTED));

        Set<Character> ab = Set.of('a', 'b');
        runConstructionCase("Construct: begWt 'ab'", new PrefixFactSystem("ab"), ab);
        runConstructionCase("Construct: endWt 'ab'", new SuffixFactSystem("ab"), ab);
        runConstructionCase("Construct: |indOf 'ab'| >= 2", new CountCompareFactSystem("ab", Comparison.GE, 2), ab);
        runConstructionCase("Construct: |indOf 'a'| = 2", new CountCompareFactSystem("a", Comparison.EQ, 2), ab);
        runConstructionCase("Construct: |indOf 'a'| <= 2", new CountCompareFactSystem("a", Comparison.LE, 2), ab);
        runConstructionCase("Construct: |indOf 'a'| mod 3 = 1", new ModularCountFactSystem("a", 3, Comparison.EQ, 1), ab);
        runConstructionCase("Construct: |s| mod 4 = 2", new LengthModFactSystem(4, Comparison.EQ, 2), ab);
        runConstructionCase("Construct: begWt 'a' AND |indOf 'b'| >= 1",
                new ProductFactSystem<>(new PrefixFactSystem("a"), new CountCompareFactSystem("b", Comparison.GE, 1), BooleanOperator.AND), ab);
        runConstructionCase("Construct: NOT endWt 'ab'", new NegationFactSystem<>(new SuffixFactSystem("ab")), ab);
        runConstructionCase("Construct: begWt 'a' AND NOT |indOf 'b'| >= 2",
                new ProductFactSystem<>(new PrefixFactSystem("a"),
                        new NegationFactSystem<>(new CountCompareFactSystem("b", Comparison.GE, 2)), BooleanOperator.AND), ab);
        Set<Character> _012 = Set.of('0', '1', '2');
        runConstructionCase("Construct: sum % 0 mod 4", new SumModFactSystem(4, Comparison.EQ, 0), _012);
        runConstructionCase("Construct: sum % 0 mod 4, with a non-digit", new SumModFactSystem(4, Comparison.EQ, 0), Set.of('0', '1', '2', 'x'));
        runConstructionCase("Construct: |s| % 3 != 0", new LengthModFactSystem(3, Comparison.NE, 0), ab);
        runConstructionCase("Construct: |s| >= 3", new LengthCompareFactSystem(Comparison.GE, 3), ab);
        runConstructionCase("Construct: a@(fst + 2)", new SymbolFromStartFactSystem('a', 2), ab);
        runConstructionCase("Construct: a@(last - 2)", new SymbolFromEndFactSystem('a', 2), ab);
        runConstructionCase("Construct: a@posMod(2, 1)", new PeriodicSymbolFactSystem('a', 2, 1), ab);
        runConstructionCase("Construct: fstOc 'a' < fstOc 'b'", new PositionCompareFactSystem(
                new Position(Position.Anchor.FST_OC, "a", 0), Comparison.LT,
                new Position(Position.Anchor.FST_OC, "b", 0)), ab);
        runConstructionCase("Construct: b@psGt fstOc 'b'", new SymbolOnRangeFactSystem('b', PositionRange.PS_GT,
                new Position(Position.Anchor.FST_OC, "b", 0)), ab);
        runConstructionCase("Construct: |psLe fstOc 'b'| % 2 = 1", new RangeCountFactSystem(PositionRange.PS_LE,
                new Position(Position.Anchor.FST_OC, "b", 0), 2, Comparison.EQ, 1), ab);
    }

    /**
     * Builds the canonical DFA for {@code system} via {@link DfaFactConstructor}, then feeds it
     * back through {@link FactVerifier}: every reachable state must come back CONSISTENT,
     * since the constructor places each state's facts and acceptance in lockstep by definition.
     */
    private static <D> void runConstructionCase(String name, FactTrackingSystem<D> system, Set<Character> alphabet) {
        FiniteStateAutomaton dfa = DfaFactConstructor.build(system, alphabet);
        runCase(name, dfa, system, Map.of());
    }

    private static <D> void runCase(String name, FiniteStateAutomaton dfa, FactTrackingSystem<D> system,
                                     Map<String, StateDiagnosis> expectedExceptions) {
        System.out.println(name);
        VerificationReport<D> report = FactVerifier.verify(dfa, system);

        boolean allMatched = true;
        for (State state : dfa.getStates()) {
            StateDiagnosis expected = expectedExceptions.getOrDefault(state.getName(), StateDiagnosis.CONSISTENT);
            StateDiagnosis actual = report.diagnosisOf(state);
            boolean matched = actual == expected;
            allMatched &= matched;
            System.out.printf("  %-5s facts=%-30s expected=%-20s actual=%-20s %s%n",
                    state.getName(), report.factsOf(state), expected, actual, matched ? "OK" : "MISMATCH");
        }
        System.out.println(allMatched ? "  PASS" : "  FAIL");
        System.out.println();
    }

    private static State newState(FiniteStateAutomaton dfa, String name, int x, int y) {
        State state = dfa.createState(new Point(x, y));
        state.setName(name);
        return state;
    }

    private static FiniteStateAutomaton correctPrefixDfa() {
        FiniteStateAutomaton dfa = new FiniteStateAutomaton();
        State q0 = newState(dfa, "q0", 0, 0);
        State q1 = newState(dfa, "q1", 100, 0);
        State q2 = newState(dfa, "q2", 200, 0);
        State fail = newState(dfa, "fail", 100, 100);
        dfa.setInitialState(q0);
        dfa.addFinalState(q2);
        dfa.addTransition(new FSATransition(q0, q1, "a"));
        dfa.addTransition(new FSATransition(q0, fail, "b"));
        dfa.addTransition(new FSATransition(q1, q2, "b"));
        dfa.addTransition(new FSATransition(q1, fail, "a"));
        dfa.addTransition(new FSATransition(q2, q2, "a"));
        dfa.addTransition(new FSATransition(q2, q2, "b"));
        dfa.addTransition(new FSATransition(fail, fail, "a"));
        dfa.addTransition(new FSATransition(fail, fail, "b"));
        return dfa;
    }

    private static FiniteStateAutomaton complementedPrefixDfa() {
        FiniteStateAutomaton dfa = correctPrefixDfa();
        for (State state : dfa.getStates()) {
            if (dfa.isFinalState(state)) {
                dfa.removeFinalState(state);
            } else {
                dfa.addFinalState(state);
            }
        }
        return dfa;
    }

    private static FiniteStateAutomaton prefixDfaMissingAcceptance() {
        FiniteStateAutomaton dfa = correctPrefixDfa();
        dfa.removeFinalState(stateNamed(dfa, "q2"));
        return dfa;
    }

    private static FiniteStateAutomaton prefixDfaSpuriousAcceptance() {
        FiniteStateAutomaton dfa = correctPrefixDfa();
        dfa.addFinalState(stateNamed(dfa, "fail"));
        return dfa;
    }

    private static State stateNamed(FiniteStateAutomaton dfa, String name) {
        for (State state : dfa.getStates()) {
            if (name.equals(state.getName())) {
                return state;
            }
        }
        throw new IllegalArgumentException("no state named " + name);
    }

    /** begWt 'ab' as q0 --ab--> q1 --λ--> q2, with q2 looping on a and b; {@code skip} adds a λ-transition from q0 straight to q2. */
    private static FiniteStateAutomaton prefixLambdaNfa(boolean skip) {
        FiniteStateAutomaton nfa = new FiniteStateAutomaton();
        State q0 = newState(nfa, "q0", 0, 0);
        State q1 = newState(nfa, "q1", 100, 0);
        State q2 = newState(nfa, "q2", 200, 0);
        nfa.setInitialState(q0);
        nfa.addFinalState(q1);
        nfa.addFinalState(q2);
        nfa.addTransition(new FSATransition(q0, q1, "ab"));
        nfa.addTransition(new FSATransition(q1, q2, ""));
        nfa.addTransition(new FSATransition(q2, q2, "a"));
        nfa.addTransition(new FSATransition(q2, q2, "b"));
        if (skip) {
            nfa.addTransition(new FSATransition(q0, q2, ""));
        }
        return nfa;
    }

    /** endWt 'ab' as the textbook NFA: q0 loops on a and b, and guesses the final 'ab' via q1 to q2. */
    private static FiniteStateAutomaton suffixGuessingNfa() {
        FiniteStateAutomaton nfa = new FiniteStateAutomaton();
        State q0 = newState(nfa, "q0", 0, 0);
        State q1 = newState(nfa, "q1", 100, 0);
        State q2 = newState(nfa, "q2", 200, 0);
        nfa.setInitialState(q0);
        nfa.addFinalState(q2);
        nfa.addTransition(new FSATransition(q0, q0, "a"));
        nfa.addTransition(new FSATransition(q0, q0, "b"));
        nfa.addTransition(new FSATransition(q0, q1, "a"));
        nfa.addTransition(new FSATransition(q1, q2, "b"));
        return nfa;
    }

    private static FiniteStateAutomaton countExactlyConflatedDfa() {
        FiniteStateAutomaton dfa = new FiniteStateAutomaton();
        State q0 = newState(dfa, "q0", 0, 0);
        State q1 = newState(dfa, "q1", 100, 0);
        dfa.setInitialState(q0);
        dfa.addFinalState(q1);
        dfa.addTransition(new FSATransition(q0, q1, "a"));
        dfa.addTransition(new FSATransition(q0, q0, "b"));
        dfa.addTransition(new FSATransition(q1, q1, "a"));
        dfa.addTransition(new FSATransition(q1, q1, "b"));
        return dfa;
    }

    private static FiniteStateAutomaton compoundDfa() {
        FiniteStateAutomaton dfa = new FiniteStateAutomaton();
        State q0 = newState(dfa, "q0", 0, 0);
        State q1 = newState(dfa, "q1", 100, 0);
        State q2 = newState(dfa, "q2", 200, 0);
        State fail = newState(dfa, "fail", 100, 100);
        dfa.setInitialState(q0);
        dfa.addFinalState(q2);
        dfa.addTransition(new FSATransition(q0, q1, "a"));
        dfa.addTransition(new FSATransition(q0, fail, "b"));
        dfa.addTransition(new FSATransition(q1, q1, "a"));
        dfa.addTransition(new FSATransition(q1, q2, "b"));
        dfa.addTransition(new FSATransition(q2, q2, "a"));
        dfa.addTransition(new FSATransition(q2, q2, "b"));
        dfa.addTransition(new FSATransition(fail, fail, "a"));
        dfa.addTransition(new FSATransition(fail, fail, "b"));
        return dfa;
    }

    private static FiniteStateAutomaton sumModDfa() {
        FiniteStateAutomaton dfa = new FiniteStateAutomaton();
        State q0 = newState(dfa, "q0", 0, 0);
        State q1 = newState(dfa, "q1", 100, 0);
        State q2 = newState(dfa, "q2", 100, 100);
        State q3 = newState(dfa, "q3", 0, 100);
        dfa.setInitialState(q0);
        dfa.addFinalState(q0);
        dfa.addTransition(new FSATransition(q0, q0, "0"));
        dfa.addTransition(new FSATransition(q1, q1, "0"));
        dfa.addTransition(new FSATransition(q2, q2, "0"));
        dfa.addTransition(new FSATransition(q3, q3, "0"));
        dfa.addTransition(new FSATransition(q0, q1, "1"));
        dfa.addTransition(new FSATransition(q1, q2, "1"));
        dfa.addTransition(new FSATransition(q2, q3, "1"));
        dfa.addTransition(new FSATransition(q3, q0, "1"));
        dfa.addTransition(new FSATransition(q0, q2, "2"));
        dfa.addTransition(new FSATransition(q1, q3, "2"));
        dfa.addTransition(new FSATransition(q2, q0, "2"));
        dfa.addTransition(new FSATransition(q3, q1, "2"));
        return dfa;
    }

    private static FiniteStateAutomaton withSelfLoops(FiniteStateAutomaton dfa, String symbol) {
        for (State state : dfa.getStates()) {
            dfa.addTransition(new FSATransition(state, state, symbol));
        }
        return dfa;
    }

    private static FiniteStateAutomaton badSumModDfa() {
        FiniteStateAutomaton dfa = new FiniteStateAutomaton();
        State q0 = newState(dfa, "q0", 0, 0);
        State q1 = newState(dfa, "q1", 100, 0);
        State q2 = newState(dfa, "q2", 100, 100);
        State q3 = newState(dfa, "q3", 0, 100);
        dfa.setInitialState(q0);
        dfa.addFinalState(q0);
        dfa.addTransition(new FSATransition(q0, q1, "0"));
        dfa.addTransition(new FSATransition(q1, q1, "0"));
        dfa.addTransition(new FSATransition(q2, q2, "0"));
        dfa.addTransition(new FSATransition(q3, q3, "0"));
        dfa.addTransition(new FSATransition(q0, q1, "1"));
        dfa.addTransition(new FSATransition(q1, q2, "1"));
        dfa.addTransition(new FSATransition(q2, q3, "1"));
        dfa.addTransition(new FSATransition(q3, q0, "1"));
        dfa.addTransition(new FSATransition(q0, q2, "2"));
        dfa.addTransition(new FSATransition(q1, q3, "2"));
        dfa.addTransition(new FSATransition(q2, q0, "2"));
        dfa.addTransition(new FSATransition(q3, q1, "2"));
        return dfa;
    }
}
