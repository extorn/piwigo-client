package delit.piwigoclient.ui.common.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.LayoutRes;
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
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import org.greenrobot.eventbus.EventBus;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.Utils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.BackButtonHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.MyBackButtonCallback;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.events.ToolbarEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class MyFragment<F extends MyFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends Fragment {

    private static final String TAG = "MyFrag";
    private static final String STATE_ACTIVE_SESSION_TOKEN = "activeSessionToken";
    private static final String STATE_ACTIVE_SERVER_CONNECTION = "activeServerConnection";
    protected SharedPreferences prefs;
    // Stored state below here.
    private FUIH uiHelper;
    private String piwigoSessionToken;
    private String piwigoServerConnected;
    private @StyleRes int theme = 0; //Resources.ID_NULL; (needs 29+)
    private boolean coreComponentsInitialised;
    private OnBackPressedCallback backPressedCallback;

    public MyFragment() {
    }

    /**
     * @param layoutId gets used by the Fragment createView.
     */
    public MyFragment(@LayoutRes int layoutId) {
        super(layoutId);
    }

    public long addActiveServiceCall(@StringRes int titleStringId, AbstractPiwigoDirectResponseHandler worker) {
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
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public Context getContext() {
        Context context = super.getContext();
        /*if(theme == Resources.ID_NULL) {
            return context;
        }*/
        if(context == null) {
            return context;
        }
        if(theme == Resources.ID_NULL) {
            theme = DisplayUtils.getThemeId(context);
        }
        if(theme != Resources.ID_NULL || !(context instanceof ContextThemeWrapper)) {
            context = new ContextThemeWrapper(context, theme);
        }

        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            // set the glow on overscroll for recycler view etc.
            //TODO this isn't used in the album view or preferences (so where is it used - point?)  :-(

            // Think these are only important on really old versions and they take ages during profiling.
            DisplayUtils.setOverscrollEdgeColor(context, DisplayUtils.getColor(context, R.attr.colorPrimary));
            DisplayUtils.setOverscrollGlowColor(context, DisplayUtils.getColor(context, R.attr.colorPrimary));
        }
        return context;
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
        Logging.log(Log.VERBOSE,TAG, "onDestroyView : " + Utils.getId(this));
        uiHelper.deregisterFromActiveServiceCalls();
        uiHelper.closeAllDialogs();
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Logging.log(Log.VERBOSE,TAG, "onSaveInstanceState : " + Utils.getId(this));
        if(uiHelper != null) {
            uiHelper.onSaveInstanceState(outState);
        }
        outState.putString(STATE_ACTIVE_SESSION_TOKEN, piwigoSessionToken);
        outState.putString(STATE_ACTIVE_SERVER_CONNECTION, piwigoServerConnected);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onAttach(Context context) {
        Logging.log(Log.VERBOSE,TAG, "onAttach : " + Utils.getId(this));
        prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        super.onAttach(context);
        if(this instanceof BackButtonHandler) {
            backPressedCallback = new MyBackButtonCallback((BackButtonHandler) this);
            requireActivity().getOnBackPressedDispatcher().addCallback(backPressedCallback);
        }
    }

    public void setBackButtonHandlerEnabled(boolean enabled) {
        if(backPressedCallback != null) {
            backPressedCallback.setEnabled(enabled);
        }
    }

    @Override
    public void onDetach() {
        if(backPressedCallback != null) {
            backPressedCallback.remove();
            backPressedCallback = null;
        }
        super.onDetach();
    }

    protected FUIH buildUIHelper(Context context, @NonNull View attachedView) {
        return (FUIH) new FragmentUIHelper((F) this, prefs, context, attachedView);
    }

    protected BasicPiwigoResponseListener<FUIH, F> buildPiwigoResponseListener(Context context) {
        return new BasicPiwigoResponseListener<>();
    }

    protected boolean isSessionDetailsChanged() {
        return !PiwigoSessionDetails.matchesSessionToken(ConnectionPreferences.getActiveProfile(), piwigoSessionToken);
    }

    protected boolean isServerConnectionChanged() {
        return !PiwigoSessionDetails.matchesServerConnection(ConnectionPreferences.getActiveProfile(), piwigoServerConnected);
    }

    public void updateActiveSessionDetails() {
        piwigoSessionToken = PiwigoSessionDetails.getActiveSessionToken(ConnectionPreferences.getActiveProfile());
        piwigoServerConnected = PiwigoSessionDetails.getActiveServerConnection(ConnectionPreferences.getActiveProfile());
    }

    @Override
    public void onPause() {
        Logging.log(Log.VERBOSE,TAG, "onPause : " + Utils.getId(this));
        super.onPause();
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
    }

    protected final <T extends ViewModel> T obtainActivityViewModel(@NonNull FragmentActivity activity, @NonNull Class<T> modelClass) {
        ViewModelProvider.AndroidViewModelFactory factory = ViewModelProvider.AndroidViewModelFactory.getInstance(activity.getApplication());
        return new ViewModelProvider(activity, factory).get(modelClass);
    }

    public final <T extends ViewModel> T obtainActivityViewModel(@NonNull FragmentActivity activity, String key, @NonNull Class<T> modelClass) {
        ViewModelProvider.AndroidViewModelFactory factory = ViewModelProvider.AndroidViewModelFactory.getInstance(activity.getApplication());
        return new ViewModelProvider(activity, factory).get(key, modelClass);
    }

    protected final <T extends ViewModel> T obtainFragmentViewModel(@NonNull Fragment fragment, @NonNull Class<T> modelClass) {
        ViewModelProvider.AndroidViewModelFactory factory = ViewModelProvider.AndroidViewModelFactory.getInstance(fragment.requireActivity().getApplication());
        return new ViewModelProvider(fragment, factory).get(modelClass);
    }

    @Override
    public void onResume() {
        Logging.log(Log.VERBOSE,TAG, "onResume : " + Utils.getId(this));
        super.onResume();
        updatePageTitle();

        // This block wrapper is to hopefully protect against a WindowManager$BadTokenException when showing a dialog as part of this call.
        if (getParentFragmentManager().isDestroyed() || getActivity() == null || getActivity().isFinishing()) {
            return;//TODO this 'safety block' is probably obsolete.
        }
        uiHelper.registerToActiveServiceCalls();
        uiHelper.handleAnyQueuedPiwigoMessages();
        uiHelper.showNextQueuedMessage();
        if(AdsManager.getInstance(getContext()).hasAdvertLoadProblem(getContext())) {
            Logging.log(Log.INFO, TAG, "warning user that adverts are unavailable");
            prefs.edit().putLong(AdsManager.BLOCK_MILLIS_PREF, 5000).apply();
            uiHelper.showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_message_advert_load_error), R.string.button_ok, false, new AdLoadErrorDialogListener<>(getUiHelper()));
        }
    }

    public void updatePageTitle() {
        ToolbarEvent event = new ToolbarEvent(getActivity());
        event.setTitle(buildPageHeading());
        EventBus.getDefault().post(event);
    }

    protected String buildPageHeading() {
        return "Piwigo Client";
    }

    public FUIH getUiHelper() {
        return uiHelper;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        initialiseCoreComponents(savedInstanceState, container);
        return super.onCreateView(inflater, container, savedInstanceState);
    }



    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // do this here in case the onCreateView isn't called in a derived class
        initialiseCoreComponents(savedInstanceState, view);
        super.onViewCreated(view, savedInstanceState);
        uiHelper.registerToActiveServiceCalls();
    }

    private void initialiseCoreComponents(Bundle savedInstanceState, View attachedView) {
        if(coreComponentsInitialised) {
            return;
        }
        coreComponentsInitialised = true;
        if (uiHelper == null) {
            uiHelper = buildUIHelper(getContext(), attachedView);
            BasicPiwigoResponseListener<FUIH, F> listener = buildPiwigoResponseListener(getContext());
            listener.withUiHelper((F)this, uiHelper);
            uiHelper.setPiwigoResponseListener(listener);
        }
        if (savedInstanceState != null) {
            Logging.log(Log.VERBOSE,TAG, "onCreateView(restore) : " + Utils.getId(this));
            uiHelper.onRestoreSavedInstanceState(savedInstanceState);
            piwigoSessionToken = savedInstanceState.getString(STATE_ACTIVE_SESSION_TOKEN);
            piwigoServerConnected = savedInstanceState.getString(STATE_ACTIVE_SERVER_CONNECTION);
        } else {
            Logging.log(Log.VERBOSE,TAG, "onCreateView(fresh) : " + Utils.getId(this));
        }
        if (piwigoSessionToken == null) {
            updateActiveSessionDetails();
        }
    }

    protected void setTheme(@StyleRes int theme) {
        this.theme = theme;
    }

    /**
     * Called when gets to the top of the backstack (all others popped off)
     */
    public void onReturnToFragment() {
        updatePageTitle();
    }

    private static class AdLoadErrorDialogListener<T extends MyFragment<T,FUIH>, FUIH extends FragmentUIHelper<FUIH,T>> extends QuestionResultAdapter<FUIH,T> implements Parcelable {

        private long shownAt;
        private LifecycleObserver observer;

        public AdLoadErrorDialogListener(FUIH uiHelper) {
            super(uiHelper);
        }

        protected AdLoadErrorDialogListener(Parcel in) {
            super(in);
            shownAt = in.readLong();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeLong(shownAt);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<AdLoadErrorDialogListener<?,?>> CREATOR = new Creator<AdLoadErrorDialogListener<?,?>>() {
            @Override
            public AdLoadErrorDialogListener<?,?> createFromParcel(Parcel in) {
                return new AdLoadErrorDialogListener<>(in);
            }

            @Override
            public AdLoadErrorDialogListener<?,?>[] newArray(int size) {
                return new AdLoadErrorDialogListener[size];
            }
        };

        @Override
        public void onShow(AlertDialog alertDialog) {
            super.onShow(alertDialog);
            observer = new LifecycleObserver() {
                @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
                public void onPause() {
                    shownAt = System.currentTimeMillis();
                }
            };
            FragmentActivity activity = getParent().getActivity();
            if(activity != null) {
                activity.getLifecycle().addObserver(observer);
                shownAt = System.currentTimeMillis();
            }
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

    public boolean isAppInReadOnlyMode() {
        return prefs.getBoolean(requireContext().getString(R.string.preference_app_read_only_mode_key), false);
    }

    public SharedPreferences getPrefs() {
        return prefs;
    }
}
