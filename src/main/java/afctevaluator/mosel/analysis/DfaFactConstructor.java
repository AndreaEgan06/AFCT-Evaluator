package afctevaluator.mosel.analysis;

import automata.State;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import afctevaluator.mosel.abstract_fact_system.FactTrackingSystem;

/** Builds the canonical correct DFA for a fact-tracking system's language over a given alphabet: one state per reachable fact, transitions by the transfer function, accepting states by the fact system's accept/reject classification. Dual to {@link DfaFactVerifier}, which goes the other direction (DFA -> facts -> diagnosis). */
public final class DfaFactConstructor {
    private DfaFactConstructor() {
    }

    public static <D> FiniteStateAutomaton build(FactTrackingSystem<D> system, Set<Character> alphabet) {
        FiniteStateAutomaton dfa = new FiniteStateAutomaton();
        Map<D, State> states = new HashMap<>();

        D initialFact = system.initialFact();
        State initialState = newState(dfa, states.size());
        dfa.setInitialState(initialState);
        states.put(initialFact, initialState);

        Deque<D> worklist = new ArrayDeque<>();
        worklist.add(initialFact);

        while (!worklist.isEmpty()) {
            D fact = worklist.poll();
            State state = states.get(fact);
            for (char symbol : alphabet) {
                D nextFact = system.transfer(fact, symbol);
                State nextState = states.get(nextFact);
                if (nextState == null) {
                    nextState = newState(dfa, states.size());
                    states.put(nextFact, nextState);
                    worklist.add(nextFact);
                }
                dfa.addTransition(new FSATransition(state, nextState, String.valueOf(symbol)));
            }
        }

        for (Map.Entry<D, State> entry : states.entrySet()) {
            if (system.isAccepting(entry.getKey())) {
                dfa.addFinalState(entry.getValue());
            }
        }

        return dfa;
    }

    private static State newState(FiniteStateAutomaton dfa, int index) {
        State state = dfa.createState(new Point((index % 5) * 100, (index / 5) * 100));
        state.setName("q" + index);
        return state;
    }
}
