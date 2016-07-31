package at.jku.fim.phonykeyboard.evaluation;

import at.jku.fim.phonykeyboard.evaluation.plot.EERPlotter;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManager;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManagerImpl;
import at.jku.fim.phonykeyboard.latin.utils.Log;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.*;

class StatisticalClassifierOptimizer {
    private static final String TAG = "StatisticalClassifierOptimizer";

    private static final int NUM_OPTIMIZATION_RUNS = 1;
    private static final boolean RANDOM_OPTIMIZATION = false;

    private static final int MAX_ACQUISITION_SET_SIZE = 99;
    private static final int MAX_TEMPLATE_SELECTION_FUNCTION = 6;
    private static final int MAX_DISTANCE_FUNCTION = 1;
    private static final int MAX_CLASSIFICATION_FUNCTION = 3;
    static final double THRESHOLD_INCREMENT = 0.01;

    private String csvFilePath;
    private String[] csvFiles;

    StatisticalClassifierOptimizer(String csvFilePath, boolean skipControlGroup) {
        this.csvFilePath = csvFilePath;
        csvFiles = new File(csvFilePath).list((dir, name) -> name.endsWith(".csv") && !name.endsWith(".old.csv") && (!skipControlGroup || !name.endsWith("cg.csv") && (!RANDOM_OPTIMIZATION || name.contains(".random."))));
    }

    int optimizeDistanceFunction() {
        int currentFunction = EvaluationParams.distanceFunction;
        int bestFunction = optimizeFunction("Distance Function", MAX_DISTANCE_FUNCTION, new ParameterProxy<Integer>() {
            @Override
            public Integer get() {
                return EvaluationParams.distanceFunction;
            }

            @Override
            public void set(Integer value) {
                EvaluationParams.distanceFunction = value;
            }

            @Override
            public String toString() {
                return EvaluationParams.distanceFunctionToString(EvaluationParams.distanceFunction);
            }
        });
        EvaluationParams.distanceFunction = currentFunction;
        return bestFunction;
    }

    int optimizeClassificationFunction() {
        int currentFunction = EvaluationParams.classificationFunction;
        int bestFunction = optimizeFunction("Classification Function", MAX_CLASSIFICATION_FUNCTION, new ParameterProxy<Integer>() {
            @Override
            public Integer get() {
                return EvaluationParams.classificationFunction;
            }

            @Override
            public void set(Integer value) {
                EvaluationParams.classificationFunction = value;
            }

            @Override
            public String toString() {
                return EvaluationParams.classificationFunctionToString(EvaluationParams.classificationFunction);
            }
        });
        EvaluationParams.classificationFunction = currentFunction;
        return bestFunction;
    }

    int optimizeAcquisitionSetSize() {
        int currentSize = EvaluationParams.acquisitionSetSize;
        int currentTemplateSize = EvaluationParams.templateSetSize;
        int bestSize = optimizeSetSize("Acquisition Set Size", MAX_ACQUISITION_SET_SIZE, new ParameterProxy<Integer>() {
            @Override
            public Integer get() {
                return EvaluationParams.acquisitionSetSize;
            }

            @Override
            public void set(Integer value) {
                EvaluationParams.acquisitionSetSize = value;
                EvaluationParams.templateSetSize = Math.min(EvaluationParams.acquisitionSetSize, currentTemplateSize);
            }
        });
        EvaluationParams.acquisitionSetSize = currentSize;
        EvaluationParams.templateSetSize = currentTemplateSize;
        return bestSize;
    }

    int optimizeTemplateSelectionFunction() {
        int currentFunction = EvaluationParams.templateSelectionFunction;
        int bestFunction = optimizeFunction("Template Selection Function", MAX_TEMPLATE_SELECTION_FUNCTION, new ParameterProxy<Integer>() {
            @Override
            public Integer get() {
                return EvaluationParams.templateSelectionFunction;
            }

            @Override
            public void set(Integer value) {
                EvaluationParams.templateSelectionFunction = value;
            }

            @Override
            public String toString() {
                return EvaluationParams.templateSelectionFunctionToString(EvaluationParams.templateSelectionFunction);
            }
        });
        EvaluationParams.templateSelectionFunction = currentFunction;
        return bestFunction;
    }

