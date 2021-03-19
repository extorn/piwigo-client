package delit.libs.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StyleRes;
import androidx.constraintlayout.helper.widget.Flow;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.widget.TextViewCompat;

import java.util.ArrayList;
import java.util.Arrays;

import delit.libs.R;
import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;

public abstract class AbstractBreadcrumbsView<T> extends ConstraintLayout {

    private static final String TAG = "BreadcrumbView";
    private PathNavigator<T> pathNavigator;
    private NavigationListener<T> navigationListener;
    private @IdRes int pathItemTextViewResId;
    private @LayoutRes int breadcrumbLayoutResId;
    private @LayoutRes int breadcrumbDivisionLayoutResId;
    private ArrayList<T> pathItems;

    public AbstractBreadcrumbsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public AbstractBreadcrumbsView(Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public AbstractBreadcrumbsView(Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setPathNavigator(PathNavigator<T> pathNavigator) {
        this.pathNavigator = pathNavigator;
    }

    @Override
    protected void onFinishInflate() {
        if(isInEditMode()) {
            // add some demo data
            DemoData demoData = new DemoData();
            setPathNavigator((PathNavigator<T>) demoData.getPathNavigator());
            populate((T)demoData.getItem());
        }
        super.onFinishInflate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if(getChildCount() > 0) {
            int desiredChildHeight = getChildAt(0).getMeasuredHeight();
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if(v.getLayoutParams().height == 0) {
                    v.getLayoutParams().height = desiredChildHeight;
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void init(Context context, AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.AbstractBreadcrumbsView, defStyleAttr, defStyleRes);

        breadcrumbLayoutResId = a.getResourceId(R.styleable.AbstractBreadcrumbsView_breadcrumbLayout, View.NO_ID);
        breadcrumbDivisionLayoutResId = a.getResourceId(R.styleable.AbstractBreadcrumbsView_breadcrumbDivisionLayout, View.NO_ID);
        pathItemTextViewResId = a.getResourceId(R.styleable.AbstractBreadcrumbsView_breadcrumbTextViewId, View.NO_ID);

        a.recycle();
        setPathNavigator(buildPathNavigator());
    }

    protected abstract PathNavigator<T> buildPathNavigator();

    public void setNavigationListener(NavigationListener<T> navigationListener) {
        this.navigationListener = navigationListener;
    }

    public int getBreadcrumbCount() {
        return pathItems.size();
    }

    public void populate(@Nullable T item) {
        removeAllViews();
        pathItems = new ArrayList<>();
        if(item != null) {
            do {
                pathItems.add(0, item);
                item = pathNavigator.getParent(item);
                if (pathItems.get(0).equals(item) || pathItems.size() > 100) {
                    throw new IllegalStateException("Infinite loop averted");
                }
            } while (item != null);
        }
        int idx = 0;

        if(!pathItems.isEmpty()){

            int verticalPaddingPx = DisplayUtils.dpToPx(getContext(), 3);
            int pathSepHorizontalPaddingPx = DisplayUtils.dpToPx(getContext(), 3);
            int pathItemHorizontalPaddingPx = DisplayUtils.dpToPx(getContext(), 9);

            View v;
            int[] ids = new int[pathItems.size() * 2 -1];
            for (final T pathItemFile : pathItems) {
                idx++;
                v = buildPathItem(idx, pathItemFile, pathItemHorizontalPaddingPx, verticalPaddingPx);
                v.setId(View.generateViewId());
                ids[(idx-1)*2] = v.getId();
                addView(v);

                if (idx < pathItems.size()) {
                    v = buildPathItemSeparator(pathSepHorizontalPaddingPx, verticalPaddingPx);
                    v.setId(View.generateViewId());
                    ids[(idx-1)*2 + 1] = v.getId();
                    addView(v);
                }
            }
            Flow flow = new Flow(getContext());
            flow.setVerticalAlign(Flow.VERTICAL_ALIGN_CENTER);
            flow.setHorizontalStyle(Flow.CHAIN_PACKED);
            flow.setWrapMode(Flow.WRAP_CHAIN);
            flow.setReferencedIds(ids);
            addView(flow);
        }
    }

    private View buildPathItemSeparator(int pathSepHorizontalPaddingPx, int verticalPaddingPx) {
        View pathItemSeparator;
        if(breadcrumbDivisionLayoutResId != View.NO_ID) {
            pathItemSeparator = LayoutInflater.from(getContext()).inflate(breadcrumbDivisionLayoutResId, null);
        } else if(breadcrumbLayoutResId == View.NO_ID) {
            pathItemSeparator = new TextView(getContext());
            TextViewCompat.setTextAppearance((TextView) pathItemSeparator, R.style.TextAppearance_AppCompat_Body2);
            ((TextView)pathItemSeparator).setText("/");
            pathItemSeparator.setPaddingRelative(pathSepHorizontalPaddingPx, verticalPaddingPx, pathSepHorizontalPaddingPx, verticalPaddingPx);
        } else {
            // don't show a path item separator.
            pathItemSeparator = null;
        }
//        pathItemSeparator.setId(View.generateViewId());
        return pathItemSeparator;
    }

    private View buildPathItem(int idx, final T pathItemFile, int horizontalPaddingPx, int verticalPaddingPx) {

        View pathItem;
        if(breadcrumbLayoutResId != View.NO_ID) {
            pathItem = LayoutInflater.from(getContext()).inflate(breadcrumbLayoutResId, null);
        } else {
            pathItem = new TextView(getContext());
            pathItem.setPaddingRelative(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx);
        }
        if(pathItem instanceof TextView) {
            ((TextView)pathItem).setText(pathNavigator.getItemName(pathItemFile));
        } else {
            if(pathItemTextViewResId == View.NO_ID) {
                throw new IllegalStateException("Either the breadcrumbLayout view must extend a text view, or the id of the text view must be declared");
            }
            TextView textView = pathItem.findViewById(pathItemTextViewResId);
            textView.setText(pathNavigator.getItemName(pathItemFile));
        }

//        pathItem.setId(View.generateViewId());
//        TextViewCompat.setTextAppearance(pathItem, R.style.Custom_TextAppearance_MaterialComponents_Body2_Clickable);


//        pathItem.setBackgroundColor(ContextCompat.getColor(getContext(), R.colors.app_primary));
        pathItem.setTag("pathIdx_"+idx);
        pathItem.setOnClickListener(v -> navigationListener.onBreadcrumbClicked(pathItemFile));
        return pathItem;
    }

    public boolean clickBreadcrumb(int breadcrumbIdx) {
        View v = findViewWithTag("pathIdx_"+breadcrumbIdx);
        if(v != null) {
            return v.callOnClick();
        } else {
            Logging.log(Log.ERROR, TAG, "Breadcrumb does not exist");
        }
        return false;
    }

    public interface NavigationListener<T> {
        void onBreadcrumbClicked(T pathItem);
    }

    public interface PathNavigator<T> {
        @Nullable T getParent(@NonNull T item);
        String getItemName(@NonNull T item);
    }


    private static class DemoData {
        ArrayList<String> items = new ArrayList<>(Arrays.asList("root","sub1","sub2", "sub3"));

        DemoPathNavigator getPathNavigator() {
            return new DemoPathNavigator();
        }

        public String getItem() {
            return items.get(items.size()-1);
        }

        public class DemoPathNavigator implements AbstractBreadcrumbsView.PathNavigator<String> {
            @Override
            public String getParent(@NonNull String item) {
                int idx = items.indexOf(item) -1;
                if(idx >= 0) {
                    return items.get(idx);
                }
                return null;
            }

            @Override
            public String getItemName(@NonNull String item) {
                return item;
            }
        }
    }
}
