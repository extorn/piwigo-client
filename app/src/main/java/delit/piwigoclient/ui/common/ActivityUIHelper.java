package delit.piwigoclient.ui.common;

import android.content.SharedPreferences;
import android.view.View;

import androidx.fragment.app.Fragment;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.fragment.MyFragment;

/**
 * Created by gareth on 17/10/17.
 */

public class ActivityUIHelper extends UIHelper<MyActivity> {
    public ActivityUIHelper(MyActivity parent, SharedPreferences prefs) {
        super(parent, prefs, parent);
    }

    @Override
    protected boolean canShowDialog() {
        return super.canShowDialog() && !getParent().isFinishing();
    }

    @Override
    protected View getParentView() {
        View v = getParent().getWindow().getDecorView().findViewById(android.R.id.content);
        View iv = v.findViewById(R.id.main_view);
        return iv != null ? iv : v;
    }

    @Override
    protected DismissListener buildDialogDismissListener() {
        return new CustomDismissListener();
    }

    class CustomDismissListener extends DismissListener {
        @Override
        protected void onNoDialogToShow() {
            Fragment f = getParent().getActiveFragment();
            if(f instanceof MyFragment) {
                UIHelper helper = ((MyFragment)f).getUiHelper();
                if(helper != null) {
                    helper.showNextQueuedMessage();
                }
            }
        }
    }
}
