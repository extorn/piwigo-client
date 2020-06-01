package delit.libs.ui.view.list;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.R;

/**
 * Created by gareth on 02/04/18.
 */

public class StaticListView extends ListView {
    public StaticListView(Context context) {
        super(context);
        addDemoData();
    }

    public StaticListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        addDemoData();
    }

    public StaticListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        addDemoData();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public StaticListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        addDemoData();
    }

    private void addDemoData() {
        if (!isInEditMode()) {
            return;
        }
        List<String> items = new ArrayList();
        for(int i = 1; i < 20; i++) {
            items.add("Example list item "+ i);
        }
        setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, items));
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
