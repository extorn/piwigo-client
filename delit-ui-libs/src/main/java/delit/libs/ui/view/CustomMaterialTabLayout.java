package delit.libs.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;

import com.google.android.material.tabs.TabLayout;

import delit.libs.R;

public class CustomMaterialTabLayout extends TabLayout {
    private int initialTabIdx;

    public CustomMaterialTabLayout(@NonNull Context context) {
        super(context);
    }

    public CustomMaterialTabLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initFromAttrs(context, attrs, 0, 0);
    }

    public CustomMaterialTabLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initFromAttrs(context, attrs, defStyleAttr, 0);
    }

    private void initFromAttrs(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        if(attrs == null) {
            return;
        }
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.CustomMaterialTabLayout, defStyleAttr, defStyleRes);
        initialTabIdx = a.getInt(R.styleable.CustomMaterialTabLayout_initialTabIdx, 0);
        a.recycle();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if(initialTabIdx != 0) {
            selectTab(getTabAt(initialTabIdx));
        }
    }
}
