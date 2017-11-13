package delit.piwigoclient.ui.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.ViewGroup;

/**
 * Created by gareth on 17/10/17.
 */

public class ViewGroupUIHelper extends UIHelper<ViewGroup> {
    public ViewGroupUIHelper(ViewGroup parent, SharedPreferences prefs, Context context) {
        super(parent, prefs, context);
    }

    @Override
    protected boolean canShowDialog() {
        return getParent().isShown();
    }
}
