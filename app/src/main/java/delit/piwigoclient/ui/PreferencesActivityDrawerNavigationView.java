package delit.piwigoclient.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.ui.common.DrawerNavigationView;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.ViewGroupUIHelper;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.LockAppEvent;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;

/**
 * Created by gareth on 08/06/17.
 */

public class PreferencesActivityDrawerNavigationView extends NavigationView implements NavigationView.OnNavigationItemSelectedListener, DrawerNavigationView {

    private static final String TAG = "NavView";
    private SharedPreferences prefs;
    private ViewGroupUIHelper uiHelper;

    public PreferencesActivityDrawerNavigationView(Context context) {
        this(context, null);
    }

    public PreferencesActivityDrawerNavigationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PreferencesActivityDrawerNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setNavigationItemSelectedListener(this);
    }

    @Override
    public void inflateMenu(int resId) {
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        if (uiHelper == null) {
            if (!isInEditMode()) {
                // don't do this if showing in the IDE.
                uiHelper = new ViewGroupUIHelper<>(this, prefs, getContext());
                BasicPiwigoResponseListener listener = new BasicPiwigoResponseListener();
                listener.withUiHelper(this, uiHelper);
                uiHelper.setPiwigoResponseListener(listener);
            }
        }
        super.inflateMenu(resId);
        setMenuVisibilityToMatchSessionState();
        if (!isInEditMode()) {
            uiHelper.registerToActiveServiceCalls();
        }
        EventBus.getDefault().register(this);
    }

    public void setMenuVisibilityToMatchSessionState() {
        boolean isReadOnly = prefs.getBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), false);
        setMenuVisibilityToMatchSessionState(isReadOnly);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            default:
            EventBus.getDefault().post(new NavigationItemSelectEvent(item.getItemId()));
        }
        return true;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        EventBus.getDefault().unregister(this);
        uiHelper.deregisterFromActiveServiceCalls();
        SavedState outstate = (SavedState) super.onSaveInstanceState();
        SavedState myState = new SavedState(outstate);
        myState.menuState = new Bundle();
        uiHelper.onSaveInstanceState(myState.menuState);
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable savedState) {
        SavedState myState = (SavedState) savedState;
        uiHelper.onRestoreSavedInstanceState(myState.menuState);
        super.onRestoreInstanceState(((SavedState) savedState).getSuperState());
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final LockAppEvent event) {
        lockAppInReadOnlyMode(true);
        EventBus.getDefault().post(new AppLockedEvent());
    }

    private void setMenuVisibilityToMatchSessionState(boolean isReadOnly) {
        Menu m = getMenu();
        if (m != null) {
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        }
    }

    private void lockAppInReadOnlyMode(boolean lockApp) {
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), lockApp);
        prefsEditor.commit();
        setMenuVisibilityToMatchSessionState(lockApp);
    }

    @Override
    public void onDrawerOpened() {
        uiHelper.showUserHint(TAG, 1, R.string.hint_navigation_panel_1);
    }

}
