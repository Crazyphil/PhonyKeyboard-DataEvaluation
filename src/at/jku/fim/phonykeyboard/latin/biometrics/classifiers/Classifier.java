package at.jku.fim.phonykeyboard.latin.biometrics.classifiers;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsEntry;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManager;
import at.jku.fim.phonykeyboard.latin.biometrics.BiometricsManagerImpl;
import at.jku.fim.phonykeyboard.latin.biometrics.data.Contract;

public abstract class Classifier {
    protected static final String MULTI_VALUE_SEPARATOR = "|";

    protected BiometricsManagerImpl manager;

    public Classifier(BiometricsManagerImpl manager) {
        this.manager = manager;
    }

    public Contract getDatabaseContract() {
        return null;
    }

    public double getScore() {
        return BiometricsManager.SCORE_NOT_ENOUGH_DATA;
    }

    public boolean clearData() {
        return false;
    }

    public abstract void onCreate();
    public abstract void onStartInput(long context, boolean restarting);
    public abstract void onKeyEvent(BiometricsEntry entry);
    public abstract void onFinishInput(boolean done);
    public abstract void onDestroy();

    protected String[] toCsvStrings(List<double[]> values) {
        String[] strings = new String[values.size()];

        StringBuilder sb = new StringBuilder(3 + 2);
        for (int i = 0; i < values.size(); i++) {
            for (int j = 0; j < values.get(i).length; j++) {
                if (j > 0) {
                    sb.append(MULTI_VALUE_SEPARATOR);
                }
                sb.append(values.get(i)[j]);
            }
            strings[i] = sb.toString();
            sb.delete(0, sb.length());
        }
        return strings;
    }

    public static class ActiveBiometricsEntries extends LinkedList<BiometricsEntry> {
        List<BiometricsEntry> pendingRemoval = new LinkedList<>();

        public BiometricsEntry getDownEntry(int pointerId) {
            for (BiometricsEntry entry : this) {
                if (entry.getPointerId() == pointerId) {
                    return entry;
                }
            }
            return null;
        }

        public BiometricsEntry getLastDownEntry(long timestamp) {
            long maxTimestamp = 0;
            BiometricsEntry foundEntry = null;
            for (BiometricsEntry entry : this) {
                if (entry.getEvent() == BiometricsEntry.EVENT_DOWN && entry.getTimestamp() < timestamp && entry.getTimestamp() > maxTimestamp) {
                    maxTimestamp = entry.getTimestamp();
                    foundEntry = entry;
                }
            }
            return foundEntry;
        }

        public void removeById(int pointerId) {
            removePending();

            ListIterator<BiometricsEntry> iter = listIterator();
            while (iter.hasNext()) {
                BiometricsEntry entry = iter.next();
                if (entry.getPointerId() == pointerId) {
                    if (size() > 1) {
                        iter.remove();
                    } else {
                        pendingRemoval.add(entry);
                    }
                }
            }
        }

        @Override
        public void clear() {
            super.clear();
            pendingRemoval.clear();
        }

        private void removePending() {
            if (size() <= 1) return;
            ListIterator<BiometricsEntry> iter = pendingRemoval.listIterator();
            while (iter.hasNext()) {
                BiometricsEntry entry = iter.next();
                if (size() > 1) {
                    remove(entry);
                    iter.remove();
                }
            }
        }
    }

    // Source: http://stackoverflow.com/questions/1240077/why-cant-i-use-foreach-on-java-enumeration/17960641#17960641
    protected static class IterableEnumeration<T> implements Iterable<T>, Iterator<T>
    {
        private final Enumeration<T> enumeration;
        private boolean used = false;

        IterableEnumeration(final Enumeration<T> enm) {
            enumeration = enm;
        }

        public Iterator<T> iterator() {
            if (used) throw new IllegalStateException("Cannot use iterator from asIterable wrapper more than once");
            used = true;
            return this;
        }

        public boolean hasNext() {
            return enumeration.hasMoreElements();
        }

        public T next() {
            return enumeration.nextElement();
        }

        public void remove() {
            throw new UnsupportedOperationException("Cannot remove elements from AsIterator wrapper around Enumeration");
        }

        public static <T> Iterable<T> asIterable(final Enumeration<T> enm) {
            return new IterableEnumeration<>(enm);
        }
    }
}
