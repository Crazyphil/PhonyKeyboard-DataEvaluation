package at.jku.fim.phonykeyboard.evaluation;

public class EvaluationParams {
    /**
     * Number of templates to acquire before selecting the best (>= templateSetSize)
     */
    public static int acquisitionSetSize = 10;

    /**
     * Number of templates to use for authentication (after selection)
     */
    public static int templateSetSize = 10;

    /**
     * Enables template selection
     */
    public static boolean enableTemplateSelection = true;

    /**
     * Function to use for selecting the best templates
     */
    public static int templateSelectionFunction = 0;

    /**
     * Function to use for calculating the distance between samples
     */
    public static int distanceFunction = 0;

    /**
     * Function to use for calculating variability of templates and authentication score
     */
    public static int classificationFunction = 2;
}
