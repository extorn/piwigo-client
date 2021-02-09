package delit.piwigoclient.ui.util;

public class TimerThreshold {
    private final long minInterval;
    private long lastUpdate;

    public TimerThreshold(long minInterval) {
        this.minInterval = minInterval;
    }

    public boolean thresholdMet() {
        long thisUpdateTime = System.currentTimeMillis();
        if(lastUpdate + minInterval < thisUpdateTime) {
            lastUpdate = thisUpdateTime;
            return true;
        }
        return false;
    }
}
