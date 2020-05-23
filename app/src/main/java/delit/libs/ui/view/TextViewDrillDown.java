package delit.libs.ui.view;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Filterable;
import android.widget.ListAdapter;

import androidx.annotation.AttrRes;

import delit.libs.ui.view.list.NonFilteringAdapterWrapper;

public class TextViewDrillDown extends androidx.appcompat.widget.AppCompatAutoCompleteTextView {
    public TextViewDrillDown(Context context) {
        super(context);
        setFocusable(false);
        setCursorVisible(false);
    }

    public TextViewDrillDown(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setCursorVisible(false);
    }

    public TextViewDrillDown(Context context, AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setFocusable(false);
        setCursorVisible(false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null && imm.isActive(this)) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }
}
