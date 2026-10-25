package afctevaluator.mosel.grading;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Parses a rubric definition. Only {@code { "version": 1, "kind": "binary" }} exists for now.
 * <p>
 * RUBRIC: binary-only; parse the new kinds here when the rubric shape is decided (see RUBRIC_LOCATIONS.md). The web
 * app validates the same shape in src/schemas/rubric.ts.
 */
public final class RubricParser {
    private RubricParser() {
    }

    public static Rubric parse(JsonElement json) {
        if (json == null || !json.isJsonObject()) {
            throw new IllegalArgumentException("the rubric must be a JSON object");
        }
        JsonObject definition = json.getAsJsonObject();

        JsonElement version = definition.get("version");
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber() || version.getAsDouble() != 1) {
            throw new IllegalArgumentException("unsupported rubric version: " + version);
        }
        JsonElement kind = definition.get("kind");
        if (kind == null || !kind.isJsonPrimitive() || !"binary".equals(kind.getAsString())) {
            throw new IllegalArgumentException("unsupported rubric kind: " + kind);
        }
        return new BinaryRubric(definition.deepCopy());
    }
}
