package delit.piwigoclient.ui.common.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.PreferenceManager;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.ToolbarEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class MyFragment<T extends MyFragment<T>> extends Fragment {

    private static final String TAG = "MyFrag";
    private static final String STATE_ACTIVE_SESSION_TOKEN = "activeSessionToken";
    private static final String STATE_ACTIVE_SERVER_CONNECTION = "activeServerConnection";
    protected SharedPreferences prefs;
    // Stored state below here.
    private FragmentUIHelper<T> uiHelper;
    private String piwigoSessionToken;
    private String piwigoServerConnected;
    private boolean onInitialCreate;
    private @StyleRes int theme = Resources.ID_NULL;

    protected long addActiveServiceCall(@StringRes int titleStringId, AbstractPiwigoDirectResponseHandler worker) {
        return addActiveServiceCall(getString(titleStringId), worker);
    }

    protected long addNonBlockingActiveServiceCall(@StringRes int titleStringId, long messageId, String serviceDesc) {
        return uiHelper.addNonBlockingActiveServiceCall(getString(titleStringId), messageId, serviceDesc);
    }

    protected long addNonBlockingActiveServiceCall(@StringRes int titleStringId, AbstractPiwigoDirectResponseHandler worker) {
        return uiHelper.addNonBlockingActiveServiceCall(getString(titleStringId), worker);
    }

    protected long addNonBlockingActiveServiceCall(String title, AbstractPiwigoDirectResponseHandler worker) {
        return uiHelper.addNonBlockingActiveServiceCall(title, worker);
    }

    protected long addActiveServiceCall(String title, AbstractPiwigoDirectResponseHandler worker) {
        return uiHelper.addActiveServiceCall(title, worker);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        onInitialCreate = true;
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public Context getContext() {
        Context context = super.getContext();
        if(theme == Resources.ID_NULL) {
            return context;
        }
        if(context == null) {
            return context;
        }
        return new ContextThemeWrapper(context, theme);
    }

    @NonNull
    @Override
    public LayoutInflater onGetLayoutInflater(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflator = super.onGetLayoutInflater(savedInstanceState);
        if(theme == Resources.ID_NULL) {
            return inflator;
        }
        if(!(inflator.getContext() instanceof ContextThemeWrapper)) {
            inflator = inflator.cloneInContext(getContext());
        }
        return inflator;
    }


    @Override
    public void onStop() {
        Crashlytics.log("onDestroyView : " + getClass().getName());
        uiHelper.deregisterFromActiveServiceCalls();
        uiHelper.closeAllDialogs();
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Crashlytics.log("onSaveInstanceState : " + getClass().getName());
        uiHelper.onSaveInstanceState(outState);
        outState.putString(STATE_ACTIVE_SESSION_TOKEN, piwigoSessionToken);
        outState.putString(STATE_ACTIVE_SERVER_CONNECTION, piwigoServerConnected);
    }

    @Override
    public void onAttach(Context context) {
        Crashlytics.log("onAttach : " + getClass().getName());
        prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        super.onAttach(context);
    }

    protected FragmentUIHelper<T> buildUIHelper(Context context) {
        return new FragmentUIHelper<>((T) this, prefs, context);
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
        onInitialCreate = false;
        super.onPause();
    }

    public boolean isOnInitialCreate() {
        return onInitialCreate;
    }

    @Override
    public void onResume() {
        Crashlytics.log("onResume : " + getClass().getName());
        super.onResume();

        updatePageTitle();


        // This block wrapper is to hopefully protect against a WindowManager$BadTokenException when showing a dialog as part of this call.
        if ((getFragmentManager() != null && getFragmentManager().isDestroyed()) || getActivity().isFinishing()) {
            return;
        }

        Context context = getContext();
        if (uiHelper.isContextOutOfSync(context)) {
            uiHelper.swapToNewContext(context);
        }
        uiHelper.registerToActiveServiceCalls();
        uiHelper.handleAnyQueuedPiwigoMessages();
        uiHelper.showNextQueuedMessage();
        if(AdsManager.getInstance().hasAdvertLoadProblem(getContext())) {
            Crashlytics.log(Log.INFO, TAG, "warning user that adverts are unavailable");
            prefs.edit().putLong(AdsManager.BLOCK_MILLIS_PREF, 5000).apply();
            uiHelper.showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_message_advert_load_error), R.string.button_ok, false, new AdLoadErrorDialogListener(getUiHelper()));
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

    public FragmentUIHelper<T> getUiHelper() {
        return uiHelper;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (uiHelper == null) {
            uiHelper = buildUIHelper(getContext());
            BasicPiwigoResponseListener listener = buildPiwigoResponseListener(getContext());
            listener.withUiHelper(this, uiHelper);
            uiHelper.setPiwigoResponseListener(listener);
        }
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

        return super.onCreateView(inflater, container, savedInstanceState);
    }



    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        uiHelper.registerToActiveServiceCalls();
    }

    protected void setTheme(@StyleRes int theme) {
        this.theme = theme;
    }

    private static class AdLoadErrorDialogListener<T extends MyFragment> extends UIHelper.QuestionResultAdapter<FragmentUIHelper<T>> {

        private long shownAt;
        private transient LifecycleObserver observer;

        public AdLoadErrorDialogListener(FragmentUIHelper<T> uiHelper) {
            super(uiHelper);
        }

        @Override
        public void onShow(AlertDialog alertDialog) {
            super.onShow(alertDialog);
            observer = new LifecycleObserver() {
                @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
                public void onPause() {
                    shownAt = System.currentTimeMillis();
                }
            };
            FragmentActivity activity = getUiHelper().getParent().getActivity();
            activity.getLifecycle().addObserver(observer);
            shownAt = System.currentTimeMillis();
        }

        @Override
        public void onDismiss(AlertDialog dialog) {
            if(System.currentTimeMillis() < shownAt + 5000) {
                dialog.show();
//                uiHelper.showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_message_advert_load_error), R.string.button_ok, false, this);
            } else {
                FragmentActivity activity = getUiHelper().getParent().getActivity();
                if(activity != null) {
                    activity.getLifecycle().removeObserver(observer);
                    getUiHelper().getPrefs().edit().putLong(AdsManager.BLOCK_MILLIS_PREF, 0).apply();
                }
            }
        }
    }

    protected boolean isAppInReadOnlyMode() {
        return prefs.getBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), false);
    }

    protected SharedPreferences getPrefs() {
        return prefs;
    }
}
