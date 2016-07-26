package at.jku.fim.phonykeyboard.evaluation.plot;

import com.panayotis.gnuplot.dataset.DataSet;
import com.panayotis.gnuplot.plot.AbstractPlot;

import java.util.*;

public class TwoDPlot<XType, YType> extends Plot<AbstractMap.SimpleEntry<XType, YType>> {
    private List<TwoDDataSetProxy<XType, YType>> data;

    public TwoDPlot(boolean is3D) {
        super(is3D);
        data = new ArrayList<>();
    }

    public int addData(Collection<AbstractMap.SimpleEntry<XType, YType>> points) {
        TwoDDataSetProxy<XType, YType> proxy = new TwoDDataSetProxy<>(new ArrayList<>(points));
        data.add(proxy);
        plotter.addPlot(proxy);
        if (proxy.hasLabels()) {
            ((AbstractPlot)plotter.getPlots().get(data.size() - 1)).set("using", "2:xticlabels(1)");
        }
        return data.size() - 1;
    }

    public void clearData() {
        super.clearData();
        data.clear();
    }

    public List<AbstractMap.SimpleEntry<XType, YType>> getData(int i) {
        return data.get(i).getList();
    }

    private class TwoDDataSetProxy<XType, YType> extends DataSetProxy<AbstractMap.SimpleEntry<XType, YType>> {
        public TwoDDataSetProxy(List<AbstractMap.SimpleEntry<XType, YType>> list) {
            super(list);
        }

        public boolean hasLabels() {
            if (list.size() > 0) {
                return !(list.get(0).getKey() instanceof Number);
            }
            return false;
        }

        @Override
        /**
         * Retrieve how many points this data set has.
         *
         * @return the number of points
         */
        public int getDimensions() {
            if (list.size() == 0 || list.get(0) == null) {
                return -1;
            }
            return 2;
        }

        @Override
        /**
         * Retrieve data information from a point.
         *
         * @param point The point number
         * @param dimension The point dimension (or "column") to request data from
         * @return the point data for this dimension
         * @see DataSet#getPointValue(int,int)
         */
        public String getPointValue(int point, int dimension) {
            String value;
            if (dimension == 0) {
                if (hasLabels()) {
                    value = String.format("\"%s\"", list.get(point).getKey());
                } else {
                    value = String.valueOf(list.get(point).getKey());
                }
            } else {
                value = String.valueOf(list.get(point).getValue());
            }
            return value;
        }
    }
}
