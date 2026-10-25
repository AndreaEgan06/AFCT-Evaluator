# AFCT Evaluator
## Dependencies
* Java Development Kit (JDK) (preferably from Oracle): https://www.oracle.com/java/technologies/downloads/
* Maven
  * A guide to install Maven on Windows: https://phoenixnap.com/kb/install-maven-windows
  * A guide to install Maven on Linux: https://www.geeksforgeeks.org/devops/how-to-install-maven-on-linux/

## Maven Command
To compile the AFCT Evaluator, you will first need to add the AFCT Client as a Maven dependency.
Run this once, and again whenever the client version in `pom.xml` changes:
```shell
./scripts/install-client-dependency.sh
```
It reads the version from `pom.xml` and downloads that jar from the
[client's releases](https://github.com/PennStateCS/AFCT-Client/releases), so it cannot go stale
the way a copied command line does. If a copy is already in `afct-client/` it uses that instead,
which is what keeps older versions building and lets you work offline. Then build with
`mvn clean verify`.

## Set Enviroment Variables
### Automatic Setup
* Go to the `Env-Vars` folder and change the path to the CFGAnalyzer binary file in either the `.ps1` (Windows) or `.sh` (Linux) file
* Run appropriate file

### Manual Setup
* Set the enviroment variable `CFGANALYZER_BINARY` to `"absolute_path_to_CFGBINARY_file"`
* Set the enviroment variable `CFGANALYZER_BINARY` to `15`

## Running Program
* Change the current directory to `target` folder
* Run `java -jar afct-evaluator.jar -h` for more details

### MOSEL analysis and rubric scoring
<!-- RUBRIC: documents the binary-only rubric in the grading config. -->
`--grading-config <path>` (or `-` to read it from stdin) takes a JSON document
`{ "mosel": ..., "rubric": ... }`, either key optional:

```bash
java -jar afct-evaluator.jar --json --grading-config config.json answer.jff submission.jff -1 false
```

* `mosel`: the target language as a MOSEL description, e.g.
  `{ "condition": { "kind": "endWt", "w": "ab" } }` (format in
  `afctevaluator.mosel.grading.MoselSpecParser`). For an FA problem the output gains `analysis`:
  every state of the submission with its reachable facts and diagnosis (a DFA is first completed
  with a trap state; an NFA is analysed as submitted, with λ-transitions passing facts on unchanged), the diagnosis counts, the minimal state count, and
  `specMatchesAnswerKey`.
* `rubric`: only `{ "version": 1, "kind": "binary" }` exists for now. The output gains `score`
  (0 to 1) and `rubricApplied`.

`correct` and `feedback` are never changed by either. A config that cannot be applied is reported
in `errors`, and an unusable rubric falls back to `score = correct ? 1 : 0`. Without the flag the
output is exactly as before.

## AFCT Client

This repository contains the evaluator for AFCT.

Related repositories:

- [AFCT Dashboard](https://github.com/PennStateCS/AFCT)
- [AFCT Client](https://github.com/PennStateCS/AFCT-Client)


## Making a release
Pushing a version tag publishes a downloadable jar. See [RELEASING.md](RELEASING.md) for the steps.

## Note
* Testing files are located in the `TESTINPUT` folder, including a text file including example scripts
