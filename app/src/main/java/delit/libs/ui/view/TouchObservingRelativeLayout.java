package delit.libs.ui.view;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.RelativeLayout;

import androidx.annotation.RequiresApi;

public class TouchObservingRelativeLayout extends RelativeLayout {

    private TouchObserver touchObserver;

    public TouchObservingRelativeLayout(Context context) {
        super(context);
    }

    public TouchObservingRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TouchObservingRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public TouchObservingRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setTouchObserver(TouchObserver touchObserver) {
        this.touchObserver = touchObserver;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if(touchObserver != null) {
            touchObserver.onTouchEvent(ev);
        }
        return super.onInterceptTouchEvent(ev);
    }

    public interface TouchObserver {
        void onTouchEvent(MotionEvent ev);
    }
}
