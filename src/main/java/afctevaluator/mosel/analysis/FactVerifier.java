package afctevaluator.mosel.analysis;

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

import afctevaluator.mosel.core.FactTrackingSystem;

/**
 * Annotates every state of a finite automaton with its reachable facts under a condition, then diagnoses each state's accept/reject designation
 * against those facts. The automaton may be nondeterministic: facts flow along every transition out of a state, a λ-transition (empty label)
 * passes the origin's facts to the destination unchanged, and a multi-symbol label applies the transfer function once per symbol.
 * A missing transition is invisible here, so a student submission should go through {@link AnalysisAutomatonBuilder} first.
 */
public final class FactVerifier {
    private FactVerifier() {
    }

    public static <D> VerificationReport<D> verify(FiniteStateAutomaton automaton, FactTrackingSystem<D> system) {
        Map<State, Set<D>> facts = new HashMap<>();
        for (State state : automaton.getStates()) {
            facts.put(state, new HashSet<>());
        }

        State start = automaton.getInitialState();
        facts.get(start).add(system.initialFact());

        Deque<State> worklist = new ArrayDeque<>();
        worklist.add(start);

        while (!worklist.isEmpty()) {
            State state = worklist.poll();
            for (Transition transition : automaton.getTransitionsFromState(state)) {
                FSATransition fsaTransition = (FSATransition) transition;
                String label = fsaTransition.getLabel();
                State next = fsaTransition.getToState();

                Set<D> newFacts = new HashSet<>();
                for (D fact : facts.get(state)) {
                    for (char symbol : label.toCharArray()) {
                        fact = system.transfer(fact, symbol);
                    }
                    newFacts.add(fact);
                }

                if (facts.get(next).addAll(newFacts)) {
                    worklist.add(next);
                }
            }
        }

        Map<State, StateDiagnosis> diagnoses = new HashMap<>();
        for (State state : automaton.getStates()) {
            diagnoses.put(state, classify(automaton, system, state, facts.get(state)));
        }

        return new VerificationReport<>(facts, diagnoses);
    }

    private static <D> StateDiagnosis classify(FiniteStateAutomaton automaton, FactTrackingSystem<D> system, State state, Set<D> stateFacts) {
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

        boolean isFinal = automaton.isFinalState(state);
        if (hasAccepting && !isFinal) {
            return StateDiagnosis.MISSING_ACCEPTANCE;
        }
        if (hasRejecting && isFinal) {
            return StateDiagnosis.SPURIOUS_ACCEPTANCE;
        }
        return StateDiagnosis.CONSISTENT;
    }
}
