package delit.piwigoclient.ui.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by gareth on 17/10/17.
 */

public class ViewGroupUIHelper<T extends ViewGroup> extends UIHelper<T> {
    public ViewGroupUIHelper(T parent, SharedPreferences prefs, Context context) {
        super(parent, prefs, context);
    }

    @Override
    protected View getParentView() {
        return getParent();
    }

    @Override
    protected boolean canShowDialog() {
        return getParent().isShown();
    }
}
