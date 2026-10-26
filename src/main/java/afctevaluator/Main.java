package afctevaluator;

import afctevaluator.mosel.grading.MoselGrader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import file.DataException;
import gui.DisableGUI;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static afctevaluator.CFGAnalyzerInterface.getStackTraceAsString;
import static afctevaluator.CheckSubmission.timeAllowedMilli;

/**
 * The main AFCT-Evaluator class is run as afct-evaluator.jar, usage message is below in showHelp().
 *
 *  @author Jesse Burdick-Pless jb4411@rit.edu
 */
public class Main {
    private static String answerFilePath;
    private static String submissionFilePath;
    private static int maxStates = -1;
    public static boolean deterministic = false;
    private static boolean outputJson = false;
    private static String gradingConfigSource = null;
    private static boolean answerFromMosel = false;
    public static Instant start;
    public static boolean testMode = false;

    /**
     * A helper method for displaying the help/usage message for AFCT-Evaluator.
     */
    private static void showHelp() {
        String helpMessage = """
                usage: afct-evaluator.jar [-h] [-v] [-j] [--grading-config path] answerFilePath submissionFilePath [maxStates] [deterministic]
                       afct-evaluator.jar [-h] [-v] [-j] --grading-config path --answer-from-mosel submissionFilePath [maxStates] [deterministic]
                                
                positional arguments:
                    answerFilePath          path to the file with the correct answer (left out with --answer-from-mosel)
                    submissionFilePath      path to the file submitted
                    
                optional positional arguments:
                    maxStates               maximum number of states for PDAs and FAs (-1 means no limit)
                    deterministic           whether the answer FA is deterministic (valid input: {true,false})
                                
                options:
                    -h, --help              show this help message and exit
                    -v, --version           show the afct evaluator version and exit
                    -j, --json              display output in JSON format
                    --grading-config path   JSON file with { "mosel": ..., "rubric": ... } (either optional);
                                            '-' reads it from stdin. Adds the MOSEL analysis (FA only) and the
                                            rubric score to the output.
                    --answer-from-mosel     grade an FA against the language of the grading config's MOSEL
                                            description (over its alphabet) instead of an answer file
                """;
        System.out.println(helpMessage);
    }

    /**
     * A helper method for displaying an error when invalid command line arguments are given.
     * The message is formatted as follows:
     *      error: invalid command line argument: {prefix} '{arg}' {suffix}
     *
     * @param prefix text to put before the invalid argument
     * @param arg the invalid argument
     * @param suffix text to put after the invalid argument
     */
    private static void invalidCliArg(String prefix, String arg, String suffix) {
        System.out.printf("error: invalid command line argument: %s '%s' %s\n", prefix, arg, suffix);
        showHelp();
        System.exit(1);
    }

    /**
     * A helper method for displaying an error when invalid command line arguments are given.
     * The message is formatted as follows:
     *      error: invalid command line argument: {prefix} '{arg}'
     *
     * @param prefix text to put before the invalid argument
     * @param arg the invalid argument
     */
    private static void invalidCliArg(String prefix, String arg) {
        System.out.printf("error: invalid command line argument: %s '%s'\n", prefix, arg);
        showHelp();
        System.exit(1);
    }

    /**
     * A helper method for displaying an error when invalid command line arguments are given.
     * The message is formatted as follows:
     *      error: invalid command line argument: '{arg}'
     *
     * @param arg the invalid argument
     */
    private static void invalidCliArg(String arg) {
        System.out.printf("error: invalid command line argument: '%s'\n", arg);
        showHelp();
        System.exit(1);
    }

    /**
     * A helper method for handling the command line argument [deterministic], and setting Main.deterministic accordingly.
     *
     * @param arg the value given for [deterministic] on the command line
     * @return true if arg is the string "true" or the string "false" (ignoring case), false otherwise
     */
    private static boolean handleDeterministic(String arg) {
        if (arg.equalsIgnoreCase("true")) {
            Main.deterministic = true;
            return true;
        } else if (arg.equalsIgnoreCase("false")) {
            Main.deterministic = false;
            return true;
        } else {
            return false;
        }
    }

