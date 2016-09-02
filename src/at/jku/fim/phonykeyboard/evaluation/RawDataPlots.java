package at.jku.fim.phonykeyboard.evaluation;

import at.jku.fim.phonykeyboard.evaluation.plot.NDPlot;
import at.jku.fim.phonykeyboard.evaluation.plot.Plot;
import at.jku.fim.phonykeyboard.evaluation.plot.TwoDPlot;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManager;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManagerImpl;
import at.jku.fim.phonykeyboard.latin.utils.Log;
import com.google.common.base.CaseFormat;
import com.google.common.primitives.Floats;
import com.panayotis.gnuplot.style.FillStyle;
import com.panayotis.gnuplot.style.Style;

import java.io.File;
import java.util.*;

public class RawDataPlots {
    private static final boolean PLOT_TO_FILE = false;
    private static final double PLOT_SCALE = 1;
    private static final String[] SITUATIONS = new String[] { "Sitting", "Standing", "Lying", "Walking", "Moving" };
    private static final String[] INPUTMETHODS = new String[] { "RT", "LT", "BT", "RL", "LR", "O" };

    private String csvFilePath;
    private String[] csvFiles;
    private List<Acquisition>[] participants;

    RawDataPlots(String csvFilePath) {
        this.csvFilePath = csvFilePath;
        csvFiles = new File(csvFilePath).list((dir, name) -> name.endsWith(".csv") && !name.endsWith(".old.csv") && !name.contains(".random.")/* && !name.contains(".cg.csv")*/);
        participants = new List[csvFiles.length];
        Log.setSilent(true);
        for (int i = 0; i < csvFiles.length; i++) {
            participants[i] = getAcquisitions(makeAbsolute(csvFiles[i]));
        }
        Log.setSilent(false);
    }

    public void plotTimeline() {
        plotDateTimeline();
        plotTimeTimeline();
    }

    public void plotVariation() {
        plotVariation(false);
        plotVariation(true);
    }

    public void plotSensors() {
        for (int i = 0; i < BiometricsManager.SENSOR_TYPES.length; i++) {
            plotSensor(i, -1, -1);
        }
    }

