package delit.piwigoclient.ui.permissions.users;

import android.os.Bundle;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.ui.common.FragmentUIHelper;

/**
 * Created by gareth on 21/06/17.
 */

public class UserFragment<F extends UserFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends BaseUserFragment<F,FUIH> {

    public static UserFragment<?,?> newInstance(User user) {
        UserFragment<?,?> fragment = new UserFragment<>();
        fragment.setTheme(R.style.Theme_App_EditPages);
        Bundle args = new Bundle();
        args.putParcelable(ARG_USER, user);
        fragment.setArguments(args);
        return fragment;
    }
}
