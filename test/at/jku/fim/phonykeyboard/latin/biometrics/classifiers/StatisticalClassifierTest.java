package at.jku.fim.phonykeyboard.latin.biometrics.classifiers;

import at.jku.fim.phonykeyboard.evaluation.EvaluationParams;
import at.jku.fim.phonykeyboard.evaluation.Keypress;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsEntry;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManager;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManagerImpl;
import org.junit.*;

import java.lang.reflect.Field;
import java.util.Random;

import static org.junit.Assert.*;

public class StatisticalClassifierTest {
    private static final int NUM_TEST_KEYPRESSES = 10;
    private static final int VARIABILITY_INDEX_POSITION = 5, VARIABILITY_INDEX_DOWNDOWN = 0;

    private BiometricsManagerImpl manager;
    private StatisticalClassifier classifier;

    /**
     * Sets up the test fixture.
     * (Called before every test case method.)
     */
    @Before
    public void setUp() {
        manager = new BiometricsManagerImpl();
        manager.init();

        EvaluationParams.acquisitionSetSize = 10;
        EvaluationParams.templateSetSize = 10;
        EvaluationParams.enableTemplateSelection = true;
        EvaluationParams.templateSelectionFunction = 0;
        EvaluationParams.distanceFunction = 0;
        EvaluationParams.classificationFunction = 2;

        classifier = (StatisticalClassifier)manager.getClassifier();
        classifier.onCreate();
    }

    /**
     * Tears down the test fixture.
     * (Called after every test case method.)
     */
    @After
    public void tearDown() {
        classifier.onDestroy();
    }

    @Test
    public void testZeroResponseZero() {
        Keypress[] keypresses = createKeypresses(0);
        for (int i = 0; i < 100; i++) {
            double score = calcScore(i, 0, keypresses);
            if (i < EvaluationParams.acquisitionSetSize) {
                assertEquals(BiometricsManager.SCORE_NOT_ENOUGH_DATA, score, 0);
            } else {
                assertEquals(0, score, 0);
            }
        }
    }

    @Test
    public void testZeroResponseRandom() {
        Keypress[] keypresses = createRandomKeypresses();
        for (int i = 0; i < 100; i++) {
            double score = calcScore(i * 1000, 0, keypresses);
            if (i < EvaluationParams.acquisitionSetSize) {
                assertEquals(BiometricsManager.SCORE_NOT_ENOUGH_DATA, score, 0);
            } else {
                assertEquals(0, score, 0);
            }
        }
    }

    @Test
    public void testResponseNonZero() {
        for (int i = 0; i < 100; i++) {
            double score = calcScore(i, 0, createRandomKeypresses());

            if (i < EvaluationParams.acquisitionSetSize) {
                assertEquals(BiometricsManager.SCORE_NOT_ENOUGH_DATA, score, 0);
            } else {
                assertNotEquals(0, score, 0);
            }
        }
    }

    @Test
    public void testVariabilityMin() {
        EvaluationParams.classificationFunction = 0;

        for (int i = 0; i <= 10; i++) {
            calcScore(i, 0, createKeypresses(i));
        }

        double[] variabilities = getVariabilities();
        for (int i = 0; i < variabilities.length; i++) {
            if (i == VARIABILITY_INDEX_DOWNDOWN) {
                assertEquals(0, variabilities[i], 0);
            } else if (i == VARIABILITY_INDEX_POSITION) {
                assertEquals(2, variabilities[i], 0);
            } else if (i > VARIABILITY_INDEX_POSITION){
                assertEquals(3, variabilities[i], 0);
            } else {
                assertEquals(1, variabilities[i], 0);
            }
        }
    }

    @Test
    public void testVariabilityMax() {
        EvaluationParams.classificationFunction = 1;

        for (int i = 0; i <= 10; i++) {
            calcScore(i, 0, createKeypresses(i < 5 ? 0 : 10));
        }

        double[] variabilities = getVariabilities();
        for (int i = 0; i < variabilities.length; i++) {
            if (i == VARIABILITY_INDEX_DOWNDOWN) {
                assertEquals(0, variabilities[i], 0);
            } else if (i == VARIABILITY_INDEX_POSITION) {
                assertEquals(2 * 10, variabilities[i], 0);
            } else if (i > VARIABILITY_INDEX_POSITION){
                assertEquals(3 * 10, variabilities[i], 0);
            } else {
                assertEquals(1 * 10, variabilities[i], 0);
            }
        }
    }

