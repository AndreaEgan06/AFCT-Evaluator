package afctevaluator.mosel.analysis;

import automata.State;
import automata.Transition;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import afctevaluator.mosel.core.FactTrackingSystem;
import afctevaluator.mosel.analysis.NfaVerificationReport.MissingAcceptance;
import afctevaluator.mosel.analysis.NfaVerificationReport.PossibleStates;
import afctevaluator.mosel.analysis.NfaVerificationReport.SpuriousAcceptance;

/**
 * The fact analysis for a nondeterministic finite automaton, as in the NFA section of the write-up.
 * <ol>
 *   <li>A trap state is added, so every state has at least one transition on every symbol of the alphabet. Every string
 *       then reaches some state, and its fact is learned somewhere.</li>
 *   <li>Facts are learned one string length at a time, keeping track of the states some string of the current length
 *       can reach. Iteration k reads one more symbol along the non-λ transitions out of the states possible at length
 *       k - 1, then carries what was learned along λ-transitions (a λ-transition's destination must end up with every
 *       fact of its origin), and only then adds the newly learned facts to the states. It stops after an iteration that
 *       learns nothing. So the iteration that learns a fact at a state is the length of the shortest string reaching the
 *       state with that fact.</li>
 *   <li>An accepting fact learned in iteration k, when no state possible at length k is accepting, is recorded as a
 *       missing acceptance: every string of length k is rejected, including one with that fact.</li>
 *   <li>A rejecting fact learned by an accepting state is recorded as a spurious acceptance.</li>
 * </ol>
 * Neither kind of error stops the analysis; all of them are in the report. Each comes with a witness string, rebuilt from
 * how each fact was first learned.
 * <p>
 * The automaton is not changed. A multi-symbol label is read one symbol at a time, through intermediate states of its
 * own; those, and the trap, are not submitted states and are not diagnosed.
 */
public final class NfaFactVerifier {
    private NfaFactVerifier() {
    }

    /** One edge of the working graph: a single symbol, or λ when {@code symbol} is null. */
    private record Edge(Character symbol, int to) {
    }

    /** How a node first learned a fact: from {@code fact} at node {@code from}, reading {@code symbol} (null for λ). */
    private record Step<D>(int from, D fact, Character symbol) {
    }

    /** The automaton as numbered nodes: the submitted states first, then intermediate nodes, then the trap (if any). */
    private static final class Graph {
        final List<List<Edge>> edges = new ArrayList<>();
        final BitSet finals = new BitSet();
        final int submittedCount;
        int start;
        int trap = -1;

        Graph(int submittedCount) {
            this.submittedCount = submittedCount;
        }

        int addNode() {
            edges.add(new ArrayList<>());
            return edges.size() - 1;
        }

        int size() {
            return edges.size();
        }
    }

