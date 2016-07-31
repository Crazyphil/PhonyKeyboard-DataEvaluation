package at.jku.fim.phonykeyboard.evaluation;

public class Acquisition {
    private int tryId;
    private long timestamp;
    private int screenOrientation;
    private Keypress[] keypresses;
    private int sensorCount;
    private int situation, inputMethod;

    public Acquisition(int tryId, long timestamp, int screenOrientation, int situation, int inputMethod, Keypress[] keypresses, int sensorCount) {
        this.tryId = tryId;
        this.timestamp = timestamp;
        this.screenOrientation = screenOrientation;
        this.situation = situation;
        this.inputMethod = inputMethod;
        this.keypresses = keypresses;
        this.sensorCount = sensorCount;
    }

    public int getTryId() {
        return tryId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getScreenOrientation() {
        return screenOrientation;
    }

    public int getSituation() {
        return situation;
    }

    public int getInputMethod() {
        return inputMethod;
    }

    public Keypress[] getKeypresses() {
        return keypresses;
    }

    public int getSensorCount() {
        return sensorCount;
    }
}
