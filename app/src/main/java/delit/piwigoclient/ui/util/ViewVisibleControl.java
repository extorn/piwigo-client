package delit.piwigoclient.ui.util;

import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Alters visibility of some collection of views by posting to the main thread after some delay.
 * Can alter visibility of the views instantly using setVisibility
 */
public class ViewVisibleControl implements Runnable {

    private static final long DEFAULT_DELAY_MILLIS = 2000;
    private long delayMillis = DEFAULT_DELAY_MILLIS;
    private final List<View> views;
    private int visibilityOnRun = View.INVISIBLE;
    private long timerStarted;

    public ViewVisibleControl(View... views) {
        this.views = new ArrayList<>(Arrays.asList(views));
    }

    public void setDelayMillis(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    public void setVisibilityOnRun(int visibility) {
        this.visibilityOnRun = visibility;
    }

    public void setVisibility(int visibility) {
        for (View v : views) {
            if(v.getVisibility() != visibility) {
                v.setVisibility(visibility);
            }
        }
    }

    @Override
    public synchronized void run() {
        if (timerStarted + delayMillis - System.currentTimeMillis() > 0) {
            // another trigger has been added.
            return;
        }
        setVisibility(visibilityOnRun);
    }

    public synchronized void runWithDelay(View v) {
        if (v != null) {
            timerStarted = System.currentTimeMillis();
            setVisibility(visibilityOnRun == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
            v.postDelayed(this, delayMillis);
        }
    }

    public synchronized void addView(View v) {
        views.add(v);
        v.setVisibility(visibilityOnRun == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
    }

}
