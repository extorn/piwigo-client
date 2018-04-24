package delit.piwigoclient.ui.common;

import android.content.Context;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.CoordinatorLayout;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ControllableBottomSheetBehavior<V extends View> extends BottomSheetBehavior<V> {
    private boolean mAllowUserDragging = true;

    /**
     * Default constructor for instantiating BottomSheetBehaviors.
     */
    public ControllableBottomSheetBehavior() {
        super();
    }

    /**
     * Default constructor for inflating BottomSheetBehaviors from layout.
     *
     * @param context The {@link Context}.
     * @param attrs   The {@link AttributeSet}.
     */
    public ControllableBottomSheetBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public static <V extends View> ControllableBottomSheetBehavior<V> from(V view) {
        return (ControllableBottomSheetBehavior<V>) BottomSheetBehavior.from(view);
    }

    public void setAllowUserDragging(boolean allowUserDragging) {
        mAllowUserDragging = allowUserDragging;
    }

    @Override
    public boolean onInterceptTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        return mAllowUserDragging && super.onInterceptTouchEvent(parent, child, event);
    }
}