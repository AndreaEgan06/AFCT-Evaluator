package afctevaluator.mosel;

import automata.State;
import automata.Transition;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Annotates every state of a DFA with its reachable facts under a condition, then diagnoses each state's accept/reject designation against those facts. */
public final class DfaFactVerifier {
    private DfaFactVerifier() {
    }

    public static <D> VerificationReport<D> verify(FiniteStateAutomaton dfa, FactTrackingSystem<D> system) {
        Map<State, Set<D>> facts = new HashMap<>();
        for (State state : dfa.getStates()) {
            facts.put(state, new HashSet<>());
        }

        State start = dfa.getInitialState();
        facts.get(start).add(system.initialFact());

        Deque<State> worklist = new ArrayDeque<>();
        worklist.add(start);

        while (!worklist.isEmpty()) {
            State state = worklist.poll();
            for (Transition transition : dfa.getTransitionsFromState(state)) {
                FSATransition fsaTransition = (FSATransition) transition;
                char symbol = fsaTransition.getLabel().charAt(0);
                State next = fsaTransition.getToState();

                Set<D> newFacts = new HashSet<>();
                for (D fact : facts.get(state)) {
                    newFacts.add(system.transfer(fact, symbol));
                }

                if (facts.get(next).addAll(newFacts)) {
                    worklist.add(next);
                }
            }
        }

        Map<State, StateDiagnosis> diagnoses = new HashMap<>();
        for (State state : dfa.getStates()) {
            diagnoses.put(state, classify(dfa, system, state, facts.get(state)));
        }

        return new VerificationReport<>(facts, diagnoses);
    }

    private static <D> StateDiagnosis classify(FiniteStateAutomaton dfa, FactTrackingSystem<D> system, State state, Set<D> stateFacts) {
        if (stateFacts.isEmpty()) {
            return StateDiagnosis.UNREACHABLE;
        }

        boolean hasAccepting = false;
        boolean hasRejecting = false;
        for (D fact : stateFacts) {
            if (system.isAccepting(fact)) {
                hasAccepting = true;
            } else {
                hasRejecting = true;
            }
        }

        if (hasAccepting && hasRejecting) {
            return StateDiagnosis.CONFLICTED;
        }

        boolean isFinal = dfa.isFinalState(state);
        if (hasAccepting && !isFinal) {
            return StateDiagnosis.MISSING_ACCEPTANCE;
        }
        if (hasRejecting && isFinal) {
            return StateDiagnosis.SPURIOUS_ACCEPTANCE;
        }
        return StateDiagnosis.CONSISTENT;
    }
}
