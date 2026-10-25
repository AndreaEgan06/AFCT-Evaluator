package afctevaluator.mosel.analysis;

import automata.State;
import automata.Transition;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;

/**
 * Turns a submitted automaton into the input {@link FactVerifier} analyses. Either way the submitted states
 * are kept one-to-one (including unreachable ones, so they can be diagnosed).
 * <ul>
 *   <li>A submission that is deterministic with single-symbol labels is completed over the alphabet: missing
 *       transitions go to an explicit trap state, so strings that fall off the machine are no longer invisible
 *       to the analysis.</li>
 *   <li>Anything else (nondeterminism, λ-transitions, multi-symbol labels) is analysed as an NFA, as submitted.
 *       No trap state is added: a string that falls off one path may still be accepted on another.</li>
 * </ul>
 * {@link #determinize} instead always produces a complete DFA, by the subset construction when needed. It is
 * for checking a whole language (the answer key against the description), not for diagnosing states.
 * <p>
 * The alphabet must include every symbol the automaton reads; {@link #symbolsOf} gives the smallest one.
 */
public final class AnalysisAutomatonBuilder {
    public static final String TRAP_NAME = "(trap)";

    /** Upper bound on determinized states, so a pathological NFA cannot blow up the evaluation. */
    public static final int MAX_SUBSET_STATES = 2000;

    private AnalysisAutomatonBuilder() {
    }

    /**
     * @param automaton the automaton to analyse
     * @param nondeterministic whether the submission was not a simple DFA (so {@code automaton} is an NFA from
     *                         {@link #build}, or the subset construction from {@link #determinize})
     * @param trapAdded whether a trap state was added for missing transitions
     */
    public record Result(FiniteStateAutomaton automaton, boolean nondeterministic, boolean trapAdded) {
    }

    /** Every symbol read on a transition of {@code fsa}, sorted. */
    public static Set<Character> symbolsOf(FiniteStateAutomaton fsa) {
        Set<Character> symbols = new TreeSet<>();
        for (Transition transition : fsa.getTransitions()) {
            for (char symbol : ((FSATransition) transition).getLabel().toCharArray()) {
                symbols.add(symbol);
            }
        }
        return symbols;
    }

    /** The automaton whose states are diagnosed: the submission completed with a trap state, or the NFA as submitted. */
    public static Result build(FiniteStateAutomaton submitted, Set<Character> alphabet) {
        checkInput(submitted, alphabet);
        if (isSimpleDeterministic(submitted)) {
            return completeCopy(submitted, alphabet);
        }
        return nfaCopy(submitted);
    }

    /** A complete DFA for the submission's language; NFAs go through the subset construction. */
    public static Result determinize(FiniteStateAutomaton submitted, Set<Character> alphabet) {
        checkInput(submitted, alphabet);
        if (isSimpleDeterministic(submitted)) {
            return completeCopy(submitted, alphabet);
        }
        return subsetConstruction(submitted, alphabet);
    }

    private static void checkInput(FiniteStateAutomaton submitted, Set<Character> alphabet) {
        if (submitted.getInitialState() == null) {
            throw new IllegalArgumentException("the submission has no initial state");
        }
        if (!alphabet.containsAll(symbolsOf(submitted))) {
            throw new IllegalArgumentException("the alphabet " + alphabet + " leaves out symbols the automaton reads");
        }
    }

