package afctevaluator.mosel.grading;

import com.google.gson.JsonObject;

/**
 * The placeholder rubric: full marks when correct, nothing otherwise. Grades come out exactly as they do with no
 * rubric at all.
 * <p>
 * RUBRIC: binary-only placeholder; replace or join with real kinds when the rubric shape is decided.
 */
public final class BinaryRubric implements Rubric {
    private final JsonObject definition;

    public BinaryRubric(JsonObject definition) {
        this.definition = definition;
    }

    @Override
    public double score(GradingInput input) {
        return input.correct() ? 1.0 : 0.0;
    }

    @Override
    public JsonObject definition() {
        return definition;
    }
}
