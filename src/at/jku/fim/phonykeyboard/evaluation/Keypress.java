package at.jku.fim.phonykeyboard.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Keypress {
    private float x, y;
    private float size, orientation, pressure;
    private float downDistance, upDistance;
    private List<float[]> sensorData;

    public Keypress(float x, float y, float size, float orientation, float pressure, float downInterval, float upInterval, int initialSensorSize) {
        this.x = x;
        this.y = y;
        this.size = size;
        this.orientation = orientation;
        this.pressure = pressure;
        this.downDistance = downInterval;
        this.upDistance = upInterval;

        sensorData = new ArrayList<>(initialSensorSize);
    }

    public void addSensorData(float[] data) {
        sensorData.add(data);
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

    public float getDownDistance() {
        return downDistance;
    }

    public float getUpDistance() {
        return upDistance;
    }

    public List<float[]> getSensorData() {
        return Collections.unmodifiableList(sensorData);
    }
}
