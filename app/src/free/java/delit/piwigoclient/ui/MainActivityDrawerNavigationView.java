package delit.piwigoclient.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Menu;

import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.ui.common.ViewGroupUIHelper;

/**
 * Created by gareth on 08/06/17.
 */

public class MainActivityDrawerNavigationView<V extends BaseMainActivityDrawerNavigationView<V,VUIH>, VUIH extends ViewGroupUIHelper<VUIH,V>> extends BaseMainActivityDrawerNavigationView<V,VUIH> {

    public MainActivityDrawerNavigationView(Context context) {
        this(context, null);
    }

    public MainActivityDrawerNavigationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MainActivityDrawerNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void setMenuVisibilityToMatchSessionState(boolean isReadOnly) {
        super.setMenuVisibilityToMatchSessionState(isReadOnly);
        Menu m = getMenu();
        m.findItem(R.id.nav_app_purchases).setVisible(!AppPreferences.isHidePaymentOptionForever(getPrefs(), getContext()));
    }

}
