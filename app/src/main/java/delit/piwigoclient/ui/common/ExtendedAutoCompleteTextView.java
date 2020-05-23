package delit.piwigoclient.ui.common;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;

import androidx.annotation.AttrRes;
import androidx.annotation.RequiresApi;
import androidx.annotation.StyleRes;

public class ExtendedAutoCompleteTextView extends AutoCompleteTextView {
    public ExtendedAutoCompleteTextView(Context context) {
        super(context);
    }

    public ExtendedAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExtendedAutoCompleteTextView(Context context, AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ExtendedAutoCompleteTextView(Context context, AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public ExtendedAutoCompleteTextView(Context context, AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes, Resources.Theme popupTheme) {
        super(context, attrs, defStyleAttr, defStyleRes, popupTheme);
    }

    @Override
    protected Filter getFilter() {
        return super.getFilter();
    }
}
