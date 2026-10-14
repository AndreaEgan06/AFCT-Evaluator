package afctevaluator.mosel;

import afctevaluator.mosel.analysis.DfaFactConstructor;
import afctevaluator.mosel.analysis.DfaFactVerifier;
import afctevaluator.mosel.analysis.StateDiagnosis;
import afctevaluator.mosel.analysis.VerificationReport;
import afctevaluator.mosel.concrete_fact_systems.CountAtLeastFactSystem;
import afctevaluator.mosel.concrete_fact_systems.CountAtMostFactSystem;
import afctevaluator.mosel.concrete_fact_systems.CountExactlyFactSystem;
import afctevaluator.mosel.concrete_fact_systems.LengthModFactSystem;
import afctevaluator.mosel.concrete_fact_systems.ModularCountFactSystem;
import afctevaluator.mosel.concrete_fact_systems.PrefixFactSystem;
import afctevaluator.mosel.concrete_fact_systems.SuffixFactSystem;
import afctevaluator.mosel.concrete_fact_systems.SumModFactSystem;
import afctevaluator.mosel.fact_system_tree_structure.BooleanOperator;
import afctevaluator.mosel.fact_system_tree_structure.FactTrackingSystem;
import afctevaluator.mosel.fact_system_tree_structure.ProductFactSystem;
import automata.State;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;

import java.awt.Point;
import java.util.Map;
import java.util.Set;

/**
 * Standalone smoke test for the fact-tracking verifier, in the same spirit as {@code TestMain}:
 * hand-built DFAs with known mistakes, run through the verifier, with actual vs. expected
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
                countExactlyConflatedDfa(), new CountExactlyFactSystem("a", 1),
                Map.of("q1", StateDiagnosis.CONFLICTED));

        runCase("Compound: begWt 'a' AND |indOf 'b'| >= 1, correct DFA",
                compoundDfa(),
                new ProductFactSystem<>(new PrefixFactSystem("a"), new CountAtLeastFactSystem("b", 1), BooleanOperator.AND),
                Map.of());

        runCase("Sum of the digits is divisible by 4: correct DFA and fully consistent",
            sumModDfa(),
            new SumModFactSystem(0, 4),
            Map.of()
        );

        runCase("Sum of the digits is divisible by 4: incorrect DFA and fully conflicted",
            badSumModDfa(),
            new SumModFactSystem(0, 4),
            Map.of("q0", StateDiagnosis.CONFLICTED,
                "q1", StateDiagnosis.CONFLICTED,
                "q2", StateDiagnosis.CONFLICTED,
                "q3", StateDiagnosis.CONFLICTED
            )
        );

        Set<Character> ab = Set.of('a', 'b');
        runConstructionCase("Construct: begWt 'ab'", new PrefixFactSystem("ab"), ab);
        runConstructionCase("Construct: endWt 'ab'", new SuffixFactSystem("ab"), ab);
        runConstructionCase("Construct: |indOf 'ab'| >= 2", new CountAtLeastFactSystem("ab", 2), ab);
        runConstructionCase("Construct: |indOf 'a'| = 2", new CountExactlyFactSystem("a", 2), ab);
        runConstructionCase("Construct: |indOf 'a'| <= 2", new CountAtMostFactSystem("a", 2), ab);
        runConstructionCase("Construct: |indOf 'a'| mod 3 = 1", new ModularCountFactSystem("a", 3, 1), ab);
        runConstructionCase("Construct: |s| mod 4 = 2", new LengthModFactSystem(4, 2), ab);
        runConstructionCase("Construct: begWt 'a' AND |indOf 'b'| >= 1",
                new ProductFactSystem<>(new PrefixFactSystem("a"), new CountAtLeastFactSystem("b", 1), BooleanOperator.AND), ab);
        Set<Character> _012 = Set.of('0', '1', '2');
        runConstructionCase("Construct: sum % 0 mod 4", new SumModFactSystem(0, 4), _012);
    }

    /**
     * Builds the canonical DFA for {@code system} via {@link DfaFactConstructor}, then feeds it
     * back through {@link DfaFactVerifier}: every reachable state must come back CONSISTENT,
     * since the constructor places each state's facts and acceptance in lockstep by definition.
     */
    private static <D> void runConstructionCase(String name, FactTrackingSystem<D> system, Set<Character> alphabet) {
        FiniteStateAutomaton dfa = DfaFactConstructor.build(system, alphabet);
        runCase(name, dfa, system, Map.of());
    }

    private static <D> void runCase(String name, FiniteStateAutomaton dfa, FactTrackingSystem<D> system,
                                     Map<String, StateDiagnosis> expectedExceptions) {
        System.out.println(name);
        VerificationReport<D> report = DfaFactVerifier.verify(dfa, system);

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
