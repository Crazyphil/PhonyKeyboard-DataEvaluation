package at.jku.fim.phonykeyboard.latin.biometrics.classifiers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsEntry;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManager;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManagerImpl;
import at.jku.fim.phonykeyboard.latin.biometrics.data.BiometricsDbHelper;
import at.jku.fim.phonykeyboard.latin.biometrics.data.Contract;
import at.jku.fim.phonykeyboard.latin.biometrics.data.Cursor;
import at.jku.fim.phonykeyboard.latin.biometrics.data.StatisticalClassifierContract;
import at.jku.fim.phonykeyboard.latin.utils.CsvUtils;
import at.jku.fim.phonykeyboard.latin.utils.Log;

/**
 * This Classifier implements the statistical classifier proposed by Maiorana, et al. 2011, extended by further features.
 */
public class StatisticalClassifier extends Classifier {
    private static final String TAG = "StatisticalClassifier";
    private static final int INDEX_DOWNDOWN = 0, INDEX_DOWNUP = 1, INDEX_SIZE =  2, INDEX_ORIENTATION = 3, INDEX_PRESSURE = 4, INDEX_POSITION = 5, INDEX_SENSOR_START = 6;
    private static final int TEMPLATE_SET_SIZE = 10;

    private final StatisticalClassifierContract dbContract;
    private final Pattern multiValueRegex = Pattern.compile("\\" + MULTI_VALUE_SEPARATOR);

    private int screenOrientation;
    private List<double[][][]> acquisitions;  // datapoint<[row][col][values]>
    private double[] means;
    private boolean invalidData;
    private ActiveBiometricsEntries activeEntries = new ActiveBiometricsEntries();
    private List<List<double[]>> currentData;   // datapoint<col<[values]>>

    /** Set to true when the user clicked the Next, Previous or Enter button and therefore submitted the input to the app **/
    private boolean submittedInput;
    /** Set to true when calcScore() was successful **/
    private boolean calculatedScore;
    private double score = BiometricsManager.SCORE_NOT_ENOUGH_DATA;

    public StatisticalClassifier(BiometricsManagerImpl manager) {
        super(manager);

        dbContract = new StatisticalClassifierContract(BiometricsManager.SENSOR_TYPES);
    }

    @Override
    public Contract getDatabaseContract() {
        return dbContract;
    }

    @Override
    public double getScore() {
        if (!calculatedScore) {
            calcScore();
            saveBiometricData();
            resetData();
        }
        return score;
    }

    @Override
    public void onCreate() {
    }

