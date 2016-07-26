package at.jku.fim.phonykeyboard.evaluation;

import at.jku.fim.phonykeyboard.evaluation.plot.NDPlot;
import at.jku.fim.phonykeyboard.evaluation.plot.TwoDPlot;
import at.jku.fim.phonykeyboard.latin.utils.Log;
import com.google.common.primitives.Floats;
import com.panayotis.gnuplot.style.Style;

import java.io.File;
import java.util.*;

public class RawDataPlots {
    private static final boolean PLOT_TO_FILE = false;

    private String csvFilePath;
    private String[] csvFiles;
    private List<Acquisition>[] participants;

    RawDataPlots(String csvFilePath) {
        this.csvFilePath = csvFilePath;
        csvFiles = new File(csvFilePath).list((dir, name) -> name.endsWith(".csv") && !name.endsWith(".old.csv") && !name.contains(".random."));
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

    public void plotGravity() {
        NDPlot<Float> plot = new NDPlot<>(true);
        for (int i = 0; i < participants.length; i++) {
            List<Acquisition> participant = participants[i];
            List<List<Float>> acquisitions = new ArrayList<>(participant.size());
            for (int j = 0; j < participant.size(); j++) {
                acquisitions.add(Floats.asList(participant.get(j).getKeypresses()[2].getSensorData().get(0)));
            }
            plot.addData(acquisitions);
            plot.setDataTitle(i, String.format("P%d", i + 1));
            plot.getDataStyle(i).setStyle(Style.POINTS);
        }
        plot.setTitle("Gravity Sensor");
        plot.getXAxis().setLabel("X");
        plot.getYAxis().setLabel("Y");
        plot.getZAxis().setLabel("Z");
        plot.plot(PLOT_TO_FILE ? "gravity" : null, 1, 1);
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
        plot.plot(PLOT_TO_FILE ? "timeline" : null, 1, 1);
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
        plot.plot(PLOT_TO_FILE ? "hours" : null, 1, 1);
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

        plot.setProperty("linetype 1", "linecolor rgb \"#999400d3\"");
        plot.setProperty("linetype 2", "linecolor rgb \"#99009e73\"");
        plot.setProperty("linetype 3", "linecolor rgb \"#9956b4e9\"");
        plot.setProperty("linetype 4", "linecolor rgb \"#99e69f00\"");
        if (holdTime) {
            plot.setProperty("xtics", "(\"\" 1, \"2\" 2, \"l\" 3, \"i\" 4, \"r\" 5, \"a\" 6, \"7\" 7) nomirror scale 0,0");
        } else {
            plot.setProperty("xtics", "(\"\" 1, \"l\" 2, \"i\" 3, \"r\" 4, \"a\" 5, \"7\" 6) nomirror scale 0,0");
        }
        plot.setProperty("paxis 1", "tics 1 format \"P%.0f\"\nunset ytics\nunset border");
        plot.setProperty(String.format("for [i=2:%d] paxis i", (participants[0].get(0).getKeypresses().length + 1)), "tics");
        plot.setProperty(String.format("for [i=2:%d] paxis i range", (participants[0].get(0).getKeypresses().length + 1)), String.format(Locale.ENGLISH, "[%f:%f]", minScale, maxScale));
        plot.plot(PLOT_TO_FILE ? (holdTime ? "holdtime" : "digraph") : null, 1, 1);
    }

    private List<Acquisition> getAcquisitions(String csvFile) {
        List<Acquisition> acquisitions = new ArrayList<>(162);
        StatisticalClassifierEvaluation.processCsvFile(csvFile, acquisitions::add);
        return acquisitions;
    }

    private String makeAbsolute(String fileName) {
        return String.format("%s%s%s", csvFilePath, File.separator, fileName);
    }
}
