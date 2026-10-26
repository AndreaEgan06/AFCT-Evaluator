#!/usr/bin/env python3
"""Run the evaluator against cases whose right answer is already known.

WHY THIS EXISTS. The evaluator is the thing that decides whether a student's automaton is
correct, so the failure that matters is not a crash, it is a verdict that quietly changes.
Compiling proves nothing about that. Each case here is a pair of files plus the answer the
evaluator is supposed to give, so a change that alters a verdict fails the build instead of
reaching a course.

It runs the built jar the same way production does: same JVM flags, same environment
variables, same command line. Calling the classes directly would be easier and would stop
testing the thing that actually ships.

    python3 scripts/run-golden-cases.py [path/to/afct-evaluator.jar]

Defaults to target/afct-evaluator.jar, so run `mvn clean verify` first.

TO ADD A CASE: put the .jff files in test/golden/cases and add an entry to
test/golden/manifest.json. `expectCorrect` is the verdict the evaluator must give.

An FA case with `"answer": null` is graded against its grading config's MOSEL description
(`--answer-from-mosel`) instead of an answer file. Give it `sameFeedbackAs`, an answer file
for the same language, and the case also checks that the student sees exactly what that
answer file would have shown them.

These cases are also kept in the dashboard repository, where they guard the jar that ships
there. The same cases in two places is deliberate: this copy guards the code, that one
guards the jar, and neither repository has to reach into the other.
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GOLDEN = ROOT / "test" / "golden"
CASES = GOLDEN / "cases"
GRADING_CONFIGS = GOLDEN / "grading-configs"
CFGANALYZER = ROOT / "bin" / "cfganalyzer"

# Production kills an evaluation that runs long; a case that hangs should fail rather than
# hold the build open until the runner's own limit.
TIMEOUT_SECONDS = 60
MAX_MEMORY_MB = 512


def type_args(case):
    """The extra command-line arguments each machine type takes.

    Mirrors buildEvaluatorArgs() in the dashboard's submission worker. Getting this wrong
    would test an invocation nobody uses.
    """
    if case["type"] == "FA":
        return [str(case.get("maxStates", -1)), str(case.get("deterministic", False)).lower()]
    if case["type"] == "PDA":
        return [str(case.get("maxStates", -1))]
    return []


def grading_args(case):
    """`--grading-config` for cases that test the MOSEL analysis and rubric score.

    RUBRIC: golden cases carry their rubric in the grading config (see RUBRIC_LOCATIONS.md).
    """
    if "gradingConfig" not in case:
        return []
    return ["--grading-config", str(GRADING_CONFIGS / case["gradingConfig"])]


def answer_args(case, answer=None):
    """The answer file, or `--answer-from-mosel` for a case graded against its description."""
    answer = answer if answer is not None else case["answer"]
    return ["--answer-from-mosel"] if answer is None else [str(CASES / answer)]


def subset_mismatches(expected, actual, path="analysis"):
    """Where `actual` differs from `expected`, looking only at the keys `expected` names."""
    if isinstance(expected, dict):
        if not isinstance(actual, dict):
            return [f"{path}: expected an object, got {actual!r}"]
        problems = []
        for key, value in expected.items():
            problems += subset_mismatches(value, actual.get(key), f"{path}.{key}")
        return problems
    return [] if expected == actual else [f"{path}: expected {expected!r}, got {actual!r}"]


def grading_mismatches(case, feedback):
    """Checks for the optional `expectScore`, `expectAnalysis` and `expectWarningContains` keys."""
    problems = []
    if "expectScore" in case and feedback.get("score") != case["expectScore"]:
        problems.append(f'expected score={case["expectScore"]}, got {feedback.get("score")}')
    if "expectAnalysis" in case:
        if case["expectAnalysis"] is None:
            if feedback.get("analysis") is not None:
                problems.append("expected no analysis, but one was returned")
        else:
            problems += subset_mismatches(case["expectAnalysis"], feedback.get("analysis"))
    wanted_warning = case.get("expectWarningContains")
    if wanted_warning and not any(
        wanted_warning.lower() in w.lower() for w in feedback.get("warnings", [])
    ):
        problems.append(f'expected a warning containing "{wanted_warning}"')
    return problems


def parse_feedback(stdout):
    """The result JSON. Tolerant of stray output before it, as the dashboard's runner is."""
    text = (stdout or "").strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r"\{[\s\S]*\}\s*$", text)
        if not match:
            raise AssertionError(f"no JSON in evaluator output: {text[:200]}")
        return json.loads(match.group(0))


