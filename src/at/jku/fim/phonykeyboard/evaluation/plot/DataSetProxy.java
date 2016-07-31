package at.jku.fim.phonykeyboard.evaluation.plot;

import com.google.common.collect.Lists;
import com.panayotis.gnuplot.dataset.DataSet;

import java.util.*;

public abstract class DataSetProxy<DataType> implements DataSet {
    protected List<DataType> list;
    protected List<String> columnHeaders;

    public DataSetProxy(List<DataType> list) {
        this.list = list;
    }

    public List<DataType> getList() {
        return list;
    }

    public void setList(List<DataType> list) {
        this.list = list;
    }

    public List<String> getColumnHeaders() {
        return columnHeaders;
    }

    public void setColumnHeaders(Collection<String> columnHeaders) {
        columnHeaders = new ArrayList<>(columnHeaders);
    }

    public void setColumnHeaders(String... columnHeaders) {
        this.columnHeaders = new ArrayList<>(columnHeaders.length);
        Collections.addAll(this.columnHeaders, columnHeaders);
    }

    @Override
    public int size() {
        if (columnHeaders != null) {
            return list.size() + 1;
        } else {
            return list.size();
        }
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
    public String getPointValue(int point, int dimension) {
        if (columnHeaders != null && point == 0) {
            return String.format("\"%s\"", columnHeaders.get(dimension));
        } else {
            return getListValue(columnHeaders != null ? point - 1 : point, dimension);
        }
    }

    protected abstract String getListValue(int point, int dimension);
}