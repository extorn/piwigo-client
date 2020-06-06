package delit.libs.ui.view.slidingsheet;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
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
        if(getChildCount() > 0) {
            configureContents();
        }
        super.onFinishInflate();


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

    private void addShadow(ViewGroup bottomSheet, Context context) {
        View shadowView = new View(context);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, DisplayUtils.dpToPx(context, 30));
        shadowView.setLayoutParams(params);
        shadowView.setBackground(ContextCompat.getDrawable(context, R.drawable.shadow_top));
        shadowView.setOnClickListener(v -> {
            close();
        });
        bottomSheet.setOnClickListener(v -> {
            close();
        });

        bottomSheet.addView(shadowView);
    }

    public void close() {
        appBarAwareBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    public void open() {
        appBarAwareBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
    }

    private void configureBottomSheetBehaviour() {
        appBarAwareBottomSheetBehavior.setPeekHeight(0);
        appBarAwareBottomSheetBehavior.addBottomSheetCallback(new BottomSheetCallbackAdapter() {

            @Override
            public void onExpanded(View bottomSheet) {
                if(onInteractListener != null) {
                    onInteractListener.onOpened();
                }
            }

            @Override
            public void onCollapsed(View bottomSheet) {
                if(onInteractListener != null) {
                    onInteractListener.onClosed();
                }
            }});
    }

    public void configureContents() {

        View bottomSheetUserContent = getChildAt(0);
        removeView(bottomSheetUserContent);
        if(bottomSheetUserContent.getBackground() == null) {
            TypedValue typedValue = new TypedValue();
            Resources.Theme theme = getContext().getTheme();
            theme.resolveAttribute(R.attr.colorSurface, typedValue, true);
            @ColorInt int color = typedValue.data;
            bottomSheetUserContent.setBackgroundColor(color);
        }

//        CoordinatorLayout.LayoutParams thisParams = (LayoutParams) getLayoutParams();
//        if(thisParams == null) {
//            thisParams = new CoordinatorLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
//            setLayoutParams(thisParams);
//        }
        CoordinatorLayout.LayoutParams bottomSheetParams = new CoordinatorLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bottomSheetParams.setBehavior(appBarAwareBottomSheetBehavior);


        LinearLayout bottomSheetContainer = new LinearLayout(getContext());
        bottomSheetContainer.setOrientation(LinearLayout.VERTICAL);
        bottomSheetContainer.setLayoutParams(bottomSheetParams);
        //bottomSheetContainer.setBackgroundColor(Color.TRANSPARENT);
        addView(bottomSheetContainer);

        // add a shadow to the top of the child content
        addShadow(bottomSheetContainer, getContext());
        // add the original xml child as content
        bottomSheetContainer.addView(bottomSheetUserContent);

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
