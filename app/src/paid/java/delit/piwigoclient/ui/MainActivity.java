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

import delit.libs.ui.view.ProgressIndicator;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.ActivityUIHelper;
import delit.piwigoclient.ui.events.ServerUpdatesAvailableEvent;
import delit.piwigoclient.ui.events.ViewJobStatusDetailsEvent;
import delit.piwigoclient.ui.events.ViewTagEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionNeededEvent;
import delit.piwigoclient.ui.upload.status.UploadJobStatusDetailsFragment;

/**
 * Created by gareth on 07/04/18.
 */

public class MainActivity<A extends MainActivity<A, AUIH>, AUIH extends ActivityUIHelper<AUIH, A>> extends AbstractMainActivity<A, AUIH> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TagSelectionNeededEvent event) {
        super.onEvent(event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ViewTagEvent event) {
        super.onEvent(event);
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

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final ViewJobStatusDetailsEvent event) {
        if (event.isHandled()) {
            return;
        }
        UploadJobStatusDetailsFragment<?,?> fragment = UploadJobStatusDetailsFragment.newInstance(event.getJob());
        showFragmentNow(fragment);
    }

}
