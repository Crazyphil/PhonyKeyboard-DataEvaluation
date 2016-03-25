package at.jku.fim.phonykeyboard.latin.biometrics;

import java.util.ArrayList;
import java.util.List;

public class BiometricsEntry {
    public static final int EVENT_DOWN = 0, EVENT_UP = 1;
    private long timestamp;
    private int screenOrientation;
    private int event, pointerId;
    private float x, y, size, orientation, pressure;
    private List<float[]> sensorData;

    public BiometricsEntry(final int sensorCount) {
        sensorData = new ArrayList<>(sensorCount);
    }

    public void setProperties(int pointerId, int eventType, long timestamp,float x, float y, float size, float orientation, float pressure, int screenOrientation) {
        this.timestamp = timestamp;
        this.screenOrientation = screenOrientation;
        this.pointerId = pointerId;
        this.event = eventType;
        this.x = x;
        this.y = y;
        this.size = size;
        this.orientation = orientation;
        this.pressure = pressure;
    }

    public void addSensorData(float[] data) {
        sensorData.add(data);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getScreenOrientation() {
        return screenOrientation;
    }

    public int getEvent() {
        return event;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getSize() {
        return size;
    }

    public float getOrientation() {
        return orientation;
    }

    public float getPressure() {
        return pressure;
    }

    public int getPointerId() {
        return pointerId;
    }

    public List<float[]> getSensorData() {
        return sensorData;
    }

    public String eventToString() {
        switch (event) {
            case EVENT_DOWN:
                return "KeyDown";
            case EVENT_UP:
                return "KeyUp";
            default:
                return null;
        }
    }
}
