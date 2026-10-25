package afctevaluator.mosel.grading;

import afctevaluator.Feedback;
import afctevaluator.mosel.core.FactTrackingSystem;
import afctevaluator.mosel.core.Pair;
import afctevaluator.mosel.analysis.AnalysisAutomatonBuilder;
import afctevaluator.mosel.analysis.DfaFactConstructor;
import afctevaluator.mosel.analysis.FactVerifier;
import afctevaluator.mosel.analysis.DfaMinimizer;
import afctevaluator.mosel.analysis.StateDiagnosis;
import afctevaluator.mosel.analysis.VerificationReport;
import automata.State;
import automata.fsa.FiniteStateAutomaton;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Applies a grading config ({@code { "mosel": ..., "rubric": ... }}, either key optional) to a finished evaluation.
 * <p>
 * The equivalence check stays authoritative: {@code correct} and the witness feedback are never changed here. This
 * only adds {@code analysis}, {@code score} and {@code rubricApplied}. Nothing in it may lose the verdict, so every
 * failure is recorded in {@code errors} and grading falls back to correct/incorrect.
 */
public final class MoselGrader {
    private MoselGrader() {
    }

    /** Parses the config document. Throws if it is not a JSON object. */
    public static JsonObject parseConfig(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("the grading config must be a JSON object");
        }
        return parsed.getAsJsonObject();
    }

    public static void apply(Feedback feedback, JsonObject config, Serializable answer, Serializable submitted) {
        JsonElement moselJson = present(config.get("mosel"));
        JsonElement rubricJson = present(config.get("rubric"));

        MoselAnalysis analysis = null;
        if (moselJson != null) {
            if (!(answer instanceof FiniteStateAutomaton answerFSA)) {
                feedback.info.add("MOSEL analysis skipped: it only applies to finite automaton problems.");
            } else if (!(submitted instanceof FiniteStateAutomaton submittedFSA)) {
                feedback.info.add("MOSEL analysis skipped: the submission is not a finite automaton.");
            } else {
                try {
                    analysis = analyze(MoselSpecParser.parse(moselJson), answerFSA, submittedFSA);
                    if (!analysis.specMatchesAnswerKey()) {
                        feedback.warnings.add("The MOSEL description does not describe the same language as the answer file; the analysis may be misleading.");
                    }
                } catch (RuntimeException e) {
                    feedback.errors.add("MOSEL analysis failed: " + e.getMessage());
                }
            }
        }
        feedback.analysis = analysis;

        // RUBRIC: where the rubric is parsed and applied to the evaluation, with the correct/incorrect fallback.
        if (rubricJson != null) {
            try {
                Rubric rubric = RubricParser.parse(rubricJson);
                double score = rubric.score(new GradingInput(feedback.correct, analysis));
                feedback.score = Math.max(0.0, Math.min(1.0, score));
                feedback.rubricApplied = rubric.definition();
            } catch (RuntimeException e) {
                feedback.errors.add("The rubric could not be applied, so the grade falls back to correct/incorrect: " + e.getMessage());
                feedback.score = feedback.correct ? 1.0 : 0.0;
            }
        }
    }

    private static JsonElement present(JsonElement json) {
        return json == null || json.isJsonNull() ? null : json;
    }

    /**
     * The description has no alphabet, so the analysis runs over every symbol the answer file or the submission
     * reads. The answer key alone is checked over its own symbols: a symbol only the submission uses says nothing
     * about whether the description matches the answer file.
     */
    static MoselAnalysis analyze(FactTrackingSystem<Object> system, FiniteStateAutomaton answer, FiniteStateAutomaton submitted) {
        Set<Character> answerSymbols = AnalysisAutomatonBuilder.symbolsOf(answer);
        Set<Character> alphabet = new TreeSet<>(answerSymbols);
        alphabet.addAll(AnalysisAutomatonBuilder.symbolsOf(submitted));

        // The answer key is checked as a whole language, so it is determinized: on an NFA, a correct state can
        // still be diagnosed MISSING_ACCEPTANCE or CONFLICTED, since another path may accept the same string.
        AnalysisAutomatonBuilder.Result answerDfa = AnalysisAutomatonBuilder.determinize(answer, answerSymbols);
        boolean specMatchesAnswerKey = allReachableConsistent(FactVerifier.verify(answerDfa.automaton(), system));

        AnalysisAutomatonBuilder.Result prepared = AnalysisAutomatonBuilder.build(submitted, alphabet);
        VerificationReport<Object> report = FactVerifier.verify(prepared.automaton(), system);

        FiniteStateAutomaton reference = DfaFactConstructor.build(system, alphabet);
        int minimalStates = DfaMinimizer.minimalStateCount(reference, alphabet);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (StateDiagnosis diagnosis : StateDiagnosis.values()) {
            counts.put(diagnosis.name(), 0);
        }
        List<MoselAnalysis.StateReport> states = new ArrayList<>();
        FiniteStateAutomaton automaton = prepared.automaton();
        for (State state : automaton.getStates()) {
            StateDiagnosis diagnosis = report.diagnosisOf(state);
            counts.merge(diagnosis.name(), 1, Integer::sum);
            List<String> facts = report.factsOf(state).stream().map(MoselGrader::formatFact).sorted().toList();
            states.add(new MoselAnalysis.StateReport(state.getName(), state == automaton.getInitialState(),
                    automaton.isFinalState(state), facts, diagnosis.name()));
        }

        return new MoselAnalysis(
                alphabet.stream().map(String::valueOf).toList(),
                specMatchesAnswerKey,
                prepared.nondeterministic(),
                prepared.trapAdded(),
                submitted.getStates().length,
                automaton.getStates().length,
                minimalStates,
                counts,
                states);
    }

    private static boolean allReachableConsistent(VerificationReport<?> report) {
        return report.diagnoses().values().stream()
                .allMatch(d -> d == StateDiagnosis.CONSISTENT || d == StateDiagnosis.UNREACHABLE);
    }

    /** Pairs from compound conditions print as tuples, e.g. {@code (2, (0, 1))}, rather than as record dumps. */
    static String formatFact(Object fact) {
        if (fact instanceof Pair<?, ?> pair) {
            return "(" + formatFact(pair.first()) + ", " + formatFact(pair.second()) + ")";
        }
        return String.valueOf(fact);
    }
}
