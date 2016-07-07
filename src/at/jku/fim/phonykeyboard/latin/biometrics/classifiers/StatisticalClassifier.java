package at.jku.fim.phonykeyboard.latin.biometrics.classifiers;

import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

import at.jku.fim.phonykeyboard.evaluation.EvaluationParams;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsEntry;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManager;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManagerImpl;
import at.jku.fim.phonykeyboard.latin.biometrics.data.*;
import at.jku.fim.phonykeyboard.latin.utils.CsvUtils;
import at.jku.fim.phonykeyboard.latin.utils.Log;
import com.apporiented.algorithm.clustering.Cluster;
import com.apporiented.algorithm.clustering.ClusteringAlgorithm;
import com.apporiented.algorithm.clustering.CompleteLinkageStrategy;
import com.apporiented.algorithm.clustering.DefaultClusteringAlgorithm;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.FuzzyKMeansClusterer;
import org.apache.commons.math3.ml.distance.DistanceMeasure;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.ml.distance.ManhattanDistance;

/**
 * This Classifier implements the statistical classifier proposed by Maiorana, et al. 2011, extended by further features.
 */
public class StatisticalClassifier extends Classifier {
    private static final String TAG = "StatisticalClassifier";
    private static final int INDEX_DOWNDOWN = 0, INDEX_DOWNUP = 1, INDEX_SIZE =  2, INDEX_ORIENTATION = 3, INDEX_PRESSURE = 4, INDEX_POSITION = 5, INDEX_SENSOR_START = 6;

    private final StatisticalClassifierContract dbContract;
    private final Pattern multiValueRegex = Pattern.compile("\\" + MULTI_VALUE_SEPARATOR);

    private int screenOrientation;
    private List<double[][][]> acquisitions;  // feature<[row][sample][values]>
    private double[] variability; // Variability scores of each feature of the enrollment templates
    private boolean templatesLocked = false;

    private boolean invalidData;
    private ActiveBiometricsEntries activeEntries = new ActiveBiometricsEntries();
    private List<List<double[]>> currentData;   // feature<sample<[values]>>
    /** Set to the ID of the newly inserted template row, because reading occurs just before the insert **/
    private int currentTemplateID;

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
        Collections.addAll(columns, dbContract.getSensorColumns());