    public void plotQuestionnaire() {
        NDPlot<Integer> plot = new NDPlot<>(false);
        List<List<Integer>> inputMethods = new ArrayList<>(INPUTMETHODS.length);
        for (int i = 0; i < INPUTMETHODS.length; i++) {
            inputMethods.add(new ArrayList<>(SITUATIONS.length));
            for (int j = 0; j < SITUATIONS.length; j++) {
                inputMethods.get(i).add(0);
            }
        }
        for (int i = 0; i < participants.length; i++) {
            List<Acquisition> participant = participants[i];
            for (int j = 0; j < participant.size(); j++) {
                int value = inputMethods.get(participant.get(j).getInputMethod()).get(participant.get(j).getSituation());
                inputMethods.get(participant.get(j).getInputMethod()).set(participant.get(j).getSituation(), value + 1);
            }
        }

        for (int i = 0; i < SITUATIONS.length; i++) {
            plot.addData(inputMethods);
            plot.setDataTitle(i, SITUATIONS[i]);
            plot.setDataProperty(i, "using", String.valueOf(i + 1));
            plot.getDataStyle(0).setStyle(Style.HISTOGRAMS);
            plot.getDataStyle(0).setFill(new FillStyle(FillStyle.Fill.SOLID));
        }

        plot.setTitle("Questionnaire Answers");
        plot.setProperty("style histogram", "gap 1");
        plot.setProperty("style data", "histogram");
        plot.setProperty("style fill", "solid");
        StringBuffer sb = new StringBuffer(INPUTMETHODS.length * 2 + 2);
        sb.append("(");
        for (int i = 0; i < INPUTMETHODS.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("\"%s\" %d", INPUTMETHODS[i], i));
        }
        sb.append(")");
        plot.setProperty("xtics", sb.toString());
        plot.plot(PLOT_TO_FILE ? "questionnaire" : null, PLOT_SCALE, PLOT_SCALE);
    }

    public void plotSituationSensors() {
        plotSensor(4, 0, 0);
        plotSensor(4, 2, 0);
    }

    public void plotROC() {
        List<Double> p = new ArrayList<>();
        List<Double> n = new ArrayList<>();
        evaluate(p, n);

        NDPlot<Double> plot = new NDPlot<>(false);
        List<List<Double>> roc = new ArrayList<>();
        double threshold = 0;
        int numP = 0, numN = 0;
        double zeroFAR = 0, eer = 0;
        double zeroFARThreshold = 0, zeroFRRThreshold = 0, eerThreshold = 0;
        while (numN < n.size() && numP < p.size()) {
            numP = 0;
            numN = 0;
            for (double pos : p) {
                if (pos < threshold) numP++;
            }
            for (double neg : n) {
                if (neg < threshold) numN++;
            }

            if (numP > 0 && numN == 0) {
                zeroFARThreshold = threshold;
                zeroFAR = numP / (double)p.size();
            }
            if (1 - (numP / (double)p.size()) > numN / (double)n.size()) {
                eerThreshold = threshold;
                //eer = numN / (double)n.size();
                eer = (numN / (double)n.size() + 1 - numP / (double)p.size()) / 2d;
            }
            threshold += StatisticalClassifierOptimizer.THRESHOLD_INCREMENT;

            if (numN == 0 && numP == 0) continue;
            roc.add(new ArrayList<>(2));
            roc.get(roc.size() - 1).add(numN > 0 ? numN / (double)n.size() : 0);
            roc.get(roc.size() - 1).add(numP > 0 ? numP / (double)p.size() : 0);
            roc.get(roc.size() - 1).add(threshold - StatisticalClassifierOptimizer.THRESHOLD_INCREMENT);
        }
        zeroFRRThreshold = threshold - StatisticalClassifierOptimizer.THRESHOLD_INCREMENT;
        plot.addData(roc);
        plot.getDataStyle(0).setStyle(Style.LINES);
        plot.setDataProperty(0, "using", "1:2");

        plot.setTitle("ROC");
        plot.getXAxis().setLabel("False Positive Rate");
        plot.getYAxis().setLabel("True Positive Rate");
        plot.setProperty("grid", null);
        plot.setProperty("size", "square");
        plot.setProperty("xtics", "0,0.1,1");
        plot.setProperty("ytics", "0,0.1,1");

        plot.setProperty("arrow 1", String.format(Locale.ROOT, "to 0,%1$f from 0.1,%1$f", zeroFAR));
        plot.setProperty("label 1", String.format(Locale.ROOT, "\"ZeroFAR=$%2$.2f$\" at 0.12,%1$f", zeroFAR, zeroFARThreshold));
        plot.setProperty("arrow 2", "to 1,1 from 0.9,0.9");
        plot.setProperty("label 2", String.format(Locale.ROOT, "\"ZeroFRR=$%.2f$\" at 0.9,0.9 right", zeroFRRThreshold));
        plot.setProperty("arrow 3", String.format(Locale.ROOT, "to %1$f,1-%1$f from %1$f+0.1,1-%1$f-0.1", eer));
        plot.setProperty("label 3", String.format(Locale.ROOT, "\"EER=$%2$.2f$\" at %1$f+0.12,1-%1$f-0.1", eer, eerThreshold));
        plot.plot(PLOT_TO_FILE ? "roc" : null, PLOT_SCALE, PLOT_SCALE);
    }

    private void plotDateTimeline() {
        TwoDPlot<Long, Integer> plot = new TwoDPlot<>(false);
        for (int i = 0; i < participants.length; i++) {
            List<Acquisition> participant = participants[i];
            List<AbstractMap.SimpleEntry<Long, Integer>> acquisitions = new ArrayList<>(participant.size());
            for (int j = 0; j < participant.size(); j++) {
                acquisitions.add(new AbstractMap.SimpleEntry<>(participant.get(j).getTimestamp(), j));
            }
            plot.addData(acquisitions);
            plot.setDataTitle(i, String.format("P%d", i + 1));
            plot.getDataStyle(i).setStyle(Style.FSTEPS);
            plot.setDataProperty(i, "using", "1:2");
        }
        plot.setTitle("Timeline");
        plot.getXAxis().setLabel("Date");
        plot.getYAxis().setLabel("Samples collected");
        plot.setProperty("xdata", "time");
        plot.setProperty("timefmt", "\"%s\"");
        plot.setProperty("format x", "\"%b %d\"");
        plot.plot(PLOT_TO_FILE ? "timeline" : null, PLOT_SCALE, PLOT_SCALE);
    }

    private void plotTimeTimeline() {
        TwoDPlot<Integer, Integer> plot = new TwoDPlot<>(false);
        Calendar calendar = GregorianCalendar.getInstance();
        for (int i = 0; i < participants.length; i++) {
            List<Acquisition> participant = participants[i];
            List<AbstractMap.SimpleEntry<Integer, Integer>> acquisitions = new ArrayList<>(24);
            for (int j = 0; j < 24; j++) {
                acquisitions.add(new AbstractMap.SimpleEntry<>(j, 0));
            }
            for (int j = 0; j < participant.size(); j++) {
                calendar.setTimeInMillis(participant.get(j).getTimestamp() * 1000);
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                acquisitions.get(hour).setValue(acquisitions.get(hour).getValue() + 1);
            }
            plot.addData(acquisitions);
            plot.setDataTitle(i, String.format("P%d", i + 1));
            plot.getDataStyle(i).setStyle(Style.HISTEPS);
        }
        plot.setTitle("Hourly Distribution");
        plot.getXAxis().setLabel("Hour");
        plot.getXAxis().setBoundaries(0, 24);
        plot.getYAxis().setLabel("Samples collected");
        plot.setProperty("format x", "\"%.0f:00\"");
        plot.plot(PLOT_TO_FILE ? "hours" : null, PLOT_SCALE, PLOT_SCALE);
    }

    private void plotVariation(boolean holdTime) {
        NDPlot<Float> plot = new NDPlot<>(false);
        List<List<Float>> acquisitions = new ArrayList<>(participants.length * participants[0].size());
        float minScale = Float.POSITIVE_INFINITY, maxScale = 0;
        for (int i = 0; i < participants.length; i++) {
            List<Acquisition> participant = participants[i];
            for (int j = 0; j < participant.size(); j++) {
                List<Float> distances = new ArrayList<>(participant.get(j).getKeypresses().length + 1);
                distances.add((float)i + 1);
                for (int k = 0; k < participant.get(j).getKeypresses().length; k++) {
                    float val = holdTime ? participant.get(j).getKeypresses()[k].getUpDistance() : participant.get(j).getKeypresses()[k].getDownDistance();
                    distances.add(val);
                    minScale = Math.min(minScale, val);
                    maxScale = Math.max(maxScale, val);
                }
                acquisitions.add(distances);
            }
        }
        plot.addData(acquisitions);
        plot.setDataProperty(0, "using", "1:" + (holdTime ? "2:" : "") + "3:4:5:6:7:1");
        plot.setDataProperty(0, " with", "parallel linecolor variable");

        if (holdTime) {
            plot.setTitle("Hold Time Distribution");
        } else {
            plot.setTitle("Digraph Distribution");
        }

        /*plot.setProperty("linetype 1", "linecolor rgb \"#999400d3\"");
        plot.setProperty("linetype 2", "linecolor rgb \"#99009e73\"");
        plot.setProperty("linetype 3", "linecolor rgb \"#9956b4e9\"");
        plot.setProperty("linetype 4", "linecolor rgb \"#99e69f00\"");*/
        setParticipantColors(plot);
        if (holdTime) {
            plot.setProperty("xtics", "(\"\" 1, \"2\" 2, \"l\" 3, \"i\" 4, \"r\" 5, \"a\" 6, \"7\" 7) nomirror scale 0,0");
        } else {
            plot.setProperty("xtics", "(\"\" 1, \"l\" 2, \"i\" 3, \"r\" 4, \"a\" 5, \"7\" 6) nomirror scale 0,0");
        }
        plot.setProperty("paxis 1", "tics 1 format \"P%.0f\"\nunset ytics\nunset border");
        plot.setProperty(String.format("for [j=2:%d] paxis j", (participants[0].get(0).getKeypresses().length + 1)), "tics");
        plot.setProperty(String.format("for [j=2:%d] paxis j range", (participants[0].get(0).getKeypresses().length + 1)), String.format(Locale.ROOT, "[%f:%f]", minScale, maxScale));
        plot.plot(PLOT_TO_FILE ? (holdTime ? "holdtime" : "digraph") : null, PLOT_SCALE, PLOT_SCALE);
    }

    private void plotSensor(int index, int inputMethod, int situation) {
        NDPlot<Float> plot = new NDPlot<>(true);
        for (int i = 0; i < participants.length; i++) {
            List<Acquisition> participant = participants[i];
            List<List<Float>> acquisitions = new ArrayList<>(participant.size());
            for (int j = 1; j < participant.size(); j++) {
                if (inputMethod == -1 || participant.get(j).getInputMethod() == inputMethod) {
                    if (situation == -1 || participant.get(j).getSituation() == situation) {
                        acquisitions.add(Floats.asList(participant.get(j).getKeypresses()[2].getSensorData().get(index)));
                    }
                }
            }
            plot.addData(acquisitions);
            plot.setDataTitle(i, String.format("P%d", i + 1));
            plot.getDataStyle(i).setStyle(Style.POINTS);
            plot.setDataProperty(i, "using", "1:2:3:xtic(\"\"):ytic(\"\"):ztic(\"\")");
        }
        plot.setTitle(String.format("%s Sensor", CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, BiometricsManager.SENSOR_TYPES[index])));
        setParticipantColors(plot);
        plot.getXAxis().setLabel("X");
        plot.getYAxis().setLabel("Y");
        plot.getZAxis().setLabel("Z");
        plot.setProperty("grid", "xtics ytics ztics");
        plot.setProperty("xtics", "add autofreq");
        plot.setProperty("xtics offset", "-0.5,-0.5");
        plot.setProperty("ytics", "add autofreq offset 0.5,-0.5");
        plot.setProperty("ztics", "add autofreq");
        StringBuffer sb = new StringBuffer(5);
        sb.append(BiometricsManager.SENSOR_TYPES[index].replace('_', '-'));
        if (inputMethod > -1) {
            sb.append("-");
            sb.append(INPUTMETHODS[inputMethod].toLowerCase());
        }
        if (situation > -1) {
            sb.append("-");
            sb.append(SITUATIONS[situation].toLowerCase());
        }
        plot.plot(PLOT_TO_FILE ? sb.toString() : null, PLOT_SCALE, PLOT_SCALE);
    }

    private List<Acquisition> getAcquisitions(String csvFile) {
        // Override evaluation params so that all sensors are loaded
        Collections.addAll(EvaluationParams.usedSensors, BiometricsManager.SENSOR_TYPES);

        List<Acquisition> acquisitions = new ArrayList<>(162);
        StatisticalClassifierEvaluation.processCsvFile(csvFile, acquisitions::add);
        return acquisitions;
    }

    private void evaluate(List<Double> p, List<Double> n) {
        Log.setSilent(true);
        for (int i = 0; i < csvFiles.length; i++) {
            StatisticalClassifierEvaluation.processCsvFile(makeAbsolute(csvFiles[i]), false, false, score -> addScore(p, score));
            for (int j = 0; j < csvFiles.length; j++) {
                if (j == i) continue;
                StatisticalClassifierEvaluation.processCsvFile(makeAbsolute(csvFiles[j]), true, false, score -> addScore(n, score));
            }
            ((BiometricsManagerImpl)BiometricsManager.getInstance()).getClassifier().clearData();
        }
        Log.setSilent(false);
    }

    private void addScore(List<Double> scores, double score) {
        if (score > 0) {
            scores.add(score);
        }
    }

    private String makeAbsolute(String fileName) {
        return String.format("%s%s%s", csvFilePath, File.separator, fileName);
    }

    private void setParticipantColors(Plot<?> plot) {
        /*plot.setProperty("linetype 1", "linecolor rgb \"#999400d3\"");
        plot.setProperty("linetype 2", "linecolor rgb \"#99009e73\"");
        plot.setProperty("linetype 3", "linecolor rgb \"#9956b4e9\"");
        plot.setProperty("linetype 4", "linecolor rgb \"#99e69f00\"");*/
        plot.setProperty("linetype 1", "linecolor rgb \"#990080ff\"");
        plot.setProperty("linetype 2", "linecolor rgb \"#9900a000\"");
        plot.setProperty("linetype 3", "linecolor rgb \"#99ff0000\"");
        plot.setProperty("linetype 4", "linecolor rgb \"#99eeee00\"");
    }
}
