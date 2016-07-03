package at.jku.fim.phonykeyboard.evaluation;

import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class EvaluationParams {
    /**
     * Number of templates to acquire before selecting the best (>= templateSetSize)
     */
    public static int acquisitionSetSize = 10;

    /**
     * Number of templates to use for authentication (after selection)
     */
    public static int templateSetSize = 7;

    /**
     * Enables template selection
     */
    public static boolean enableTemplateSelection = true;

    /**
     * Function to use for selecting the best templates
     */
    public static int templateSelectionFunction = 2;

    /**
     * Function to use for calculating the distance between samples
     */
    public static int distanceFunction = 0;

    /**
     * Function to use for calculating variability of templates and authentication score
     */
    public static int classificationFunction = 2;

    /**
     * Types of sensors to use for authenticating users
     */
    public static Set<String> usedSensors;
    static {
        usedSensors = new HashSet<>(BiometricsManager.SENSOR_TYPES.length);
        Collections.addAll(usedSensors, BiometricsManager.SENSOR_TYPES);
    }

    public static String templateSelectionFunctionToString(int templateSelectionFunction) {
        switch (templateSelectionFunction) {
            case 0:
                return "none";
            case 1:
                return "mdistMin";
            case 3:
                return "gmms";
            case 4:
                return "dend";
            case 5:
                return "fuzzyCMeans";
            case 2:
            default:
                return "mdistMax";
        }
    }

    public static String distanceFunctionToString(int distanceFunction) {
        switch (distanceFunction) {
            case 1:
                return "manhattanDistance";
            case 0:
            default:
                return "euclideanDistance";
        }
    }

    public static String classificationFunctionToString(int classificationFunction) {
        switch (classificationFunction) {
            case 0:
                return "min";
            case 1:
                return "max";
            case 3:
                return "temp";
            case 2:
            default:
                return "mean";
        }
    }
}
