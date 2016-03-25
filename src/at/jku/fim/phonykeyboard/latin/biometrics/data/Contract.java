package at.jku.fim.phonykeyboard.latin.biometrics.data;

import java.sql.Connection;

public abstract class Contract {
    protected Contract() {
    }

    public int getVersion() {
        return 1;
    }

    public abstract void onCreate(Connection db);
    public abstract void onUpgrade(Connection db, int oldVersion, int newVersion);
}
