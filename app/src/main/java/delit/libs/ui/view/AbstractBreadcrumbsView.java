package delit.libs.ui.view;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.drew.lang.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;

import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.R;

public abstract class AbstractBreadcrumbsView<T> extends FlowLayout {

    private NavigationListener navigationListener;

    public AbstractBreadcrumbsView(Context context) {
        super(context, null);
    }

    public AbstractBreadcrumbsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AbstractBreadcrumbsView(Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public AbstractBreadcrumbsView(Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setNavigationListener(NavigationListener navigationListener) {
        this.navigationListener = navigationListener;
    }

    protected abstract @Nullable T getParent(@NonNull T item);

    protected abstract String getItemName(@NonNull T item);

    public void populate(@Nullable T item) {
        removeAllViews();
        ArrayList<T> pathItems = new ArrayList<>();
        if(item != null) {
            do {
                pathItems.add(0, item);
                item = getParent(item);
                if (pathItems.get(0).equals(item) || pathItems.size() > 100) {
                    throw new IllegalStateException("Infinite loop averted");
                }
            } while (item != null);
        }
        int idx = 0;

        int verticalPaddingPx = DisplayUtils.dpToPx(getContext(), 3);
        int pathSepHorizontalPaddingPx = DisplayUtils.dpToPx(getContext(), 3);
        int pathItemHorizontalPaddingPx = DisplayUtils.dpToPx(getContext(), 9);

        for (final T pathItemFile : pathItems) {
            idx++;
            addView(buildPathItem(pathItemFile, pathItemHorizontalPaddingPx, verticalPaddingPx));

            if (idx < pathItems.size()) {
                addView(buildPathItemSeparator(pathSepHorizontalPaddingPx, verticalPaddingPx));
            }
        }
    }

    private TextView buildPathItemSeparator(int pathSepHorizontalPaddingPx, int verticalPaddingPx) {
        TextView pathItemSeperator = new TextView(getContext());
        TextViewCompat.setTextAppearance(pathItemSeperator, R.style.TextAppearance_AppCompat_Body2);
        pathItemSeperator.setText("/");
        pathItemSeperator.setPaddingRelative(pathSepHorizontalPaddingPx, verticalPaddingPx, pathSepHorizontalPaddingPx, verticalPaddingPx);
        pathItemSeperator.setId(View.generateViewId());
        return pathItemSeperator;
    }

    private TextView buildPathItem(final T pathItemFile, int horizontalPaddingPx, int verticalPaddingPx) {
        TextView pathItem = new TextView(getContext());
        pathItem.setId(View.generateViewId());
        TextViewCompat.setTextAppearance(pathItem, R.style.Custom_TextAppearance_AppCompat_Body2_Clickable);
        pathItem.setPaddingRelative(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx);
        pathItem.setText(getItemName(pathItemFile));
        pathItem.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.primary));
        pathItem.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationListener.onBreadcrumbClicked(pathItemFile);
            }
        });
        return pathItem;
    }

    public interface NavigationListener<T> {
        void onBreadcrumbClicked(T pathItemFile);
    }
}
