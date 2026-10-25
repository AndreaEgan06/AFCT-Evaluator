package afctevaluator.mosel.grading;

import java.util.List;
import java.util.Map;

/**
 * The MOSEL analysis of a submission, in the shape it is serialized into the evaluator's JSON output (as
 * {@code analysis}). Not shown to students; stored so it can be displayed or used by rubrics later.
 *
 * @param alphabet the symbols the analysis was run over: every symbol the answer file or the submission reads
 * @param specMatchesAnswerKey whether the answer file itself is fully consistent with the description; when false
 *                             the description and the answer file disagree, and the analysis should not be trusted
 * @param nondeterministic whether the submission was analysed as an NFA (nondeterminism, λ-transitions or
 *                         multi-symbol labels); its states are then diagnosed as submitted, without a trap state
 * @param trapAdded whether a trap state was added for missing transitions
 * @param submittedStates the number of states in the submission as handed in
 * @param analyzedStates the number of states that were analysed (the submitted ones, plus the trap state if added)
 * @param minimalStates the number of states in the minimal DFA for the description
 * @param counts how many analysed states received each diagnosis
 * @param states one entry per analysed state
 */
public record MoselAnalysis(
        List<String> alphabet,
        boolean specMatchesAnswerKey,
        boolean nondeterministic,
        boolean trapAdded,
        int submittedStates,
        int analyzedStates,
        int minimalStates,
        Map<String, Integer> counts,
        List<StateReport> states) {

    /**
     * @param name the state's name
     * @param initial whether it is the initial state
     * @param accepting whether the submission marks it accepting
     * @param facts the facts reachable at the state, as text
     * @param diagnosis the {@link afctevaluator.mosel.analysis.StateDiagnosis} name
     */
    public record StateReport(String name, boolean initial, boolean accepting, List<String> facts, String diagnosis) {
    }
}
