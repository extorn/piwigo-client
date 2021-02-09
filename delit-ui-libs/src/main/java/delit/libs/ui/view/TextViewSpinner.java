package delit.libs.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.BaseAdapter;
import android.widget.Filterable;
import android.widget.ListAdapter;

import androidx.annotation.AttrRes;

import delit.libs.ui.view.list.NonFilteringAdapterWrapper;

public class TextViewSpinner extends androidx.appcompat.widget.AppCompatAutoCompleteTextView {
    public TextViewSpinner(Context context) {
        super(context);
//        setFocusable(false);
        setCursorVisible(false);
    }

    public TextViewSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
//        setFocusable(false);
        setCursorVisible(false);
    }

    public TextViewSpinner(Context context, AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
//        setFocusable(false);
        setCursorVisible(false);
    }

    @Override
    public <T extends ListAdapter & Filterable> void setAdapter(T adapter) {
        if(adapter == null) {
            super.setAdapter(null);
        }
        super.setAdapter(new NonFilteringAdapterWrapper((BaseAdapter)adapter));
    }
}
