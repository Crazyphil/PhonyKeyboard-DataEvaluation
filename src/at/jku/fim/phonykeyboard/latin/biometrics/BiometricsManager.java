package at.jku.fim.phonykeyboard.latin.biometrics;

public abstract class BiometricsManager {
    public static final String[] SENSOR_TYPES = { "gyroscope_uncalibrated", "gravity", "accelerometer", "gyroscope", "rotation_vector", "linear_acceleration" };
    public static final double SCORE_NOT_ENOUGH_DATA = -1, SCORE_CAPTURING_ERROR = -2;

    private static BiometricsManager instance;

    private int screenOrientation;

    public void init() {
        instance = this;
    }

    public static BiometricsManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Must init() before getting instance");
        }
        return instance;
    }

    public void setScreenOrientation(int orientation) {
        screenOrientation = orientation;
    }

    public int getScreenOrientation() {
        return screenOrientation;
    }

    public CharSequence getInputText() {
        return "";
    }
}