    /**
     * A helper method for parsing and handling command line arguments.
     *
     * @param args the given command line arguments
     */
    private static void handleArgs(String[] args) {
        List<String> argList = Arrays.asList(args);

        if (argList.contains("-h") || argList.contains("--help")) {
            showHelp();
            System.exit(0);
        }

        if (argList.contains("-vj") || argList.contains("-jv")) {
            Gson gson = new Gson();
            String json = gson.toJson(new Version());
            System.out.println(json);
            System.exit(0);
        }

        if (argList.contains("-v") || argList.contains("--version")) {
            if (argList.contains("-j") || argList.contains("--json")) {
                Gson gson = new Gson();
                String json = gson.toJson(new Version());
                System.out.println(json);
            } else {
                System.out.println(Main.class.getPackage().getImplementationVersion());
            }
            System.exit(0);
        }

        argList = new ArrayList<>(argList);

        if (argList.contains("-j")) {
            argList.removeAll(Collections.singleton("-j"));
            Main.outputJson = true;
        }
        if (argList.contains("--json")) {
            argList.removeAll(Collections.singleton("--json"));
            Main.outputJson = true;
        }

        int gradingConfigIndex = argList.indexOf("--grading-config");
        if (gradingConfigIndex >= 0) {
            if (gradingConfigIndex + 1 >= argList.size()) {
                invalidCliArg("--grading-config needs a path (or '-' for stdin)", "--grading-config");
            }
            Main.gradingConfigSource = argList.get(gradingConfigIndex + 1);
            argList.remove(gradingConfigIndex + 1);
            argList.remove(gradingConfigIndex);
        }

        if (argList.contains("--answer-from-mosel")) {
            argList.removeAll(Collections.singleton("--answer-from-mosel"));
            if (Main.gradingConfigSource == null) {
                invalidCliArg("--answer-from-mosel needs --grading-config with a MOSEL description:", "--answer-from-mosel");
            }
            Main.answerFromMosel = true;
            // Stands in for answerFilePath, so the positional arguments after it are where they always are.
            argList.add(0, null);
        }

        if (argList.size() < 2) {
            System.out.print("error: not enough command line arguments: ");
            if (argList.isEmpty()) {
                System.out.println("answerFilePath and submissionFilePath are required.");
            } else if (argList.size() == 1) {
                System.out.println("submissionFilePath is required.");
            }
            showHelp();
            System.exit(1);
        }

        Main.answerFilePath = argList.get(0);
        Main.submissionFilePath = argList.get(1);

        if (argList.size() > 3) {
            try {
                Main.maxStates = Integer.parseInt(argList.get(2));
            } catch (NumberFormatException ignored) {
                invalidCliArg("maxStates must be an integer: unable to convert", argList.get(2), "to an integer.");
            }
            if (!handleDeterministic(argList.get(3))) {
                invalidCliArg("deterministic must be 'true' or 'false' but got:", argList.get(3));
            }
        } else if (argList.size() > 2) {
            try {
                Main.maxStates = Integer.parseInt(argList.get(2));
            } catch (NumberFormatException ignored) {
                if (!handleDeterministic(argList.get(2))) {
                    invalidCliArg("maxStates must be an integer: unable to convert", argList.get(2), "to an integer.");
                }
            }
        }
    }

    public static long getTimeLeft() {
        // TODO: use this to stop the witnessType checking early if time is running out.
        return timeAllowedMilli - Duration.between(start, Instant.now()).toMillis();
    }

    public static long getTimeTaken() {
        return Duration.between(start, Instant.now()).toMillis();
    }

    public static long getTimeTaken(Instant end) {
        return Duration.between(start, end).toMillis();
    }

    public static String getTimeTakenWithUnits(Instant start, Instant end) {
        //TODO: pick unit (ns, ms, secs) based on time taken
        Duration duration = Duration.between(start, end);
        //duration.
        return "";
    }

    /** Reads the --grading-config document; throws if it cannot be read or is not a JSON object. */
    private static JsonObject readGradingConfig() throws IOException {
        String text = Main.gradingConfigSource.equals("-")
                ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
                : Files.readString(Path.of(Main.gradingConfigSource));
        return MoselGrader.parseConfig(text);
    }

    /**
     * The answer the submission is checked against: the answer file, or with --answer-from-mosel the DFA built from
     * the config's MOSEL description. Each call gives a fresh one, since the equivalence check is free to modify the
     * automata it was given.
     */
    private static Serializable answer(CheckSubmission checkSubmission, JsonObject gradingConfig) throws DataException {
        return Main.answerFromMosel
                ? MoselGrader.answerAutomaton(MoselGrader.spec(gradingConfig))
                : checkSubmission.readAndDecode(Main.answerFilePath);
    }