    public static <D> NfaVerificationReport<D> verify(FiniteStateAutomaton automaton, FactTrackingSystem<D> system,
                                                      Set<Character> alphabet) {
        // Everything is read in a fixed order (states as JFLAP lists them, each state's transitions in the order they
        // were added, symbols sorted), so the same automaton always gets the same witnesses.
        State[] states = automaton.getStates();
        Set<Character> symbols = new TreeSet<>(alphabet);
        Graph graph = buildGraph(automaton, states, symbols);

        // Step 1: the trap state.
        addTrap(graph, symbols);

        // Step 2: learn facts one length at a time. known(q) maps each fact to the iteration that learned it, and
        // how(q) maps it to the step that did, for rebuilding witnesses.
        List<Map<D, Integer>> known = new ArrayList<>();
        List<Map<D, Step<D>>> how = new ArrayList<>();
        for (int node = 0; node < graph.size(); node++) {
            known.add(new LinkedHashMap<>());
            how.add(new HashMap<>());
        }

        List<MissingAcceptance<D>> missing = new ArrayList<>();
        List<SpuriousAcceptance<D>> spurious = new ArrayList<>();

        List<PossibleStates> possibleStates = new ArrayList<>();

        List<Map<D, Step<D>>> learned = emptyLearned(graph.size());
        // The empty string reaches the start state with the initial fact; it has no step that produced it.
        learned.get(graph.start).put(system.initialFact(), null);
        BitSet start = new BitSet();
        start.set(graph.start);
        // The states some string of length k can reach.
        BitSet possible = lambdaClosure(graph, start);
        int iterations = 0;
        for (int k = 0; ; k++) {
            if (k > 0) {
                readSymbols(graph, system, known, learned, possible);
                possible = lambdaClosure(graph, move(graph, possible));
            }
            followLambdas(graph, known, learned, possible);
            if (learned.stream().allMatch(Map::isEmpty)) {
                break;
            }
            iterations = k;
            possibleStates.add(possibleStates(graph, states, possible));
            for (int node = 0; node < graph.size(); node++) {
                for (Map.Entry<D, Step<D>> entry : learned.get(node).entrySet()) {
                    known.get(node).put(entry.getKey(), k);
                    how.get(node).put(entry.getKey(), entry.getValue());
                }
            }
            // Steps 3 and 4, recorded now and reported at the end.
            recordErrors(graph, states, system, how, learned, possible, k, missing, spurious);
            learned = emptyLearned(graph.size());
        }

        spurious.sort(Comparator.comparingInt((SpuriousAcceptance<D> error) -> indexOf(states, error.state()))
                .thenComparingInt(SpuriousAcceptance::length));

        Map<State, Map<D, Integer>> facts = new LinkedHashMap<>();
        Map<State, StateDiagnosis> diagnoses = new LinkedHashMap<>();
        for (int node = 0; node < states.length; node++) {
            Map<D, Integer> stateFacts = Collections.unmodifiableMap(known.get(node));
            facts.put(states[node], stateFacts);
            diagnoses.put(states[node], diagnose(system, stateFacts.keySet(), graph.finals.get(node)));
        }
        Map<D, Integer> trapFacts = graph.trap < 0 ? Map.of() : Collections.unmodifiableMap(known.get(graph.trap));

        return new NfaVerificationReport<>(facts, trapFacts, graph.trap >= 0, iterations, List.copyOf(possibleStates),
                diagnoses, List.copyOf(missing), List.copyOf(spurious));
    }

    private static Graph buildGraph(FiniteStateAutomaton automaton, State[] states, Set<Character> alphabet) {
        if (automaton.getInitialState() == null) {
            throw new IllegalArgumentException("the automaton has no initial state");
        }
        Graph graph = new Graph(states.length);
        Map<State, Integer> index = new HashMap<>();
        for (State state : states) {
            int node = graph.addNode();
            index.put(state, node);
            if (automaton.isFinalState(state)) {
                graph.finals.set(node);
            }
        }
        graph.start = index.get(automaton.getInitialState());

        for (State state : states) {
            for (Transition transition : automaton.getTransitionsFromState(state)) {
                addEdges(graph, index, (FSATransition) transition, alphabet);
            }
        }
        return graph;
    }

    private static void addEdges(Graph graph, Map<State, Integer> index, FSATransition fsaTransition,
                                 Set<Character> alphabet) {
        String label = fsaTransition.getLabel();
        int from = index.get(fsaTransition.getFromState());
        int to = index.get(fsaTransition.getToState());
        if (label.isEmpty()) {
            graph.edges.get(from).add(new Edge(null, to));
            return;
        }
        // A label of several symbols becomes a chain through intermediate nodes, one symbol per edge.
        int current = from;
        for (int i = 0; i < label.length(); i++) {
            char symbol = label.charAt(i);
            if (!alphabet.contains(symbol)) {
                throw new IllegalArgumentException("the alphabet " + alphabet + " leaves out '" + symbol
                        + "', which the automaton reads");
            }
            int next = i < label.length() - 1 ? graph.addNode() : to;
            graph.edges.get(current).add(new Edge(symbol, next));
            current = next;
        }
    }

