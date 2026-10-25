package afctevaluator.mosel.grading;

import com.google.gson.JsonObject;

/**
 * Turns an evaluation into a score between 0 and 1. The web app multiplies the score by the problem's points, so
 * one rubric works on problems worth different amounts.
 * <p>
 * RUBRIC: the rubric abstraction; real rubric kinds implement this (see RUBRIC_LOCATIONS.md).
 */
public interface Rubric {
    double score(GradingInput input);

    /** The definition this rubric was parsed from, echoed back as {@code rubricApplied}. */
    JsonObject definition();
}
