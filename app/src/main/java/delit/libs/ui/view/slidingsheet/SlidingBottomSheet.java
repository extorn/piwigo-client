package delit.libs.ui.view.slidingsheet;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.AttrRes;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.R;

/**
 * A self contained sliding sheet.
 */
public class SlidingBottomSheet extends CoordinatorLayout {
    private AppBarAwareBottomSheetBehaviour appBarAwareBottomSheetBehavior;
    private OnInteractListener onInteractListener;
    private RelativeLayout bottomSheetContent;

    public SlidingBottomSheet(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
    }

    public SlidingBottomSheet(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initAttribs(context, attrs, 0, 0);
        setBackgroundColor(Color.TRANSPARENT);
    }

    public SlidingBottomSheet(Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initAttribs(context, attrs, defStyleAttr, 0);
        setBackgroundColor(Color.TRANSPARENT);
    }

    public SlidingBottomSheet(Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr);
        initAttribs(context, attrs, defStyleAttr, defStyleRes);
        setBackgroundColor(Color.TRANSPARENT);
    }

    private void initAttribs(Context context, AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SlidingBottomSheet, defStyleAttr, defStyleRes);
        @IdRes int appBarViewId = a.getResourceId(R.styleable.SlidingBottomSheet_appBarViewId, View.NO_ID);
        boolean isExpanded = a.getBoolean(R.styleable.SlidingBottomSheet_stateExpanded, false);
        appBarAwareBottomSheetBehavior = new AppBarAwareBottomSheetBehaviour(appBarViewId, isExpanded);
        a.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if(getChildCount() > 0) {
            configureContents();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        appBarAwareBottomSheetBehavior.stopListeningToAppBar(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        appBarAwareBottomSheetBehavior.startListeningToAppBar(this);
    }

    private void addShadow(RelativeLayout bottomSheet, Context context) {
        View shadowView = new View(context);
        shadowView.setBackground(ContextCompat.getDrawable(context, R.drawable.shadow_top));
        shadowView.setOnClickListener(v -> {
            close();
        });
        bottomSheet.setOnClickListener(v -> {
            close();
        });
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, DisplayUtils.dpToPx(context, 30));
//        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        int viewContentId = bottomSheet.getChildAt(0).getId();
        params.addRule(RelativeLayout.ABOVE, viewContentId);

        shadowView.setLayoutParams(params);
        bottomSheet.addView(shadowView);
    }

    public void close() {
        appBarAwareBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    public void open() {
        appBarAwareBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }



    private void configureBottomSheetBehaviour() {
        appBarAwareBottomSheetBehavior.setPeekHeight(0);
        appBarAwareBottomSheetBehavior.addBottomSheetCallback(new BottomSheetCallbackAdapter() {

            @Override
            public void onExpanded(View bottomSheet) {
                onInteractListener.onOpened();
            }

            @Override
            public void onCollapsed(View bottomSheet) {
                onInteractListener.onClosed();
            }});
    }

    public void configureContents() {

        View bottomSheetContent = getChildAt(0);
        removeView(bottomSheetContent);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        bottomSheetContent.setLayoutParams(params);

        CoordinatorLayout.LayoutParams thisParams = (LayoutParams) getLayoutParams();
        if(thisParams == null) {
            thisParams = new CoordinatorLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            setLayoutParams(thisParams);
        }
        CoordinatorLayout.LayoutParams bottomSheetParams = new CoordinatorLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bottomSheetParams.setBehavior(appBarAwareBottomSheetBehavior);


        this.bottomSheetContent = new RelativeLayout(getContext());
        this.bottomSheetContent.setLayoutParams(bottomSheetParams);
        this.bottomSheetContent.setBackgroundColor(Color.TRANSPARENT);
        addView(this.bottomSheetContent);

        // add the original xml child as content
        this.bottomSheetContent.addView(bottomSheetContent);
        // add a shadow to the top of the child content
        addShadow(this.bottomSheetContent, getContext());

        configureBottomSheetBehaviour();
        appBarAwareBottomSheetBehavior.refreshState();
    }



    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        super.addView(child, params);
    }

    public void setOnInteractListener(OnInteractListener onInteractListener) {
        this.onInteractListener = onInteractListener;
    }

    public boolean isOpen() {
        return appBarAwareBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED;
    }


    public interface OnInteractListener {
        void onOpened();

        void onClosed();
    }

}
