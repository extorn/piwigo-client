package delit.piwigoclient.ui.util;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ItemSpacingDecoration extends RecyclerView.ItemDecoration {

    private final int vertical;
    private final int horizontal;

    public ItemSpacingDecoration(@IntRange(from=0)int all) {
        this(all,all);
    }

    public ItemSpacingDecoration(@IntRange(from=0)int horizontal, @IntRange(from=0)int vertical) {
        super();
        this.horizontal = horizontal;
        this.vertical = vertical;
    }
    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildLayoutPosition(view);
        int cols = 1;
        int startsInCol = 0;
        if(parent.getLayoutManager() instanceof GridLayoutManager) {
            cols = ((GridLayoutManager) parent.getLayoutManager()).getSpanCount();
            startsInCol = ((GridLayoutManager) parent.getLayoutManager()).getSpanSizeLookup().getSpanIndex(position, cols);
        }
        setItemOffsets(outRect, view, parent, state, position, startsInCol, cols, horizontal, vertical);
    }

    protected void setItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state, int position, int startsInCol, int cols, int horizontalMargin, int verticalMargin) {
        boolean atBottom = position == state.getItemCount();
        if(!atBottom) {
            int rows = (int)Math.ceil(((double)state.getItemCount()) / cols);
            atBottom = position >= ((rows - 1) * cols);
        }
        applyItemOffsets(outRect, atBottom,startsInCol == 0, horizontalMargin, verticalMargin);
    }

    protected void applyItemOffsets(Rect outRect, boolean atBottom, boolean onLhs, int horizontalMargin, int verticalMargin) {
        if(!onLhs) {
            outRect.left = horizontalMargin;
        }
        if(!atBottom) {
            outRect.bottom = verticalMargin;
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        //called when the parent is drawn
    }
}
