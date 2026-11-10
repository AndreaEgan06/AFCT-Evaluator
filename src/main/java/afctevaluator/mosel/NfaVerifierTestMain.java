package afctevaluator.mosel;

import afctevaluator.mosel.analysis.NfaFactVerifier;
import afctevaluator.mosel.analysis.NfaVerificationReport;
import afctevaluator.mosel.analysis.NfaVerificationReport.MissingAcceptance;
import afctevaluator.mosel.analysis.NfaVerificationReport.PossibleStates;
import afctevaluator.mosel.analysis.NfaVerificationReport.SpuriousAcceptance;
import afctevaluator.mosel.core.Comparison;
import afctevaluator.mosel.core.FactTrackingSystem;
import afctevaluator.mosel.logic.ConstantFactSystem;
import afctevaluator.mosel.predicates.counts.CountCompareFactSystem;
import afctevaluator.mosel.predicates.counts.LengthModFactSystem;
import afctevaluator.mosel.predicates.counts.ModularCountFactSystem;
import afctevaluator.mosel.predicates.words.PrefixFactSystem;
import afctevaluator.mosel.predicates.words.SuffixFactSystem;
import automata.State;
import automata.Transition;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Standalone test for {@link NfaFactVerifier}, in the style of {@link MoselVerifierTestMain}: hand-built automata with
 * known mistakes, each with the errors the algorithm should report. Exits with status 1 if any case fails.
 * <p>
 * Besides the expected errors, every case is checked against brute force over all strings up to {@link #BOUND} symbols,
 * using a simulator written independently of the verifier:
 * <ul>
 *   <li>each state's facts, and the iteration that learned each, are exactly the facts of the strings reaching it and
 *       the length of the shortest such string;</li>
 *   <li>the states possible at each length are exactly those some string of that length reaches;</li>
 *   <li>every witness string has the reported length and fact, and is really accepted (step 4) or rejected
 *       (step 3);</li>
 *   <li>step 4 reports something exactly when the automaton accepts a string outside the language;</li>
 *   <li>whether the automaton is correct at all, to show where the reports and the truth part ways.</li>
 * </ul>
 * The same checks then run over small random NFAs.
 */
public final class NfaVerifierTestMain {
    private static final int BOUND = 8;
    private static final Set<Character> AB = Set.of('a', 'b');
    private static final Set<Character> A = Set.of('a');

    private static boolean allPassed = true;

    public static void main(String[] args) {
        runCase("endWt 'ab': the textbook NFA that guesses where the final 'ab' starts is correct;"
                        + " the trap learns the accepting fact at length 3, but q2 is possible then",
                suffixGuessingNfa(false), new SuffixFactSystem("ab"), AB,
                true, List.of(), List.of());

        runCase("endWt 'a': the smallest guessing NFA; 'aa' falls off q1 into the trap but is accepted through q0",
                endsWithANfa(), new SuffixFactSystem("a"), AB,
                true, List.of(), List.of());

        runCase("endWt 'ab': marking the guessing state q1 accepting accepts strings ending in 'a'",
                suffixGuessingNfa(true), new SuffixFactSystem("ab"), AB,
                false, List.of("q1@1"), List.of());

        runCase("begWt 'ab': a multi-symbol label and a λ-transition, correct",
                prefixLambdaNfa(false), new PrefixFactSystem("ab"), AB,
                true, List.of(), List.of());

        runCase("begWt 'ab': a λ-transition that skips the prefix lets q2 accept '', 'a' and 'b'",
                prefixLambdaNfa(true), new PrefixFactSystem("ab"), AB,
                false, List.of("q2@0", "q2@1", "q2@1"), List.of());

        runCase("begWt 'ab': no b-transition out of the accepting state, so 'abb' falls into the trap; missed, because"
                        + " 'aba' reaches the accepting q2 at the same length",
                prefixMissingLoopNfa(), new PrefixFactSystem("ab"), AB,
                false, List.of(), List.of());

        runCase("true over {a}: only q1 is possible at length 1, so 'a' is rejected",
                alternatingNfa(), new ConstantFactSystem(true), A,
                false, List.of(), List.of("1"));

        runCase("true over {a}: the same facts and iterations as the previous case, but correct; q0 is possible at"
                        + " length 1, so nothing is reported",
                loopingNfa(), new ConstantFactSystem(true), A,
                true, List.of(), List.of());

        runCase("true over {a}: rejects 'aa' and longer, yet nothing is reported (a blind spot of steps 3 and 4)",
                blindSpotNfa(), new ConstantFactSystem(true), A,
                false, List.of(), List.of());

        runCase("DFA, begWt 'ab': correct and complete, so nothing to report",
                correctPrefixDfa(false), new PrefixFactSystem("ab"), AB,
                true, List.of(), List.of());

        runCase("DFA, begWt 'ab': the fully-matched state is not accepting",
                correctPrefixDfa(true), new PrefixFactSystem("ab"), AB,
                false, List.of(), List.of("2"));

        runCase("DFA, |indOf 'a'| = 1: the accepting state also holds 'more than one a'",
                countExactlyConflatedDfa(), new CountCompareFactSystem("a", Comparison.EQ, 1), AB,
                false, List.of("q1@2"), List.of());

        // A seed may be given to try other random automata.
        runRandomCases(300, args.length > 0 ? Long.parseLong(args[0]) : 20261031L);

        System.out.println(allPassed ? "ALL PASSED" : "SOME CASES FAILED");
        if (!allPassed) {
            System.exit(1);
        }
    }

    /**
     * @param expectCorrect  whether the automaton accepts exactly the language (checked by brute force)
     * @param expectSpurious step 4 reports as {@code state@length}, in the report's order
     * @param expectMissing  step 3 reports as their lengths, in order
     */
    private static <D> void runCase(String name, FiniteStateAutomaton fa, FactTrackingSystem<D> system,
                                    Set<Character> alphabet, boolean expectCorrect, List<String> expectSpurious,
                                    List<String> expectMissing) {
        System.out.println(name);
        NfaVerificationReport<D> report = NfaFactVerifier.verify(fa, system, alphabet);
        List<String> problems = new ArrayList<>();

        for (State state : fa.getStates()) {
            System.out.printf("  %-5s %-6s facts=%-34s %s%n", state.getName(), fa.isFinalState(state) ? "final" : "",
                    report.facts().get(state), report.diagnosisOf(state));
        }
        if (report.trapAdded()) {
            System.out.printf("  %-12s facts=%s%n", "(trap)", report.trapFacts());
        }
        System.out.println("  iterations: " + report.iterations());
        for (int k = 0; k < report.possibleStates().size(); k++) {
            PossibleStates possible = report.possibleStates().get(k);
            System.out.printf("  possible at length %d: %s%s%n", k,
                    possible.states().stream().map(State::getName).toList(), possible.trap() ? " and the trap" : "");
        }
        for (SpuriousAcceptance<D> error : report.spuriousAcceptances()) {
            System.out.printf("  spurious acceptance: %s accepts fact %s, e.g. \"%s\" (length %d)%n",
                    error.state().getName(), error.fact(), error.witness(), error.length());
        }
        for (MissingAcceptance<D> error : report.missingAcceptances()) {
            System.out.printf("  missing acceptance: fact %s at length %d, learned by %s%s; \"%s\" is rejected%n",
                    error.fact(), error.length(), error.learners().stream().map(State::getName).toList(),
                    error.trapLearned() ? " and the trap" : "", error.witness());
        }

        List<String> spurious = report.spuriousAcceptances().stream()
                .map(error -> error.state().getName() + "@" + error.length()).toList();
        List<String> missing = report.missingAcceptances().stream()
                .map(error -> String.valueOf(error.length())).toList();
        if (!spurious.equals(expectSpurious)) {
            problems.add("spurious acceptances " + spurious + ", expected " + expectSpurious);
        }
        if (!missing.equals(expectMissing)) {
            problems.add("missing acceptances " + missing + ", expected " + expectMissing);
        }

        crossCheck(fa, system, alphabet, report, expectCorrect, problems);

        for (String problem : problems) {
            System.out.println("  MISMATCH: " + problem);
        }
        System.out.println(problems.isEmpty() ? "  PASS" : "  FAIL");
        System.out.println();
        allPassed &= problems.isEmpty();
    }

    /**
     * Random NFAs over {a, b} with 1 to 4 states, single-symbol and λ-transitions, and a random fact system, all checked
     * against brute force. Prints a tally of what the analysis made of them, plus the details of any that fails.
     */
    private static void runRandomCases(int count, long seed) {
        List<FactTrackingSystem<?>> systems = List.of(new SuffixFactSystem("ab"), new PrefixFactSystem("ab"),
                new CountCompareFactSystem("a", Comparison.EQ, 1), new ModularCountFactSystem("b", 2, Comparison.EQ, 0),
                new LengthModFactSystem(3, Comparison.EQ, 1), new ConstantFactSystem(true));
        Random random = new Random(seed);
        int checked = 0;
        int failed = 0;
        int[] tally = new int[RandomTally.values().length];
        for (int i = 0; i < count; i++) {
            FiniteStateAutomaton nfa = randomNfa(random);
            FactTrackingSystem<?> system = systems.get(random.nextInt(systems.size()));
            List<String> problems = randomCase(nfa, system, tally);
            if (problems == null) {
                continue;
            }
            checked++;
            if (!problems.isEmpty()) {
                failed++;
                System.out.println("  random NFA #" + i + " failed: " + problems);
            }
        }
        System.out.printf("Random NFAs: %d checked against brute force (seed %d), %d failed%n", checked, seed, failed);
        System.out.printf("  %d incorrect: %d with a spurious acceptance, %d more with a missing acceptance,"
                        + " %d with neither (missed by the analysis)%n",
                tally[RandomTally.INCORRECT.ordinal()], tally[RandomTally.SPURIOUS.ordinal()],
                tally[RandomTally.MISSING_ONLY.ordinal()], tally[RandomTally.MISSED.ordinal()]);
        System.out.printf("  %d correct, none of which may get an error%n%n", tally[RandomTally.CORRECT.ordinal()]);
        allPassed &= failed == 0 && checked > count / 2;
    }

    /** What the random automata were, and what the analysis made of them. */
    private enum RandomTally { INCORRECT, SPURIOUS, MISSING_ONLY, MISSED, CORRECT }

    /** The problems found with one random automaton, or null when it needs too many iterations to brute-force. */
    private static <D> List<String> randomCase(FiniteStateAutomaton nfa, FactTrackingSystem<D> system, int[] tally) {
        NfaVerificationReport<D> report = NfaFactVerifier.verify(nfa, system, AB);
        if (report.iterations() >= BOUND) {
            return null;
        }
        boolean correct = stringsUpTo(AB, BOUND).stream().allMatch(input ->
                reach(nfa, input).stream().anyMatch(nfa::isFinalState) == system.isAccepting(factOf(system, input)));
        if (correct) {
            tally[RandomTally.CORRECT.ordinal()]++;
        } else {
            tally[RandomTally.INCORRECT.ordinal()]++;
            RandomTally found = !report.spuriousAcceptances().isEmpty() ? RandomTally.SPURIOUS
                    : !report.missingAcceptances().isEmpty() ? RandomTally.MISSING_ONLY : RandomTally.MISSED;
            tally[found.ordinal()]++;
        }
        List<String> problems = new ArrayList<>();
        crossCheck(nfa, system, AB, report, null, problems);
        return problems;
    }

    private static FiniteStateAutomaton randomNfa(Random random) {
        FiniteStateAutomaton nfa = new FiniteStateAutomaton();
        int size = 1 + random.nextInt(4);
        State[] states = new State[size];
        // Half the automata make most states accepting, so some are correct (mostly for `true`).
        boolean mostlyFinal = random.nextBoolean();
        for (int i = 0; i < size; i++) {
            states[i] = newState(nfa, "q" + i, 100 * i, 0);
            if (mostlyFinal ? random.nextInt(4) != 0 : random.nextInt(3) == 0) {
                nfa.addFinalState(states[i]);
            }
        }
        nfa.setInitialState(states[0]);
        String[] labels = {"a", "b", ""};
        for (State from : states) {
            for (State to : states) {
                for (String label : labels) {
                    // λ-transitions are rarer, so most automata still read symbols.
                    if (random.nextInt(label.isEmpty() ? 8 : 3) == 0) {
                        add(nfa, from, to, label);
                    }
                }
            }
        }
        return nfa;
    }

    /** @param expectCorrect whether the automaton should accept exactly the language, or null not to check */
    private static <D> void crossCheck(FiniteStateAutomaton fa, FactTrackingSystem<D> system, Set<Character> alphabet,
                                       NfaVerificationReport<D> report, Boolean expectCorrect, List<String> problems) {
        if (report.iterations() >= BOUND) {
            problems.add("the analysis ran " + report.iterations() + " iterations, past the brute-force bound " + BOUND);
            return;
        }

        // Each state's facts, with the length of the shortest string reaching it with that fact, and the states each
        // length reaches.
        Map<State, Map<D, Integer>> expectedFacts = new HashMap<>();
        for (State state : fa.getStates()) {
            expectedFacts.put(state, new HashMap<>());
        }
        List<Set<State>> expectedPossible = new ArrayList<>();
        for (int k = 0; k <= BOUND; k++) {
            expectedPossible.add(new HashSet<>());
        }
        boolean acceptsOutside = false;
        boolean correct = true;
        for (String input : stringsUpTo(alphabet, BOUND)) {
            D fact = factOf(system, input);
            Set<State> reached = reach(fa, input);
            for (State state : reached) {
                expectedFacts.get(state).putIfAbsent(fact, input.length());
            }
            expectedPossible.get(input.length()).addAll(reached);
            boolean accepted = reached.stream().anyMatch(fa::isFinalState);
            acceptsOutside |= accepted && !system.isAccepting(fact);
            correct &= accepted == system.isAccepting(fact);
        }
        for (State state : fa.getStates()) {
            if (!expectedFacts.get(state).equals(report.facts().get(state))) {
                problems.add(state.getName() + " has facts " + report.facts().get(state) + ", brute force says "
                        + expectedFacts.get(state));
            }
        }
        if (report.possibleStates().size() != report.iterations() + 1) {
            problems.add("possible states are given for " + report.possibleStates().size() + " lengths, not "
                    + (report.iterations() + 1));
        }
        for (int k = 0; k < report.possibleStates().size(); k++) {
            if (!report.possibleStates().get(k).states().equals(expectedPossible.get(k))) {
                problems.add("possible at length " + k + ": " + report.possibleStates().get(k).states()
                        + ", brute force says " + expectedPossible.get(k));
            }
        }

        for (SpuriousAcceptance<D> error : report.spuriousAcceptances()) {
            String w = error.witness();
            if (w.length() != error.length() || !factOf(system, w).equals(error.fact())
                    || system.isAccepting(error.fact()) || !fa.isFinalState(error.state())
                    || !reach(fa, w).contains(error.state())) {
                problems.add("the spurious acceptance witness \"" + w + "\" at " + error.state().getName()
                        + " does not show a wrongly accepted string");
            }
        }
        for (MissingAcceptance<D> error : report.missingAcceptances()) {
            if (!system.isAccepting(error.fact()) || error.learners().stream().anyMatch(fa::isFinalState)
                    || report.possibleStates().get(error.length()).states().stream().anyMatch(fa::isFinalState)) {
                problems.add("a missing acceptance of fact " + error.fact() + " breaks the step 3 condition");
            }
            String w = error.witness();
            if (w.length() != error.length() || !factOf(system, w).equals(error.fact())
                    || reach(fa, w).stream().anyMatch(fa::isFinalState)) {
                problems.add("the missing acceptance witness \"" + w + "\" is not a wrongly rejected string");
            }
        }

        if (acceptsOutside == report.spuriousAcceptances().isEmpty()) {
            problems.add("step 4 should report something exactly when a string outside the language is accepted ("
                    + (acceptsOutside ? "one is" : "none is") + ")");
        }
        if (expectCorrect != null && correct != expectCorrect) {
            problems.add("brute force says the automaton is " + (correct ? "" : "not ") + "correct");
        }
    }

    private static <D> D factOf(FactTrackingSystem<D> system, String input) {
        D fact = system.initialFact();
        for (char symbol : input.toCharArray()) {
            fact = system.transfer(fact, symbol);
        }
        return fact;
    }

    /** The states the automaton can be in after reading all of {@code input}, following its labels as written. */
    private static Set<State> reach(FiniteStateAutomaton fa, String input) {
        record Config(State state, int read) {
        }
        Set<Config> seen = new HashSet<>();
        Deque<Config> worklist = new ArrayDeque<>();
        Config start = new Config(fa.getInitialState(), 0);
        seen.add(start);
        worklist.add(start);
        Set<State> reached = new LinkedHashSet<>();
        while (!worklist.isEmpty()) {
            Config config = worklist.poll();
            if (config.read() == input.length()) {
                reached.add(config.state());
            }
            for (Transition transition : fa.getTransitionsFromState(config.state())) {
                String label = ((FSATransition) transition).getLabel();
                if (input.startsWith(label, config.read())) {
                    Config next = new Config(transition.getToState(), config.read() + label.length());
                    if (seen.add(next)) {
                        worklist.add(next);
                    }
                }
            }
        }
        return reached;
    }

    /** Every string over {@code alphabet} of at most {@code bound} symbols, shortest first. */
    private static List<String> stringsUpTo(Set<Character> alphabet, int bound) {
        List<String> strings = new ArrayList<>(List.of(""));
        int from = 0;
        for (int length = 1; length <= bound; length++) {
            int to = strings.size();
            for (int i = from; i < to; i++) {
                for (char symbol : alphabet) {
                    strings.add(strings.get(i) + symbol);
                }
            }
            from = to;
        }
        return strings;
    }

    private static State newState(FiniteStateAutomaton fa, String name, int x, int y) {
        State state = fa.createState(new Point(x, y));
        state.setName(name);
        return state;
    }

    private static void add(FiniteStateAutomaton fa, State from, State to, String label) {
        fa.addTransition(new FSATransition(from, to, label));
    }

    /** endWt 'ab': q0 loops on a and b, and guesses the final 'ab' via q1 to q2. {@code q1Final} also accepts at q1. */
    private static FiniteStateAutomaton suffixGuessingNfa(boolean q1Final) {
        FiniteStateAutomaton nfa = new FiniteStateAutomaton();
        State q0 = newState(nfa, "q0", 0, 0);
        State q1 = newState(nfa, "q1", 100, 0);
        State q2 = newState(nfa, "q2", 200, 0);
        nfa.setInitialState(q0);
        nfa.addFinalState(q2);
        if (q1Final) {
            nfa.addFinalState(q1);
        }
        add(nfa, q0, q0, "a");
        add(nfa, q0, q0, "b");
        add(nfa, q0, q1, "a");
        add(nfa, q1, q2, "b");
        return nfa;
    }

    /** endWt 'a': q0 loops on a and b, and guesses the final 'a' into q1, which has no transitions. */
    private static FiniteStateAutomaton endsWithANfa() {
        FiniteStateAutomaton nfa = new FiniteStateAutomaton();
        State q0 = newState(nfa, "q0", 0, 0);
        State q1 = newState(nfa, "q1", 100, 0);
        nfa.setInitialState(q0);
        nfa.addFinalState(q1);
        add(nfa, q0, q0, "a");
        add(nfa, q0, q0, "b");
        add(nfa, q0, q1, "a");
        return nfa;
    }

    /** begWt 'ab' as q0 --ab--> q1 --λ--> q2, with q2 looping on a and b; {@code skip} adds a λ-transition from q0 to q2. */
    private static FiniteStateAutomaton prefixLambdaNfa(boolean skip) {
        FiniteStateAutomaton nfa = new FiniteStateAutomaton();
        State q0 = newState(nfa, "q0", 0, 0);
        State q1 = newState(nfa, "q1", 100, 0);
        State q2 = newState(nfa, "q2", 200, 0);
        nfa.setInitialState(q0);
        nfa.addFinalState(q1);
        nfa.addFinalState(q2);
        add(nfa, q0, q1, "ab");
        add(nfa, q1, q2, "");
        add(nfa, q2, q2, "a");
        add(nfa, q2, q2, "b");
        if (skip) {
            add(nfa, q0, q2, "");
        }
        return nfa;
    }

    /** begWt 'ab' as q0 --a--> q1 --b--> q2, but q2 loops on a only. */
    private static FiniteStateAutomaton prefixMissingLoopNfa() {
        FiniteStateAutomaton nfa = new FiniteStateAutomaton();
        State q0 = newState(nfa, "q0", 0, 0);
        State q1 = newState(nfa, "q1", 100, 0);
        State q2 = newState(nfa, "q2", 200, 0);
        nfa.setInitialState(q0);
        nfa.addFinalState(q2);
        add(nfa, q0, q1, "a");
        add(nfa, q1, q2, "b");
        add(nfa, q2, q2, "a");
        return nfa;
    }

    /** Over {a}: q0 (accepting) and q1 swap on every a, so odd lengths are rejected. */
    private static FiniteStateAutomaton alternatingNfa() {
        FiniteStateAutomaton nfa = new FiniteStateAutomaton();
        State q0 = newState(nfa, "q0", 0, 0);
        State q1 = newState(nfa, "q1", 100, 0);
        nfa.setInitialState(q0);
        nfa.addFinalState(q0);
        add(nfa, q0, q1, "a");
        add(nfa, q1, q0, "a");
        return nfa;
    }

    /** Over {a}: q0 (accepting) loops on a and can also move to q1, which loops on a. Accepts everything. */
    private static FiniteStateAutomaton loopingNfa() {
        FiniteStateAutomaton nfa = new FiniteStateAutomaton();
        State q0 = newState(nfa, "q0", 0, 0);
        State q1 = newState(nfa, "q1", 100, 0);
        nfa.setInitialState(q0);
        nfa.addFinalState(q0);
        add(nfa, q0, q0, "a");
        add(nfa, q0, q1, "a");
        add(nfa, q1, q1, "a");
        return nfa;
    }

    /** Over {a}: q0 --a--> {qA, qB}, qA --a--> qB, qB loops; q0 and qA accept. Accepts only '' and 'a'. */
    private static FiniteStateAutomaton blindSpotNfa() {
        FiniteStateAutomaton nfa = new FiniteStateAutomaton();
        State q0 = newState(nfa, "q0", 0, 0);
        State qA = newState(nfa, "qA", 100, 0);
        State qB = newState(nfa, "qB", 100, 100);
        nfa.setInitialState(q0);
        nfa.addFinalState(q0);
        nfa.addFinalState(qA);
        add(nfa, q0, qA, "a");
        add(nfa, q0, qB, "a");
        add(nfa, qA, qB, "a");
        add(nfa, qB, qB, "a");
        return nfa;
    }

    /** begWt 'ab' as a complete DFA; {@code q2NotFinal} forgets to make the fully-matched state accepting. */
    private static FiniteStateAutomaton correctPrefixDfa(boolean q2NotFinal) {
        FiniteStateAutomaton dfa = new FiniteStateAutomaton();
        State q0 = newState(dfa, "q0", 0, 0);
        State q1 = newState(dfa, "q1", 100, 0);
        State q2 = newState(dfa, "q2", 200, 0);
        State fail = newState(dfa, "fail", 100, 100);
        dfa.setInitialState(q0);
        if (!q2NotFinal) {
            dfa.addFinalState(q2);
        }
        add(dfa, q0, q1, "a");
        add(dfa, q0, fail, "b");
        add(dfa, q1, q2, "b");
        add(dfa, q1, fail, "a");
        add(dfa, q2, q2, "a");
        add(dfa, q2, q2, "b");
        add(dfa, fail, fail, "a");
        add(dfa, fail, fail, "b");
        return dfa;
    }

    /** |indOf 'a'| = 1 with the accepting state looping on a, so it also accepts two or more a's. */
    private static FiniteStateAutomaton countExactlyConflatedDfa() {
        FiniteStateAutomaton dfa = new FiniteStateAutomaton();
        State q0 = newState(dfa, "q0", 0, 0);
        State q1 = newState(dfa, "q1", 100, 0);
        dfa.setInitialState(q0);
        dfa.addFinalState(q1);
        add(dfa, q0, q1, "a");
        add(dfa, q0, q0, "b");
        add(dfa, q1, q1, "a");
        add(dfa, q1, q1, "b");
        return dfa;
    }
}