    /** Sends every missing (node, symbol) transition to a new non-accepting trap that loops on every symbol. */
    private static void addTrap(Graph graph, Set<Character> alphabet) {
        int nodeCount = graph.size();
        for (int node = 0; node < nodeCount; node++) {
            for (char symbol : alphabet) {
                boolean covered = graph.edges.get(node).stream()
                        .anyMatch(edge -> edge.symbol() != null && edge.symbol() == symbol);
                if (covered) {
                    continue;
                }
                if (graph.trap < 0) {
                    graph.trap = graph.addNode();
                    for (char loop : alphabet) {
                        graph.edges.get(graph.trap).add(new Edge(loop, graph.trap));
                    }
                }
                graph.edges.get(node).add(new Edge(symbol, graph.trap));
            }
        }
    }

    private static <D> List<Map<D, Step<D>>> emptyLearned(int size) {
        List<Map<D, Step<D>>> learned = new ArrayList<>();
        for (int node = 0; node < size; node++) {
            learned.add(new LinkedHashMap<>());
        }
        return learned;
    }

    /** One more symbol along every non-λ transition out of {@code possible}, from every fact known at its origin. */
    private static <D> void readSymbols(Graph graph, FactTrackingSystem<D> system, List<Map<D, Integer>> known,
                                        List<Map<D, Step<D>>> learned, BitSet possible) {
        for (int from = possible.nextSetBit(0); from >= 0; from = possible.nextSetBit(from + 1)) {
            for (Edge edge : graph.edges.get(from)) {
                if (edge.symbol() == null) {
                    continue;
                }
                for (D fact : known.get(from).keySet()) {
                    D next = system.transfer(fact, edge.symbol());
                    learn(known, learned, edge.to(), next, new Step<>(from, fact, edge.symbol()));
                }
            }
        }
    }

    /**
     * Along λ-transitions out of {@code possible}, until nothing changes: the destination learns every fact its origin
     * knows or has just learned. {@code possible} is closed under λ-transitions, so the destinations are in it too.
     */
    private static <D> void followLambdas(Graph graph, List<Map<D, Integer>> known, List<Map<D, Step<D>>> learned,
                                          BitSet possible) {
        Deque<Integer> worklist = new ArrayDeque<>();
        possible.stream().forEach(worklist::add);
        while (!worklist.isEmpty()) {
            int from = worklist.poll();
            List<D> facts = new ArrayList<>(known.get(from).keySet());
            facts.addAll(learned.get(from).keySet());
            for (Edge edge : graph.edges.get(from)) {
                if (edge.symbol() != null) {
                    continue;
                }
                boolean changed = false;
                for (D fact : facts) {
                    changed |= learn(known, learned, edge.to(), fact, new Step<>(from, fact, null));
                }
                if (changed) {
                    worklist.add(edge.to());
                }
            }
        }
    }

    /** Records that {@code node} learned {@code fact} this iteration, unless it knew it already. */
    private static <D> boolean learn(List<Map<D, Integer>> known, List<Map<D, Step<D>>> learned, int node, D fact,
                                     Step<D> step) {
        if (known.get(node).containsKey(fact) || learned.get(node).containsKey(fact)) {
            return false;
        }
        learned.get(node).put(fact, step);
        return true;
    }

