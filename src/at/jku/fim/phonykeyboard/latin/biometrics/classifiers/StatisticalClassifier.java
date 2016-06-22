package at.jku.fim.phonykeyboard.latin.biometrics.classifiers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
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

    // NOTE: Used for evaluation to quickly disable enhancements
    private static final boolean EVALUATION_ENABLE_BESTOF = false;
    // NOTE: Used for evaluation to set the variability calculation function
    private static final int EVALUATION_VARIABILITY_FUNCTION = 2;
    // NOTE: Used for evaluation to set the distance calculation function
    private static final int EVALUATION_DISTANCE_FUNCTION = 0;

    private final StatisticalClassifierContract dbContract;
    private final Pattern multiValueRegex = Pattern.compile("\\" + MULTI_VALUE_SEPARATOR);

    private int screenOrientation;
    private List<double[][][]> acquisitions;  // feature<[row][sample][values]>
    private double[] distances; // Distances for each enrollment template
    private double[] variability; // Variability scores of each feature of the enrollment templates
    private boolean invalidData;
    private ActiveBiometricsEntries activeEntries = new ActiveBiometricsEntries();
    private List<List<double[]>> currentData;   // feature<sample<[values]>>

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
        return getScore(false);
    }

    // NOTE: The discard parameter is used for evaluation to avoid polluting the dataset of the original user
    public double getScore(boolean discard) {
        if (!calculatedScore) {
            calcScore();
            if (!discard) {
                saveBiometricData();
            }
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
            if (EVALUATION_ENABLE_BESTOF) {
                c = manager.getDb().query(false, StatisticalClassifierContract.StatisticalClassifierData.TABLE_NAME,
                        columns.toArray(new String[columns.size()]),
                        StatisticalClassifierContract.StatisticalClassifierData.COLUMN_CONTEXT + " = ? AND " + StatisticalClassifierContract.StatisticalClassifierData.COLUMN_SCREEN_ORIENTATION + " = ? AND " + StatisticalClassifierContract.StatisticalClassifierData._ID + " IN (" +
                                "SELECT " + StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_DATA_ID + " FROM " + StatisticalClassifierContract.StatisticalClassifierTemplates.TABLE_NAME + " WHERE " + StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_CONTEXT + " = ? AND " + StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_SCREEN_ORIENTATION + " = ?)",
                        new String[] { String.valueOf(context), String.valueOf(screenOrientation), String.valueOf(context), String.valueOf(screenOrientation) },
                        null, null, null, null);
            } else {
                c = manager.getDb().query(false, StatisticalClassifierContract.StatisticalClassifierData.TABLE_NAME,
                        columns.toArray(new String[columns.size()]),
                        StatisticalClassifierContract.StatisticalClassifierData.COLUMN_CONTEXT + " = ? AND " + StatisticalClassifierContract.StatisticalClassifierData.COLUMN_SCREEN_ORIENTATION + " = ?",
                        new String[] { String.valueOf(context), String.valueOf(screenOrientation) },
                        null, null, null, null);
            }

            c.beforeFirst();
            currentData = new ArrayList<>(c.getColumnCount());
            for (int i = 0; i < c.getColumnCount(); i++) {
                currentData.add(new LinkedList<>());
            }

            invalidData = false;
            submittedInput = false;
            calculatedScore = false;
            score = BiometricsManager.SCORE_NOT_ENOUGH_DATA;

            distances = new double[c.getColumnCount()];
            acquisitions = new ArrayList<>(c.getColumnCount());
            for (int i = 0; i < c.getColumnCount(); i++) {
                acquisitions.add(new double[c.getCount()][0][0]);
                calcStatistics(c, i);
            }
            variability = new double[c.getColumnCount()];
            calcVariability();
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
        onFinishInput(done, false);
    }

    // NOTE: The discard parameter is used for evaluation to avoid polluting the dataset of the original user
    public void onFinishInput(boolean done, boolean discard) {
        if (!done) {
            CharSequence text = manager.getInputText();
            if (text != null && text.length() != 0) {
                invalidData = true;
            }
        } else if (!calculatedScore) {
            calcScore();
            if (!discard) {
                saveBiometricData();
            }
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
                distances[columnIndex] += mean / Math.max(c.getCount() - 1, 1);
            }
            distances[columnIndex] /= c.getCount();
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
        // Calculate distance for all features
        for (int k = 0; k < f1.length; k++) {
            switch (EVALUATION_DISTANCE_FUNCTION) {
                case 1:
                    distance += euclideanDistance(f1[k], f2[k]);
                    break;
                case 0:
                default:
                    distance += manhattanDistance(f1[k], f2[k]);
                    break;
            }
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

        // Calculate variability of captured and template acquisitions
        double result = calcAuthentication();
        // NaN results from unequal sample sizes, score will already be set to SCORE_CAPTURING_ERROR by ensureEqualSampleCount()
        if (!Double.isNaN(result)) {
            score = result;
        }
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

    // ---- BEGIN DISTANCE METRICS ----
    private double manhattanDistance(double[] f1, double[] f2) {
        double result = 0;
        for (int i = 0; i < f1.length; i++) {
            result += Math.abs(f1[i] - f2[i]);
        }
        return result;
    }

    private double euclideanDistance(double[] f1, double[] f2) {
        double result = 0;
        for (int i = 0; i < f1.length; i++) {
            result += Math.pow(f1[i] - f2[i], 2);
        }
        return result;
    }
    // ---- END DISTANCE METRICS ----

    // ---- BEGIN VARIABILITY METRICS ----
    private void calcVariability() {
        if (acquisitions.get(0).length > 0) {
            for (int delta = 0; delta < acquisitions.size(); delta++) {
                double result;
                switch (EVALUATION_VARIABILITY_FUNCTION) {
                    case 0:
                        result = minVariability(delta);
                        break;
                    case 1:
                        result = maxVariability(delta);
                        break;
                    case 3:
                        result = tempVariability(delta);
                        break;
                    case 2:
                    default:
                        result = meanVariability(delta);
                        break;
                }
                variability[delta] = result == 0f ? 1f : result;
            }
        }
    }

    /**
     * Mean value of the distances of each enrollment acquisition to its nearest neighbor
     * @param delta the feature index
     * @return MIN^δ_u
     */
    private double minVariability(int delta) {
        double min = 0;
        int E = acquisitions.get(delta).length;
        for (int e = 0; e < E; e++) {  // SUM(e=1, E)
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i < E; i++) {
                if (i == e) continue;
                minDist = Math.min(minDist, getDistance(acquisitions.get(delta)[e], acquisitions.get(delta)[i]));  // MIN(D[f(e), f(i)])
            }
            min += minDist;
        }
        min /= E;   // 1/E
        return min;
    }

    /**
     * Mean value of the distances of each enrollment acquisition to its farthest neighbor
     * @param delta the feature index
     * @return MAX^δ_u
     */
    private double maxVariability(int delta) {
        double max = 0;
        int E = acquisitions.get(delta).length;
        for (int e = 0; e < E; e++) {  // SUM(e=1, E)
            double maxDist = Double.MIN_VALUE;
            for (int i = 0; i < E; i++) {
                if (i == e) continue;
                maxDist = Math.max(maxDist, getDistance(acquisitions.get(delta)[e], acquisitions.get(delta)[i]));  // MAX(D[f(e), f(i)])
            }
            max += maxDist;
        }
        max /= E;   // 1/E
        return max;
    }

    /**
     * Mean value of all the computed distances, over all the enrollment acquisitions
     * @param delta the feature index
     * @return MEAN^δ_u
     */
    private double meanVariability(int delta) {
        double mean = 0;
        int E = acquisitions.get(delta).length;
        for (int e = 0; e < E; e++) {  // SUM(e=1, E)
            double meanDist = 0;
            for (int i = 0; i < E; i++) {
                if (i == e) continue;
                meanDist += getDistance(acquisitions.get(delta)[e], acquisitions.get(delta)[i]);    // SUM(i=1, E, i!=e)
            }
            mean += meanDist / (E - 1); // 1/(E-1)
        }
        mean /= E;   // 1/E
        return mean;
    }

    /**
     * Mean value of the distances of each enrollment acquisition to the "template keystroke dynamics", defined as the
     * acquisition with minimum average distance to all the other ones, and identified by the index tu
     * @param delta the feature index
     * @return TEMP^δ_u
     */
    private double tempVariability(int delta) {
        double temp = 0;
        int E = acquisitions.get(delta).length;
        int tu = findTemplateDynamics(delta);
        for (int e = 0; e < E; e++) {  // SUM(e=1, E, e!=tu)
            if (e == tu) continue;
            temp += getDistance(acquisitions.get(delta)[e], acquisitions.get(delta)[tu]);
        }
        temp /= E;   // 1/E
        return temp;
    }

    /**
     * Finds the index of the "template keystroke dynamics", defined as the acquisition with minimum average distance to
     * all the other ones
     * @param delta the feature index
     * @return tu
     */
    private int findTemplateDynamics(int delta) {
        int tu = -1;
        double minAvgDist = Double.MAX_VALUE;
        int E = acquisitions.get(delta).length;
        for (int e = 0; e < E; e++) {
            double avgDist = 0;
            for (int i = 0; i < E; i++) {
                if (i == e) continue;
                avgDist += getDistance(acquisitions.get(delta)[e], acquisitions.get(delta)[i]);
            }
            avgDist /= E - 1;
            if (avgDist < minAvgDist) {
                tu = e;
                minAvgDist = avgDist;
            }
        }
        return tu;
    }
    // ---- END VARIABILITY METRICS

    // ---- BEGIN AUTHENTICATION METRICS ----
    private double calcAuthentication() {
        double result = Double.NaN;
        if (acquisitions.get(0).length > 0) {
            for (int delta = 0; delta < acquisitions.size(); delta++) {
                switch (EVALUATION_VARIABILITY_FUNCTION) {
                    case 0:
                        result = minAuthentication();
                        break;
                    case 1:
                        result = maxAuthentication();
                        break;
                    case 3:
                        result = tempAuthentication();
                        break;
                    case 2:
                    default:
                        result = meanAuthentication();
                        break;
                }
            }
        }
        return result;
    }

    private double minAuthentication() {
        double min = 0;
        for (int delta = 0; delta < currentData.size(); delta++) {
            double minDist = Double.MAX_VALUE;
            int E = acquisitions.get(delta).length;
            for (int e = 0; e < E; e++) {
                if (!ensureEqualSampleCount(delta, e)) {
                    return Double.NaN;
                }

                double[][] sample = new double[currentData.get(delta).size()][currentData.get(delta).get(0).length];
                minDist = Math.min(minDist, getDistance(acquisitions.get(delta)[e], currentData.get(delta).toArray(sample)));  // MIN(D[f(e), f(u)])
            }
            min += minDist / variability[delta];
        }
        min /= currentData.size();   // 1/δ
        return min;
    }

    private double maxAuthentication() {
        double max = 0;
        for (int delta = 0; delta < currentData.size(); delta++) {
            double maxDist = Double.MIN_VALUE;
            int E = acquisitions.get(delta).length;
            for (int e = 0; e < E; e++) {
                if (!ensureEqualSampleCount(delta, e)) {
                    return Double.NaN;
                }

                double[][] sample = new double[currentData.get(delta).size()][currentData.get(delta).get(0).length];
                maxDist = Math.max(maxDist, getDistance(acquisitions.get(delta)[e], currentData.get(delta).toArray(sample)));  // MAX(D[f(e), f(u)])
            }
            max += maxDist / variability[delta];
        }
        max /= currentData.size();   // 1/δ
        return max;
    }

    private double meanAuthentication() {
        double mean = 0;
        for (int delta = 0; delta < currentData.size(); delta++) {
            double meanDist = 0;
            int E = acquisitions.get(delta).length;
            for (int e = 0; e < E; e++) {
                if (!ensureEqualSampleCount(delta, e)) {
                    return Double.NaN;
                }

                double[][] sample = new double[currentData.get(delta).size()][currentData.get(delta).get(0).length];
                meanDist += getDistance(acquisitions.get(delta)[e], currentData.get(delta).toArray(sample));  // D[f(e), f(u)]
            }
            mean += meanDist / variability[delta];
        }
        mean /= currentData.size();   // 1/δ
        return mean;
    }

    private double tempAuthentication() {
        double temp = 0;
        for (int delta = 0; delta < currentData.size(); delta++) {
            int tu = findTemplateDynamics(delta);
            if (!ensureEqualSampleCount(delta, tu)) {
                return Double.NaN;
            }

            double[][] sample = new double[currentData.get(delta).size()][currentData.get(delta).get(0).length];
            temp += getDistance(acquisitions.get(delta)[tu], currentData.get(delta).toArray(sample)) / variability[delta];
        }
        temp /= currentData.size();   // 1/δ
        return temp;
    }

    private boolean ensureEqualSampleCount(int delta, int e) {
        if (currentData.get(delta).size() != acquisitions.get(delta)[e].length) {
            Log.e(TAG, "Authentication data has " + currentData.get(delta).size() + " samples, needs " + acquisitions.get(delta)[e].length);
            score = BiometricsManager.SCORE_CAPTURING_ERROR;
            invalidData = true;
            return false;
        }
        return true;
    }
    // ---- END AUTHENTICATION METRICS ----

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

        int index = 0;
        try {
            index = db.insert(StatisticalClassifierContract.StatisticalClassifierData.TABLE_NAME, values);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (EVALUATION_ENABLE_BESTOF && index > 0) {
            try {
                Cursor c = db.query(false, StatisticalClassifierContract.StatisticalClassifierTemplates.TABLE_NAME,
                        new String[] {StatisticalClassifierContract.StatisticalClassifierTemplates._ID, StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_DATA_ID, StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_SCORE },
                        StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_CONTEXT + " = ? AND " + StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_SCREEN_ORIENTATION + " = ?",
                        new String[] { String.valueOf(manager.getBiometricsContext()), String.valueOf(screenOrientation) }, null, null, StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_SCORE, null);
                if (c.getCount() < TEMPLATE_SET_SIZE) {
                    saveTemplate(index);
                } else {
                    while (c.next()) {
                        if (score < c.getDouble(StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_SCORE)) {
                            db.delete(StatisticalClassifierContract.StatisticalClassifierTemplates.TABLE_NAME,
                                    StatisticalClassifierContract.StatisticalClassifierTemplates._ID + " = ?",
                                    new String[] { String.valueOf(c.getInt(StatisticalClassifierContract.StatisticalClassifierTemplates._ID)) });
                            saveTemplate(index);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveTemplate(int dbId) {
        BiometricsDbHelper db = manager.getDb();
        BiometricsDbHelper.ContentValues values = new BiometricsDbHelper.ContentValues(4);
        values.put(StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_CONTEXT, manager.getBiometricsContext());
        values.put(StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_SCREEN_ORIENTATION, screenOrientation);
        values.put(StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_DATA_ID, dbId);
        values.put(StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_SCORE, score);
        try {
            db.insert(StatisticalClassifierContract.StatisticalClassifierTemplates.TABLE_NAME, values);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
