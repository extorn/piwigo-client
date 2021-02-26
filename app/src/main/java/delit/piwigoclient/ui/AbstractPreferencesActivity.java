package delit.piwigoclient.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.AppBarLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.CustomToolbar;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.ui.album.drillDownSelect.CategoryItemViewAdapterPreferences;
import delit.piwigoclient.ui.album.drillDownSelect.RecyclerViewCategoryItemSelectFragment;
import delit.piwigoclient.ui.common.ActivityUIHelper;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;
import delit.piwigoclient.ui.events.StatusBarChangeEvent;
import delit.piwigoclient.ui.events.ToolbarEvent;
import delit.piwigoclient.ui.events.ViewJobStatusDetailsEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionNeededEvent;
import delit.piwigoclient.ui.preferences.PreferencesFragment;
import delit.piwigoclient.ui.upload.status.UploadJobStatusDetailsFragment;

public abstract class AbstractPreferencesActivity<A extends AbstractPreferencesActivity<A,AUIH>, AUIH extends ActivityUIHelper<AUIH, A>> extends MyActivity<A,AUIH> {

    private static final String TAG = "PrefAct";
    private CustomToolbar toolbar;
    private AppBarLayout appBar;


    public AbstractPreferencesActivity() {
        super(R.layout.activity_preferences);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.preferences_overall_heading);
        setSupportActionBar(toolbar);
        appBar = findViewById(R.id.appbar);
        DrawerLayout drawer = configureDrawer(toolbar);

        showFragmentNow(new PreferencesFragment());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onNavigationItemSelected(NavigationItemSelectEvent event) {
        // Handle navigation view item clicks here.
        int id = event.navigationitemSelected;

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        }
        if (id == R.id.nav_gallery) {
            showGallery();
        } else {
            onNavigationItemSelected(event, id);
        }
    }

    protected void onNavigationItemSelected(NavigationItemSelectEvent event, @IdRes int itemId) {
    }

    private void showGallery() {
        try {
            startActivity(MainActivity.buildShowGalleryIntent(this));
        } catch(ActivityNotFoundException e) {
            Logging.recordException(e);
        }
    }

    @Override
    protected String getDesiredLanguage(Context context) {
        return AppPreferences.getDesiredLanguage(getSharedPrefs(context), context);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if(BuildConfig.DEBUG) {
            BundleUtils.logSize("Current Preferences Activity", outState);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            return; // don't mess with the status bar
        }

        View v = getWindow().getDecorView();
        v.setFitsSystemWindows(!hasFocus);

        if (hasFocus) {
            DisplayUtils.setUiFlags(this, AppPreferences.isAlwaysShowNavButtons(prefs, this), AppPreferences.isAlwaysShowStatusBar(prefs, this));
            Logging.log(Log.ERROR, TAG, "hiding status bar!");
        } else {
            Logging.log(Log.ERROR, TAG, "showing status bar!");
        }

        v.requestApplyInsets();
        EventBus.getDefault().post(new StatusBarChangeEvent(!hasFocus));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final ViewJobStatusDetailsEvent event) {
        if (event.isHandled()) {
            return;
        }
        UploadJobStatusDetailsFragment<?,?> fragment = UploadJobStatusDetailsFragment.newInstance(event.getJob());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ExpandingAlbumSelectionNeededEvent event) {
//        ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences prefs = new ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences();
//        AlbumSelectExpandableFragment f = AlbumSelectExpandableFragment.newInstance(prefs, event.getActionId(), event.getInitialSelection());
        CategoryItemViewAdapterPreferences prefs = new CategoryItemViewAdapterPreferences(event.getInitialRoot(), event.isAllowEditing(), event.getInitialSelection(), event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        prefs.withConnectionProfile(event.getConnectionProfileName());
        RecyclerViewCategoryItemSelectFragment<?,?> f = RecyclerViewCategoryItemSelectFragment.newInstance(prefs, event.getActionId());
        showFragmentNow(f);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ToolbarEvent event) {
        if(!this.equals(event.getActivity())) {
            return;
        }
        if(toolbar == null) {
            Log.e(TAG, "Cannot set title. Toolbar not initialised yet");
            return;
        }
        toolbar.setTitle(event.getTitle());
        if(event.isExpandToolbarView()) {
            ((AppBarLayout) toolbar.getParent()).setExpanded(true, true);
        } else if(event.isContractToolbarView()) {
            ((AppBarLayout) toolbar.getParent()).setExpanded(false, true);
        }
        appBar.setEnabled(event.getTitle()!= null);
    }
}
