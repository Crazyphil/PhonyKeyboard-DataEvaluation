package at.jku.fim.phonykeyboard.latin.biometrics.data;

import com.google.common.primitives.Doubles;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.distance.DistanceMeasure;

import java.util.LinkedList;
import java.util.List;

/**
 * This class is used as a datapoint for clustering algorithms. It flattens the feature, sample and value arrays of an acquisition to a single-dimensional array.
 */
public class ClusterPoint implements Clusterable {
    List<Double> dataList;

    public ClusterPoint() {
        dataList = new LinkedList<>();
    }

    public void addSamples(double[][] row) {
        for (double[] sample : row) {
            for (double value : sample) {
                dataList.add(value);
            }
        }
    }

    public void addSamples(List<double[]> row) {
        for (double[] sample : row) {
            for (double value : sample) {
                dataList.add(value);
            }
        }
    }

    @Override
    public double[] getPoint() {
        return Doubles.toArray(dataList);
    }

    public double distanceTo(DistanceMeasure measure, Clusterable other) {
        return measure.compute(getPoint(), other.getPoint());
    }
}
