package delit.libs.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;

import androidx.annotation.AttrRes;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.widget.LinearLayoutCompat;

/**
 * Created by gareth on 18/05/17.
 */

public class SquareLinearLayout extends LinearLayoutCompat {
    public SquareLinearLayout(Context context) {
        super(context);
    }

    public SquareLinearLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareLinearLayout(Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if(LayoutParams.MATCH_PARENT == getLayoutParams().width) {
            if(width == 0) {
                // match parent will always be 0.
                width = ((ViewGroup)getParent()).getLayoutParams().width;
            }
            height = width;
        }
        if (LayoutParams.MATCH_PARENT == getLayoutParams().height) {
            if(height == 0) {
                // match parent will always be 0.
                height = ((ViewGroup)getParent()).getMeasuredHeight();
            }
            width = height;
        }
        if(height > 0 && width > 0) {
            setMeasuredDimension(width, height);
        }
    }
}