        Cursor c = null;
        try {
            if (EvaluationParams.enableTemplateSelection) {
                c = manager.getDb().query(false, StatisticalClassifierContract.StatisticalClassifierTemplateStatus.TABLE_NAME, new String[] {StatisticalClassifierContract.StatisticalClassifierTemplateStatus._ID },
                        StatisticalClassifierContract.StatisticalClassifierTemplateStatus.COLUMN_CONTEXT + " = ? AND " + StatisticalClassifierContract.StatisticalClassifierTemplateStatus.COLUMN_SCREEN_ORIENTATION + " = ?",
                        new String[] { String.valueOf(manager.getBiometricsContext()), String.valueOf(screenOrientation) }, null, null, null, null);
                templatesLocked = c.getCount() > 0;

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

            acquisitions = new ArrayList<>(c.getColumnCount());
            for (int i = 0; i < c.getColumnCount(); i++) {
                acquisitions.add(new double[c.getCount()][0][0]);
                fillArray(c, i);
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

    private void fillArray(Cursor c, int columnIndex) {
        if (c.getCount() == 0) return;
        double[][][] values = acquisitions.get(columnIndex);

        // Load rows from DB to array
        try {
            c.beforeFirst();
            int row = 0;
            while (c.next()) {
                String[] rowValues = CsvUtils.split(c.getString(columnIndex));
                if (rowValues.length == 1 && rowValues[0].isEmpty()) {
                    if (row == 0) {
                        return;    // Skip empty features (e.g. unavailable sensors)
                    } else {
                        Log.e(TAG, String.format("BUG: Template %d has no data for column %d", row, columnIndex));
                        values[row] = new double[values[row-1].length][0];
                        continue;
                    }
                }

                values[row] = new double[rowValues.length][0];
                for (int col = 0; col < rowValues.length; col++) {
                    values[row][col] = stringsToDoubles(multiValueRegex.split(rowValues[col]));
                }
                row++;
            }
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
        DistanceMeasure measure;
        switch (EvaluationParams.distanceFunction) {
            case 1:
                measure = new EuclideanDistance();
                break;
            default:
            case 0:
                measure = new ManhattanDistance();
                break;
        }

        // Calculate distance for all features
        for (int k = 0; k < f1.length; k++) {
            distance += measure.compute(f1[k], f2[k]);
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
        if (!templatesLocked && acquisitions.get(0).length < EvaluationParams.acquisitionSetSize) {
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
            doubles[i] = Double.valueOf(strings[i]);
        }
        return doubles;
    }

    // ---- BEGIN VARIABILITY METRICS ----
    private void calcVariability() {
        if (acquisitions.get(0).length > 0) {
            for (int delta = 0; delta < acquisitions.size(); delta++) {
                double result;
                switch (EvaluationParams.classificationFunction) {
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
                variability[delta] = result;
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
            double minDist = Double.POSITIVE_INFINITY;
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
            double maxDist = Double.NEGATIVE_INFINITY;
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
        if (tu == -1) return Double.NaN; // No template found, because feature is empty

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
        double minAvgDist = Double.POSITIVE_INFINITY;
        int E = acquisitions.get(delta).length;
        if (E == 1) return 0;   // Avoid NaN through DIV/0

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
            switch (EvaluationParams.classificationFunction) {
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
        return result;
    }

    private double minAuthentication() {
        double min = 0;
        int skippedFeatures = 0;
        for (int delta = 0; delta < currentData.size(); delta++) {
            double minDist = Double.POSITIVE_INFINITY;
            int E = acquisitions.get(delta).length;
            for (int e = 0; e < E; e++) {
                if (!ensureEqualSampleCount(delta, e)) {
                    return Double.NaN;
                }

                if (currentData.get(delta).size() > 0) {
                    double[][] sample = new double[currentData.get(delta).size()][currentData.get(delta).get(0).length];
                    minDist = Math.min(minDist, getDistance(acquisitions.get(delta)[e], currentData.get(delta).toArray(sample)));  // MIN(D[f(e), f(u)])
                }
            }
            if (!Double.isNaN(variability[delta])) {
                min += variability[delta] == 0 ? 0 : (minDist / variability[delta]);
            } else {
                skippedFeatures++;
            }
        }
        min /= currentData.size() - skippedFeatures;   // 1/δ
        return min;
    }

    private double maxAuthentication() {
        double max = 0;
        int skippedFeatures = 0;
        for (int delta = 0; delta < currentData.size(); delta++) {
            double maxDist = Double.NEGATIVE_INFINITY;
            int E = acquisitions.get(delta).length;
            for (int e = 0; e < E; e++) {
                if (!ensureEqualSampleCount(delta, e)) {
                    return Double.NaN;
                }

                if (currentData.get(delta).size() > 0) {
                    double[][] sample = new double[currentData.get(delta).size()][currentData.get(delta).get(0).length];
                    maxDist = Math.max(maxDist, getDistance(acquisitions.get(delta)[e], currentData.get(delta).toArray(sample)));  // MAX(D[f(e), f(u)])
                }
            }
            if (!Double.isNaN(variability[delta])) {
                max += variability[delta] == 0 ? 0 : (maxDist / variability[delta]);
            } else {
                skippedFeatures++;
            }
        }
        max /= currentData.size() - skippedFeatures;   // 1/δ
        return max;
    }

    private double meanAuthentication() {
        double mean = 0;
        int skippedFeatures = 0;
        for (int delta = 0; delta < currentData.size(); delta++) {
            double meanDist = 0;
            int E = acquisitions.get(delta).length;
            for (int e = 0; e < E; e++) {
                if (!ensureEqualSampleCount(delta, e)) {
                    return Double.NaN;
                }

                if (currentData.get(delta).size() > 0) {
                    double[][] sample = new double[currentData.get(delta).size()][currentData.get(delta).get(0).length];
                    meanDist += getDistance(acquisitions.get(delta)[e], currentData.get(delta).toArray(sample));  // D[f(e), f(u)]
                }
            }
            if (!Double.isNaN(variability[delta])) {
                mean += variability[delta] == 0 ? 0 : (meanDist / variability[delta]);
            } else {
                skippedFeatures++;
            }
        }
        mean /= currentData.size() - skippedFeatures;   // 1/δ
        return mean;
    }

    private double tempAuthentication() {
        double temp = 0;
        int skippedFeatures = 0;
        for (int delta = 0; delta < currentData.size(); delta++) {
            int tu = findTemplateDynamics(delta);
            if (tu == -1) { // No template found, because feature is empty
                skippedFeatures++;
                continue;
            }

            if (!ensureEqualSampleCount(delta, tu)) {
                return Double.NaN;
            }

            if (currentData.get(delta).size() > 0) {
                double[][] sample = new double[currentData.get(delta).size()][currentData.get(delta).get(0).length];
                double tempDist = getDistance(acquisitions.get(delta)[tu], currentData.get(delta).toArray(sample));
                temp += variability[delta] == 0 ? 0 : (tempDist / variability[delta]);
            }
        }
        temp /= currentData.size() - skippedFeatures;   // 1/δ
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

        if (EvaluationParams.enableTemplateSelection && !templatesLocked && index > 0) {
            try {
                Cursor c = db.query(false, StatisticalClassifierContract.StatisticalClassifierTemplates.TABLE_NAME,
                        new String[] {StatisticalClassifierContract.StatisticalClassifierTemplates._ID, StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_DATA_ID, StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_SCORE },
                        StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_CONTEXT + " = ? AND " + StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_SCREEN_ORIENTATION + " = ?",
                        new String[] { String.valueOf(manager.getBiometricsContext()), String.valueOf(screenOrientation) }, null, null, StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_SCORE, null);
                if (c.getCount() < EvaluationParams.acquisitionSetSize - 1) {
                    saveTemplate(index);
                } else if (c.getCount() == EvaluationParams.acquisitionSetSize - 1) {
                    currentTemplateID = saveTemplate(index);
                    selectTemplates(c);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // ---- BEGIN TEMPLATE SELECTION ----
    private void selectTemplates(Cursor c) {
        if (EvaluationParams.acquisitionSetSize < 2 || EvaluationParams.templateSetSize == EvaluationParams.acquisitionSetSize) return;

        switch (EvaluationParams.templateSelectionFunction) {
            case 1:
                mdistSelect(c, true);
                break;
            case 2:
                mdistSelect(c, false);
                break;
            case 3:
                gmmsSelect(c);
                break;
            case 4:
                dendSelect(c);
                break;
            case 5:
                fuzzyCMeansSelect(c);
                break;
            case 0:
            default:
                return;
        }
    }

    /**
     * Implementation of the MDIST algorithm in Uludag et. al. 2004
     * @param minSelect Whether to select templates with minimum or maximum distance
     */
    private void mdistSelect(Cursor c, boolean minSelect) {
        // Step 1: Find the pair-wise distance score between the N impressions.
        double[] distances = new double[acquisitions.get(0).length+1];
        for (int delta = 0; delta < acquisitions.size(); delta++) {
            if (acquisitions.get(delta)[0].length == 0) continue;   // Skip empty features (e.g. unavailable sensors)

            for (int i = 0; i < distances.length; i++) {
                for (int j = 0; j < distances.length; j++) {
                    if (i == j) continue;
                    if (i == distances.length - 1) {
                        double[][] sample = new double[currentData.get(delta).size()][currentData.get(delta).get(0).length];
                        distances[i] += getDistance(currentData.get(delta).toArray(sample), acquisitions.get(delta)[j]);
                    } else {
                        if (j == distances.length - 1) {
                            double[][] sample = new double[currentData.get(delta).size()][currentData.get(delta).get(0).length];
                            distances[i] += getDistance(acquisitions.get(delta)[i], currentData.get(delta).toArray(sample));
                        } else {
                            distances[i] += getDistance(acquisitions.get(delta)[i], acquisitions.get(delta)[j]);
                        }
                    }
                }
            }
        }

        // Step 2: For the jth impression, compute its average distance score, dj, with respect to the other (N-1) impressions.
        for (int i = 0; i < distances.length; i++) {
            distances[i] /= distances.length - 1;
        }

        // Step 3: Choose K impressions that have the smallest average distance scores. These constitute the template set T.
        int[] templates = new int[EvaluationParams.templateSetSize];
        for (int i = 0; i < templates.length; i++) {
            double dist = minSelect ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            for (int j = 0; j < distances.length; j++) {
                if ((minSelect && distances[j] < dist) || (!minSelect && distances[j] > dist)) {
                    dist = distances[j];
                    templates[i] = j;
                }
            }
            distances[templates[i]] = Double.NaN;
        }
        lockTemplates(c, templates);
    }

    /**
     * Implementation of the Greedy Maximum Match Scores algorithm in Li et. al. 2008
     */
    private void gmmsSelect(Cursor c) {
        // Initialize N, K, S(N×N), Choose[K]
        int N = acquisitions.get(0).length + 1, K = EvaluationParams.templateSetSize;
        int[] Choose = new int[K];

        double[][] S = new double[N][N];
        for (int delta = 0; delta < acquisitions.size(); delta++) {
            if (acquisitions.get(delta)[0].length == 0) continue;   // Skip empty features (e.g. unavailable sensors)

            for (int i = 0; i < N-1; i++) {
                for (int j = i+1; j < N-1; j++) {
                    if (j == i) continue;
                    S[i][j] += getDistance(acquisitions.get(delta)[i], acquisitions.get(delta)[j]);
                    S[j][i] = S[i][j];
                }

                if (currentData.get(delta).size() > 0) {
                    double[][] sample = new double[currentData.get(delta).size()][currentData.get(delta).get(0).length];
                    S[i][S.length - 1] += getDistance(acquisitions.get(delta)[i], currentData.get(delta).toArray(sample));
                    S[S.length - 1][i] = S[i][S.length - 1];
                }
            }
        }

        for (int i = 0; i < K; i++) {
            // Find j* where sum(j*) >= sum(j); sum(j) = SUM(m=1, N, m!=j), j=1..N
            int jStar = 0;
            double maxSumJ = 0;
            for (int j = 0; j < N; j++) {
                double sumJ = 0;
                for (int m = 0; m < N; m++) {
                    if (m == j) continue;
                    sumJ += S[j][m];
                }
                if (sumJ >= maxSumJ) {
                    jStar = j;
                    maxSumJ = sumJ;
                }
            }

            Choose[i] = jStar;
            for (int m = 0; m < N; m++) {
                S[jStar][m] = 0;
                S[m][jStar] = 0;
            }
        }
        lockTemplates(c, Choose);
    }

    private void fuzzyCMeansSelect(Cursor c) {
        List<ClusterPoint> points = new ArrayList<>(acquisitions.get(0).length+1);
        for (int delta = 0; delta < acquisitions.size(); delta++) {
            for (int i = 0; i < acquisitions.get(delta).length; i++) {
                if (delta == 0) {
                    points.add(new ClusterPoint());
                }
                points.get(i).addSamples(acquisitions.get(delta)[i]);
            }
            if (delta == 0) {
                points.add(new ClusterPoint());
            }
            points.get(points.size()-1).addSamples(currentData.get(delta));
        }

        FuzzyKMeansClusterer<ClusterPoint> clusterer = new FuzzyKMeansClusterer<>(EvaluationParams.templateSetSize, 2, -1,
                EvaluationParams.distanceFunction == 0 ? new ManhattanDistance() : new EuclideanDistance());
        List<CentroidCluster<ClusterPoint>> clusters = clusterer.cluster(points);

        int[] templates = new int[clusters.size()];
        for (int i = 0; i < templates.length; i++) {
            double minDist = Double.POSITIVE_INFINITY;
            for (ClusterPoint point : clusters.get(i).getPoints()) {
                double dist = point.distanceTo(clusterer.getDistanceMeasure(), clusters.get(i).getCenter());
                if (dist < minDist) {
                    minDist = dist;
                    templates[i] = points.indexOf(point);
                }
            }
        }
        lockTemplates(c, templates);
    }

    private void dendSelect(Cursor c) {
        // Step 1: Generate the N×N dissimilarity matrix M, where entry (i, j) (i, j∈{1..N}) is the distance score between impressions i and j
        double[][] distances = new double[acquisitions.get(0).length+1][acquisitions.get(0).length+1];
        for (int delta = 0; delta < acquisitions.size(); delta++) {
            if (acquisitions.get(delta)[0].length == 0) continue;   // Skip empty features (e.g. unavailable sensors)

            for (int i = 0; i < acquisitions.get(delta).length; i++) {
                for (int j = i+1; j < acquisitions.get(delta).length; j++) {
                    distances[i][j] += getDistance(acquisitions.get(delta)[i], acquisitions.get(delta)[j]);
                    distances[j][i] = distances[i][j];
                }

                double[][] sample = new double[currentData.get(delta).size()][currentData.get(delta).get(0).length];
                distances[i][distances.length-1] += getDistance(acquisitions.get(delta)[i], currentData.get(delta).toArray(sample));
                distances[distances.length-1][i] = distances[i][distances.length-1];
            }
        }

        String[] ids = new String[acquisitions.get(0).length+1];
        c.beforeFirst();
        for (int i = 0; i < acquisitions.get(0).length; i++) {
            ids[i] = String.valueOf(i);
        }
        ids[ids.length-1] = String.valueOf(ids.length-1);

        // Step 2: Apply the complete link clustering algorithm on M, and generate the dendrogram, D. Use the dendrogram D to identify K clusters
        ClusteringAlgorithm algorithm = new DefaultClusteringAlgorithm();
        Cluster cluster = algorithm.performClustering(distances, ids, new CompleteLinkageStrategy());

        List<List<Cluster>> cut = new ArrayList<>(EvaluationParams.acquisitionSetSize / 2), singleItems = new ArrayList<>();
        cut.add(new ArrayList<>(1));
        cut.get(0).add(cluster);
        singleItems.add(new ArrayList<>(0));
        buildClusterMap(cluster, cut, 1, singleItems);

        List<Cluster> clusters = null;
        for (List<Cluster> level : cut) {
            if (level.size() >= EvaluationParams.templateSetSize) {
                clusters = level;
                break;
            }
        }

        /* Step 3: In each of the clusters identified in step 2, select an acquisition whose average distance from the rest of the acquisitions in
           the cluster is minimum. If a cluster has only 2 acquisitions, choose any one of the two acquisitions at random */
        int[] templates = new int[EvaluationParams.templateSetSize];
        for (int i = 0; i < templates.length; i++) {
            templates[i] = getDendTemplate(clusters.get(i), distances);
        }
        lockTemplates(c, templates);
    }

    private void buildClusterMap(Cluster dendrogram, List<List<Cluster>> cut, int level, List<List<Cluster>> singleItems) {
        if (cut.size() - 1 < level || cut.get(level) == null) {
            cut.add(level, new ArrayList<>());
            singleItems.add(level, new ArrayList<>());
        }
        cut.get(level).addAll(dendrogram.getChildren());

        for (Cluster child : dendrogram.getChildren()) {
            if (child.isLeaf()) {
                singleItems.get(level).add(child);
            } else {
                buildClusterMap(child, cut, level + 1, singleItems);
            }
        }

        if (level == 1) {
            for (int i = 1; i < cut.size(); i++) {
                for (Cluster item : singleItems.get(i)) {
levels:             for (int j = i + 1; j < cut.size(); j++) {
                        if (cut.get(j).contains(item)) continue;
                        for (int k = 0; k < cut.get(j).size(); k++) {
                            Cluster other = cut.get(j).get(k);
                            if (isInParent(item, other.getParent())) {
                                cut.get(j).add(k, item);
                                continue levels;
                            }
                        }
                        cut.get(j).add(item);
                    }
                }
            }
        }
    }

    private boolean isInParent(Cluster cluster, Cluster other) {
        if (other.getParent() == null) {
            return false;
        }
        for (Cluster child : other.getParent().getChildren()) {
            if (child.equals(cluster)) {
                return true;
            }
        }
        return isInParent(cluster, other.getParent());
    }

    private int getDendTemplate(Cluster cluster, double[][] distances) {
        String index = "-1";
        if (cluster.isLeaf()) {
            index = cluster.getName();
        } else if (cluster.countLeafs() == 2) {
            index = cluster.getChildren().get((int)Math.round(Math.random())).getName();
        } else {
            List<Cluster> leafs = getLeafs(cluster);
            double minDist = Double.POSITIVE_INFINITY;
            for (int i = 0; i < leafs.size(); i++) {
                int dist = 0;
                for (Cluster leaf : leafs) {
                    dist += distances[Integer.parseInt(leafs.get(i).getName())][Integer.parseInt(leaf.getName())];
                }
                dist /= leafs.size() - 1;
                if (dist < minDist) {
                    index = leafs.get(i).getName();
                }
            }

        }
        return Integer.parseInt(index);
    }

    private List<Cluster> getLeafs(Cluster cluster) {
        List<Cluster> leafs = new ArrayList<>();
        for (Cluster child : cluster.getChildren()) {
            if (child.isLeaf()) {
                leafs.add(child);
            } else {
                leafs.addAll(getLeafs(child));
            }
        }
        return leafs;
    }

    private void lockTemplates(Cursor c, int[] templates) {
        BiometricsDbHelper db = manager.getDb();
        String[] values = new String[templates.length];
        StringBuilder sb = new StringBuilder(templates.length * 2 - 1);
        for (int i = 0; i < templates.length; i++) {
            if (sb.length() > 0) {
                sb.append(" AND ");
            }
            sb.append(StatisticalClassifierContract.StatisticalClassifierTemplates._ID + " != ?");

            if (c.getCount() <= templates[i]) {
                values[i] = String.valueOf(currentTemplateID);
                continue;
            }

            c.beforeFirst();
            for (int j = 0; j <= templates[i]; j++) {
                c.next();
            }
            try {
                values[i] = String.valueOf(c.getInt(c.getColumnIndex(StatisticalClassifierContract.StatisticalClassifierTemplates._ID)));
            } catch (SQLException e) {
                throw new IllegalArgumentException("Row ID not found in Cursor data", e);
            }
        }
        try {
            db.delete(StatisticalClassifierContract.StatisticalClassifierTemplates.TABLE_NAME, sb.toString(), values);

            BiometricsDbHelper.ContentValues dbValues = new BiometricsDbHelper.ContentValues(2);
            dbValues.put(StatisticalClassifierContract.StatisticalClassifierTemplateStatus.COLUMN_CONTEXT, manager.getBiometricsContext());
            dbValues.put(StatisticalClassifierContract.StatisticalClassifierTemplateStatus.COLUMN_SCREEN_ORIENTATION, screenOrientation);
            db.insert(StatisticalClassifierContract.StatisticalClassifierTemplateStatus.TABLE_NAME, dbValues);
        } catch (SQLException e) {
            Log.e(TAG, "Couldn't lock templates: " + e.getMessage());
        }
    }
    // ---- END TEMPLATE SELECTION ----

    private int saveTemplate(int dbId) {
        BiometricsDbHelper db = manager.getDb();
        BiometricsDbHelper.ContentValues values = new BiometricsDbHelper.ContentValues(4);
        values.put(StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_CONTEXT, manager.getBiometricsContext());
        values.put(StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_SCREEN_ORIENTATION, screenOrientation);
        values.put(StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_DATA_ID, dbId);
        values.put(StatisticalClassifierContract.StatisticalClassifierTemplates.COLUMN_SCORE, score);
        try {
            return db.insert(StatisticalClassifierContract.StatisticalClassifierTemplates.TABLE_NAME, values);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public boolean clearData() {
        BiometricsDbHelper db = manager.getDb();
        try {
            db.delete(StatisticalClassifierContract.StatisticalClassifierData.TABLE_NAME, null, null);
            if (EvaluationParams.enableTemplateSelection) {
                db.delete(StatisticalClassifierContract.StatisticalClassifierTemplates.TABLE_NAME, null, null);
                db.delete(StatisticalClassifierContract.StatisticalClassifierTemplateStatus.TABLE_NAME, null, null);
            }
        } catch (SQLException e) {
            return false;
        }
        return true;
    }
}
