package delit.libs.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import java.io.File;
import java.util.ArrayList;

import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.R;

public class FileBreadcrumbsView extends FlowLayout {

    private NavigationListener navigationListener;

    public FileBreadcrumbsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setNavigationListener(NavigationListener navigationListener) {
        this.navigationListener = navigationListener;
    }

    public void populate(File f) {
        removeAllViews();
        ArrayList<File> pathItems = new ArrayList<>();
        while (!f.getName().isEmpty()) {
            pathItems.add(0, f);
            f = f.getParentFile();
        }
        int idx = 0;

        int verticalPaddingPx = DisplayUtils.dpToPx(getContext(), 3);
        int pathSepHorizontalPaddingPx = DisplayUtils.dpToPx(getContext(), 3);
        int pathItemHorizontalPaddingPx = DisplayUtils.dpToPx(getContext(), 9);

        for (final File pathItemFile : pathItems) {
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

    private TextView buildPathItem(final File pathItemFile, int horizontalPaddingPx, int verticalPaddingPx) {
        TextView pathItem = new TextView(getContext());
        pathItem.setId(View.generateViewId());
        TextViewCompat.setTextAppearance(pathItem, R.style.Custom_TextAppearance_AppCompat_Body2_Clickable);
        pathItem.setPaddingRelative(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx);
        pathItem.setText(pathItemFile.getName());
        pathItem.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.primary));
        pathItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationListener.onBreadcrumbClicked(pathItemFile);
            }
        });
        return pathItem;
    }

    public interface NavigationListener {

        void onBreadcrumbClicked(File pathItemFile);
    }
}
