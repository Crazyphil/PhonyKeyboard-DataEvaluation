package at.jku.fim.phonykeyboard.evaluation;

import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManager;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManagerImpl;
import at.jku.fim.phonykeyboard.latin.utils.Log;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.*;

class StatisticalClassifierOptimizer {
    private static final String TAG = "StatisticalClassifierOptimizer";

    private static final int MAX_ACQUISITION_SET_SIZE = 99;
    private static final int MAX_TEMPLATE_SELECTION_FUNCTION = 5;
    private static final int MAX_DISTANCE_FUNCTION = 1;
    private static final int MAX_CLASSIFICATION_FUNCTION = 3;
    private static final double THRESHOLD_INCREMENT = 0.01;

    private String csvFilePath;
    private String[] csvFiles;

    StatisticalClassifierOptimizer(String csvFilePath, boolean skipControlGroup) {
        this.csvFilePath = csvFilePath;
        csvFiles = new File(csvFilePath).list((dir, name) -> name.endsWith(".csv") && !name.endsWith(".old.csv") && (!skipControlGroup || !name.endsWith("cg.csv")));
    }

    int optimizeDistanceFunction() {
        int currentFunction = EvaluationParams.distanceFunction;
        int bestFunction = optimizeFunction(MAX_DISTANCE_FUNCTION, new ParameterProxy<Integer>() {
            @Override
            public Integer get() {
                return EvaluationParams.distanceFunction;
            }

            @Override
            public void set(Integer value) {
                EvaluationParams.distanceFunction = value;
            }
        });
        EvaluationParams.distanceFunction = currentFunction;
        return bestFunction;
    }

    int optimizeClassificationFunction() {
        int currentFunction = EvaluationParams.classificationFunction;
        int bestFunction = optimizeFunction(MAX_CLASSIFICATION_FUNCTION, new ParameterProxy<Integer>() {
            @Override
            public Integer get() {
                return EvaluationParams.classificationFunction;
            }

            @Override
            public void set(Integer value) {
                EvaluationParams.classificationFunction = value;
            }
        });
        EvaluationParams.classificationFunction = currentFunction;
        return bestFunction;
    }

    int optimizeAcquisitionSetSize() {
        int currentSize = EvaluationParams.acquisitionSetSize;
        int bestSize = optimizeSetSize(MAX_ACQUISITION_SET_SIZE, new ParameterProxy<Integer>() {
            @Override
            public Integer get() {
                return EvaluationParams.acquisitionSetSize;
            }

            @Override
            public void set(Integer value) {
                EvaluationParams.acquisitionSetSize = value;
            }
        });
        EvaluationParams.acquisitionSetSize = currentSize;
        return bestSize;
    }

    int optimizeTemplateSelectionFunction() {
        int currentFunction = EvaluationParams.templateSelectionFunction;
        int bestFunction = optimizeFunction(MAX_TEMPLATE_SELECTION_FUNCTION, new ParameterProxy<Integer>() {
            @Override
            public Integer get() {
                return EvaluationParams.templateSelectionFunction;
            }

            @Override
            public void set(Integer value) {
                EvaluationParams.templateSelectionFunction = value;
            }
        });
        EvaluationParams.templateSelectionFunction = currentFunction;
        return bestFunction;
    }

    int optimizeTemplateSetSize() {
        int currentSize = EvaluationParams.templateSetSize;
        int bestSize = optimizeSetSize(EvaluationParams.acquisitionSetSize, new ParameterProxy<Integer>() {
            @Override
            public Integer get() {
                return EvaluationParams.templateSetSize;
            }

            @Override
            public void set(Integer value) {
                EvaluationParams.templateSetSize = value;
            }
        });
        EvaluationParams.templateSetSize = currentSize;
        return bestSize;
    }

