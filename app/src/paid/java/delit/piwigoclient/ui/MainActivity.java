package delit.piwigoclient.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;

import delit.libs.ui.view.ProgressIndicator;
import delit.libs.util.VersionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.ui.events.ServerUpdatesAvailableEvent;
import delit.piwigoclient.ui.events.ViewJobStatusDetailsEvent;
import delit.piwigoclient.ui.events.ViewTagEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionNeededEvent;
import delit.piwigoclient.ui.favorites.ViewFavoritesFragment;
import delit.piwigoclient.ui.orphans.ViewOrphansFragment;
import delit.piwigoclient.ui.tags.TagRecyclerViewAdapter;
import delit.piwigoclient.ui.tags.TagSelectFragment;
import delit.piwigoclient.ui.tags.TagsListFragment;
import delit.piwigoclient.ui.tags.ViewTagFragment;
import delit.piwigoclient.ui.upload.UploadJobStatusDetailsFragment;

/**
 * Created by gareth on 07/04/18.
 */

public class MainActivity extends AbstractMainActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void showFavorites() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        int[] pluginVersion = VersionUtils.parseVersionString(sessionDetails.getPiwigoClientPluginVersion());
        boolean versionSupported = VersionUtils.versionExceeds(new int[]{1,0,8}, pluginVersion);
        if(versionSupported) {
            showFragmentNow(ViewFavoritesFragment.newInstance());
        } else {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_plugin_required_pattern, "PiwigoClientWsExts", "1.0.8"), R.string.button_close);
        }
    }

    @Override
    protected void showOrphans() {
        boolean restore = false;
        // check if we've shown any albums before. If so, pop everything off the stack.
        if (null == getSupportFragmentManager().findFragmentByTag(ViewOrphansFragment.class.getName())) {
            // we're opening the activity freshly.

            // check for reopen details and use them instead if possible.
            if (ViewOrphansFragment.canHandleReopenAction(getUiHelper())) {
                restore = true;
            }
        }
        AdsManager.getInstance(this).showAlbumBrowsingAdvertIfAppropriate(this);

        if (restore) {
            showFragmentNow(ViewOrphansFragment.newInstance());
        } else {
            showFragmentNow(ViewOrphansFragment.newInstance(), false);
        }
    }

    @Override
    protected void showTags() {
        TagRecyclerViewAdapter.TagViewAdapterPreferences prefs = new TagRecyclerViewAdapter.TagViewAdapterPreferences();
        prefs.setEnabled(true);
        prefs.setAllowItemAddition(true);
        //TODO tags deletion is not working (unsupported in piwigo server at the moment)
//        if(PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
//            prefs.deletable();
//        }
        TagsListFragment<?,?> fragment = TagsListFragment.newInstance(prefs);
        showFragmentNow(fragment);
    }

    private void showTagSelectionFragment(int actionId, TagRecyclerViewAdapter.TagViewAdapterPreferences prefs, HashSet<Long> initialSelection, HashSet<Tag> unsavedTags) {
        TagSelectFragment<?,?> fragment = TagSelectFragment.newInstance(prefs, actionId, initialSelection, unsavedTags);
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TagSelectionNeededEvent event) {
        TagRecyclerViewAdapter.TagViewAdapterPreferences prefs = new TagRecyclerViewAdapter.TagViewAdapterPreferences(event.isAllowEditing(), event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        showTagSelectionFragment(event.getActionId(), prefs , event.getInitialSelection(), event.getNewUnsavedTags());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ServerUpdatesAvailableEvent event) {
        String serverText = "";
        String pluginsText = "";
        boolean showMsg = false;
        if(event.isServerUpdateAvailable()) {
            serverText = getString(R.string.piwigo_server);
            showMsg = true;
        }
        if(event.isPluginUpdateAvailable()) {
            pluginsText = getString(R.string.piwigo_server_plugin);
            showMsg = true;
        }
        if(showMsg) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.piwigo_server_updates_available_pattern, serverText, pluginsText));
        }
    }

    protected void showPrivacy() {
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_EditPages));
        View view = LayoutInflater.from(dialogBuilder.getContext()).inflate(R.layout.layout_dialog_privacy_information, null);
        ProgressIndicator progressIndicator = view.findViewById(R.id.website_loading_progress);
        progressIndicator.showProgressIndicator(R.string.loading_please_wait, 0);
        TextView privacyField = view.findViewById(R.id.privacy_policy);
        privacyField.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(v.getContext().getString(R.string.privacy_policy_uri));
            intent.setDataAndType(uri, "text/html");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            v.getContext().startActivity(Intent.createChooser(intent, v.getContext().getString(R.string.open_link)));
        });
        WebView myWebView = view.findViewById(R.id.privacy_text);
        myWebView.setWebChromeClient(new WebChromeClient(){

            public void onProgressChanged(WebView view, int progress) {
                progressIndicator.showProgressIndicator(R.string.loading_please_wait, progress * 100);
                if(progress == 100) {
                    progressIndicator.hideProgressIndicator();
                }
            }
        });
        myWebView.loadUrl(getString(R.string.privacy_policy_uri));
        dialogBuilder.setView(view);
        dialogBuilder.setPositiveButton(android.R.string.ok, (dialog, which) -> {});

        dialogBuilder.show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ViewTagEvent event) {
        ViewTagFragment<?,?> fragment = ViewTagFragment.newInstance(event.getTag());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final ViewJobStatusDetailsEvent event) {
        if (event.isHandled()) {
            return;
        }
        UploadJobStatusDetailsFragment<?,?> fragment = UploadJobStatusDetailsFragment.newInstance(event.getJob());
        showFragmentNow(fragment);
    }

}
