package delit.libs.ui.view;

import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ScrollView;

import androidx.core.view.GestureDetectorCompat;

import delit.libs.core.util.Logging;

/**
 * Created by gareth on 04/07/17.
 */

public abstract class CustomClickTouchListener implements View.OnTouchListener {

    private static final String TAG = "CustomClickTouchListener";
    private final GestureDetectorCompat detector;
    private boolean allowScrollWhenNested;

    public CustomClickTouchListener(final View linkedView) {

        GestureDetector.SimpleOnGestureListener listener = new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDown(MotionEvent e) {
                // triggers first for both single tap and long press
                return true; // needed otherwise long click triggered for every touch.
            }

            @Override
            public final boolean onSingleTapUp(MotionEvent e) {
                return onClick();
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                return super.onFling(e1, e2, velocityX, velocityY);
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (allowScrollWhenNested) {
                    int intDistanceX = (int) Math.rint(distanceX);
                    int intDistanceY = (int) Math.rint(distanceY);
                    if (!linkedView.canScrollVertically(intDistanceY)) {

                        View parentView = getParentScrollView(linkedView);
                        int adjustedXscroll = parentView.canScrollHorizontally(intDistanceX) ? intDistanceX : 0;
                        int adjustedYscroll = parentView.canScrollVertically(intDistanceY) ? intDistanceY : 0;
                        parentView.scrollBy(adjustedXscroll, adjustedYscroll);

                    }
                }
                return super.onScroll(e1, e2, distanceX, distanceY);
            }

            private View getParentScrollView(View linkedView) {
                View v = linkedView;
                while (!(v instanceof ScrollView)) {
                    v = (View) v.getParent();
                    if (v == null) {
                        throw new IllegalStateException("Component must be inside a scroll view for this to work");
                    }
                }
                return v;
            }

            @Override
            public final void onLongPress(MotionEvent e) {
                onLongClick();
            }
        };
        detector = new GestureDetectorCompat(linkedView.getContext(), listener);
        detector.setIsLongpressEnabled(true);
    }

    public static void callClickOnTouch(View field, View.OnClickListener listener) {
        callClickOnTouch(field, listener, true);
    }

    public static void callClickOnTouch(View field, View.OnClickListener listener, boolean consumeEvent) {
        if(field instanceof AdapterView) {
            Logging.log(Log.DEBUG, TAG, "Unable to set click listener on an AdapterView, calling direct");
            addClickListenerOverride(field, listener, consumeEvent);
        } else {
            callClickOnTouch(field, consumeEvent);
            field.setOnClickListener(listener);
        }
    }

    private static void addClickListenerOverride(View field, View.OnClickListener listener, boolean consumeEvent) {
        field.setOnTouchListener(new CustomClickTouchListener(field) {
            @Override
            public boolean onClick() {
                listener.onClick(field);
                return consumeEvent;
            }
        });
    }

    public static void callClickOnTouch(View field, boolean consumeEvent) {
        field.setOnTouchListener(new CustomClickTouchListener(field) {
            @Override
            public boolean onClick() {
                field.performClick();
                return consumeEvent;
            }
        });
    }

    public static void callClickOnTouch(View field) {
        callClickOnTouch(field, true);
    }

    /**
     * @return true if the event was consumed
     */
    public boolean onClick() {
        return false;
    }

    public CustomClickTouchListener withScrollingWhenNested() {
        allowScrollWhenNested = true;
        return this;
    }

    public void onLongClick() {
    }

    @Override
    public final boolean onTouch(View v, MotionEvent event) {
        if (!detector.onTouchEvent(event)) {
            if (allowScrollWhenNested) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return onTouchNonClick(v, event);
        }
        return true;
    }

    public boolean onTouchNonClick(View v, MotionEvent event) {
        return false;
    }

}
