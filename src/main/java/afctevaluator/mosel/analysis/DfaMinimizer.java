package afctevaluator.mosel.analysis;

import automata.State;
import automata.Transition;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Counts the states of the minimal DFA equivalent to a complete DFA (Moore's partition refinement). */
public final class DfaMinimizer {
    private DfaMinimizer() {
    }

    public static int minimalStateCount(FiniteStateAutomaton dfa, Set<Character> alphabet) {
        List<State> reachable = reachableStates(dfa);
        Map<State, Map<Character, State>> delta = new HashMap<>();
        for (State state : reachable) {
            Map<Character, State> out = new HashMap<>();
            for (Transition transition : dfa.getTransitionsFromState(state)) {
                FSATransition fsaTransition = (FSATransition) transition;
                out.put(fsaTransition.getLabel().charAt(0), fsaTransition.getToState());
            }
            delta.put(state, out);
        }

        Map<State, Integer> block = new HashMap<>();
        for (State state : reachable) {
            block.put(state, dfa.isFinalState(state) ? 1 : 0);
        }
        int blockCount = new HashSet<>(block.values()).size();

        while (true) {
            Map<List<Integer>, Integer> signatures = new HashMap<>();
            Map<State, Integer> refined = new HashMap<>();
            for (State state : reachable) {
                List<Integer> signature = new ArrayList<>();
                signature.add(block.get(state));
                for (char symbol : alphabet) {
                    State next = delta.get(state).get(symbol);
                    signature.add(next == null ? -1 : block.get(next));
                }
                refined.put(state, signatures.computeIfAbsent(signature, key -> signatures.size()));
            }
            if (signatures.size() == blockCount) {
                return blockCount;
            }
            block = refined;
            blockCount = signatures.size();
        }
    }

    private static List<State> reachableStates(FiniteStateAutomaton dfa) {
        List<State> order = new ArrayList<>();
        Set<State> seen = new HashSet<>();
        Deque<State> worklist = new ArrayDeque<>();
        worklist.add(dfa.getInitialState());
        seen.add(dfa.getInitialState());
        while (!worklist.isEmpty()) {
            State state = worklist.poll();
            order.add(state);
            for (Transition transition : dfa.getTransitionsFromState(state)) {
                State next = transition.getToState();
                if (seen.add(next)) {
                    worklist.add(next);
                }
            }
        }
        return order;
    }
}
