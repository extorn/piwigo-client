package delit.libs.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

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

    public static <V extends View> ControllableBottomSheetBehavior<V> from(@NonNull V view) {
        return (ControllableBottomSheetBehavior<V>) BottomSheetBehavior.from(view);
    }

    public void setAllowUserDragging(boolean allowUserDragging) {
        mAllowUserDragging = allowUserDragging;
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull CoordinatorLayout parent, @NonNull V child, @NonNull MotionEvent event) {
        return mAllowUserDragging && super.onInterceptTouchEvent(parent, child, event);
    }
}