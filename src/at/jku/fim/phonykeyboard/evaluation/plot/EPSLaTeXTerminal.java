package at.jku.fim.phonykeyboard.evaluation.plot;

import com.panayotis.gnuplot.terminal.TextFileTerminal;

public class EPSLaTeXTerminal extends TextFileTerminal {
    public EPSLaTeXTerminal() {
        this("");
    }

    public EPSLaTeXTerminal(String filename) {
        super("epslatex", filename);
        setColor(true);
    }

    public void setStandalone(boolean standalone) {
        if(standalone) {
            this.set("standalone");
        } else {
            this.unset("standalone");
        }
    }

    public void setColor(boolean color) {
        if (color) {
            this.set("color");
            this.unset("monochrome");
        } else {
            this.set("monochrome");
            this.unset("color");
        }
    }
}
