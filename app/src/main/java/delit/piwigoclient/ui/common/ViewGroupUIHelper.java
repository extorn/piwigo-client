package delit.piwigoclient.ui.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by gareth on 17/10/17.
 */

public class ViewGroupUIHelper<VUIH extends ViewGroupUIHelper<VUIH, V>, V extends ViewGroup> extends UIHelper<VUIH, V> {
    public ViewGroupUIHelper(V parent, SharedPreferences prefs, Context context) {
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
