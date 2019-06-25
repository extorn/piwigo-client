package delit.libs.ui.view.list;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.List;

public class CheckableRelativeLayout extends RelativeLayout implements Checkable {

    private List<Checkable> checkableChildren = new ArrayList<>();
    private boolean checked;

    public CheckableRelativeLayout(Context context) {
        super(context);
    }

    public CheckableRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckableRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void setChecked(boolean checked) {
        this.checked = checked;
        updateCheckableChildrenToMatch();
    }

    @Override
    public void toggle() {
        checked = !checked;
    }

    private void updateCheckableChildrenToMatch() {
        for (Checkable child : checkableChildren) {
            child.setChecked(checked);
        }
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        checkableChildren.remove(child);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final int childCount = this.getChildCount();
        for (int i = 0; i < childCount; ++i) {
            collectCheckableChildren(this.getChildAt(i));
        }
    }

    private void collectCheckableChildren(View v) {
        if (v instanceof Checkable) {
            checkableChildren.add((Checkable) v);
        }

        if (v instanceof ViewGroup) {
            final ViewGroup vg = (ViewGroup) v;
            final int childCount = vg.getChildCount();
            for (int i = 0; i < childCount; ++i) {
                collectCheckableChildren(vg.getChildAt(i));
            }
        }
    }
}
