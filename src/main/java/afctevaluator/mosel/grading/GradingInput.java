package afctevaluator.mosel.grading;

/**
 * Everything a rubric may look at.
 * <p>
 * RUBRIC: real rubric kinds will likely need more here (e.g. per-component facts); see RUBRIC_LOCATIONS.md.
 *
 * @param correct the authoritative verdict from the equivalence check
 * @param analysis the MOSEL analysis, or null when there was no description or it could not run
 */
public record GradingInput(boolean correct, MoselAnalysis analysis) {
}
