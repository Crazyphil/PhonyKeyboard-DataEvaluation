package at.jku.fim.phonykeyboard.evaluation.plot;

import com.panayotis.gnuplot.JavaPlot;
import com.panayotis.gnuplot.plot.AbstractPlot;
import com.panayotis.gnuplot.plot.Axis;
import com.panayotis.gnuplot.style.PlotStyle;
import com.panayotis.gnuplot.swing.JPlot;
import com.panayotis.gnuplot.terminal.ImageTerminal;
import com.panayotis.gnuplot.utils.Debug;

import javax.swing.*;
import java.io.File;
import java.util.*;

public class TwoDPlot<XType, YType> {
    private JavaPlot plotter;
    private List<DataSetProxy<XType, YType>> data;

    public TwoDPlot() {
        if (System.getProperty("os.name").startsWith("Windows")) {
            plotter = new JavaPlot("./gnuplot/bin/gnuplot.exe", false);
        } else {
            plotter = new JavaPlot(false);
        }
        plotter.set("grid", "ytics");
        data = new ArrayList<>();
    }

    public String getTitle() {
        return plotter.getParameters().get("title");
    }

    public void setTitle(String title) {
        plotter.setTitle(title);
    }

    public Axis getXAxis() {
        return plotter.getAxis("x");
    }

    public Axis getYAxis() {
        return plotter.getAxis("y");
    }

    public int addData(Collection<AbstractMap.SimpleEntry<XType, YType>> points) {
        DataSetProxy<XType, YType> proxy = new DataSetProxy<>(new ArrayList<>(points));
        data.add(proxy);
        plotter.addPlot(proxy);
        if (proxy.hasLabels()) {
            ((AbstractPlot)plotter.getPlots().get(data.size() - 1)).set("using", "2:xticlabels(1)");
        }
        return data.size() - 1;
    }

    public List<AbstractMap.SimpleEntry<XType, YType>> getData(int i) {
        return data.get(i).getList();
    }

    public String getDataTitle(int i) {
        return ((AbstractPlot)plotter.getPlots().get(i)).get("title");
    }

    public void setDataTitle(int i, String title) {
        ((AbstractPlot)plotter.getPlots().get(i)).setTitle(title);
    }

    public PlotStyle getDataStyle(int i) {
        return ((AbstractPlot)plotter.getPlots().get(i)).getPlotStyle();
    }

    public void setProperty(String key, String value) {
        plotter.set(key, value);
    }

    public void plot(String filename) {
        int maxSize = 0;
        for (DataSetProxy<?, ?> dataset : data) {
            maxSize = Math.max(maxSize, dataset.size());
        }

        JPlot jplot = null;
        if (filename != null) {
            File plotDir = new File("./plots/");
            plotDir.mkdir();
            EPSLaTeXTerminal epsTerm = new EPSLaTeXTerminal(new File(plotDir, filename + ".eps").getAbsolutePath());
            if (maxSize > 10) {
                epsTerm.set("size", "5*2,3.5");
            }
            plotter.setTerminal(epsTerm);
        } else {
            jplot = new JPlot(plotter);
            if (maxSize > 10) {
                ((ImageTerminal)plotter.getTerminal()).set("size", "640*2,480");
            }
        }

        if (data.size() == 1) {
            plotter.setKey(JavaPlot.Key.OFF);
        }

        plotter.getDebugger().setLevel(Debug.VERBOSE);
        plotter.plot();

        if (filename == null) {
            JFrame f = new JFrame();
            f.getContentPane().add(jplot);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.setVisible(true);
        }
    }
}
