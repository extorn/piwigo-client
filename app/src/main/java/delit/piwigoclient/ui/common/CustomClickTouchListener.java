package delit.piwigoclient.ui.common;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by gareth on 04/07/17.
 */

public abstract class CustomClickTouchListener implements View.OnTouchListener {

    private final GestureDetector.SimpleOnGestureListener listener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public final boolean onSingleTapUp(MotionEvent e) {
            return onClick();
        }

        @Override
        public final void onLongPress(MotionEvent e) {
            onLongClick();
        }
    };

    public boolean onClick() {
        return false;
    }

    private final GestureDetector detector;

    public CustomClickTouchListener(Context context) {
        detector = new GestureDetector(context, listener);
    }

    public void onLongClick() {
    }

    @Override
    public final boolean onTouch(View v, MotionEvent event) {
        detector.onTouchEvent(event);
        return false;
    }

}
