package delit.piwigoclient.ui.album.view;

import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import delit.piwigoclient.ui.util.ItemSpacingDecoration;

public class AlbumItemSpacingDecoration extends ItemSpacingDecoration {
    private final int multiColumnSpacing;

    public AlbumItemSpacingDecoration(int resourceSpacing, int multiColumnSpacing) {
        super(resourceSpacing);
        this.multiColumnSpacing = multiColumnSpacing;
    }

    @Override
    protected void setItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state, int position, int startsInCol, int cols, int horizontal, int vertical) {
        int colsSpanned = 1;
        if(parent.getLayoutManager() instanceof GridLayoutManager) {
            colsSpanned = ((GridLayoutManager) parent.getLayoutManager()).getSpanSizeLookup().getSpanSize(position);
        }
        if(colsSpanned > 1) {
            super.setItemOffsets(outRect, view, parent, state, position, startsInCol, cols, horizontal, multiColumnSpacing);
        } else {
            super.setItemOffsets(outRect, view, parent, state, position, startsInCol, cols, horizontal, vertical);
        }
    }
}
