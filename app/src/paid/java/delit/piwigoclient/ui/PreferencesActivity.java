package delit.piwigoclient.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IdRes;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.ui.album.drillDownSelect.CategoryItemViewAdapterPreferences;
import delit.piwigoclient.ui.album.drillDownSelect.RecyclerViewCategoryItemSelectFragment;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;
import delit.piwigoclient.ui.events.ViewJobStatusDetailsEvent;
import delit.piwigoclient.ui.events.trackable.AutoUploadJobViewRequestedEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionNeededEvent;
import delit.piwigoclient.ui.preferences.AutoUploadJobPreferenceFragment;
import delit.piwigoclient.ui.upload.UploadJobStatusDetailsFragment;

public class PreferencesActivity extends AbstractPreferencesActivity {

    private static final String TAG = "PrefAct";

    public static Intent buildIntent(Context context) {
        //Intent intent = new Intent(Intent.ACTION_APPLICATION_PREFERENCES, null, context.getApplicationContext(), PreferencesActivity.class);
        Intent intent = new Intent(context.getApplicationContext(), PreferencesActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @Override
    protected String getDesiredLanguage(Context context) {
        return AppPreferences.getDesiredLanguage(getSharedPrefs(context), context);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if(BuildConfig.DEBUG) {
            BundleUtils.logSize("Current Preferences Activity", outState);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final ViewJobStatusDetailsEvent event) {
        UploadJobStatusDetailsFragment fragment = UploadJobStatusDetailsFragment.newInstance(event.getJob());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AutoUploadJobViewRequestedEvent event) {
        AutoUploadJobPreferenceFragment fragment = AutoUploadJobPreferenceFragment.newInstance(event.getActionId(), event.getJobId());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ExpandingAlbumSelectionNeededEvent event) {
//        ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences prefs = new ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences();
//        AlbumSelectExpandableFragment f = AlbumSelectExpandableFragment.newInstance(prefs, event.getActionId(), event.getInitialSelection());
        CategoryItemViewAdapterPreferences prefs = new CategoryItemViewAdapterPreferences();
        if(event.isAllowEditing()) {
            prefs.selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        }
        if(event.getInitialRoot() != null) {
            prefs.withInitialRoot(new CategoryItemStub("???", event.getInitialRoot()));
        } else {
            prefs.withInitialRoot(CategoryItemStub.ROOT_GALLERY);
        }
        prefs.setAllowItemAddition(true);
        prefs.withInitialSelection(event.getInitialSelection());
        RecyclerViewCategoryItemSelectFragment f = RecyclerViewCategoryItemSelectFragment.newInstance(prefs, event.getActionId());
        showFragmentNow(f);
    }
}
