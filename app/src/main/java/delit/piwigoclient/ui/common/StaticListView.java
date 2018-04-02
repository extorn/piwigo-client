package delit.piwigoclient.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

/**
 * Created by gareth on 02/04/18.
 */

public class StaticListView extends ListView {
    public StaticListView(Context context) {
        super(context);
    }

    public StaticListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StaticListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 4, MeasureSpec.AT_MOST));
    }


}