    /**
     * Checks the submission. With --answer-from-mosel there is no answer without a valid description, and that is
     * the problem's fault rather than the student's, so it is reported the way a broken answer file is.
     */
    private static Feedback check(CheckSubmission checkSubmission, JsonObject gradingConfig, String gradingConfigError) {
        if (!Main.answerFromMosel) {
            return checkSubmission.isCorrect(Main.answerFilePath, Main.submissionFilePath, Main.maxStates, Main.deterministic);
        }
        Serializable answer;
        try {
            if (gradingConfigError != null) {
                throw new IllegalArgumentException(gradingConfigError);
            }
            answer = answer(checkSubmission, gradingConfig);
        } catch (Exception e) {
            return Feedback.contactProfessorError("ERROR: the problem's MOSEL description could not be used as its answer: "
                    + e.getMessage() + ".");
        }
        try {
            Serializable submitted = checkSubmission.readAndDecode(Main.submissionFilePath);
            return checkSubmission.isCorrect(answer, submitted, Main.maxStates, Main.deterministic);
        } catch (DataException e) {
            return new Feedback("Error: " + e.getMessage(), false, getStackTraceAsString(e));
        }
    }

    /**
     * Adds the MOSEL analysis and the rubric score from --grading-config to the feedback. The verdict is already
     * decided by this point and must survive anything that goes wrong here, so failures become errors entries.
     * RUBRIC: the --grading-config entry point; the rubric travels in the config document.
     *
     * @param checkSubmission the checker that produced the feedback, used to decode the files again
     * @param gradingConfig the parsed config
     * @param feedback the feedback to add to
     */
    private static void applyGradingConfig(CheckSubmission checkSubmission, JsonObject gradingConfig, Feedback feedback) {
        try {
            Serializable answer = answer(checkSubmission, gradingConfig);
            Serializable submitted = checkSubmission.readAndDecode(Main.submissionFilePath);
            MoselGrader.apply(feedback, gradingConfig, answer, submitted);
        } catch (Exception e) {
            feedback.errors.add("The grading config could not be applied: " + e.getMessage());
        }
    }

    /**
     * The main entry point for the AFCT-Evaluator program. Prints feedback to System.out.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        start = Instant.now();

        handleArgs(args);
        DisableGUI.allowGUI = false;
        CheckSubmission checkSubmission = new CheckSubmission();
        // Read before checking: with --answer-from-mosel the config holds the answer.
        JsonObject gradingConfig = null;
        String gradingConfigError = null;
        if (Main.gradingConfigSource != null) {
            try {
                gradingConfig = readGradingConfig();
            } catch (Exception e) {
                gradingConfigError = e.getMessage();
            }
        }
        Feedback feedback;
        try {
            feedback = check(checkSubmission, gradingConfig, gradingConfigError);
        } catch (Exception e) {
            String feedbackMessage = "An error occurred while checking your answer. Please try again. If this occurs repeatedly, please contact your professor.";
            String errorMessage = getStackTraceAsString(e);
            feedback = new Feedback(feedbackMessage, false, errorMessage);
        }
        if (gradingConfigError != null) {
            feedback.errors.add("The grading config could not be applied: " + gradingConfigError);
        } else if (gradingConfig != null) {
            applyGradingConfig(checkSubmission, gradingConfig, feedback);
        }
        feedback.addWarningsAndErrors(checkSubmission.warnings, checkSubmission.errors);
        feedback.info.add("Total time taken: " + getTimeTaken() + " ms.");


        if (Main.outputJson) {
            if (!testMode) {
                Gson gson = new Gson();
                String json = gson.toJson(feedback);
                System.out.println(json);
            } else {
                // Create a Gson instance configured for pretty printing
                Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
                String prettyJson = prettyGson.toJson(feedback);
                System.out.println(prettyJson);
            }
        } else {
            System.out.println(feedback.correct);
            System.out.println(feedback.feedback);
            if (feedback.score != null) {
                System.out.println("Score: " + feedback.score);
            }

            if (!feedback.warnings.isEmpty()) {
                System.out.println("\nWarnings:");
                for (String warning : feedback.warnings) {
                    System.out.println(warning);
                }
            }

            if (!feedback.errors.isEmpty()) {
                System.out.println("\nErrors:");
                for (String error : feedback.errors) {
                    System.out.println(error);
                }
            }
        }
    }
}