def main():
    jar = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "target" / "afct-evaluator.jar"
    if not jar.is_file():
        print(f"ERROR: no jar at {jar}. Run `mvn clean verify` first.", file=sys.stderr)
        return 1

    manifest = json.loads((GOLDEN / "manifest.json").read_text())
    cases = manifest["cases"]

    # The jar reads four variables. Production sets all four, so this does too: without
    # TIMEOUT_SECONDS the jar turns off early stopping, and without UPGRADED_FEEDBACK it
    # warns and defaults it on. Testing with different settings tests a different program.
    env = {
        **os.environ,
        "CFGANALYZER_BINARY": str(CFGANALYZER),
        "CFGANALYZER_LIMIT": str(manifest.get("analyzerLimit", 15)),
        "TIMEOUT_SECONDS": str(TIMEOUT_SECONDS),
        "UPGRADED_FEEDBACK": "true",
    }

    print(f"Evaluating {len(cases)} golden case(s) against {jar.relative_to(ROOT)}\n")
    failures = []

    def evaluate(case, answer=None):
        cmd = [
            "java",
            "-Djava.awt.headless=true",
            f"-Xmx{MAX_MEMORY_MB}m",
            "-XX:+ExitOnOutOfMemoryError",
            "-jar",
            str(jar),
            "--json",
            *grading_args(case),
            *answer_args(case, answer),
            str(CASES / case["submission"]),
            *type_args(case),
        ]
        done = subprocess.run(
            cmd, capture_output=True, text=True, env=env, timeout=TIMEOUT_SECONDS + 10
        )
        return parse_feedback(done.stdout)

    for case in cases:
        name = case["name"]
        try:
            feedback = evaluate(case)

            if not isinstance(feedback.get("correct"), bool):
                raise AssertionError(
                    f"no boolean 'correct' in the result (feedback: {feedback.get('feedback', '?')})"
                )

            # Cases that assert the refusal path: an unsupported file must be refused with a
            # recognisable message rather than silently marked wrong, or a student uploading
            # the wrong kind of machine is told their answer is incorrect.
            wanted_error = case.get("expectErrorContains")
            if wanted_error:
                haystack = " ".join(
                    [feedback.get("feedback", ""), *feedback.get("errors", [])]
                ).lower()
                if wanted_error.lower() not in haystack:
                    failures.append(
                        f'{name}\n        expected an error containing "{wanted_error}"'
                        f'\n        feedback: {feedback.get("feedback", "")}'
                    )
                    print(f"  FAIL  {name}")
                    continue

            problems = grading_mismatches(case, feedback)
            if "sameFeedbackAs" in case:
                reference = evaluate(case, case["sameFeedbackAs"])
                for key in ("correct", "feedback"):
                    if feedback.get(key) != reference.get(key):
                        problems.append(
                            f"{key} differs from grading against {case['sameFeedbackAs']}: "
                            f"{feedback.get(key)!r} vs {reference.get(key)!r}"
                        )
            if problems:
                failures.append(f"{name}\n        " + "\n        ".join(problems))
                print(f"  FAIL  {name}")
            elif feedback["correct"] == case["expectCorrect"]:
                print(f'  ok    {name}  (correct={feedback["correct"]})')
            else:
                failures.append(
                    f'{name}\n        expected correct={case["expectCorrect"]}, '
                    f'got {feedback["correct"]}\n        feedback: {feedback.get("feedback", "")}'
                )
                print(f"  FAIL  {name}")
        except subprocess.TimeoutExpired:
            failures.append(f"{name}\n        the evaluator did not finish in {TIMEOUT_SECONDS}s")
            print(f"  FAIL  {name}")
        except Exception as err:  # noqa: BLE001 - one bad case must not stop the rest
            failures.append(f"{name}\n        evaluator error: {err}")
            print(f"  FAIL  {name}")

    print(f"\n{len(cases) - len(failures)}/{len(cases)} evaluator cases passed")
    if failures:
        print("\nFailures:", file=sys.stderr)
        for f in failures:
            print(f"  {f}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
