package afctevaluator.mosel.analysis;

import automata.State;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What {@link NfaFactVerifier} found: every state's facts, each with the iteration that learned it, and the errors
 * collected along the way.
 *
 * @param facts               for each submitted state, its facts mapped to the iteration that learned them, which is the
 *                            length of the shortest string that reaches the state with that fact
 * @param trapFacts           the same for the trap state added in step 1 (empty when none was needed)
 * @param trapAdded           whether some state lacked a transition on some symbol, so a trap state was added
 * @param iterations          the last iteration in which anything was learned
 * @param possibleStates      for each iteration k up to {@code iterations}, the states some string of length k reaches
 * @param diagnoses           per submitted state: UNREACHABLE, SPURIOUS_ACCEPTANCE (an accepting state with a rejecting
 *                            fact), or CONSISTENT. A non-accepting state may hold accepting facts in an NFA, so missing
 *                            acceptance is reported per fact instead, in {@code missingAcceptances}
 * @param missingAcceptances  step 3, in order of length
 * @param spuriousAcceptances step 4, in order of state, then length
 */
public record NfaVerificationReport<D>(
        Map<State, Map<D, Integer>> facts,
        Map<D, Integer> trapFacts,
        boolean trapAdded,
        int iterations,
        List<PossibleStates> possibleStates,
        Map<State, StateDiagnosis> diagnoses,
        List<MissingAcceptance<D>> missingAcceptances,
        List<SpuriousAcceptance<D>> spuriousAcceptances) {

    /**
     * The states some string of a given length reaches.
     *
     * @param states the submitted states among them
     * @param trap   whether the trap state is among them
     */
    public record PossibleStates(Set<State> states, boolean trap) {
    }

    /**
     * Step 3: accepting fact {@code fact} was learned in iteration {@code length}, and no state possible at that length is
     * accepting. Always a real error: every string of that length is rejected, and {@code witness} is one of them with
     * that fact, so it should have been accepted.
     *
     * @param learners    the submitted states that learned the fact in that iteration, none of them accepting
     * @param trapLearned whether the trap state learned it then too
     */
    public record MissingAcceptance<D>(D fact, int length, List<State> learners, boolean trapLearned, String witness) {
    }

    /**
     * Step 4: accepting state {@code state} learned rejecting fact {@code fact} in iteration {@code length}. Always a
     * real error: {@code witness} is a string of that length with that fact, which reaches {@code state} and so is
     * accepted.
     */
    public record SpuriousAcceptance<D>(State state, D fact, int length, String witness) {
    }

    public StateDiagnosis diagnosisOf(State state) {
        return diagnoses.get(state);
    }

    public Set<D> factsOf(State state) {
        return facts.get(state).keySet();
    }

    /** The iteration in which {@code state} learned {@code fact}, or null if it never did. */
    public Integer iterationLearned(State state, D fact) {
        return facts.get(state).get(fact);
    }

    public boolean hasErrors() {
        return !spuriousAcceptances.isEmpty() || !missingAcceptances.isEmpty();
    }
}
