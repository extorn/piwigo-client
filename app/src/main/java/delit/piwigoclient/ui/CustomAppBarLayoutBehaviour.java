package delit.piwigoclient.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.appbar.AppBarLayout;

import org.jetbrains.annotations.NotNull;

public class CustomAppBarLayoutBehaviour extends AppBarLayout.Behavior {
    public CustomAppBarLayoutBehaviour(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDragCallback(new DragCallback() {
            @Override public boolean canDrag(@NonNull AppBarLayout appBarLayout) {
                return appBarLayout.isEnabled();
            }
        });
    }

    @Override
    public boolean onStartNestedScroll(@NotNull CoordinatorLayout parent, AppBarLayout child, @NotNull View directTargetChild, View target, int nestedScrollAxes, int type) {
        return child.isEnabled() && super.onStartNestedScroll(parent, child, directTargetChild, target, nestedScrollAxes, type);
    }
}
