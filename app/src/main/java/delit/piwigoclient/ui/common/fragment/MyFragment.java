package delit.piwigoclient.ui.common.fragment;

import android.app.Activity;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.ToolbarEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class MyFragment extends Fragment {

    private static final String STATE_ACTIVE_SESSION_TOKEN = "activeSessionToken";
    private static final String STATE_ACTIVE_SERVER_CONNECTION = "activeServerConnection";
    protected SharedPreferences prefs;
    // Stored state below here.
    private FragmentUIHelper uiHelper;
    private String piwigoSessionToken;
    private String piwigoServerConnected;

    protected long addActiveServiceCall(@StringRes int titleStringId, long messageId) {
        return addActiveServiceCall(getString(titleStringId), messageId);
    }

    protected long addNonBlockingActiveServiceCall(@StringRes int titleStringId, long messageId) {
        uiHelper.addNonBlockingActiveServiceCall(getString(titleStringId), messageId);
        return messageId;
    }

    protected long addNonBlockingActiveServiceCall(String title, long messageId) {
        uiHelper.addNonBlockingActiveServiceCall(title, messageId);
        return messageId;
    }

    protected long addActiveServiceCall(String title, long messageId) {
        uiHelper.addActiveServiceCall(title, messageId);
        return messageId;
    }

    @Override
    public void onDetach() {
        Crashlytics.log("onDetach : " + getClass().getName());
        uiHelper.deregisterFromActiveServiceCalls();
        uiHelper.closeAllDialogs();
        super.onDetach();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        Crashlytics.log("onSaveInstanceState : " + getClass().getName());
        uiHelper.onSaveInstanceState(outState);
        outState.putString(STATE_ACTIVE_SESSION_TOKEN, piwigoSessionToken);
        outState.putString(STATE_ACTIVE_SERVER_CONNECTION, piwigoServerConnected);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onAttach(Context context) {
        Crashlytics.log("onAttach : " + getClass().getName());
        prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        if (uiHelper == null) {
            uiHelper = buildUIHelper(context);
            BasicPiwigoResponseListener listener = buildPiwigoResponseListener(context);
            listener.withUiHelper(this, uiHelper);
            uiHelper.setPiwigoResponseListener(listener);
        }
        super.onAttach(context);
    }

    protected FragmentUIHelper buildUIHelper(Context context) {
        return new FragmentUIHelper(this, prefs, context);
    }

    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new BasicPiwigoResponseListener();
    }

    protected boolean isSessionDetailsChanged() {
        return !PiwigoSessionDetails.matchesSessionToken(ConnectionPreferences.getActiveProfile(), piwigoSessionToken);
    }

    protected boolean isServerConnectionChanged() {
        return !PiwigoSessionDetails.matchesServerConnection(ConnectionPreferences.getActiveProfile(), piwigoServerConnected);
    }

    protected void updateActiveSessionDetails() {
        piwigoSessionToken = PiwigoSessionDetails.getActiveSessionToken(ConnectionPreferences.getActiveProfile());
        piwigoServerConnected = PiwigoSessionDetails.getActiveServerConnection(ConnectionPreferences.getActiveProfile());
    }

    @Override
    public void onPause() {
        Crashlytics.log("onPause : " + getClass().getName());
        super.onPause();
    }

    @Override
    public void onResume() {
        Crashlytics.log("onResume : " + getClass().getName());
        super.onResume();

        updatePageTitle();

        // This block wrapper is to hopefully protect against a WindowManager$BadTokenException when showing a dialog as part of this call.
        if (getActivity().isDestroyed() || getActivity().isFinishing()) {
            return;
        }

        Context context = getContext();
        if (uiHelper.isContextOutOfSync(context)) {
            uiHelper.swapToNewContext(context);
        }
        uiHelper.handleAnyQueuedPiwigoMessages();
        uiHelper.showNextQueuedMessage();
        if(AdsManager.getInstance().hasAdvertLoadProblem(getContext())) {
            Crashlytics.log(Log.INFO, getTag(), "warning user that adverts are unavailable");
            prefs.edit().putLong(AdsManager.BLOCK_MILLIS_PREF, 5000).commit();
            uiHelper.showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_message_advert_load_error), R.string.button_ok, false, new AdLoadErrorDialogListener());
        }
    }

    protected void updatePageTitle() {
        ToolbarEvent event = new ToolbarEvent();
        event.setTitle(buildPageHeading());
        EventBus.getDefault().post(event);
    }

    protected String buildPageHeading() {
        return "Piwigo Client";
    }

    private class AdLoadErrorDialogListener extends UIHelper.QuestionResultAdapter {

        private long shownAt;
        LifecycleObserver observer;

        @Override
        public void onShow(AlertDialog alertDialog) {
            super.onShow(alertDialog);
            observer = new LifecycleObserver() {
                @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
                public void onPause() {
                    shownAt = System.currentTimeMillis();
                }
            };
            getActivity().getLifecycle().addObserver(observer);
            shownAt = System.currentTimeMillis();
        }

        @Override
        public void onDismiss(AlertDialog dialog) {
            if(System.currentTimeMillis() < shownAt + 5000) {
                dialog.show();
//                uiHelper.showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_message_advert_load_error), R.string.button_ok, false, this);
            } else {
                FragmentActivity a = getActivity();
                if(a != null) {
                    a.getLifecycle().removeObserver(observer);
                    prefs.edit().putLong(AdsManager.BLOCK_MILLIS_PREF, 0).commit();
                }
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            Crashlytics.log("onCreateView(restore) : " + getClass().getName());
            uiHelper.onRestoreSavedInstanceState(savedInstanceState);
            piwigoSessionToken = savedInstanceState.getString(STATE_ACTIVE_SESSION_TOKEN);
            piwigoServerConnected = savedInstanceState.getString(STATE_ACTIVE_SERVER_CONNECTION);
        } else {
            Crashlytics.log("onCreateView(fresh) : " + getClass().getName());
        }
        if (piwigoSessionToken == null) {
            updateActiveSessionDetails();
        }

        doInOnCreateView();

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    /**
     * Currently registers for active service calls.
     */
    protected void doInOnCreateView() {
        uiHelper.registerToActiveServiceCalls();
    }

    public FragmentUIHelper getUiHelper() {
        return uiHelper;
    }

    protected boolean isAppInReadOnlyMode() {
        return prefs.getBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), false);
    }

}
