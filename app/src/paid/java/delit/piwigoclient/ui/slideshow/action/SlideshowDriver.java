package delit.piwigoclient.ui.slideshow.action;

import android.util.Log;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.slideshow.SlideshowFragment;

public class SlideshowDriver<F extends SlideshowFragment<F,FUIH,?>, FUIH extends FragmentUIHelper<FUIH,F>> implements Runnable {

    private static final String TAG = "SlideshowDriver";
    private final F slideshow;
    private int moveToPage;
    private int cancelledPage = -1;

    public SlideshowDriver(F slideshow) {
        this.slideshow = slideshow;
    }

    public void setMoveToPage(int moveToPage) {
        this.moveToPage = moveToPage;
        this.cancelledPage = -1;
    }

    @Override
    public void run() {
        if(cancelledPage < 0) {
            if(BuildConfig.DEBUG) {
                Log.d(TAG, "Moving to slideshow page : " + moveToPage);
            }
            slideshow.showItemAtSlideshowIndex(moveToPage);
        }
    }

    public void cancel(int currentPage) {
        cancelledPage = currentPage;
    }

    public boolean isActive(int currentPage) {
        return cancelledPage != currentPage;
    }
}
