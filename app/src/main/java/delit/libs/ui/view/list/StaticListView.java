package delit.libs.ui.view.list;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ListView;

import androidx.annotation.RequiresApi;

import delit.libs.ui.util.DisplayUtils;

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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public StaticListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int overrideMeasureSpec = heightMeasureSpec;
        if(MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
//            int desiredSize = MeasureSpec.getSize(heightMeasureSpec);
//            String desiredMode = DisplayUtils.getMeasureModeText(MeasureSpec.getMode(heightMeasureSpec));
            overrideMeasureSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 4, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, overrideMeasureSpec);
    }


}