    Set<String> optimizeSensorSet() {
        Set<String> sensorSet = new HashSet<>(BiometricsManager.SENSOR_TYPES.length);
        Collections.addAll(sensorSet, BiometricsManager.SENSOR_TYPES);
        Set<String> bestSet = optimizeSet(sensorSet, new ParameterProxy<Set<String>>() {
            @Override
            public Set<String> get() {
                return EvaluationParams.usedSensors;
            }

            @Override
            public void set(Set<String> value) {
                EvaluationParams.usedSensors = value;
            }
        });
        return bestSet;
    }

    private int optimizeFunction(int maxValue, ParameterProxy<Integer> proxy) {
        return optimizeInt(0, maxValue, proxy);
    }

    private int optimizeSetSize(int maxValue, ParameterProxy<Integer> proxy) {
        return optimizeInt(1, maxValue, proxy);
    }

    private int optimizeInt(int minValue, int maxValue, ParameterProxy<Integer> proxy) {
        double minEER = Double.POSITIVE_INFINITY;
        int bestInt = 0;
        for (int i = minValue; i <= maxValue; i++) {
            System.out.print(" ");
            System.out.print(i);
            proxy.set(i);
            double eer = processFiles();
            if (eer < minEER) {
                minEER = eer;
                bestInt = i;
            }
        }
        System.out.println();
        Log.i(TAG, String.format("Best EER: %.4f %%", minEER * 100));
        return bestInt;
    }

    private <T> Set<T> optimizeSet(Set<T> setItems, ParameterProxy<Set<T>> proxy) {
        double minEER = Double.POSITIVE_INFINITY;
        Set<T> bestSet = new HashSet<>(setItems.size());
        Set<Set<T>> powerSet = Sets.powerSet(setItems);
        for (Set<T> set : powerSet) {
            System.out.print(" ");
            System.out.print(powerSetToString(set));
            proxy.set(set);
            double eer = processFiles();
            if (eer < minEER) {
                minEER = eer;
                bestSet.clear();
                bestSet.addAll(proxy.get());
            }
        }
        System.out.println();
        Log.i(TAG, String.format("Best EER: %.4f %%", minEER * 100));
        return bestSet;
    }

    private <T> String powerSetToString(Set<T> set) {
        Iterator<T> it = set.iterator();
        if (!it.hasNext())
            return "[]";

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (;;) {
            T t = it.next();
            sb.append(t);
            if (!it.hasNext())
                return sb.append(']').toString();
            sb.append(',');
        }
    }

    private double processFiles() {
        Log.setSilent(true);
        List<Double> p = new ArrayList<>();
        List<Double> n = new ArrayList<>();
        for (int i = 0; i < csvFiles.length; i++) {
            StatisticalClassifierEvaluation.processCsvFile(makeAbsolute(csvFiles[i]), false, score -> addScore(p, score));
            for (int j = 0; j < csvFiles.length; j++) {
                if (j == i) continue;
                StatisticalClassifierEvaluation.processCsvFile(makeAbsolute(csvFiles[j]), true, score -> addScore(n, score));
            }
            ((BiometricsManagerImpl)BiometricsManager.getInstance()).getClassifier().clearData();
        }
        Log.setSilent(false);
        return calcEER(p, n);
    }

    private String makeAbsolute(String fileName) {
        return String.format("%s%s%s", csvFilePath, File.separator, fileName);
    }

    private void addScore(List<Double> scores, double score) {
        if (score != BiometricsManager.SCORE_NOT_ENOUGH_DATA && score != BiometricsManager.SCORE_CAPTURING_ERROR) {
            scores.add(score);
        }
    }

    double calcEER(List<Double> p, List<Double> n) {
        double threshold = 0;
        int numP = 0, numN = 0;
        while (numN < p.size() - numP && numN < n.size()) {
            numP = 0;
            numN = 0;
            for (double pos : p) {
                if (pos < threshold) numP++;
            }
            for (double neg : n) {
                if (neg < threshold) numN++;
            }
            threshold += THRESHOLD_INCREMENT;
        }

        double eer = numN / (double)n.size();
        System.out.print(String.format("(%.2f %%)", eer * 100));
        return eer;
    }

    private interface ParameterProxy<T> {
        T get();
        void set(T value);
    }
}
