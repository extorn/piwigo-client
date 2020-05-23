package delit.libs.ui.view;

import android.content.Context;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;

public class CustomToolbar extends MaterialToolbar {
    private TextView titleView;

    public CustomToolbar(Context context) {
        super(context);
    }

    public CustomToolbar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomToolbar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    @Override
    public void setTitle(int resId) {
        super.setTitle(resId);
        if(titleView != null) {
            titleView.setClickable(false);
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        if(titleView != null) {
            titleView.setClickable(false);
        }
    }

    public void setSpannableTitle(SpannableString spannedTitle) {
        initTitleTextField();
        setTitle(spannedTitle.toString());
        titleView.setText(spannedTitle, TextView.BufferType.SPANNABLE);
        titleView.setClickable(true);
        titleView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void initTitleTextField() {
        if(titleView == null) {
            CharSequence currentTitle = getTitle();
            setTitle("ABCDEFG");
            ArrayList<View> matchingViews = new ArrayList<>(1);
            findViewsWithText(matchingViews, "ABCDEFG", FIND_VIEWS_WITH_TEXT);
            if (matchingViews.size() != 1) {
                throw new IllegalStateException("Unable to find title text field");
            }
            setTitle(currentTitle);
            titleView = (TextView) matchingViews.get(0);
        }
    }
}