    int optimizeTemplateSetSize() {
        int currentSize = EvaluationParams.templateSetSize;
        int bestSize = optimizeSetSize("Template Set Size", EvaluationParams.acquisitionSetSize, new ParameterProxy<Integer>() {
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

    int[] optimizeTemplate() {
        int currentSize = EvaluationParams.templateSetSize;
        int currentFunction = EvaluationParams.templateSelectionFunction;
        int[] best = optimizeInts("Template Selection", 0, MAX_TEMPLATE_SELECTION_FUNCTION, 2, EvaluationParams.acquisitionSetSize, new ParameterProxy<Integer>() {
            @Override
            public Integer get() {
                return EvaluationParams.templateSelectionFunction;
            }

            @Override
            public void set(Integer value) {
                EvaluationParams.templateSelectionFunction = value;
            }

            @Override
            public String toString() {
                return EvaluationParams.templateSelectionFunctionToString(EvaluationParams.templateSelectionFunction);
            }
        }, new ParameterProxy<Integer>() {
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
        EvaluationParams.templateSelectionFunction = currentFunction;
        return best;
    }

    Set<String> optimizeSensorSet() {
        Set<String> sensorSet = new HashSet<>(BiometricsManager.SENSOR_TYPES.length);
        Collections.addAll(sensorSet, BiometricsManager.SENSOR_TYPES);
        Set<String> bestSet = optimizeSet("Sensor Set", sensorSet, new ParameterProxy<Set<String>>() {
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

    private int optimizeFunction(String title, int maxValue, ParameterProxy<Integer> proxy) {
        return optimizeInt(title, 0, maxValue, proxy, false);
    }

    private int optimizeSetSize(String title, int maxValue, ParameterProxy<Integer> proxy) {
        return optimizeInt(title, 2, maxValue, proxy, true);
    }

    private int optimizeInt(String title, int minValue, int maxValue, ParameterProxy<Integer> proxy, boolean isRange) {
        double minEER = Double.POSITIVE_INFINITY;
        int bestInt = 0;
        List<AbstractMap.SimpleEntry<Integer, Double>> rangeEers = new ArrayList<>(maxValue - minValue);
        List<AbstractMap.SimpleEntry<String, Double>> elementEers = new ArrayList<>(maxValue - minValue);
        for (int i = minValue; i <= maxValue; i++) {
            System.out.print(" ");
            System.out.print(i);
            proxy.set(i);

            double eer = processFiles();
            if (isRange) {
                rangeEers.add(new AbstractMap.SimpleEntry<>(i, eer));
            } else {
                elementEers.add(new AbstractMap.SimpleEntry<>(proxy.toString(), eer));
            }

            if (eer < minEER) {
                minEER = eer;
                bestInt = i;
            }
        }
        System.out.println();
        Log.i(TAG, String.format("Best EER: %.4f %%", minEER * 100));
        if (isRange) {
            EERPlotter.plotSize(title, rangeEers);
        } else {
            EERPlotter.plotFunction(title, elementEers);
        }
        return bestInt;
    }

    private int[] optimizeInts(String title, int min1, int max1, int min2, int max2, ParameterProxy<Integer> proxy1, ParameterProxy<Integer> proxy2) {
        double minEER = Double.POSITIVE_INFINITY;
        int[] bestInts = new int[2];
        String[] titles = new String[max1 - min1 + 1];
        List<AbstractMap.SimpleEntry<Integer, Double>>[] eers = new ArrayList[max1 - min1 + 1];
        for (int i = min1; i <= max1; i++) {
            System.out.print(" ");
            System.out.print(i);
            System.out.print("[");
            proxy1.set(i);

            titles[i - min1] = proxy1.toString();
            eers[i - min1] = new ArrayList<>(max2 - min2);
            for (int j = min2; j <= max2; j++) {
                System.out.print(" ");
                System.out.print(j);
                proxy2.set(j);
                double eer = processFiles();
                eers[i - min1].add(new AbstractMap.SimpleEntry<>(j, eer));
                if (eer < minEER) {
                    minEER = eer;
                    bestInts[0] = i;
                    bestInts[1] = j;
                }
            }
            System.out.print("]");
        }
        System.out.println();
        Log.i(TAG, String.format("Best EER with %d: %.4f %%", bestInts[0], minEER * 100));
        EERPlotter.plotSizes(title, titles, eers);
        return bestInts;
    }

    private <T> Set<T> optimizeSet(String title, Set<T> setItems, ParameterProxy<Set<T>> proxy) {
        double minEER = Double.POSITIVE_INFINITY;
        Set<T> bestSet = new HashSet<>(setItems.size());
        Set<Set<T>> powerSet = Sets.powerSet(setItems);
        List<AbstractMap.SimpleEntry<Set<T>, Double>> eers = new ArrayList<>((int)Math.pow(2, setItems.size()));
        for (Set<T> set : powerSet) {
            System.out.print(" ");
            System.out.print(powerSetToString(set));
            proxy.set(set);
            double eer = processFiles();
            eers.add(new AbstractMap.SimpleEntry<>(set, eer));
            if (eer < minEER) {
                minEER = eer;
                bestSet.clear();
                bestSet.addAll(proxy.get());
            }
        }
        System.out.println();
        Log.i(TAG, String.format("Best EER: %.4f %%", minEER * 100));
        EERPlotter.plotSet(title, eers);
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
        double eer = 0;
        for (int r = 0; r < NUM_OPTIMIZATION_RUNS; r++) {
            List<Double> p = new ArrayList<>();
            List<Double> n = new ArrayList<>();
            for (int i = 0; i < csvFiles.length; i++) {
                StatisticalClassifierEvaluation.processCsvFile(makeAbsolute(csvFiles[i]), false, RANDOM_OPTIMIZATION, score -> addScore(p, score));
                for (int j = 0; j < csvFiles.length; j++) {
                    if (j == i) continue;
                    StatisticalClassifierEvaluation.processCsvFile(makeAbsolute(csvFiles[j]), true, RANDOM_OPTIMIZATION, score -> addScore(n, score));
                }
                ((BiometricsManagerImpl) BiometricsManager.getInstance()).getClassifier().clearData();
            }
            eer += calcEER(p, n, false);
        }
        Log.setSilent(false);
        eer /= NUM_OPTIMIZATION_RUNS;
        System.out.print(String.format("(%.2f %%)", eer * 100));
        return eer;
    }

    private String makeAbsolute(String fileName) {
        return String.format("%s%s%s", csvFilePath, File.separator, fileName);
    }

    private void addScore(List<Double> scores, double score) {
        if (score != BiometricsManager.SCORE_NOT_ENOUGH_DATA && score != BiometricsManager.SCORE_CAPTURING_ERROR) {
            scores.add(score);
        }
    }

    double calcEER(List<Double> p, List<Double> n, boolean print) {
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
        if (print) {
            System.out.print(String.format("(%.2f %%)", eer * 100));
        }
        return eer;
    }

    private interface ParameterProxy<T> {
        T get();
        void set(T value);
        String toString();
    }
}