    private static boolean isSimpleDeterministic(FiniteStateAutomaton fsa) {
        for (State state : fsa.getStates()) {
            Set<Character> seen = new TreeSet<>();
            for (Transition transition : fsa.getTransitionsFromState(state)) {
                String label = ((FSATransition) transition).getLabel();
                if (label.length() != 1) {
                    return false;
                }
                char symbol = label.charAt(0);
                if (!seen.add(symbol)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Copies the submission's states into {@code target}, keeping names, positions, and initial/final marks. */
    private static Map<State, State> copyStates(FiniteStateAutomaton submitted, FiniteStateAutomaton target) {
        Map<State, State> copies = new LinkedHashMap<>();
        for (State original : submitted.getStates()) {
            State copy = target.createState(new Point(original.getPoint()));
            copy.setName(original.getName());
            copies.put(original, copy);
            if (submitted.isFinalState(original)) {
                target.addFinalState(copy);
            }
        }
        target.setInitialState(copies.get(submitted.getInitialState()));
        return copies;
    }

    private static Result nfaCopy(FiniteStateAutomaton submitted) {
        FiniteStateAutomaton nfa = new FiniteStateAutomaton();
        Map<State, State> copies = copyStates(submitted, nfa);
        for (Transition transition : submitted.getTransitions()) {
            FSATransition fsaTransition = (FSATransition) transition;
            nfa.addTransition(new FSATransition(copies.get(fsaTransition.getFromState()), copies.get(fsaTransition.getToState()), fsaTransition.getLabel()));
        }
        return new Result(nfa, true, false);
    }

    private static Result completeCopy(FiniteStateAutomaton submitted, Set<Character> alphabet) {
        FiniteStateAutomaton dfa = new FiniteStateAutomaton();
        Map<State, State> copies = copyStates(submitted, dfa);

        State trap = null;
        for (State original : submitted.getStates()) {
            Set<Character> covered = new TreeSet<>();
            for (Transition transition : submitted.getTransitionsFromState(original)) {
                FSATransition fsaTransition = (FSATransition) transition;
                char symbol = fsaTransition.getLabel().charAt(0);
                covered.add(symbol);
                dfa.addTransition(new FSATransition(copies.get(original), copies.get(fsaTransition.getToState()), String.valueOf(symbol)));
            }
            for (char symbol : alphabet) {
                if (!covered.contains(symbol)) {
                    if (trap == null) {
                        trap = newTrap(dfa, alphabet);
                    }
                    dfa.addTransition(new FSATransition(copies.get(original), trap, String.valueOf(symbol)));
                }
            }
        }
        return new Result(dfa, false, trap != null);
    }

    private static State newTrap(FiniteStateAutomaton dfa, Set<Character> alphabet) {
        State trap = dfa.createState(new Point(0, 0));
        trap.setName(TRAP_NAME);
        for (char symbol : alphabet) {
            dfa.addTransition(new FSATransition(trap, trap, String.valueOf(symbol)));
        }
        return trap;
    }

    /** One NFA edge: a single symbol, or λ when {@code symbol} is null. */
    private record Edge(Character symbol, int to) {
    }

    private static Result subsetConstruction(FiniteStateAutomaton submitted, Set<Character> alphabet) {
        // Index the submitted states, then add intermediate nodes so every edge reads at most one symbol.
        State[] states = submitted.getStates();
        Map<State, Integer> index = new HashMap<>();
        List<String> names = new ArrayList<>();
        List<List<Edge>> edges = new ArrayList<>();
        BitSet finals = new BitSet();
        for (State state : states) {
            index.put(state, names.size());
            names.add(state.getName());
            edges.add(new ArrayList<>());
            if (submitted.isFinalState(state)) {
                finals.set(index.get(state));
            }
        }
        int originalCount = names.size();
        for (Transition transition : submitted.getTransitions()) {
            FSATransition fsaTransition = (FSATransition) transition;
            String label = fsaTransition.getLabel();
            int from = index.get(fsaTransition.getFromState());
            int to = index.get(fsaTransition.getToState());
            if (label.isEmpty()) {
                edges.get(from).add(new Edge(null, to));
                continue;
            }
            int current = from;
            for (int i = 0; i < label.length(); i++) {
                int next = to;
                if (i < label.length() - 1) {
                    next = names.size();
                    names.add(null);
                    edges.add(new ArrayList<>());
                }
                edges.get(current).add(new Edge(label.charAt(i), next));
                current = next;
            }
        }

        FiniteStateAutomaton dfa = new FiniteStateAutomaton();
        Map<BitSet, State> subsets = new HashMap<>();
        Deque<BitSet> worklist = new ArrayDeque<>();

        BitSet initial = new BitSet();
        initial.set(index.get(submitted.getInitialState()));
        initial = closure(initial, edges);
        State initialState = newSubsetState(dfa, initial, names, originalCount, finals, subsets.size());
        subsets.put(initial, initialState);
        dfa.setInitialState(initialState);
        worklist.add(initial);

        boolean trapAdded = false;
        while (!worklist.isEmpty()) {
            BitSet subset = worklist.poll();
            State from = subsets.get(subset);
            for (char symbol : alphabet) {
                BitSet moved = new BitSet();
                for (int node = subset.nextSetBit(0); node >= 0; node = subset.nextSetBit(node + 1)) {
                    for (Edge edge : edges.get(node)) {
                        if (edge.symbol() != null && edge.symbol() == symbol) {
                            moved.set(edge.to());
                        }
                    }
                }
                BitSet target = closure(moved, edges);
                State to = subsets.get(target);
                if (to == null) {
                    if (subsets.size() >= MAX_SUBSET_STATES) {
                        throw new IllegalStateException("determinizing the automaton needs more than " + MAX_SUBSET_STATES + " states");
                    }
                    to = newSubsetState(dfa, target, names, originalCount, finals, subsets.size());
                    subsets.put(target, to);
                    worklist.add(target);
                    trapAdded |= target.isEmpty();
                }
                dfa.addTransition(new FSATransition(from, to, String.valueOf(symbol)));
            }
        }
        return new Result(dfa, true, trapAdded);
    }

    private static BitSet closure(BitSet nodes, List<List<Edge>> edges) {
        BitSet result = (BitSet) nodes.clone();
        Deque<Integer> stack = new ArrayDeque<>();
        nodes.stream().forEach(stack::push);
        while (!stack.isEmpty()) {
            int node = stack.pop();
            for (Edge edge : edges.get(node)) {
                if (edge.symbol() == null && !result.get(edge.to())) {
                    result.set(edge.to());
                    stack.push(edge.to());
                }
            }
        }
        return result;
    }

    private static State newSubsetState(FiniteStateAutomaton dfa, BitSet subset, List<String> names, int originalCount, BitSet finals, int index) {
        State state = dfa.createState(new Point((index % 5) * 100, (index / 5) * 100));
        if (subset.isEmpty()) {
            state.setName(TRAP_NAME);
        } else {
            // Intermediate nodes (from multi-symbol labels) have no name of their own; show them by position.
            StringJoiner joiner = new StringJoiner(",", "{", "}");
            for (int node = subset.nextSetBit(0); node >= 0; node = subset.nextSetBit(node + 1)) {
                joiner.add(node < originalCount ? names.get(node) : "~" + (node - originalCount));
            }
            state.setName(joiner.toString());
        }
        if (subset.intersects(finals)) {
            dfa.addFinalState(state);
        }
        return state;
    }
}