    @Override
    public void onStartInput(long context, boolean restarting) {
        if (restarting || invalidData) {
            if (submittedInput) return;

            CharSequence text = manager.getInputText();
            if (text != null && text.length() != 0) {
                invalidData = true;
            } else {
                resetData();
            }
            return;
        }

        screenOrientation = manager.getScreenOrientation();

        List<String> columns = new ArrayList<>(dbContract.getSensorColumns().length + INDEX_SENSOR_START);
        columns.add(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_KEY_DOWNDOWN);
        columns.add(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_KEY_DOWNUP);
        columns.add(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_SIZE);
        columns.add(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_ORIENTATION);
        columns.add(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_PRESSURE);
        columns.add(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_POSITION);
        for (String sensor : dbContract.getSensorColumns()) {
            columns.add(sensor);
        }

        Cursor c = null;
        try {
            c = manager.getDb().query(false, StatisticalClassifierContract.StatisticalClassifierData.TABLE_NAME,
                    columns.toArray(new String[columns.size()]),
                    StatisticalClassifierContract.StatisticalClassifierData.COLUMN_CONTEXT + " = ? AND " + StatisticalClassifierContract.StatisticalClassifierData.COLUMN_SCREEN_ORIENTATION + " = ?",
                    new String[] { String.valueOf(context), String.valueOf(screenOrientation) },
                    null, null, null, null);

            c.beforeFirst();
            currentData = new ArrayList<>(c.getColumnCount());
            for (int i = 0; i < c.getColumnCount(); i++) {
                currentData.add(new LinkedList<>());
            }

            invalidData = false;
            submittedInput = false;
            calculatedScore = false;
            score = BiometricsManager.SCORE_NOT_ENOUGH_DATA;

            means = new double[c.getColumnCount()];
            acquisitions = new ArrayList<>(c.getColumnCount());
            for (int i = 0; i < c.getColumnCount(); i++) {
                acquisitions.add(new double[c.getCount()][0][0]);
                calcStatistics(c, i);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException e) { }
            }
        }
    }

    @Override
    public void onFinishInput(boolean done) {
        if (!done) {
            CharSequence text = manager.getInputText();
            if (text != null && text.length() != 0) {
                invalidData = true;
            }
        } else if (!calculatedScore) {
            calcScore();
            saveBiometricData();
            resetData();
        }
    }

    @Override
    public void onKeyEvent(BiometricsEntry entry) {
        if (submittedInput) return;
        if (invalidData) {
            if (manager.getInputText().length() == 0) {
                resetData();
            } else {
                return;
            }
        }

        if (entry.getEvent() == BiometricsEntry.EVENT_UP) {
            BiometricsEntry downEntry = activeEntries.getDownEntry(entry.getPointerId());
            if (downEntry == null) {
                Log.e(TAG, "BUG: Got UP event, but no matching DOWN event found");
            } else {
                currentData.get(INDEX_DOWNUP).add(new double[] { entry.getTimestamp() - downEntry.getTimestamp() });
            }
            currentData.get(INDEX_POSITION).add(new double[] { entry.getX(), entry.getY() });
            activeEntries.removeById(entry.getPointerId());
        } else {
            BiometricsEntry prevEntry = activeEntries.getLastDownEntry(entry.getTimestamp());
            if (prevEntry != null) {
                currentData.get(INDEX_DOWNDOWN).add(new double[] { entry.getTimestamp() - prevEntry.getTimestamp() });
                for (int i = 0; i < entry.getSensorData().size(); i++) {
                    float[] prevData = prevEntry.getSensorData().get(i);
                    double[] sensorData = new double[prevData.length];
                    for (int j = 0; j < sensorData.length; j++) {
                        //sensorData[j] = entry.getSensorData().get(i)[j] - prevData[j];
                        // NOTE: Because this is the evaluation, all sensor data already contains relative numbers
                        sensorData[j] = entry.getSensorData().get(i)[j];
                    }
                    currentData.get(INDEX_SENSOR_START + i).add(sensorData);
                }
            }
            currentData.get(INDEX_SIZE).add(new double[] { entry.getSize() });
            currentData.get(INDEX_ORIENTATION).add(new double[] { entry.getOrientation() });
            currentData.get(INDEX_PRESSURE).add(new double[] { entry.getPressure() });
            activeEntries.add(entry);
        }
    }

    @Override
    public void onDestroy() {
    }

    private void calcStatistics(Cursor c, int columnIndex) {
        if (c.getCount() == 0) return;
        double[][][] values = acquisitions.get(columnIndex);

        // Load rows from DB to array
        try {
            c.beforeFirst();
            int row = 0;
            while (c.next()) {
                String[] rowValues = CsvUtils.split(c.getString(columnIndex));
                values[row] = new double[rowValues.length][0];
                for (int col = 0; col < rowValues.length; col++) {
                    values[row][col] = stringsToDoubles(multiValueRegex.split(rowValues[col]));
                }
                row++;
            }

            double mean = 0;
            // Calculate mean of distance between each and every row
            for (int e = 0; e < c.getCount(); e++) {
                for (int i = 0; i < c.getCount(); i++) {
                    if (e == i) continue;
                    mean += getDistance(values[e], values[i]);
                }
                means[columnIndex] += mean / Math.max(c.getCount() - 1, 1);
            }
            means[columnIndex] /= c.getCount();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Implementation of the D(.,.) function in Maiorana, et al. 2011
     * @param f1 Fu,e
     * @param f2 Fu,i
     * @return D(.,.)
     */
    private double getDistance(double[][] f1, double[][] f2) {
        double distance = 0;
        // Calculate Manhattan distance for all datapoints
        for (int k = 0; k < f1.length; k++) {
            distance += manhattanDistance(f1[k], f2[k]);
        }
        distance /= f1.length;
        return distance;
    }

    private void resetData() {
        invalidData = false;
        submittedInput = false;
        for (List<double[]> data : currentData) {
            data.clear();
        }
        activeEntries.clear();
    }

    private void calcScore() {
        if (acquisitions.get(0).length < TEMPLATE_SET_SIZE) {
            Log.i(TAG, "Template set too small (" + acquisitions.get(0).length + ") for authentication");
            score = BiometricsManager.SCORE_NOT_ENOUGH_DATA;
            calculatedScore = true;
            return;
        }

        if (currentData.size() != acquisitions.size()) {
            Log.e(TAG, "Authentication data has " + currentData.size() + " datapoints, needs " + acquisitions.size());
            score = BiometricsManager.SCORE_CAPTURING_ERROR;
            invalidData = true;
            calculatedScore = true;
            return;
        }

        // Calculate mean of distance to template acquisitions
        double mean = 0;
        for (int i = 0; i < acquisitions.size(); i++) {
            double distance = 0;
            for (int row = 0; row < acquisitions.get(i).length; row++) {
                if (currentData.get(i).size() != acquisitions.get(i)[row].length) {
                    Log.e(TAG, "Authentication data has " + currentData.get(i).size() + " samples, needs " + acquisitions.get(i)[row].length);
                    score = BiometricsManager.SCORE_CAPTURING_ERROR;
                    invalidData = true;
                    return;
                }

                double[][] rowData = new double[currentData.get(i).size()][currentData.get(i).get(0).length];
                distance += getDistance(acquisitions.get(i)[row], currentData.get(i).toArray(rowData));
            }
            distance /= means[i] == 0f ? 1f : means[i];
            mean += distance;
        }
        mean /= acquisitions.size();
        score = mean;
        calculatedScore = true;
    }

    private double[] stringsToDoubles(String[] strings) {
        double[] doubles = new double[strings.length];
        for (int i = 0; i < strings.length; i++) {
            // NOTE: This is a fix for empty sensor values that seem to only occur in evaluation
            if (!strings[i].isEmpty()) {
                doubles[i] = Double.valueOf(strings[i]);
            } else {
                doubles[i] = 0;
            }
        }
        return doubles;
    }

    private double manhattanDistance(double[] f1, double[] f2) {
        double result = 0;
        for (int i = 0; i < f1.length; i++) {
            result += Math.abs(f1[i] - f2[i]);
        }
        return result;
    }

    private void saveBiometricData() {
        if (invalidData || !calculatedScore) return;

        BiometricsDbHelper db = manager.getDb();
        BiometricsDbHelper.ContentValues values = new BiometricsDbHelper.ContentValues(INDEX_SENSOR_START + dbContract.getSensorColumns().length + 2);
        values.put(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_CONTEXT, manager.getBiometricsContext());
        values.put(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_SCREEN_ORIENTATION, screenOrientation);
        values.put(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_KEY_DOWNDOWN, CsvUtils.join(toCsvStrings(currentData.get(INDEX_DOWNDOWN))));
        values.put(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_KEY_DOWNUP, CsvUtils.join(toCsvStrings(currentData.get(INDEX_DOWNUP))));
        values.put(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_SIZE, CsvUtils.join(toCsvStrings(currentData.get(INDEX_SIZE))));
        values.put(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_ORIENTATION, CsvUtils.join(toCsvStrings(currentData.get(INDEX_ORIENTATION))));
        values.put(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_PRESSURE, CsvUtils.join(toCsvStrings(currentData.get(INDEX_PRESSURE))));
        values.put(StatisticalClassifierContract.StatisticalClassifierData.COLUMN_POSITION, CsvUtils.join(toCsvStrings(currentData.get(INDEX_POSITION))));
        for (int i = 0; i < dbContract.getSensorColumns().length; i++) {
            values.put(dbContract.getSensorColumns()[i], CsvUtils.join(toCsvStrings(currentData.get(INDEX_SENSOR_START + i))));
        }

        try {
            db.insert(StatisticalClassifierContract.StatisticalClassifierData.TABLE_NAME, values);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
