package at.jku.fim.phonykeyboard.evaluation;

import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsEntry;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManager;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManagerImpl;
import at.jku.fim.phonykeyboard.latin.biometrics.classifiers.StatisticalClassifier;
import at.jku.fim.phonykeyboard.latin.biometrics.data.StatisticalClassifierContract;
import at.jku.fim.phonykeyboard.latin.utils.CsvUtils;
import at.jku.fim.phonykeyboard.latin.utils.Log;
import com.opencsv.CSVReader;
import org.apache.commons.cli.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public class StatisticalClassifierEvaluation {
    private static final String TAG = "StatisticalClassifierEvaluation";
    private static final int NUM_TEST_KEYPRESSES = 10, NUM_TEST_TRIES = 100;
    private static final int NUM_STATIC_COLUMNS = 9;
    private static final Map<String, Integer> columnMapping = new HashMap<>();

    private static final Pattern entryPattern = Pattern.compile(";");
    private static final Pattern arrayPattern = Pattern.compile("\\|");

    public static void main(String[] args) {
        CommandLine cmd = parseArgs(args);
        if (cmd == null) return;

        BiometricsManagerImpl manager = new BiometricsManagerImpl();
        manager.init();

        if (cmd.hasOption("t")) {
            processTestData();
        } else {
            if (cmd.hasOption("o")) {
                processCsvFile(cmd.getOptionValue("o"), false);
            }
            if (cmd.hasOption("e")) {
                processCsvFile(cmd.getOptionValue("e"), true);
            }
        }
    }

    private static CommandLine parseArgs(String[] args) {
        Options options = new Options();
        OptionGroup group = new OptionGroup();
        group.setRequired(true);
        group.addOption(Option.builder("t").desc("Test mode, use the same (generated) data many times, resulting in a 0-score").build());
        group.addOption(Option.builder("o").hasArg().type(String.class).desc("Ordered mode, evaluate one user as given in file").build());
        options.addOption("d", "Only output data to STDOUT instead of full log");
        options.addOption("e", true, "A file that should be evaluated using the original mode's data");
        options.addOptionGroup(group);

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("d")) {
                Log.setDataOnly(true);
            }
            if (cmd.hasOption("o")) {
                ensureFileExists(cmd.getOptionValue("o"));
            }
            if (cmd.hasOption("e")) {
                ensureFileExists(cmd.getOptionValue("e"));
            }

            return cmd;
        } catch (ParseException e) {
            Log.e(TAG, String.format("Parsing command line arguments failed. Reason: %s", e.getMessage()));
        }
        return null;
    }

    private static void ensureFileExists(String path) {
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException(String.format("The file \"%s\" does not exist.", file.getAbsolutePath()));
        }
    }

    private static void processTestData() {
        Log.i(TAG, "Testing classifier with equal data for " + NUM_TEST_KEYPRESSES + " keypresses");

        Keypress[] keypresses = new Keypress[NUM_TEST_KEYPRESSES];
        Random random = new Random();
        float x = random.nextFloat() * 100;
        float y = random.nextFloat() * 100;
        float size = random.nextFloat();
        float orientation = random.nextFloat() * 360;
        float pressure = random.nextFloat();
        float downDistance = random.nextFloat() * 10000;
        float upDistance = random.nextFloat() * 10000;

        float[] sensorX = new float[NUM_TEST_KEYPRESSES];
        float[] sensorY = new float[NUM_TEST_KEYPRESSES];
        float[] sensorZ = new float[NUM_TEST_KEYPRESSES];
        for (int i = 0; i < NUM_TEST_KEYPRESSES; i++) {
            sensorX[i] = random.nextFloat() * 100;
            sensorY[i] = random.nextFloat() * 100;
            sensorZ[i] = random.nextFloat() * 100;
        }

        for (int i = 0; i < NUM_TEST_KEYPRESSES; i++) {
            keypresses[i] = new Keypress(x, y, size, orientation, pressure, downDistance, upDistance, BiometricsManager.SENSOR_TYPES.length);
            for (int j = 0; j < BiometricsManager.SENSOR_TYPES.length; j++) {
                keypresses[i].addSensorData(new float[] { sensorX[i], sensorY[i], sensorZ[i] });
            }
        }

        for (int i = 0; i < NUM_TEST_TRIES; i++) {
            double score = calcScore(i, i * 1000, 0, keypresses, sensorX.length, false);
            if (score != BiometricsManager.SCORE_NOT_ENOUGH_DATA && score != 0) {
                Log.e(TAG, String.format("Deviating score in try %d: %f != 0.0", i, score));
            }
        }
    }

    private static void processCsvFile(String csvFile, boolean evaluationMode) {
        CSVReader reader = null;
        try {
            reader = new CSVReader(new BufferedReader(new FileReader(csvFile)), CsvUtils.COMMA, CsvUtils.QUOTE);
        } catch (FileNotFoundException e) {
            // Shouldn't happen, because file existence is checked in advance
            e.printStackTrace();
        }

        try {
            String[] line = reader.readNext();
            while (line != null) {
                if (line.length < NUM_STATIC_COLUMNS) {
                    Log.e(TAG, String.format("Line %d has not enough elements", reader.getLinesRead()));
                    line = reader.readNext();
                    continue;
                }
                if (reader.getRecordsRead() > 1) {
                    Log.i(TAG, String.format("Processing entry %d", reader.getRecordsRead()));
                    processLine(line, evaluationMode);
                } else {
                    Log.i(TAG, "Creating column index mapping");
                    for (int i = 0; i < line.length; i++) {
                        columnMapping.put(line[i], i);
                    }
                }
                line = reader.readNext();
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void processLine(String[] line, boolean evaluationMode) {
        int id = toInt(line[columnMapping.get(StatisticalClassifierContract.StatisticalClassifierData._ID)]);
        long timestamp = toLong(line[columnMapping.get(StatisticalClassifierContract.CaptureClassifierData.COLUMN_TIMESTAMP)]);
        int screenOrientation = toInt(line[columnMapping.get(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_SCREEN_ORIENTATION)]);

        String[] key = new String[0];
        int inputmethod = -1;
        int situation = -1;
        if (columnMapping.containsKey(StatisticalClassifierContract.CaptureClassifierData.COLUMN_KEY)) {
            // Support both old and new study files
            key = entryPattern.split(line[columnMapping.get(StatisticalClassifierContract.CaptureClassifierData.COLUMN_KEY)]);
            inputmethod = toInt(line[columnMapping.get(StatisticalClassifierContract.CaptureClassifierData.COLUMN_INPUTMETHOD)]);
            situation = toInt(line[columnMapping.get(StatisticalClassifierContract.CaptureClassifierData.COLUMN_INPUTMETHOD)]);
        }

        String[] downDistances = entryPattern.split(line[columnMapping.get(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_KEY_DOWNDOWN)]);
        String[] upDistances = entryPattern.split(line[columnMapping.get(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_KEY_DOWNUP)]);
        String[] positions = entryPattern.split(line[columnMapping.get(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_POSITION)]);
        String[] sizes = entryPattern.split(line[columnMapping.get(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_SIZE)]);
        String[] orientations = entryPattern.split(line[columnMapping.get(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_ORIENTATION)]);
        String[] pressures = entryPattern.split(line[columnMapping.get(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_PRESSURE)]);
        String[][] sensors;
        if (key.length == 0) {
            sensors = new String[line.length - NUM_STATIC_COLUMNS][0];
        } else {
            sensors = new String[line.length - NUM_STATIC_COLUMNS - 3][0];
        }

        int sensorIndex = 0;
        for (String sensor : BiometricsManager.SENSOR_TYPES) {
            if (columnMapping.containsKey(sensor)) {
                sensors[sensorIndex] = entryPattern.split(line[columnMapping.get(sensor)]);
            }
            sensorIndex++;
        }

        Log.i(TAG, String.format("Processing study id %d", id));
        if (upDistances.length != positions.length || positions.length != sizes.length || sizes.length != orientations.length
                || orientations.length != pressures.length || downDistances.length != upDistances.length - 1) {
            Log.e(TAG, String.format("ID %d: Unequal number of data points, skipping try", id));
            return;
        }

        Keypress[] keypresses = new Keypress[upDistances.length];
        for (int i = 0; i < upDistances.length; i++) {
            float downDistance = 0;
            if (i > 0) {
                downDistance = toFloat(downDistances[i-1]);
            }
            float upDistance = toFloat(upDistances[i]);
            float[] position = toFloatArray(positions[i]);
            float size = toFloat(sizes[i]);
            float orientation = toFloat(orientations[i]);
            float pressure = toFloat(pressures[i]);
            keypresses[i] = new Keypress(position[0], position[1], size, orientation, pressure, downDistance, upDistance, sensors.length);

            if (i > 0) {
                for (String[] sensor : sensors) {
                    if (sensor.length > 0) {
                        keypresses[i].addSensorData(toFloatArray(sensor[i - 1]));
                    } else {
                        Log.e(TAG, String.format("ID %d: A sensor has no data for keypress %d, skipping try", id, i + 1));
                        return;
                    }
                }
            }
        }

        // Add zero sensor data to first keypress, because no absolute value is available from reports
        for (int i = 0; i < sensors.length; i++) {
            keypresses[0].addSensorData(new float[keypresses[1].getSensorData().get(i).length]);
        }
        calcScore(id, timestamp, screenOrientation, keypresses, sensors.length, evaluationMode);
    }

    private static double calcScore(int tryId, long timestamp, int screenOrientation, Keypress[] keypresses, int sensorCount, boolean evaluationMode) {
        BiometricsManagerImpl manager = (BiometricsManagerImpl)BiometricsManager.getInstance();
        manager.setScreenOrientation(screenOrientation);
        StatisticalClassifier classifier = (StatisticalClassifier)manager.getClassifier();
        classifier.onCreate();

        classifier.onStartInput(manager.getBiometricsContext(), false);
        long entryTimestamp = timestamp;
        for (int i = 0; i < keypresses.length; i++) {
            Keypress keypress = keypresses[i];
            entryTimestamp += keypress.getDownDistance();
            for (int downOrUp = 0; downOrUp <= 1; downOrUp++) {
                BiometricsEntry entry = new BiometricsEntry(sensorCount);
                entry.setProperties(i, downOrUp, entryTimestamp + (int)keypress.getUpDistance() * downOrUp, keypress.getX(), keypress.getY(), keypress.getSize(), keypress.getOrientation(), keypress.getPressure(), screenOrientation);
                entry.setSensorData(keypress.getSensorData());
                classifier.onKeyEvent(entry);
            }
        }
        classifier.onFinishInput(true, evaluationMode);

        double score = classifier.getScore(evaluationMode);
        if (score == BiometricsManager.SCORE_CAPTURING_ERROR) {
            Log.e(TAG, String.format("ID %d: Capturing error, check data", tryId));
        } else if (score == BiometricsManager.SCORE_NOT_ENOUGH_DATA) {
            Log.i(TAG, "No score yet, need more data");
        } else {
            Log.i(TAG, String.format("Score: %f", score));
        }
        Log.data(String.format("%d\t%f", tryId, score));
        classifier.onDestroy();
        return score;
    }

    private static int toInt(String value) {
        if (value.isEmpty()) {
            return 0;
        }
        return Integer.valueOf(value);
    }

    private static long toLong(String value) {
        if (value.isEmpty()) {
            return 0;
        }
        return Long.valueOf(value);
    }

    private static float toFloat(String value) {
        if (value.isEmpty()) {
            return 0;
        }
        return Float.valueOf(value);
    }

    private static float[] toFloatArray(String value) {
        String[] values = arrayPattern.split(value);
        float[] floats = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            floats[i] = toFloat(values[i]);
        }
        return floats;
    }
}
