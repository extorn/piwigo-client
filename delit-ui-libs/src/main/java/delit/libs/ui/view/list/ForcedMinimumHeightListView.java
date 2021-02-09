package delit.libs.ui.view.list;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ListView;

import androidx.annotation.RequiresApi;

/**
 * Created by gareth on 02/04/18.
 */

public class ForcedMinimumHeightListView extends ListView {
    public ForcedMinimumHeightListView(Context context) {
        super(context);
    }

    public ForcedMinimumHeightListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ForcedMinimumHeightListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ForcedMinimumHeightListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(getMeasuredWidth(), Math.max(getMeasuredHeight(), getMinimumHeight()));
    }


}
