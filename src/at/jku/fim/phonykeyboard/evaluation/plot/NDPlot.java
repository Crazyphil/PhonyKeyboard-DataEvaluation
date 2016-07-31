package at.jku.fim.phonykeyboard.evaluation.plot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NDPlot<T> extends Plot<List<T>> {
    private List<NDDataSetProxy<T>> data;

    public NDPlot(boolean is3D) {
        super(is3D);
        data = new ArrayList<>();
    }

    @Override
    public int addData(Collection<List<T>> points) {
        NDDataSetProxy<T> proxy = new NDDataSetProxy<>(new ArrayList<>(points));
        data.add(proxy);
        plotter.addPlot(proxy);
        return data.size() - 1;
    }

    @Override
    public List<List<T>> getData(int i) {
        return data.get(i).getList();
    }


    public void setDataColumnHeaders(int i, String... columnHeaders) {
        data.get(i).setColumnHeaders(columnHeaders);
    }

    public void setDataColumnHeaders(int i, Collection<String> columnHeaders) {
        data.get(i).setColumnHeaders(columnHeaders);
    }

    public List<String> getDataColumnHeaders(int i) {
        return data.get(i).getColumnHeaders();
    }

    private class NDDataSetProxy<T> extends DataSetProxy<List<T>> {
        public NDDataSetProxy(List<List<T>> list) {
            super(list);
        }
        @Override
        public int getDimensions() {
            if (list.size() == 0 || list.get(0) == null) {
                return -1;
            }
            return list.get(0).size();
        }

        @Override
        public String getListValue(int point, int dimension) {
            return String.valueOf(list.get(point).get(dimension));
        }
    }
}
