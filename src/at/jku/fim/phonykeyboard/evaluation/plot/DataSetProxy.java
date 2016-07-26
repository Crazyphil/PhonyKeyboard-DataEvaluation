package at.jku.fim.phonykeyboard.evaluation.plot;

import com.panayotis.gnuplot.dataset.DataSet;

import java.util.AbstractMap;
import java.util.List;

public abstract class DataSetProxy<DataType> implements DataSet {
    protected List<DataType> list;

    public DataSetProxy(List<DataType> list) {
        this.list = list;
    }

    public List<DataType> getList() {
        return list;
    }

    public void setList(List<DataType> list) {
        this.list = list;
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
    public abstract int getDimensions();

    @Override
    /**
     * Retrieve data information from a point.
     *
     * @param point The point number
     * @param dimension The point dimension (or "column") to request data from
     * @return the point data for this dimension
     * @see DataSet#getPointValue(int,int)
     */
    public abstract String getPointValue(int point, int dimension);
}