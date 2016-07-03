package at.jku.fim.phonykeyboard.latin.biometrics.data;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Struct;

public class StatisticalClassifierContract extends Contract {
    public static final int DATABASE_VERSION = 3;

    private final String sqlCreateData;

    private final String SQL_CREATE_TEMPLATES = "CREATE TABLE " + StatisticalClassifierTemplates.TABLE_NAME + " (" +
            StatisticalClassifierTemplates._ID + " INTEGER PRIMARY KEY, " +
            StatisticalClassifierTemplates.COLUMN_CONTEXT + " INTEGER, " +
            StatisticalClassifierTemplates.COLUMN_SCREEN_ORIENTATION + " INTEGER, " +
            StatisticalClassifierTemplates.COLUMN_DATA_ID + " INTEGER, " +
            StatisticalClassifierTemplates.COLUMN_SCORE + " REAL)";

    private final String SQL_CREATE_TEMPLATE_STATUS = "CREATE TABLE " + StatisticalClassifierTemplateStatus.TABLE_NAME + " (" +
            StatisticalClassifierTemplateStatus._ID + " INTEGER PRIMARY KEY, " +
            StatisticalClassifierTemplateStatus.COLUMN_CONTEXT + " INTEGER, " +
            StatisticalClassifierTemplateStatus.COLUMN_SCREEN_ORIENTATION + " INTEGER)";

    private String[] sensorColumns;

    public StatisticalClassifierContract(String[] sensorTypes) {
        super();

        StringBuilder sb = new StringBuilder(sensorTypes.length * 3 + 2);
        sb.append("CREATE TABLE " + StatisticalClassifierData.TABLE_NAME + " (" +
                StatisticalClassifierData._ID + " INTEGER PRIMARY KEY, " +
                StatisticalClassifierData.COLUMN_CONTEXT + " INTEGER, " +
                StatisticalClassifierData.COLUMN_SCREEN_ORIENTATION + " INTEGER, " +
                StatisticalClassifierData.COLUMN_KEY_DOWNDOWN + " TEXT, " +
                StatisticalClassifierData.COLUMN_KEY_DOWNUP + " TEXT, " +
                StatisticalClassifierData.COLUMN_POSITION + " TEXT, " +
                StatisticalClassifierData.COLUMN_SIZE + " TEXT, " +
                StatisticalClassifierData.COLUMN_ORIENTATION + " TEXT, " +
                StatisticalClassifierData.COLUMN_PRESSURE + " TEXT");

        sensorColumns = new String[sensorTypes.length];
        int i = 0;
        for (String sensorType : sensorTypes) {
            sensorColumns[i] = sensorType.replace('.', '_');
            sb.append(", ");
            sb.append(sensorColumns[i]);
            sb.append(" TEXT");
            i++;
        }
        sb.append(")");
        sqlCreateData = sb.toString();
    }

    @Override
    public void onCreate(Connection db) {
        try {
            Statement statement = db.createStatement();
            statement.execute(sqlCreateData);
            statement.execute(SQL_CREATE_TEMPLATES);
            statement.execute(SQL_CREATE_TEMPLATE_STATUS);
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(Connection db, int oldVersion, int newVersion) {
        try {
            if (oldVersion < 2) {
                Statement statement = db.createStatement();
                statement.execute(SQL_CREATE_TEMPLATES);
                statement.close();
            }
            if (oldVersion < 3) {
                Statement statement = db.createStatement();
                statement.execute(SQL_CREATE_TEMPLATE_STATUS);
                statement.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getVersion() {
        return DATABASE_VERSION;
    }

    public String[] getSensorColumns() {
        return sensorColumns;
    }

    public static abstract class StatisticalClassifierData {
        public static final String TABLE_NAME = "StatisticalClassifierData";
        public static final String _ID = "_id";
        public static final String _COUNT = "_count";
        public static final String COLUMN_CONTEXT = "context";
        public static final String COLUMN_SCREEN_ORIENTATION = "screen_orientation";
        public static final String COLUMN_KEY_DOWNDOWN = "key_downdown";    // n = k-1
        public static final String COLUMN_KEY_DOWNUP = "key_downup";        // n = k
        public static final String COLUMN_POSITION = "position";            // n = k
        public static final String COLUMN_SIZE = "size";            // n = k
        public static final String COLUMN_ORIENTATION = "orientation";            // n = k
        public static final String COLUMN_PRESSURE = "pressure";            // n = k
    }

    public static abstract class StatisticalClassifierTemplates {
        public static final String TABLE_NAME = "StatisticalClassifierTemplates";
        public static final String _ID = "_id";
        public static final String _COUNT = "_count";
        public static final String COLUMN_CONTEXT = "context";
        public static final String COLUMN_SCREEN_ORIENTATION = "screen_orientation";
        public static final String COLUMN_DATA_ID = "data_id";
        public static final String COLUMN_SCORE = "score";
    }

    public static abstract class StatisticalClassifierTemplateStatus {
        public static final String TABLE_NAME = "StatisticalClassifierTemplateStatus";
        public static final String _ID = "_id";
        public static final String COLUMN_CONTEXT = "context";
        public static final String COLUMN_SCREEN_ORIENTATION = "screen_orientation";
    }

    public static abstract class CaptureClassifierData {
        public static final String COLUMN_TIMESTAMP = "timestamp";
        public static final String COLUMN_KEY = "key";
        public static final String COLUMN_INPUTMETHOD = "inputmethod";
        public static final String COLUMN_SITUATION = "situation";
    }
}
