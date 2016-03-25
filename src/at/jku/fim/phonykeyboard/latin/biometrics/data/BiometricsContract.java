package at.jku.fim.phonykeyboard.latin.biometrics.data;

public final class BiometricsContract {
    private BiometricsContract() {
    }

    public static final String SQL_CREATE_CONTRACT_VERSIONS =
            "CREATE TABLE " + ContractVersions.TABLE_NAME + " (" +
                    ContractVersions.COLUMN_CONTRACT + " TEXT PRIMARY KEY, " +
                    ContractVersions.COLUMN_VERSION + " INTEGER)";

    public static final String SQL_CREATE_CONTEXTS =
            "CREATE TABLE " + Contexts.TABLE_NAME + " (" +
                    Contexts._ID + " INTEGER PRIMARY KEY, " +
                    Contexts.COLUMN_CONTEXT + " TEXT UNIQUE)";


    public static abstract class ContractVersions {
        public static final String TABLE_NAME = "ContractVersions";
        public static final String COLUMN_CONTRACT = "contract";
        public static final String COLUMN_VERSION = "version";
    }

    public static abstract class Contexts {
        public static final String _ID = "_id";
        public static final String _COUNT = "_count";
        public static final String TABLE_NAME = "Contexts";
        public static final String COLUMN_CONTEXT = "context";
    }
}