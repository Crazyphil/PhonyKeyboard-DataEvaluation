package at.jku.fim.phonykeyboard.latin.biometrics;


import at.jku.fim.phonykeyboard.latin.biometrics.classifiers.Classifier;
import at.jku.fim.phonykeyboard.latin.biometrics.classifiers.StatisticalClassifier;
import at.jku.fim.phonykeyboard.latin.biometrics.data.BiometricsDbHelper;

public class BiometricsManagerImpl extends BiometricsManager {
    private Classifier classifier;
    private BiometricsDbHelper dbHelper;

    @Override
    public void init() {
        super.init();

        classifier = new StatisticalClassifier(this);
        dbHelper = new BiometricsDbHelper(classifier.getDatabaseContract());
    }

    public BiometricsDbHelper getDb() {
        return dbHelper;
    }

    public Classifier getClassifier() {
        return classifier;
    }

    public long getBiometricsContext() {
        return 0;
    }
}
