package delit.libs.ui.view.slidingsheet;

import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

public class BottomSheetCallbackAdapter extends BottomSheetBehavior.BottomSheetCallback {

    @Override
    public void onStateChanged(@NonNull View bottomSheet, int newState) {
        switch(newState) {
            case BottomSheetBehavior.STATE_EXPANDED:
                onExpanded(bottomSheet);
                return;
            case BottomSheetBehavior.STATE_COLLAPSED:
                onCollapsed(bottomSheet);
                return;
            default:
                onOtherStateChanged(bottomSheet, newState);
                return;
        }
    }

    public void onOtherStateChanged(View bottomSheet, int newState) {
    }

    public void onCollapsed(View bottomSheet) {
    }

    public void onExpanded(View bottomSheet) {
    }

    @Override
    public void onSlide(@NonNull View bottomSheet, float slideOffset) {
    }
}
