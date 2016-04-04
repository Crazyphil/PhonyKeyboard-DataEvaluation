package at.jku.fim.phonykeyboard.latin.biometrics.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// NOTE: Row indexes in this class are 1-based while Column indexes are 0-based, for compatibility with JDBC
public class Cursor {
    List<Object[]> data;
    String[] cols;
    int currentRow = 0;

    public Cursor(ResultSet result) throws SQLException {
        cols = new String[result.getMetaData().getColumnCount()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = result.getMetaData().getColumnName(i+1);
        }

        data = new ArrayList<>();
        while (result.next()) {
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                row[i] = result.getObject(i+1);
            }
            data.add(row);
        }
        result.close();
    }

    public int getColumnCount() {
        return cols.length;
    }

    public int getColumnIndex(String columnName) throws SQLException {
        for (int i = 0; i < cols.length; i++) {
            if (cols[i].equals(columnName)) {
                return i;
            }
        }
        throw new SQLException(String.format("Unknown column %s", columnName));
    }

    public int getCount() {
        return data.size();
    }

    public boolean isAfterLast() {
        return currentRow > data.size();
    }

    public int getInt(int columnIndex) throws SQLException {
        validColumnIndex(columnIndex);
        return (int)data.get(currentRow-1)[columnIndex];
    }

    public int getInt(String columnName) throws SQLException {
        return getInt(getColumnIndex(columnName));
    }

    public String getString(int columnIndex) throws SQLException {
        validColumnIndex(columnIndex);
        return (String)data.get(currentRow-1)[columnIndex];
    }

    public String getString(String columnName) throws SQLException {
        return getString(getColumnIndex(columnName));
    }

    public void beforeFirst() {
        currentRow = 0;
    }

    public boolean first() {
        if (data.size() > 0) {
            currentRow = 1;
            return true;
        }
        return false;
    }

    public boolean next() {
        if (currentRow == data.size() + 1) {
            return false;
        }
        currentRow++;
        return !isAfterLast();
    }

    public void close() throws SQLException {
        // No-op
    }

    private void validColumnIndex(int columnIndex) throws SQLException {
        if (columnIndex < 0 || columnIndex >= cols.length) {
            throw new SQLException(String.format("Column index out of bounds - %d ∉ [0..%d]", columnIndex, cols.length - 1));
        }
    }
}
