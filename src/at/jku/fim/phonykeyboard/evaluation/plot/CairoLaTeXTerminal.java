package at.jku.fim.phonykeyboard.evaluation.plot;

import com.panayotis.gnuplot.terminal.TextFileTerminal;

public class CairoLaTeXTerminal extends TextFileTerminal {
    public CairoLaTeXTerminal() {
        this("");
    }

    public CairoLaTeXTerminal(String filename) {
        super("cairolatex", filename);
        setEPS(false);
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

    public void setEPS(boolean useEPS) {
        if (useEPS) {
            this.set("eps");
            this.unset("pdf");
        } else {
            this.set("pdf");
            this.unset("eps");
        }
    }
}
