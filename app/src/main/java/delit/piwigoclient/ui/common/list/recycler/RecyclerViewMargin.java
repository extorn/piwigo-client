package delit.piwigoclient.ui.common.list.recycler;

import android.content.Context;
import android.graphics.Rect;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

import delit.piwigoclient.util.DisplayUtils;

public class RecyclerViewMargin extends RecyclerView.ItemDecoration {

    public static final int DEFAULT_MARGIN_DP = 10;

    private final int columns;
    private int margin;

    /**
     * constructor
     * @param marginDp desirable margin size in dp between the views in the recyclerView
     * @param columns number of columns of the RecyclerView
     */
    public RecyclerViewMargin(Context c, @IntRange(from=0)int marginDp , @IntRange(from=0) int columns ) {
        this.margin = DisplayUtils.dpToPx(c, marginDp);
        this.columns=columns;
    }

    /**
     * Set different margins for the items inside the recyclerView: no top margin for the first row
     * and no left margin for the first column.
     */
    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {

        int position = parent.getChildLayoutPosition(view);
        //set right margin to all
        outRect.right = margin;
        //set bottom margin to all
        outRect.bottom = margin;
        //we only add top margin to the first row
        if (position <columns) {
            outRect.top = margin;
        }
        //add left margin only to the first column
        if(position%columns==0){
            outRect.left = margin;
        }
    }
}
