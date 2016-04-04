package at.jku.fim.phonykeyboard.latin.biometrics.data;

import com.sun.rowset.CachedRowSetImpl;
import org.sqlite.SQLiteConfig;

import javax.sql.rowset.CachedRowSet;
import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class BiometricsDbHelper {
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "biometrics.db";
    private static final Pattern limitPattern = Pattern.compile("\\s*\\d+\\s*(,\\s*\\d+\\s*)?");

    private Connection connection;
    private List<Contract> contracts;
    private boolean wasCreated;

    public BiometricsDbHelper(Contract contract) {
        this.contracts = new LinkedList<>();
        if (contract != null) {
            this.contracts.add(contract);
        }

        File dbFile = new File(DATABASE_NAME);
        boolean exists = dbFile.exists();
        try {
            //connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_NAME);
            connection = DriverManager.getConnection("jdbc:sqlite::memory:");
            if (!exists) {
                onCreate(connection);
            }
            onOpen(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void finalize() {
        try {
            connection.close();
            super.finalize();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void onCreate(Connection db) throws SQLException {
        if (!db.isValid(0)) {
            throw new IllegalArgumentException("DB not opened");
        }

        try (Statement statement = db.createStatement()) {
            statement.execute("PRAGMA user_version = " + DATABASE_VERSION);
            statement.execute(BiometricsContract.SQL_CREATE_CONTRACT_VERSIONS);
            statement.execute(BiometricsContract.SQL_CREATE_CONTEXTS);

            ContentValues values = new ContentValues(2);
            for (Contract contract : contracts) {
                createContractTables(db, contract, values);
            }
            wasCreated = true;
        }
    }

    private void createContractTables(Connection db, Contract contract, ContentValues values) throws SQLException {
        if (values == null) {
            values = new ContentValues(2);
        }
        values.put(BiometricsContract.ContractVersions.COLUMN_CONTRACT, contract.getClass().getSimpleName());
        values.put(BiometricsContract.ContractVersions.COLUMN_VERSION, contract.getVersion());

        insert(BiometricsContract.ContractVersions.TABLE_NAME, values);
        contract.onCreate(db);
    }

    private void upgradeContractTables(Connection db, Contract contract, int oldVersion, ContentValues values) throws SQLException {
        if (values == null) {
            values = new ContentValues(2);
        }
        values.put(BiometricsContract.ContractVersions.COLUMN_CONTRACT, contract.getClass().getSimpleName());
        values.put(BiometricsContract.ContractVersions.COLUMN_VERSION, contract.getVersion());
        update(BiometricsContract.ContractVersions.TABLE_NAME, values, BiometricsContract.ContractVersions.COLUMN_CONTRACT + " = ?", new String[] { contract.getClass().getSimpleName() });
        contract.onUpgrade(db, oldVersion, contract.getVersion());
    }

    public void onUpgrade(Connection db, int oldVersion, int newVersion) {
    }

    public void onOpen(Connection db) throws SQLException {
        if (wasCreated) return;

        try (Statement statement = db.createStatement()) {
            ResultSet result = statement.executeQuery("PRAGMA user_version");
            if (result.next()) {
                int version = result.getInt(1);
                if (version < DATABASE_VERSION) {
                    onUpgrade(db, version, DATABASE_VERSION);
                }
            }
            result.close();
        }

        StringBuilder selection = new StringBuilder(contracts.size() + 2);
        String[] selectionArgs = new String[contracts.size()];
        selection.append(BiometricsContract.ContractVersions.COLUMN_CONTRACT + " IN (");
        int i = 0;
        for (Contract contract : contracts) {
            if (i == 0) {
                selection.append("?");
            } else {
                selection.append(", ?");
            }
            selectionArgs[i] = contract.getClass().getSimpleName();
            i++;
        }
        selection.append(")");

        Cursor c = query(false, BiometricsContract.ContractVersions.TABLE_NAME,
                new String[] { BiometricsContract.ContractVersions.COLUMN_CONTRACT, BiometricsContract.ContractVersions.COLUMN_VERSION },
                selection.toString(), selectionArgs, null, null, null, null);
        for (Contract contract : contracts) {
            c.beforeFirst();
            while (c.next()) {
                if (c.getString(BiometricsContract.ContractVersions.COLUMN_CONTRACT).equals(contract.getClass().getSimpleName())) {
                    int version = c.getInt(BiometricsContract.ContractVersions.COLUMN_VERSION);
                    if (contract.getVersion() > version) {
                        upgradeContractTables(db, contract, version, null);
                    }
                    break;
                }
            }
            if (c.isAfterLast()) {
                createContractTables(db, contract, null);
            }
        }
        c.close();
    }

    public void addContract(Contract contract) throws SQLException {
        contracts.add(contract);
        if (wasCreated) {
            createContractTables(connection, contract, null);
        } else {
            Cursor c = query(false, BiometricsContract.ContractVersions.TABLE_NAME,
                    new String[] { BiometricsContract.ContractVersions.COLUMN_CONTRACT, BiometricsContract.ContractVersions.COLUMN_VERSION },
                    BiometricsContract.ContractVersions.COLUMN_CONTRACT + " = ?", new String[] { contract.getClass().getSimpleName() }, null, null, null, null);
            if (c.isAfterLast()) {
                createContractTables(connection, contract, null);
            } else {
                c.first();
                upgradeContractTables(connection, contract, c.getInt(BiometricsContract.ContractVersions.COLUMN_CONTRACT), null);
            }
            c.close();
        }
    }

    public void removeContract(Contract contract) {
        contracts.remove(contract);
    }

    public Cursor query(boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) throws SQLException {
        String query = buildQueryString(distinct, table, columns, selection, groupBy, having, orderBy, limit);
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            for (int i = 0; i < selectionArgs.length; i++) {
                statement.setObject(i + 1, selectionArgs[i]);
            }
            ResultSet result = statement.executeQuery();
            return new Cursor(result);
        }
    }

    private static String buildQueryString(boolean distinct, String table, String[] columns, String selection, String groupBy, String having, String orderBy, String limit) {
        if ((groupBy == null || groupBy.isEmpty()) && having != null && !having.isEmpty()) {
            throw new IllegalArgumentException(
                    "HAVING clauses are only permitted when using a groupBy clause");
        }
        if (limit != null && !limit.isEmpty() && !limitPattern.matcher(limit).matches()) {
            throw new IllegalArgumentException("invalid LIMIT clauses:" + limit);
        }

        StringBuilder query = new StringBuilder(120);

        query.append("SELECT ");
        if (distinct) {
            query.append("DISTINCT ");
        }
        if (columns != null && columns.length != 0) {
            int n = columns.length;
            for (int i = 0; i < n; i++) {
                String column = columns[i];

                if (column != null) {
                    if (i > 0) {
                        query.append(", ");
                    }
                    query.append(column);
                }
            }
            query.append(' ');
        } else {
            query.append("* ");
        }
        query.append("FROM ");
        query.append(table);
        appendClause(query, " WHERE ", selection);
        appendClause(query, " GROUP BY ", groupBy);
        appendClause(query, " HAVING ", having);
        appendClause(query, " ORDER BY ", orderBy);
        appendClause(query, " LIMIT ", limit);

        return query.toString();
    }

    private static void appendClause(StringBuilder s, String name, String clause) {
        if (clause != null && !clause.isEmpty()) {
            s.append(name);
            s.append(clause);
        }
    }

    public boolean insert(String table, ContentValues initialValues) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT");
        sql.append(" INTO ");
        sql.append(table);
        sql.append('(');

        Object[] bindArgs;
        int size = (initialValues != null && initialValues.size() > 0)
                ? initialValues.size() : 0;
        if (size > 0) {
            bindArgs = new Object[size];
            int i = 0;
            for (String colName : initialValues.keySet()) {
                sql.append((i > 0) ? "," : "");
                sql.append(colName);
                bindArgs[i++] = initialValues.get(colName);
            }
            sql.append(')');
            sql.append(" VALUES (");
            for (i = 0; i < size; i++) {
                sql.append((i > 0) ? ",?" : "?");
            }
        } else {
            sql.append(") VALUES (NULL");
        }
        sql.append(')');

        PreparedStatement statement = connection.prepareStatement(sql.toString());
        int i = 1;
        for (Object value : initialValues.values()) {
            statement.setObject(i++, value);
        }
        try {
            return statement.execute();
        } finally {
            statement.close();
        }
    }

    public boolean update(String table, ContentValues values, String whereClause, String[] whereArgs) throws SQLException {
        if (values == null || values.size() == 0) {
            throw new IllegalArgumentException("Empty values");
        }

        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(table);
        sql.append(" SET ");

        // move all bind args to one array
        int setValuesSize = values.size();
        int i = 0;
        for (String colName : values.keySet()) {
            sql.append((i > 0) ? "," : "");
            sql.append(colName);
            sql.append("=");
            sql.append(colName);
            i++;
        }
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(whereClause);
        }

        PreparedStatement statement = connection.prepareStatement(sql.toString());
        i = 0;
        for (Object value : values.values()) {
            statement.setObject(i, value);
            i++;
        }
        try {
            return statement.execute();
        } finally {
            statement.close();
        }
    }

    public static class ContentValues extends LinkedHashMap<String, Object> {
        public ContentValues() {
            super(8);
        }

        public ContentValues(int size) {
            super(size, 1.0f);
        }
    }
}