    private static <D> void recordErrors(Graph graph, State[] states, FactTrackingSystem<D> system,
                                         List<Map<D, Step<D>>> how, List<Map<D, Step<D>>> learned, BitSet possible,
                                         int k, List<MissingAcceptance<D>> missing,
                                         List<SpuriousAcceptance<D>> spurious) {
        // Step 3: only when no string of length k is accepted. The nodes that learned a fact are all possible, so then
        // none of them is accepting either.
        if (!possible.intersects(graph.finals)) {
            // Group this iteration's accepting facts by fact, with the nodes that learned each.
            Map<D, List<Integer>> learners = new LinkedHashMap<>();
            for (int node = 0; node < graph.size(); node++) {
                for (D fact : learned.get(node).keySet()) {
                    if (system.isAccepting(fact)) {
                        learners.computeIfAbsent(fact, f -> new ArrayList<>()).add(node);
                    }
                }
            }
            for (Map.Entry<D, List<Integer>> entry : learners.entrySet()) {
                List<Integer> nodes = entry.getValue();
                List<State> submitted = nodes.stream().filter(node -> node < graph.submittedCount)
                        .map(node -> states[node]).toList();
                missing.add(new MissingAcceptance<>(entry.getKey(), k, submitted, nodes.contains(graph.trap),
                        witness(how, nodes.get(0), entry.getKey())));
            }
        }

        // Step 4: accepting states that learned a rejecting fact.
        for (int node = graph.finals.nextSetBit(0); node >= 0; node = graph.finals.nextSetBit(node + 1)) {
            for (D fact : learned.get(node).keySet()) {
                if (!system.isAccepting(fact)) {
                    spurious.add(new SpuriousAcceptance<>(states[node], fact, k, witness(how, node, fact)));
                }
            }
        }
    }

    /** The string that first brought {@code fact} to {@code node}, read back along the recorded steps. */
    private static <D> String witness(List<Map<D, Step<D>>> how, int node, D fact) {
        StringBuilder reversed = new StringBuilder();
        Step<D> step = how.get(node).get(fact);
        while (step != null) {
            if (step.symbol() != null) {
                reversed.append(step.symbol());
            }
            step = how.get(step.from()).get(step.fact());
        }
        return reversed.reverse().toString();
    }

    /** The nodes one symbol, any symbol, away from {@code nodes}. */
    private static BitSet move(Graph graph, BitSet nodes) {
        BitSet moved = new BitSet();
        for (int node = nodes.nextSetBit(0); node >= 0; node = nodes.nextSetBit(node + 1)) {
            for (Edge edge : graph.edges.get(node)) {
                if (edge.symbol() != null) {
                    moved.set(edge.to());
                }
            }
        }
        return moved;
    }

    private static BitSet lambdaClosure(Graph graph, BitSet nodes) {
        BitSet result = (BitSet) nodes.clone();
        Deque<Integer> stack = new ArrayDeque<>();
        nodes.stream().forEach(stack::push);
        while (!stack.isEmpty()) {
            int node = stack.pop();
            for (Edge edge : graph.edges.get(node)) {
                if (edge.symbol() == null && !result.get(edge.to())) {
                    result.set(edge.to());
                    stack.push(edge.to());
                }
            }
        }
        return result;
    }

    /** {@code possible} as the report gives it: submitted states, and whether the trap is among them. */
    private static PossibleStates possibleStates(Graph graph, State[] states, BitSet possible) {
        Set<State> submitted = new LinkedHashSet<>();
        for (int node = possible.nextSetBit(0); node >= 0 && node < graph.submittedCount;
             node = possible.nextSetBit(node + 1)) {
            submitted.add(states[node]);
        }
        return new PossibleStates(Collections.unmodifiableSet(submitted), graph.trap >= 0 && possible.get(graph.trap));
    }

    private static <D> StateDiagnosis diagnose(FactTrackingSystem<D> system, Set<D> facts, boolean accepting) {
        if (facts.isEmpty()) {
            return StateDiagnosis.UNREACHABLE;
        }
        if (accepting && facts.stream().anyMatch(fact -> !system.isAccepting(fact))) {
            return StateDiagnosis.SPURIOUS_ACCEPTANCE;
        }
        return StateDiagnosis.CONSISTENT;
    }

    private static int indexOf(State[] states, State state) {
        for (int i = 0; i < states.length; i++) {
            if (states[i] == state) {
                return i;
            }
        }
        return -1;
    }
}