    @Test
    public void testVariabilityMean() {
        EvaluationParams.classificationFunction = 2;
        EvaluationParams.acquisitionSetSize = 11;

        for (int i = 0; i <= 11; i++) {
            calcScore(i, 0, createKeypresses(i+1));
        }

        double[] variabilities = getVariabilities();
        for (int i = 0; i < variabilities.length; i++) {
            if (i == VARIABILITY_INDEX_DOWNDOWN) {
                assertEquals(0, variabilities[i], 0);
            } else if (i == VARIABILITY_INDEX_POSITION) {
                assertEquals(2 * 4, variabilities[i], 0);
            } else if (i > VARIABILITY_INDEX_POSITION){
                assertEquals(3 * 4, variabilities[i], 0);
            } else {
                assertEquals(1 * 4, variabilities[i], 0);
            }
        }
    }

    @Test
    public void testVariabilityTemp() {
        EvaluationParams.classificationFunction = 3;

        for (int i = 0; i <= 10; i++) {
            calcScore(i, 0, createKeypresses(i));
        }

        double[] variabilities = getVariabilities();
        for (int i = 0; i < variabilities.length; i++) {
            if (i == VARIABILITY_INDEX_DOWNDOWN) {
                assertEquals(0, variabilities[i], 0);
            } else if (i == VARIABILITY_INDEX_POSITION) {
                assertEquals(2 * 5 / 2f, variabilities[i], 0);
            } else if (i > VARIABILITY_INDEX_POSITION){
                assertEquals(3 * 5 / 2f, variabilities[i], 0);
            } else {
                assertEquals(1 * 5 / 2f, variabilities[i], 0);
            }
        }
    }

    private Keypress[] createRandomKeypresses() {
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
            for (String SENSOR_TYPE : BiometricsManager.SENSOR_TYPES) {
                keypresses[i].addSensorData(new float[] { sensorX[i], sensorY[i], sensorZ[i] });
            }
        }
        return keypresses;
    }

    private Keypress[] createKeypresses(float value) {
        Keypress[] keypresses = new Keypress[NUM_TEST_KEYPRESSES];

        float[] sensorX = new float[NUM_TEST_KEYPRESSES];
        float[] sensorY = new float[NUM_TEST_KEYPRESSES];
        float[] sensorZ = new float[NUM_TEST_KEYPRESSES];
        for (int i = 0; i < NUM_TEST_KEYPRESSES; i++) {
            sensorX[i] = value;
            sensorY[i] = value;
            sensorZ[i] = value;
        }

        for (int i = 0; i < NUM_TEST_KEYPRESSES; i++) {
            keypresses[i] = new Keypress(value, value, value, value, value, value, value, BiometricsManager.SENSOR_TYPES.length);
            for (String SENSOR_TYPE : BiometricsManager.SENSOR_TYPES) {
                keypresses[i].addSensorData(new float[] { sensorX[i], sensorY[i], sensorZ[i] });
            }
        }
        return keypresses;
    }

    private double calcScore(long timestamp, int screenOrientation, Keypress[] keypresses) {
        manager.setScreenOrientation(screenOrientation);

        classifier.onStartInput(manager.getBiometricsContext(), false);
        long entryTimestamp = timestamp;
        int sensorCount = keypresses[0].getSensorData().get(0).length;
        for (int i = 0; i < keypresses.length; i++) {
            Keypress keypress = keypresses[i];
            //entryTimestamp += keypress.getDownDistance();
            entryTimestamp += 1;
            for (int downOrUp = 0; downOrUp <= 1; downOrUp++) {
                BiometricsEntry entry = new BiometricsEntry(sensorCount);
                entry.setProperties(i, downOrUp, entryTimestamp + (int)keypress.getUpDistance() * downOrUp, keypress.getX(), keypress.getY(), keypress.getSize(), keypress.getOrientation(), keypress.getPressure(), screenOrientation);
                entry.setSensorData(keypress.getSensorData());
                classifier.onKeyEvent(entry);
            }
        }
        classifier.onFinishInput(true);
        return classifier.getScore();
    }

    private double[] getVariabilities() {
        Class<?> c = classifier.getClass();
        Field f = null;
        try {
            f = c.getDeclaredField("variability");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        f.setAccessible(true);
        Object variability = new Object();
        try {
            variability = f.get(classifier);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return (double[])variability;
    }
}
