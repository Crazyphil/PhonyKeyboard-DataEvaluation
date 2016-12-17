package at.jku.fim.phonykeyboard.evaluation;

import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class EvaluationParams {
    public static final String[] TOUCH_PROPERTIES = { "position", "size", "orientation", "pressure" };

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
    public static int templateSelectionFunction = 3;

    /**
     * Function to use for calculating the distance between samples
     */
    public static int distanceFunction = 1;

    /**
     * Function to use for calculating variability of templates and authentication score
     */
    public static int classificationFunction = 0;

    /**
     * Touch properties to use for authenticating users
     */
    public static Set<String> usedTouchProperties;
    static {
        usedTouchProperties = new HashSet<>(TOUCH_PROPERTIES.length, 1);
        usedTouchProperties.add("position");
        usedTouchProperties.add("size");
        usedTouchProperties.add("orientation");
        usedTouchProperties.add("pressure");
    }

    /**
     * Types of sensors to use for authenticating users
     */
    public static Set<String> usedSensors;
    static {
        usedSensors = new HashSet<>(BiometricsManager.SENSOR_TYPES.length, 1);
        //Collections.addAll(usedSensors, BiometricsManager.SENSOR_TYPES);
        usedSensors.add("gravity");
        usedSensors.add("accelerometer");
        //usedSensors.add("rotation_vector");
        //usedSensors.add("linear_acceleration");
        //usedSensors.add("gyroscope");
    }

    public static String templateSelectionFunctionToString(int templateSelectionFunction) {
        switch (templateSelectionFunction) {
            case 0:
                return "none";
            case 1:
                return "mdistMin";
            case 3:
                return "gmmsMin";
            case 4:
                return "gmmsMax";
            case 5:
                return "dend";
            case 6:
                return "fuzzyCMeans";
            case 2:
            default:
                return "mdistMax";
        }
    }

    public static String distanceFunctionToString(int distanceFunction) {
        switch (distanceFunction) {
            case 1:
                return "euclideanDistance";
            case 0:
            default:
                return "manhattanDistance";
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
