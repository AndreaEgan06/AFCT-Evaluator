# Golden cases

Each case is a pair of `.jff` files and the verdict the evaluator is supposed to give for
them. The point is not that the evaluator runs, it is that it keeps giving the same answers:
this program decides marks, so a change that quietly flips a verdict is the failure worth
catching, and it is invisible to a build that only checks the code compiles.

Run them against a built jar:

```bash
mvn clean verify
python3 scripts/run-golden-cases.py
```

They also run on every pull request, on every push to `main`, and again on the jar a release
is about to publish.

## Adding a case

1. Put the `.jff` files in `cases/`.
2. Add an entry to `manifest.json`:

```json
{
  "name": "Say what the case shows, in words",
  "type": "FA",
  "answer": "the-solution.jff",
  "submission": "what-a-student-handed-in.jff",
  "maxStates": -1,
  "deterministic": false,
  "expectCorrect": false
}
```

`type` is one of `FA`, `PDA`, `CFG`, `RE`, `TM`, and decides which extra arguments the
evaluator is given. `maxStates` and `deterministic` apply to `FA`; `maxStates` alone applies
to `PDA`. Set `expectCorrect` to the verdict the evaluator must return.

For a file the evaluator should refuse rather than mark, add `expectErrorContains` with a
piece of the message it should give. That is what stops an unsupported machine being reported
to a student as simply wrong.

### MOSEL analysis and rubric cases
<!-- RUBRIC: the rubric golden cases and their grading configs. -->

A case can also pass a grading config (`--grading-config`) from `grading-configs/` and check
what comes back beyond the verdict:

```json
{
  "gradingConfig": "endswith-ab-binary.json",
  "expectScore": 0.0,
  "expectAnalysis": { "trapAdded": true, "counts": { "CONFLICTED": 1 } },
  "expectWarningContains": "does not describe the same language"
}
```

`expectAnalysis` only checks the keys it names; set it to `null` to assert that no analysis is
returned. The `mosel-*` files in `cases/` are small hand-written automata for "ends with ab".

Cases are worth adding whenever a grading bug is found: the case that reproduces it is the
thing that stops it coming back.

## The same cases live in the dashboard

The dashboard repository keeps its own copy, run by `npm run test:evaluator`. That is not an
accident. This copy guards the evaluator's code as it changes; that one guards the jar that
actually ships, which is a different question and can fail for different reasons, such as a
jar swapped by hand for the wrong build.

Two copies can drift. If you add a case here that catches something real, add it there too.
