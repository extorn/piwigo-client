package delit.piwigoclient.ui.common.list;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.GridView;
import android.widget.ListView;

/**
 * Created by gareth on 02/04/18.
 */

public class StaticGridView extends GridView {
    public StaticGridView(Context context) {
        super(context);
    }

    public StaticGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StaticGridView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 4, MeasureSpec.AT_MOST));
    }


}
