package at.jku.fim.phonykeyboard.evaluation.plot;

import com.panayotis.gnuplot.dataset.DataSet;

import java.util.AbstractMap;
import java.util.List;

public class DataSetProxy<XType, YType> implements DataSet {
    private List<AbstractMap.SimpleEntry<XType, YType>> list;

    public DataSetProxy(List<AbstractMap.SimpleEntry<XType, YType>> list) {
        this.list = list;
    }

    public List<AbstractMap.SimpleEntry<XType, YType>> getList() {
        return list;
    }

    public void setList(List<AbstractMap.SimpleEntry<XType, YType>> list) {
        this.list = list;
    }

    public boolean hasLabels() {
        if (list.size() > 0) {
            return !(list.get(0).getKey() instanceof Number);
        }
        return false;
    }

    @Override
    public int size() {
        return list.size();
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