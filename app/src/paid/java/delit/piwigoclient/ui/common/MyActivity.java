package delit.piwigoclient.ui.common;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import delit.libs.core.util.Logging;
import delit.libs.util.Utils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.piwigoApi.upload.BackgroundUploadServiceEventHandler;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class MyActivity<A extends MyActivity<A,AUIH>, AUIH extends ActivityUIHelper<AUIH,A>> extends BaseMyActivity<A,AUIH> {

    private static final String TAG = "MyActivity";
    private final BackgroundUploadServiceEventHandler backgroundUploadServiceEventHandler = new BackgroundUploadServiceEventHandler();

    public MyActivity(@LayoutRes int contentView) {
        super(contentView);
    }

    @Override
    protected void onStart() {
        Logging.log(Log.DEBUG, TAG, "Starting activity %1$s", Utils.getId(this));
        super.onStart();
        backgroundUploadServiceEventHandler.register(getUiHelper());
        getUiHelper().showReleaseNotesIfNewlyUpgradedOrInstalled(BuildConfig.VERSION_NAME);
    }

    @Override
    public void onResume() {
        Logging.log(Log.DEBUG, TAG, "Resuming activity %1$s", Utils.getId(this));
        super.onResume();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        getUiHelper().showNextQueuedMessage();
        getUiHelper().showQueuedToastMsg();
    }

    @Override
    protected void onPause() {
        Logging.log(Log.DEBUG, TAG, "Pausing activity %1$s", Utils.getId(this));
        super.onPause();
    }

    @Override
    protected void onRestart() {
        Logging.log(Log.DEBUG, TAG, "ReStarting activity %1$s", Utils.getId(this));
        super.onRestart();
    }

    @Override
    public void onStop() {
        Logging.log(Log.DEBUG, TAG, "Stopping activity %1$s", Utils.getId(this));
        backgroundUploadServiceEventHandler.unregister();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Logging.log(Log.DEBUG, TAG, "Destroying activity %1$s", Utils.getId(this));
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        Logging.log(Log.DEBUG, TAG, "Saving state of activity %1$s", Utils.getId(this));
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        Logging.log(Log.DEBUG, TAG, "Restoring state activity %1$s", Utils.getId(this));
        super.onRestoreInstanceState(savedInstanceState);
    }
}
