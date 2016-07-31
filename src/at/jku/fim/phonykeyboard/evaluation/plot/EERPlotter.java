package at.jku.fim.phonykeyboard.evaluation.plot;

import com.panayotis.gnuplot.style.FillStyle;
import com.panayotis.gnuplot.style.Style;

import java.util.*;

public class EERPlotter {
    private static final boolean PLOT_ENABLED = false;
    private static final boolean PLOT_TO_FILE = true;

    public static <T> void plotSize(String title, Collection<AbstractMap.SimpleEntry<T, Double>> data) {
        plot(title, "Size", data, false);
    }

    public static <T> void plotFunction(String title, Collection<AbstractMap.SimpleEntry<T, Double>> data) {
        plot(title, "Function", data, true);
    }

    public static <T> void plotSet(String title, Collection<AbstractMap.SimpleEntry<Set<T>, Double>> data) {
        List<AbstractMap.SimpleEntry<String, Double>> entries = new ArrayList<>(data.size());
        for (AbstractMap.SimpleEntry<Set<T>, Double> entry : data) {
            entries.add(new AbstractMap.SimpleEntry<>(setToAxisLabel(entry.getKey()), entry.getValue()));
        }
        plot(title, "Set", entries, true);
    }

    public static <T> void plotSizes(String graphName, String[] titles, Collection<AbstractMap.SimpleEntry<T, Double>>[] data) {
        plot("Size", titles, data, PLOT_TO_FILE ? graphName : null, false);
    }

    private static <T> void plot(String title, String axisLabel, Collection<AbstractMap.SimpleEntry<T, Double>> data, boolean isBoxChart) {
        plot(axisLabel, new String[] { title }, new Collection[] { data }, PLOT_TO_FILE ? title : null, isBoxChart);
    }

    private static <T> void plot(String axisLabel, String[] dataTitles, Collection<AbstractMap.SimpleEntry<T, Double>>[] data, String graphName, boolean isBoxChart) {
        if (!PLOT_ENABLED) return;

        TwoDPlot<T, Double> plot = new TwoDPlot<>(false);
        double widthMultiplier = 1;
        for (int i = 0; i < data.length; i++) {
            plot.addData(data[i]);
            plot.setDataTitle(i, dataTitles[i]);
            plot.getDataStyle(i).setStyle(isBoxChart ? Style.BOXES : (data[i].size() > 10 ? Style.LINES : Style.LINESPOINTS));
            if (isBoxChart) {
                FillStyle fill = new FillStyle(FillStyle.Fill.SOLID);
                fill.setDensity(1);
                plot.getDataStyle(i).setFill(fill);
                plot.setProperty("boxwidth", "0.9");
                plot.setProperty("xtics", "left rotate offset 0,1");
                plot.setProperty("tics", "front");
            }
            if (data.length > 10) {
                widthMultiplier = 2;
            }
        }
        plot.setTitle("EER" + (graphName != null ? " of " + graphName : (dataTitles.length == 1 ? " of " + dataTitles[0] : "")));
        plot.getYAxis().setBoundaries(0, 0.2);
        plot.getYAxis().setLabel("EER [\\%]");

        plot.getXAxis().setLabel(axisLabel);
        plot.plot(graphName != null ? graphName.toLowerCase().replace(' ', '-') : null, widthMultiplier, 1);
    }

    private static <T> String setToAxisLabel(Set<T> set) {
        Iterator<T> it = set.iterator();
        if (!it.hasNext())
            return "{}";

        StringBuilder sb = new StringBuilder();
        for (;;) {
            T t = it.next();
            if (t instanceof String) {
                String[] parts = ((String) t).split("_");
                for (int i = 0; i < parts.length; i++) {
                    if (i == 0) {
                        sb.append(parts[i].substring(0, 1).toUpperCase());
                    } else {
                        sb.append(parts[i].substring(0, 1));
                    }
                    if (sb.charAt(sb.length() - 1) == 'G') {
                        sb.append(parts[i].substring(1, 2));
                    }
                }
            } else {
                sb.append(t);
            }
            if (!it.hasNext()) {
                return sb.toString();
            }
        }
    }
}
