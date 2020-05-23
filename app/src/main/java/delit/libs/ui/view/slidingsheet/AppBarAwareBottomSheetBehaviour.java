package delit.libs.ui.view.slidingsheet;

import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.ArrayList;

class AppBarAwareBottomSheetBehaviour extends BottomSheetBehavior implements AppBarLayout.OnOffsetChangedListener {

    private @IdRes
    int appBarViewId;
    private BottomSheetCallbackWrapper stateChangeCallback;
    private int currentState = BottomSheetBehavior.STATE_COLLAPSED;
    private int appBarOffset;

    public AppBarAwareBottomSheetBehaviour(@IdRes int appBarViewId, boolean isExpanded) {
        this.appBarViewId = appBarViewId;
        if (isExpanded) {
            currentState = BottomSheetBehavior.STATE_EXPANDED;
        }
        stateChangeCallback = new BottomSheetCallbackWrapper();
        super.addBottomSheetCallback(stateChangeCallback);
    }

    private @Nullable
    AppBarLayout getAppBarLayout(View v) {
        return v.getRootView().findViewById(appBarViewId);
    }

    @Override
    public void addBottomSheetCallback(@NonNull BottomSheetCallback callback) {
        stateChangeCallback.addBottomSheetCallback(callback);
    }

    @Override
    public void removeBottomSheetCallback(@NonNull BottomSheetCallback callback) {
        stateChangeCallback.removeBottomSheetCallback(callback);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBottomSheetCallback(BottomSheetCallback callback) {
        throw new UnsupportedOperationException("Use Set and Remove methods.");
    }

    /**
     * AppBar Listener code
     *
     * @param appBarLayout
     * @param verticalOffset
     */
    @Override
    public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
        appBarOffset = appBarLayout.getHeight() + verticalOffset;
    }

    @Override
    public boolean onLayoutChild(@NonNull CoordinatorLayout parent, @NonNull View child, int layoutDirection) {
        boolean retVal = super.onLayoutChild(parent, child, layoutDirection);
        ViewCompat.offsetTopAndBottom(parent, -appBarOffset - parent.getTop());
        return retVal;
    }

    public void refreshState() {
        setState(currentState);
    }

    public void stopListeningToAppBar(View v) {
        AppBarLayout appBar = getAppBarLayout(v);
        if (appBar != null) {
            appBar.removeOnOffsetChangedListener(this);
        }
    }

    public void startListeningToAppBar(View v) {
        AppBarLayout appBar = getAppBarLayout(v);
        if (appBar != null) {
            appBar.addOnOffsetChangedListener(this);
        }
    }

    private class BottomSheetCallbackWrapper extends BottomSheetCallback {

        @NonNull
        private final ArrayList<BottomSheetCallback> callbacks = new ArrayList<>();

        public void addBottomSheetCallback(BottomSheetCallback callback) {
            if (!callbacks.contains(callback)) {
                callbacks.add(callback);
            }
        }

        public void removeBottomSheetCallback(@NonNull BottomSheetCallback callback) {
            callbacks.remove(callback);
        }

        @Override
        public void onStateChanged(@NonNull View bottomSheetContent, int newState) {
            // set the offset on the bottom sheet content container (Constraint Layout)
            CoordinatorLayout bottomSheetContainer = (CoordinatorLayout) bottomSheetContent.getParent();
            ViewCompat.offsetTopAndBottom(bottomSheetContainer, -appBarOffset - bottomSheetContainer.getTop());
            if (currentState == newState) {
                return;
            }
            currentState = newState;
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).onStateChanged(bottomSheetContent, currentState);
            }
        }

        @Override
        public void onSlide(@NonNull View bottomSheetContent, float slideOffset) {
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).onSlide(bottomSheetContent, slideOffset);
            }
        }
    }
}
