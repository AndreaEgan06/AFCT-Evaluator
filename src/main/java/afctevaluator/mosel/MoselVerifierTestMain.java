package afctevaluator.mosel;

import automata.State;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;

import java.awt.Point;
import java.util.Map;

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
}
