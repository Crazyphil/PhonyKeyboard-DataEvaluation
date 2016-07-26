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

public abstract class Plot<DataType> {
    protected JavaPlot plotter;

    public Plot(boolean is3D) {
        if (System.getProperty("os.name").startsWith("Windows")) {
            plotter = new JavaPlot("./gnuplot/bin/gnuplot.exe", is3D);
        } else {
            plotter = new JavaPlot(is3D);
        }
        plotter.set("grid", "ytics");
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

    public Axis getZAxis() {
        return plotter.getAxis("z");
    }

    public abstract int addData(Collection<DataType> points);

    public void clearData() {
        plotter.getParameters().getPlots().clear();
    }

    public abstract List<DataType> getData(int i);

    public String getDataTitle(int i) {
        return ((AbstractPlot)plotter.getPlots().get(i)).get("title");
    }

    public void setDataTitle(int i, String title) {
        ((AbstractPlot)plotter.getPlots().get(i)).setTitle(title);
    }

    public PlotStyle getDataStyle(int i) {
        return ((AbstractPlot)plotter.getPlots().get(i)).getPlotStyle();
    }

    public void setDataProperty(int i, String key, String value) {
        ((AbstractPlot)plotter.getPlots().get(i)).set(key, value);
    }

    public void setProperty(String key, String value) {
        plotter.set(key, value);
    }

    public void plot(String filename, double widthMultiplier, double heightMultiplier) {
        JPlot jplot = null;
        if (filename != null) {
            File plotDir = new File("./plots/");
            plotDir.mkdir();
            CairoLaTeXTerminal epsTerm = new CairoLaTeXTerminal(new File(plotDir, filename + ".tex").getAbsolutePath().replace('\\', '/'));
            epsTerm.set("size", String.format(Locale.ENGLISH, "5*%f,3.5*%f", widthMultiplier, heightMultiplier));
            plotter.setTerminal(epsTerm);
        } else {
            jplot = new JPlot(plotter);
            ((ImageTerminal)plotter.getTerminal()).set("size", String.format(Locale.ENGLISH, "640*%f,480*%f", widthMultiplier, heightMultiplier));
        }

        if (plotter.getPlots().size() == 1) {
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
