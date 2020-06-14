package delit.piwigoclient.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Filter;

import androidx.annotation.AttrRes;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;

public class ExtendedAutoCompleteTextView extends AppCompatAutoCompleteTextView {
    public ExtendedAutoCompleteTextView(Context context) {
        super(context);
    }

    public ExtendedAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExtendedAutoCompleteTextView(Context context, AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected Filter getFilter() {
        return super.getFilter();
    }
}
