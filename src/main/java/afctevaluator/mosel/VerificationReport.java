package afctevaluator.mosel;

import automata.State;

import java.util.Map;
import java.util.Set;

public record VerificationReport<D>(Map<State, Set<D>> facts, Map<State, StateDiagnosis> diagnoses) {
    public StateDiagnosis diagnosisOf(State state) {
        return diagnoses.get(state);
    }

    public Set<D> factsOf(State state) {
        return facts.get(state);
    }
}
