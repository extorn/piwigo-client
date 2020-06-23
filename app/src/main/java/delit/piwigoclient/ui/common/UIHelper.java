package delit.piwigoclient.ui.common;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import delit.libs.core.util.Logging;
import delit.libs.ui.events.NewUnTrustedCaCertificateReceivedEvent;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.ProgressIndicator;
import delit.libs.util.CustomSnackbar;
import delit.libs.util.ObjectUtils;
import delit.libs.util.X509Utils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.HttpConnectionCleanup;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.ui.events.BadRequestExposesInternalServerEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedRequestEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.ui.util.TransientMsgUtils;

import static android.content.Context.CLIPBOARD_SERVICE;

/**
 * Created by gareth on 13/10/17.
 */

public abstract class UIHelper<T> {

    private static final String TAG = "UiHelper";
    private static final String STATE_UIHELPER = "uiHelperState";
    private static final String ACTIVE_SERVICE_CALLS = "UIHelper.activeServiceCalls";
    private static final String STATE_TRACKED_REQUESTS = "UIHelper.trackedRequests";
    private static final String STATE_ACTIONS_ON_RESPONSES = "UIHelper.actionOnResponse";
    private static final String STATE_RUN_WITH_PERMS_LIST = "UIHelper.runWithPermsList";
    private static final String STATE_PERMS_FOR_REASON = "UIHelper.reasonForPermissionsRequired";
    private static final String STATE_SIMPLE_MESSAGE_QUEUE = "UIHelper.simpleMessageQueue";
    private static final String STATE_DIALOG_MESSAGE_QUEUE = "UIHelper.dialogMessageQueue";
    private static ExecutorService executors;
    private final WeakReference<T> parent;
    private final SharedPreferences prefs;
    private final Deque<QueuedDialogMessage> dialogMessageQueue = new LinkedBlockingDeque<>(20);
    private final Queue<QueuedSimpleMessage> simpleMessageQueue = new LinkedBlockingQueue<>(50);
    private boolean toastShowing = false;
    private Context appContext;
    private DismissListener dismissListener;
    private AlertDialog alertDialog;
    private final Map<Long, String> activeServiceCalls = Collections.synchronizedMap(new HashMap<>(3));
    private HashMap<Integer, PermissionsWantedRequestEvent> runWithPermissions = new HashMap<>();
    private int trackedRequest = -1;
    private BasicPiwigoResponseListener piwigoResponseListener;
    private int permissionsNeededReason;
    private NotificationManagerCompat notificationManager;
    private WeakReference<ProgressIndicator> progressIndicator;
    private ConcurrentHashMap<Long, Action> actionOnServerCallComplete = new ConcurrentHashMap<>();

    public UIHelper(T parent, SharedPreferences prefs, Context context) {
        this(parent, prefs, context, DisplayUtils.getActivity(context).getWindow().getDecorView());
    }

    public UIHelper(T parent, SharedPreferences prefs, Context context, @Nullable View attachedView) {
        this.appContext = context.getApplicationContext();
        this.prefs = prefs;
        this.parent = new WeakReference<>(parent);
        if(canShowDialog()) {
            setupDialogBoxes();
        }
        loadProgressIndicatorIfPossible(attachedView);
        setupNotificationsManager();
    }

    public static void recycleImageViewContent(ImageView imgView) {
        if (imgView != null) {
            imgView.setImageDrawable(null);
        }
    }

    /**
     * Now I'm using app context here, this is pointless I think.
     * @param context
     */
    public void swapToNewContext(Context context) {
        Context newAppCtx = context.getApplicationContext();
        if(ObjectUtils.areEqual(appContext, newAppCtx)) {
            return;
        }
        try {
            closeAllDialogs();
        } catch (RuntimeException e) {
            Logging.recordException(e);
            Logging.log(Log.ERROR, TAG, "unable to flush old dialogs");
        }
        this.appContext = newAppCtx;
        setupDialogBoxes();
        loadProgressIndicatorIfPossible(getParentView());
        setupNotificationsManager();
    }

    private void setupNotificationsManager() {
        notificationManager = NotificationManagerCompat.from(getAppContext());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String name = appContext.getString(R.string.app_name);
            NotificationChannel channel = notificationManager.getNotificationChannel(getLowImportanceNotificationChannelId());
            int importance = NotificationManager.IMPORTANCE_LOW; // no noise for low.
            if (channel == null || channel.getImportance() != importance) {
                channel = new NotificationChannel(getLowImportanceNotificationChannelId(), name, importance);
                notificationManager.createNotificationChannel(channel);
            }

            channel = notificationManager.getNotificationChannel(getDefaultNotificationChannelId());
            importance = NotificationManager.IMPORTANCE_DEFAULT; // noise
            if (channel == null || channel.getImportance() != importance) {
                channel = new NotificationChannel(getLowImportanceNotificationChannelId(), name, importance);
                notificationManager.createNotificationChannel(channel);
            }
        }

    }

    public SharedPreferences getPrefs() {
        return prefs;
    }

    public void clearNotification(String source, int notificationId) {
        notificationManager.cancel(source, notificationId);
    }

    public void showNotification(String source, int notificationId, android.app.Notification notification) {
        // Builds the notification and issues it.
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "Posting notification : " + notificationId + " " + notification);
        }
        notificationManager.notify(source, notificationId, notification);
    }

    public String getLowImportanceNotificationChannelId() {
        return appContext.getString(R.string.app_name) + "_Misc_Low";
    }

    public String getDefaultNotificationChannelId() {
        return appContext.getString(R.string.app_name) + "_Misc";
    }

    public boolean isContextOutOfSync(Context context) {
        return this.appContext != context;
    }

    public void showDetailedMsg(@StringRes int titleResId, @StringRes int messageResId) {
        showDetailedMsg(titleResId, getAppContext().getString(messageResId), Toast.LENGTH_LONG);
    }

    public void showDetailedShortMsg(@StringRes int titleResId, @StringRes int messageResId) {
        showDetailedMsg(titleResId, getAppContext().getString(messageResId), Toast.LENGTH_LONG);
    }

    public void showDetailedShortMsg(@StringRes int titleResId, String message) {
        showDetailedMsg(titleResId, message, Toast.LENGTH_SHORT);
    }

    public void showDetailedMsg(@StringRes int titleResId, String message) {
        showDetailedMsg(titleResId, message, Toast.LENGTH_LONG);
    }

    public void showDetailedMsg(@StringRes int titleResId, String message, int duration, int id) {
        QueuedSimpleMessage msg = new QueuedSimpleMessage(titleResId, message, duration);
        msg.setId(id);
        showDetailedMsg(msg);
    }

    public void showDetailedMsg(@StringRes int titleResId, String message, int duration) {
        QueuedSimpleMessage msg = new QueuedSimpleMessage(titleResId, message, duration);
        showDetailedMsg(msg);
    }

    private synchronized void showDetailedMsg(QueuedSimpleMessage newItem) {
        if(!DisplayUtils.isRunningOnUIThread()) {
            // make certain this isn't called on a background thread.
            DisplayUtils.postOnUiThread(() -> {showDetailedMsg(newItem);});
            return;
        }
        if(!toastShowing) {
            // do this here in case the queue is already full (will only occur if there is a bug in the display logic really).
            showQueuedMsg();
        }
        if (newItem.id >= 0) {
            if (!simpleMessageQueue.isEmpty()) {
                simpleMessageQueue.remove(newItem);
            }
        }
        if(!simpleMessageQueue.contains(newItem)) {
            try {
                simpleMessageQueue.add(newItem);
            } catch(IllegalStateException e) {
                StringBuilder sb = new StringBuilder();
                sb.append("SimpleMessageQueue Full : \n");
                for(QueuedSimpleMessage item : simpleMessageQueue) {
                    sb.append("title : ");
                    sb.append(getAppContext().getString(item.titleResId));
                    sb.append('\n');
                    sb.append("msg : ");
                    sb.append(item.message);
                    sb.append('\n');
                    sb.append("duration : ");
                    sb.append(item.duration);
                    sb.append('\n');
                }
                Logging.log(Log.ERROR, TAG, sb.toString());
                Logging.recordException(e);
            }
        }
        if(!toastShowing) {
            showQueuedMsg();
        }
    }

    public void showShortMsg(@StringRes int messageResId) {
        showDetailedShortMsg(messageResId, null);
    }

    protected void showQueuedMsg() {
        View parentView = getParentView();
        if(parentView == null || !canShowDialog()) {
            return;
        }
        if(canShowDialog()) {
            setupDialogBoxes();
        }
        if(simpleMessageQueue.isEmpty()) {
            return;
        }
        final CustomSnackbar snackbar;
        toastShowing = true;
        QueuedSimpleMessage toastMsg = simpleMessageQueue.remove();
        final String message;
        if(toastMsg.message == null) {
            message = getAppContext().getString(toastMsg.titleResId);
            snackbar = TransientMsgUtils.makeSnackbar(parentView, toastMsg.titleResId, null, toastMsg.getSnackbarDuration());
        } else {
            message = toastMsg.message;
            snackbar = TransientMsgUtils.makeSnackbar(parentView, toastMsg.titleResId, toastMsg.message, toastMsg.getSnackbarDuration());
        }
        snackbar.addCallback(new CustomSnackbar.BaseCallback() {
            private boolean dismissHandled;

            @Override
            public void onDismissed(CustomSnackbar transientBottomBar, int event) {
                super.onDismissed(transientBottomBar, event);
                if(!dismissHandled) {
                    toastShowing = false;
                    showQueuedMsg();
                }
            }
            
            @Override
            public boolean onLongClick(View v) {
                if(v == null) {
                    //TODO check why... this can occur if the app is minimised at this point?
                    return false;
                }
                ClipboardManager clipboardService = (ClipboardManager) v.getContext().getSystemService(CLIPBOARD_SERVICE);
                if(clipboardService != null) {
                    clipboardService.setPrimaryClip(ClipData.newPlainText("Piwigo Client", message));
                    dismissHandled = true;
                    snackbar.dismiss();
                    CustomSnackbar snackbarNotification = TransientMsgUtils.makeSnackbar(v, R.string.copied_to_clipboard, null, CustomSnackbar.LENGTH_SHORT);
                    snackbarNotification.getView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View v) {
                            //do nothing.
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {
                            toastShowing = false;
                            showQueuedMsg();
                        }
                    });
                    snackbarNotification.show();
                }
                return false;
            }
        });
        snackbar.show();
    }


    protected abstract View getParentView();

    public T getParent() {
        return parent.get();
    }

    public long invokeSilentServiceCall(AbstractPiwigoDirectResponseHandler worker) {
        worker.setRunInBackground(true);
        long msgId = worker.getMessageId();
        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(msgId, piwigoResponseListener);
        worker.invokeAsync(appContext);
        return msgId;
    }

    public long invokeSilentServiceCall(AbstractPiwigoDirectResponseHandler worker, Action actionOnResponse) {
        addActionOnResponse(worker.getMessageId(), actionOnResponse);
        return invokeSilentServiceCall(worker);
    }

    public void addBackgroundServiceCall(long messageId) {
        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, piwigoResponseListener);
    }



    private  boolean isProgressIndicatorVisible() {
        ProgressIndicator progressIndicator = getProgressIndicator();
        return progressIndicator != null && progressIndicator.getVisibility() == View.VISIBLE;
    }

    private void loadProgressIndicatorIfPossible(View view) {
        if(view != null) {
            progressIndicator = new WeakReference<>(ViewCompat.requireViewById(view.getRootView(), R.id.progressIndicator));
        } else {
            if (BuildConfig.DEBUG) {
                Logging.recordException(new Exception().fillInStackTrace());
                Logging.log(Log.ERROR, TAG, "Progress indicator not available in " + getParent().getClass().getName());
            }
        }
    }

    /**
     * Called when retrying a failed call.
     */
    public long addActiveServiceCall(AbstractPiwigoDirectResponseHandler handler) {
        long messageId = handler.invokeAsync(getAppContext());
        synchronized (activeServiceCalls) {
            activeServiceCalls.put(messageId, handler.getTag());
        }
        if (!isProgressIndicatorVisible()) {
            // assume it still has the correct text... (fingers crossed)
            showProgressIndicator();
        }
        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, piwigoResponseListener);
        return messageId;
    }

    public long addNonBlockingActiveServiceCall(String titleString, AbstractPiwigoDirectResponseHandler handler) {
//        activeServiceCalls.add(messageId);
//        showShortMsg(titleString);
//        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, piwigoResponseListener);
        return addActiveServiceCall(titleString, handler);
    }

    public long addNonBlockingActiveServiceCall(String titleString, long messageId, String serviceDesc) {
//        activeServiceCalls.add(messageId);
//        showShortMsg(titleString);
//        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, piwigoResponseListener);
        return addActiveServiceCall(titleString, messageId, serviceDesc);
    }

    public static <T extends AsyncTask<S, ?, ?>, S> T submitAsyncTask(T task, S... params) {
        if (executors == null) {
            executors = Executors.newCachedThreadPool();
        }
        task.executeOnExecutor(executors, params);
        return task;
    }

    public long addActiveServiceCall(String titleString, AbstractPiwigoDirectResponseHandler handler) {
        long messageId = handler.invokeAsync(getAppContext());
        synchronized (activeServiceCalls) {
            activeServiceCalls.put(messageId, handler.getTag());
        }
        if (!isProgressIndicatorVisible()) {
            showProgressIndicator(titleString, -1);
        }
        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, piwigoResponseListener);
        return messageId;
    }

    private void setupDialogBoxes() {
        buildAlertDialog();
    }

    protected void buildAlertDialog() {
        MaterialAlertDialogBuilder builder1 = new MaterialAlertDialogBuilder(new android.view.ContextThemeWrapper(getParentView().getContext(), R.style.Theme_App_EditPages));
        builder1.setCancelable(true);
        dismissListener = buildDialogDismissListener();
        builder1.setOnDismissListener(dismissListener);
        alertDialog = builder1.create();
    }

    protected DismissListener buildDialogDismissListener() {
        return new DismissListener();
    }

    protected void showDialog(final QueuedQuestionMessage nextMessage) {
        buildAlertDialog();
        alertDialog.setCancelable(nextMessage.isCancellable());
        alertDialog.setTitle(nextMessage.getTitleId());
        alertDialog.setMessage(nextMessage.getMessage());

        if (nextMessage.getLayoutId() != View.NO_ID) {
            LayoutInflater inflater = LayoutInflater.from(alertDialog.getContext());
            final ViewGroup dialogView = (ViewGroup) inflater.inflate(nextMessage.getLayoutId(), null, false);
            alertDialog.setView(dialogView);
            nextMessage.populateCustomView(dialogView);
        }

        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, appContext.getString(nextMessage.getPositiveButtonTextId()), (dialog, which) -> nextMessage.getListener().onResult(alertDialog, true));
        if(nextMessage.isShowNegativeButton()) {
            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, appContext.getString(nextMessage.getNegativeButtonTextId()), (dialog, which) -> nextMessage.getListener().onResult(alertDialog, false));
        }
        if (nextMessage.isShowNeutralButton()) {
            alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, appContext.getString(nextMessage.getNeutralButtonTextId()), (dialog, which) -> nextMessage.getListener().onResult(alertDialog, null));
        }
        dismissListener.setListener(nextMessage.getListener());
        dismissListener.setBuildNewDialogOnDismiss(nextMessage.getLayoutId() != View.NO_ID);

        try {
            alertDialog.show();
            nextMessage.getListener().onShow(alertDialog);
        } catch(WindowManager.BadTokenException e) {
            Logging.recordException(e);
            Logging.log(Log.ERROR, TAG, "Unable to show dialog as window is detached from parent : " + getParent());
        }
    }

    protected void showDialog(final QueuedDialogMessage nextMessage) {
        buildAlertDialog();
        alertDialog.setCancelable(nextMessage.isCancellable());
        alertDialog.setTitle(nextMessage.getTitleId());
        alertDialog.setMessage(nextMessage.getMessage());

        if (nextMessage.getLayoutId() != View.NO_ID) {
            LayoutInflater inflater = LayoutInflater.from(alertDialog.getContext());
            final LinearLayout dialogView = (LinearLayout) inflater.inflate(nextMessage.getLayoutId(), null, false);
            alertDialog.setView(dialogView);
            nextMessage.populateCustomView(dialogView);
        }

        Button b = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (b != null) {
            b.setVisibility(View.GONE);
        }
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, appContext.getString(nextMessage.getPositiveButtonTextId()), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                QuestionResultListener l = nextMessage.getListener();
                if (l != null) {
                    l.onResultInternal(alertDialog, true);
                }
            }
        });
        dismissListener.setListener(nextMessage.getListener());
        alertDialog.show();
        QuestionResultListener l = nextMessage.getListener();
        if (l != null) {
            l.onShow(alertDialog);
        }
    }

    public void invokeActiveServiceCall(String progressMsg, AbstractPiwigoDirectResponseHandler worker) {
        addActiveServiceCall(progressMsg, worker);
        worker.invokeAsync(appContext);
    }

    public long invokeActiveServiceCall(String progressMsg, AbstractPiwigoDirectResponseHandler worker, Action actionOnResponse) {
        addActionOnResponse(worker.getMessageId(), actionOnResponse);
        addActiveServiceCall(progressMsg, worker);
        return worker.getMessageId();
    }

    public void invokeActiveServiceCall(int progressMsgId, AbstractPiwigoDirectResponseHandler worker, Action actionOnResponse) {
        invokeActiveServiceCall(appContext.getString(progressMsgId), worker, actionOnResponse);
    }

    public long addActiveServiceCall(int titleStringId, AbstractPiwigoDirectResponseHandler worker) {
        return addActiveServiceCall(appContext.getString(titleStringId), worker);
    }

    public BasicPiwigoResponseListener getPiwigoResponseListener() {
        return piwigoResponseListener;
    }

    public void setPiwigoResponseListener(BasicPiwigoResponseListener piwigoResponseListener) {
        this.piwigoResponseListener = piwigoResponseListener;
    }

    public boolean isServiceCallInProgress(Long serviceCallId) {
        synchronized (activeServiceCalls) {
            return activeServiceCalls.containsKey(serviceCallId);
        }
    }

    public void deregisterFromActiveServiceCalls() {

        EventBus.getDefault().unregister(this);
        synchronized (activeServiceCalls) {
            for (long activeCall : activeServiceCalls.keySet()) {
                PiwigoResponseBufferingHandler.getDefault().deRegisterResponseHandler(activeCall);
            }
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        Bundle thisBundle = new Bundle();
        synchronized (activeServiceCalls) {
            BundleUtils.writeMap(thisBundle, ACTIVE_SERVICE_CALLS, activeServiceCalls);
        }
        thisBundle.putInt(STATE_TRACKED_REQUESTS, trackedRequest);
        BundleUtils.writeMap(thisBundle, STATE_RUN_WITH_PERMS_LIST, runWithPermissions);
        BundleUtils.writeMap(thisBundle, STATE_ACTIONS_ON_RESPONSES, actionOnServerCallComplete);
        thisBundle.putInt(STATE_PERMS_FOR_REASON, permissionsNeededReason);
        BundleUtils.writeQueue(thisBundle, STATE_SIMPLE_MESSAGE_QUEUE, simpleMessageQueue);
        BundleUtils.writeQueue(thisBundle, STATE_DIALOG_MESSAGE_QUEUE, dialogMessageQueue);

        piwigoResponseListener.onSaveInstanceState(thisBundle);
        outState.putBundle(STATE_UIHELPER, thisBundle);

    }

    public void onRestoreSavedInstanceState(Bundle savedInstanceState) {
        try {
            if (savedInstanceState != null) {
                Bundle thisBundle = savedInstanceState.getBundle(STATE_UIHELPER);
                if (thisBundle != null) {
                    synchronized (activeServiceCalls) {
                        BundleUtils.readMap(thisBundle, ACTIVE_SERVICE_CALLS, activeServiceCalls, null);
                    }
                    trackedRequest = thisBundle.getInt(STATE_TRACKED_REQUESTS);
                    runWithPermissions = BundleUtils.readMap(thisBundle, STATE_RUN_WITH_PERMS_LIST, PermissionsWantedRequestEvent.class.getClassLoader());
                    actionOnServerCallComplete = BundleUtils.readMap(thisBundle, STATE_ACTIONS_ON_RESPONSES, new ConcurrentHashMap<>(), Action.class.getClassLoader());
                    permissionsNeededReason = thisBundle.getInt(STATE_PERMS_FOR_REASON);

                    BundleUtils.readQueue(savedInstanceState, STATE_SIMPLE_MESSAGE_QUEUE, simpleMessageQueue);
                    BundleUtils.readQueue(savedInstanceState, STATE_DIALOG_MESSAGE_QUEUE, dialogMessageQueue);


                    piwigoResponseListener.onRestoreInstanceState(thisBundle);

                    for (QueuedDialogMessage message : dialogMessageQueue) {
                        if (message.getListener() != null) {
                            message.getListener().setUiHelper(this);
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            Logging.log(Log.WARN, TAG, "Ditching all saved instance state due to error loading");
            Logging.recordException(e);
        }
    }

    public int getPermissionsNeededReason() {
        return permissionsNeededReason;
    }

    public void setPermissionsNeededReason(int permissionsNeededReason) {
        this.permissionsNeededReason = permissionsNeededReason;
    }

    public boolean isDialogShowing() {
        return alertDialog != null && alertDialog.isShowing();
    }

    public void showNextQueuedMessage() {
        synchronized (dialogMessageQueue) {
            if (dialogMessageQueue.size() > 0 && !isDialogShowing()) {
                // show the dialog now we're able.
                QueuedDialogMessage nextMessage = dialogMessageQueue.peek();
                if (nextMessage instanceof QueuedQuestionMessage) {
                    showDialog((QueuedQuestionMessage) nextMessage);
                } else if (nextMessage != null) {
                    showDialog(nextMessage);
                }
            }
        }
    }

    public int getActiveServiceCallCount() {
        return activeServiceCalls.size();
    }

    public void closeAllDialogs() {
        if(alertDialog != null) {
            alertDialog.dismiss();
        }
        hideProgressIndicator();
    }

    public int runWithExtraPermissions(final Fragment fragment, int sdkVersionRequiredFrom, int sdkVersionRequiredUntil, final String permissionNeeded, String permissionJustificationString) {
        return runWithExtraPermissions(fragment, sdkVersionRequiredFrom, sdkVersionRequiredUntil, new String[]{permissionNeeded}, permissionJustificationString);
    }

    public int runWithExtraPermissions(final Fragment fragment, int sdkVersionRequiredFrom, int sdkVersionRequiredUntil, final String[] permissionsNeeded, String permissionJustificationString) {
        PermissionsWantedRequestEvent event = new PermissionsWantedRequestEvent();
        for(String permissionNeeded : permissionsNeeded) {
            event.addPermissionNeeded(permissionNeeded);
        }
        event.setJustification(permissionJustificationString);
        return runWithExtraPermissions(fragment.getActivity(), sdkVersionRequiredFrom, sdkVersionRequiredUntil, event);
    }

    public int runWithExtraPermissions(final Activity activity, int sdkVersionRequiredFrom, int sdkVersionRequiredUntil, final String permissionNeeded, String permissionJustificationString) {
        PermissionsWantedRequestEvent event = new PermissionsWantedRequestEvent();
        event.addPermissionNeeded(permissionNeeded);
        event.setJustification(permissionJustificationString);
        return runWithExtraPermissions(activity, sdkVersionRequiredFrom, sdkVersionRequiredUntil, event);
    }

    private int runWithExtraPermissions(final Activity activity, int sdkVersionRequiredFrom, int sdkVersionRequiredUntil, final PermissionsWantedRequestEvent event) {

        final PermissionRequester requester = new ActivityPermissionRequester(activity);
        if (activity instanceof FragmentActivity) {
            event.setActionId(event.getActionId() & 0xffff);
        }
        runWithPermissions.put(event.getActionId(), event);

        final HashSet<String> permissionsWanted = event.getPermissionsWanted();

        final HashSet<String> permissionsNeeded = new HashSet<>(permissionsWanted.size());
        if (Build.VERSION.SDK_INT <= sdkVersionRequiredUntil && Build.VERSION.SDK_INT >= sdkVersionRequiredFrom) {
            for (String permissionWanted : permissionsWanted) {
                if (ContextCompat.checkSelfPermission(appContext,
                        permissionWanted)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(permissionWanted);
                }
            }
        }
        event.setPermissionsNeeded(permissionsNeeded);

        if (permissionsNeeded.size() > 0) {

            // Should we show an explanation?
            boolean showExplanation = false;
            for (String permissionNeeded : permissionsNeeded) {
                showExplanation |= ActivityCompat.shouldShowRequestPermissionRationale(activity,
                        permissionNeeded);
            }


            if (showExplanation) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                final MaterialAlertDialogBuilder alert = new MaterialAlertDialogBuilder(new android.view.ContextThemeWrapper(appContext, R.style.Theme_App_EditPages));
                alert.setTitle(appContext.getString(R.string.alert_title_permissions_needed));
                alert.setMessage(event.getJustification());

                alert.setPositiveButton(appContext.getString(R.string.button_ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
//                        EventBus.getDefault().post(event);
                        requester.requestPermission(event.getActionId(), permissionsNeeded);
                    }
                });
                alert.setNegativeButton(appContext.getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Do nothing, automatically the dialog is going to be closed.
                        onRequestPermissionsResult(activity, event.getActionId(), permissionsNeeded.toArray(new String[0]), new int[0]);
                    }
                });
                alert.create().show();

            } else {
//                EventBus.getDefault().post(event);
                // No explanation needed, we can request the permission.
                requester.requestPermission(event.getActionId(), permissionsNeeded);
            }
        } else {
            // have permission - run immediately.
            int[] permissionResponses = new int[permissionsWanted.size()];
            Arrays.fill(permissionResponses, PackageManager.PERMISSION_GRANTED);
            onRequestPermissionsResult(activity, event.getActionId(), permissionsWanted.toArray(new String[0]), permissionResponses);
        }
        return event.getActionId();
    }

    public void onRequestPermissionsResult(Activity activity, int requestCode, String[] permissions, int[] grantResults) {
        int actionId = requestCode;
        if (activity instanceof FragmentActivity) {
            actionId &= 0xffff;
        }
        // If request is cancelled, the result arrays are empty.
        PermissionsWantedResponse event = new PermissionsWantedResponse(actionId, permissions, grantResults);
        EventBus.getDefault().post(event);
    }

    public boolean messagesQueuedOrShowing() {
        return dialogMessageQueue.size() > 0;
    }

    protected boolean canShowDialog() {
        return true;
    }

    public void showProgressIndicator(final String titleString, final int progress) {
        ProgressIndicator progressIndicator = getProgressIndicator();
        if (progressIndicator == null) {
            Logging.log(Log.ERROR, TAG, "The current activity does not have a progress indicator.");
        } else {
            if (DisplayUtils.isRunningOnUIThread()) {
                progressIndicator.showProgressIndicator(titleString, progress);
            } else {
                // publish on the main thread
                progressIndicator.post(() -> progressIndicator.showProgressIndicator(titleString, progress));
            }
        }
    }

    public void showProgressIndicator() {
        ProgressIndicator progressIndicator = getProgressIndicator();
        if (progressIndicator == null) {
            Logging.log(Log.ERROR, TAG, "The current activity does not have a progress indicator.");
        } else {
            if (DisplayUtils.isRunningOnUIThread()) {
                progressIndicator.setVisibility(View.VISIBLE);
            } else {
                // publish on the main thread
                progressIndicator.post(new Runnable() {
                    @Override
                    public void run() {
                        progressIndicator.setVisibility(View.VISIBLE);
                    }
                });
            }
        }
    }

    public void hideProgressIndicator() {
        ProgressIndicator progressIndicator = getProgressIndicator();
        if(progressIndicator != null) {
            if (DisplayUtils.isRunningOnUIThread()) {
                progressIndicator.setVisibility(View.GONE);
            } else {
                // publish on the main thread
                progressIndicator.post(new Runnable() {
                    @Override
                    public void run() {
                        progressIndicator.setVisibility(View.GONE);
                    }
                });
            }
        }
    }

    public boolean showMessageImmediatelyIfPossible(QueuedDialogMessage message) {
        if (!canShowDialog()) {
            return false;
        }
        synchronized (dialogMessageQueue) {
            if (!dialogMessageQueue.contains(message)) {
                if (isDialogShowing()) {
                    dismissListener.setDialogClosingForUrgentMessage(true);
                    alertDialog.dismiss();
                }
                dialogMessageQueue.addFirst(message);
                showNextQueuedMessage();
                return true;
            } else {
                QueuedDialogMessage msg = dialogMessageQueue.peek();
                msg.getListener().chainResult(message.getListener());
                return true;
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final BadRequestExposesInternalServerEvent event) {
        if (event.isHandled()) {
            return;
        }
        String msg = getAppContext().getString(R.string.alert_internal_server_exposed_pattern, event.getOldAuthority(), event.getNewAuthority());
        showOrQueueDialogQuestion(R.string.alert_warning, msg, R.string.button_stop_warning_me, R.string.button_ok, new UnexpectedUriQuestionResult<>(this));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final NewUnTrustedCaCertificateReceivedEvent event) {

        if (event.isHandled()) {
            return;
        }
        final Set<String> preNotifiedCerts = new HashSet<>(prefs.getStringSet(appContext.getString(R.string.preference_pre_user_notified_certificates_key), new HashSet<>()));
        if (preNotifiedCerts.containsAll(event.getUntrustedCerts().keySet())) {
            // already dealt with this
            return;
        }

        DateFormat sdf = SimpleDateFormat.getDateInstance();
        StringBuilder sb = new StringBuilder();
        sb.append(appContext.getString(R.string.alert_add_cert_to_truststore_pattern));

        String subjectName = event.getEndCertificate().getSubjectX500Principal().getName();
        Date validFrom = event.getEndCertificate().getNotBefore();
        Date validTo = event.getEndCertificate().getNotAfter();
        BigInteger serialNumber = event.getEndCertificate().getSerialNumber();
        sb.append(appContext.getString(R.string.certificate_summary_pattern, subjectName, sdf.format(validFrom), sdf.format(validTo), serialNumber.toString()));

        int untrustedCertCount = event.getUntrustedCerts().size();
        for (X509Certificate cert : event.getUntrustedCerts().values()) {

            if (untrustedCertCount == 1 && cert.equals(event.getEndCertificate())) {
                // don't add the self signed cert to a list of itself
                break;
            }

            sb.append(appContext.getString(R.string.certificate_chain_seperator));

            subjectName = cert.getSubjectX500Principal().getName();
            validFrom = cert.getNotBefore();
            validTo = cert.getNotAfter();
            serialNumber = cert.getSerialNumber();
            sb.append(appContext.getString(R.string.certificate_summary_pattern, subjectName, sdf.format(validFrom), sdf.format(validTo), serialNumber.toString()));
        }
        String message = sb.toString();

        showOrQueueDialogQuestion(R.string.alert_information, message, R.string.button_no, R.string.button_yes, new NewUnTrustedCaCertificateReceivedAction<>(this, event.getUntrustedCerts()));
    }

    public long addActiveServiceCall(String titleString, long messageId, String serviceDesc) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, titleString);
        }
        synchronized (activeServiceCalls) {
            activeServiceCalls.put(messageId, serviceDesc);
        }
        if (!isProgressIndicatorVisible()) {
            showProgressIndicator(titleString, -1);
        }
        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, piwigoResponseListener);
        return messageId;
    }

    public <S extends QueuedDialogMessage> void showOrQueueDialogMessage(S message) {
        synchronized (dialogMessageQueue) {
            if (!isDialogShowing() && canShowDialog()) {
                QueuedDialogMessage nextMessage = dialogMessageQueue.peek();
                if (nextMessage instanceof QueuedQuestionMessage) {
                    showDialog((QueuedQuestionMessage) nextMessage);
                } else if (nextMessage != null) {
                    showDialog(nextMessage);
                }
            }

            if (!dialogMessageQueue.contains(message)) {
                dialogMessageQueue.add(message);
            }
            if (!isDialogShowing() && canShowDialog()) {
                QueuedDialogMessage nextMessage = dialogMessageQueue.peek();
                if (nextMessage instanceof QueuedQuestionMessage) {
                    showDialog((QueuedQuestionMessage) nextMessage);
                } else if (nextMessage != null) {
                    showDialog(nextMessage);
                }
            }
        }
    }

    public void showOrQueueEnhancedDialogQuestion(int titleId, String message, String detail, int negativeButtonTextId, int positiveButtonTextId, final QuestionResultListener listener) {
        showOrQueueDialogMessage(new QueuedQuestionMessage(titleId, message, detail, positiveButtonTextId, negativeButtonTextId, listener));
    }

    public void showOrQueueDialogQuestion(int titleId, String message, int negativeButtonTextId, int positiveButtonTextId, final QuestionResultListener listener) {
        showOrQueueDialogMessage(new QueuedQuestionMessage(titleId, message, positiveButtonTextId, negativeButtonTextId, listener));
    }

    public void showOrQueueDialogQuestion(int titleId, String message, int layoutId, int negativeButtonTextId, int neutralButtonTextId, int positiveButtonTextId, final QuestionResultListener listener) {
        showOrQueueDialogMessage(new QueuedQuestionMessage(titleId, message, null, layoutId, positiveButtonTextId, negativeButtonTextId, neutralButtonTextId, listener));
    }

    public void showOrQueueCancellableDialogQuestion(int titleId, String message, int negativeButtonTextId, int cancellableButtonTextId, int positiveButtonTextId, final QuestionResultListener listener) {
        showOrQueueDialogMessage(new QueuedQuestionMessage(titleId, message, null, View.NO_ID, positiveButtonTextId, negativeButtonTextId, cancellableButtonTextId, listener));
    }

    public void showOrQueueDialogQuestion(int titleId, String message, int layoutId, int negativeButtonTextId, int positiveButtonTextId, final QuestionResultListener listener) {
        showOrQueueDialogMessage(new QueuedQuestionMessage(titleId, message, layoutId, positiveButtonTextId, negativeButtonTextId, listener));
    }

    public void showOrQueueDialogMessage(int titleId, String message, int positiveButtonTextId) {
        showOrQueueDialogMessage(new QueuedDialogMessage(titleId, message, null, positiveButtonTextId));
    }

    public void showOrQueueDialogMessage(int titleId, String message, int positiveButtonTextId, boolean cancellable, QuestionResultListener listener) {
        showOrQueueDialogMessage(new QueuedDialogMessage(titleId, message, null, positiveButtonTextId, cancellable, listener));
    }

    public void showOrQueueDialogMessage(int titleId, String message, QuestionResultListener listener) {
        showOrQueueDialogMessage(new QueuedDialogMessage(titleId, message, null, listener));
    }

    public void showOrQueueDialogMessage(int titleId, String message) {
        showOrQueueDialogMessage(new QueuedDialogMessage(titleId, message, null));
    }

    public void removeActionForResponse(PiwigoResponseBufferingHandler.Response response) {
        actionOnServerCallComplete.remove(response.getMessageId());
    }

    public void registerToActiveServiceCalls() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        synchronized (activeServiceCalls) {
            for (long activeCall : activeServiceCalls.keySet()) {
                PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(activeCall, piwigoResponseListener);
            }
        }
    }

    public void onServiceCallComplete(long messageId) {
        synchronized (activeServiceCalls) {
            activeServiceCalls.remove(messageId);
            if (activeServiceCalls.size() == 0) {
                hideProgressIndicator();
            }
        }
    }

    public void onServiceCallComplete(PiwigoResponseBufferingHandler.Response response) {
        if (response.isEndResponse()) {
            onServiceCallComplete(response.getMessageId());
        }
    }

    public int getTrackedRequest() {
        return trackedRequest;
    }

    public void setTrackingRequest(int requestId) {
        trackedRequest = requestId;
    }

    public boolean isTrackingRequest(int requestId) {
        if (trackedRequest == requestId) {
            trackedRequest = -1;
            return true;
        }
        return false;
    }

    public Context getAppContext() {
        return appContext;
    }

    public void handleAnyQueuedPiwigoMessages() {
        PiwigoResponseBufferingHandler.getDefault().handleAnyQueuedMessagesForHandler(piwigoResponseListener);
    }

    public void updateHandlerForAllMessages() {
        PiwigoResponseBufferingHandler.getDefault().replaceHandler(piwigoResponseListener);
    }

    public boolean completePermissionsWantedRequest(PermissionsWantedResponse response) {
        PermissionsWantedRequestEvent request = runWithPermissions.remove(response.getActionId());
        if (request != null) {
            response.addAllPermissionsAlreadyHaveFromRequest(request);
            return true;
        }
        return false;
    }

    public ProgressIndicator getProgressIndicator() {
        if(progressIndicator == null) {
            loadProgressIndicatorIfPossible(getParentView());
        }
        if(progressIndicator == null) {
            return null;
        }
        return progressIndicator.get();
    }

    public void showUserHint(String tag, int hintId, @StringRes int hintStrResId) {
        String hintsKey = appContext.getString(R.string.usage_hints_shown_list_key);
        Set<String> hintsShown = new HashSet<>(getPrefs().getStringSet(hintsKey, new HashSet<>()));
        if (hintsShown.add(tag + '_' + hintId)) {
            int userHintDuration = Toast.LENGTH_LONG; //TODO use custom toast impl so I can set other duration perhaps. - AppPreferences.getUserHintDuration(getPrefs(), context);
            TransientMsgUtils.makeDetailedToast(appContext, R.string.usage_hint_title, appContext.getString(hintStrResId), userHintDuration).show();
            SharedPreferences.Editor editor = getPrefs().edit();
            editor.putStringSet(hintsKey, hintsShown);
            editor.apply();
        }
    }

    public SharedPreferences getResumePrefs() {
        return getAppContext().getSharedPreferences("resume-actions", Context.MODE_PRIVATE);
    }

    public void doOnce(@NonNull String key, @NonNull String newValue, @NonNull Runnable action) {

        synchronized (UIHelper.class) {
            SharedPreferences globalsPrefs = getAppContext().getSharedPreferences("globals", Context.MODE_PRIVATE);
            String globalVal = globalsPrefs.getString(key, null);
            if (!newValue.equals(globalVal)) {
                globalsPrefs.edit().putString(key, newValue).apply();
                // notify user once and only once
                action.run();
            }
        }
    }

    public String getString(@StringRes int stringRes) {
        return getAppContext().getString(stringRes);
    }

    public String getString(@StringRes int stringPatternRes, Object ... args) {
        return getAppContext().getString(stringPatternRes, args);
    }

    public interface QuestionResultListener<S extends UIHelper<T>, T> extends Parcelable {
        void onDismiss(AlertDialog dialog);

        void onResultInternal(AlertDialog dialog, Boolean positiveAnswer);

        void onResult(AlertDialog dialog, Boolean positiveAnswer);

        void onShow(AlertDialog alertDialog);

        void setUiHelper(S uiHelper);

        T getParent();

        void chainResult(QuestionResultListener listener);

        void onPopulateDialogView(ViewGroup dialogView, @LayoutRes int layoutId);
    }

    public Action getActionOnResponse(PiwigoResponseBufferingHandler.Response response) {
        return actionOnServerCallComplete.get(response.getMessageId());
    }

    public void addActionOnResponse(long msgId, Action loginAction) {
        actionOnServerCallComplete.put(msgId, loginAction);
    }

    private static class NewUnTrustedCaCertificateReceivedAction<T> extends UIHelper.QuestionResultAdapter<UIHelper<T>,T> implements Parcelable {

        private final HashMap<String, X509Certificate> untrustedCerts;

        public NewUnTrustedCaCertificateReceivedAction(UIHelper<T> uiHelper, HashMap<String, X509Certificate> untrustedCerts) {
            super(uiHelper);
            this.untrustedCerts = untrustedCerts;
        }

        protected NewUnTrustedCaCertificateReceivedAction(Parcel in) {
            super(in);
            untrustedCerts = ParcelUtils.readMap(in, X509Certificate.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            ParcelUtils.writeMap(dest, untrustedCerts);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<NewUnTrustedCaCertificateReceivedAction> CREATOR = new Creator<NewUnTrustedCaCertificateReceivedAction>() {
            @Override
            public NewUnTrustedCaCertificateReceivedAction createFromParcel(Parcel in) {
                return new NewUnTrustedCaCertificateReceivedAction(in);
            }

            @Override
            public NewUnTrustedCaCertificateReceivedAction[] newArray(int size) {
                return new NewUnTrustedCaCertificateReceivedAction[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {

                final Set<String> preNotifiedCerts = new HashSet<>(getUiHelper().prefs.getStringSet(getContext().getString(R.string.preference_pre_user_notified_certificates_key), new HashSet<>()));
                if (preNotifiedCerts.containsAll(untrustedCerts.keySet())) {
                    // already dealt with this
                    return;
                }

                KeyStore trustStore = X509Utils.loadTrustedCaKeystore(getContext());
                try {
                    for (Map.Entry<String, X509Certificate> entry : untrustedCerts.entrySet()) {
                        trustStore.setCertificateEntry(entry.getKey(), entry.getValue());
                    }
                    X509Utils.saveTrustedCaKeystore(getContext(), trustStore);
                } catch (KeyStoreException e) {
                    Logging.recordException(e);
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getContext().getString(R.string.alert_error_adding_certificate_to_truststore));
                }
                preNotifiedCerts.addAll(untrustedCerts.keySet());
                getUiHelper().prefs.edit().putStringSet(getContext().getString(R.string.preference_pre_user_notified_certificates_key), preNotifiedCerts).commit();
                long messageId = new HttpConnectionCleanup(ConnectionPreferences.getActiveProfile(), getContext(), true).start();
                PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, new BasicPiwigoResponseListener() {
                    @Override
                    public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
                        getUiHelper().showDetailedMsg(R.string.alert_information, getContext().getString(R.string.alert_http_engine_shutdown));
                    }
                });
            }
        }

        @Override
        public void onShow(AlertDialog alertDialog) {

        }
    }

    public static class QuestionResultAdapter<Q extends UIHelper<T>,T> implements QuestionResultListener<Q,T>, Parcelable {

        private QuestionResultListener chainedListener;

        private Q uiHelper;

        public QuestionResultAdapter(Q uiHelper) {
            this.uiHelper = uiHelper;
        }

        protected QuestionResultAdapter(Parcel in) {
            chainedListener = in.readParcelable(QuestionResultListener.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(chainedListener, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<QuestionResultAdapter> CREATOR = new Creator<QuestionResultAdapter>() {
            @Override
            public QuestionResultAdapter createFromParcel(Parcel in) {
                return new QuestionResultAdapter(in);
            }

            @Override
            public QuestionResultAdapter[] newArray(int size) {
                return new QuestionResultAdapter[size];
            }
        };

        public T getParent() {
            return getUiHelper().getParent();
        }

        public Q getUiHelper() {
            return uiHelper;
        }

        @Override
        public void setUiHelper(Q uiHelper) {
            this.uiHelper = uiHelper;
        }

        public Context getContext() {
            return uiHelper.getAppContext();
        }

        @Override
        public void onShow(AlertDialog alertDialog) {
        }

        @Override
        public void onResultInternal(AlertDialog dialog, Boolean positiveAnswer) {
            onResult(dialog, positiveAnswer);
            if (chainedListener != null) {
                chainedListener.onResult(dialog, positiveAnswer);
            }
        }
        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {

        }

        @Override
        public void onDismiss(AlertDialog dialog) {

        }

        @Override
        public void onPopulateDialogView(ViewGroup dialogView, @LayoutRes int layoutId) {
            Logging.log(Log.DEBUG, TAG, "Unsupported layout id for dialog message : " + layoutId);
        }

        @Override
        public void chainResult(QuestionResultListener listener) {
            chainedListener = listener;
        }

    }

    private static class QueuedSimpleMessage implements Parcelable {
        private int id = -1;
        private final int duration;
        private final int titleResId;
        private final String message;

        public QueuedSimpleMessage(@StringRes int titleResId, String message, int duration) {
            this.duration = duration;
            this.titleResId = titleResId;
            this.message = message;
        }

        public QueuedSimpleMessage(Parcel in) {
            duration = in.readInt();
            titleResId = in.readInt();
            message = in.readString();
            id = in.readInt();
        }

        public void setId(int id) {
            this.id = id;
        }

        public static final Creator<QueuedSimpleMessage> CREATOR = new Creator<QueuedSimpleMessage>() {
            @Override
            public QueuedSimpleMessage createFromParcel(Parcel in) {
                return new QueuedSimpleMessage(in);
            }

            @Override
            public QueuedSimpleMessage[] newArray(int size) {
                return new QueuedSimpleMessage[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(duration);
            dest.writeInt(titleResId);
            dest.writeString(message);
            dest.writeInt(id);
        }

        public int getSnackbarDuration() {
            return duration == Toast.LENGTH_SHORT ? CustomSnackbar.LENGTH_SHORT : duration == Toast.LENGTH_LONG ? CustomSnackbar.LENGTH_LONG : CustomSnackbar.LENGTH_INDEFINITE;
        }

        @Override
        public int hashCode() {
            return duration + (titleResId * 3) + (5 * message.hashCode());
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if(!(obj instanceof QueuedSimpleMessage)) {
                return false;
            }
            QueuedSimpleMessage other = (QueuedSimpleMessage) obj;
            return (id >= 0 && id == other.id) || (duration == other.duration && titleResId == other.titleResId && ObjectUtils.areEqual(message, other.message));
        }
    }

    public static class QueuedDialogMessage implements Parcelable {
        private static final AtomicInteger idGen = new AtomicInteger();
        private final int id;
        private final int titleId;
        private final String message;
        private final int positiveButtonTextId;
        private final boolean cancellable;
        private final String detail;
        private final QuestionResultListener listener;
        private final boolean hasListener;

        public QueuedDialogMessage(Parcel in) {
            id = in.readInt();
            titleId = in.readInt();
            message = in.readString();
            positiveButtonTextId = in.readInt();
            cancellable = ParcelUtils.readBool(in);
            detail = in.readString();
            listener = ParcelUtils.readValue(in, QuestionResultListener.class.getClassLoader(), QuestionResultListener.class);
            hasListener = ParcelUtils.readBool(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(id);
            dest.writeInt(titleId);
            dest.writeString(message);
            dest.writeInt(positiveButtonTextId);
            ParcelUtils.writeBool(dest, cancellable);
            dest.writeString(detail);
            try {
                dest.writeParcelable(listener, 0);
            } catch(RuntimeException e) {
                dest.writeParcelable(null, 0); // so we can still read the non parcelable object in (as null)
            }
            ParcelUtils.writeBool(dest, listener != null); // has listener
        }

        public QueuedDialogMessage(int titleId, String message, String detail) {
            this(titleId, message, detail, R.string.button_ok, true, null);
        }

        public QueuedDialogMessage(int titleId, String message, String detail, QuestionResultListener listener) {
            this(titleId, message, detail, R.string.button_ok, true, listener);
        }

        public QueuedDialogMessage(int titleId, String message, String detail, int positiveButtonTextId) {
            this(titleId, message, detail, positiveButtonTextId, true, null);
        }

        public QueuedDialogMessage(int titleId, String message, String detail, int positiveButtonTextId, boolean cancellable, QuestionResultListener listener) {
            this.id = idGen.incrementAndGet();
            this.titleId = titleId;
            if (message == null) {
                throw new IllegalArgumentException("Message cannot be null");
            }
            this.message = message;
            this.positiveButtonTextId = positiveButtonTextId;
            this.listener = listener;
            this.detail = detail;
            this.cancellable = cancellable;
            this.hasListener = listener != null;
        }

        public static final Creator<QueuedDialogMessage> CREATOR = new Creator<QueuedDialogMessage>() {
            @Override
            public QueuedDialogMessage createFromParcel(Parcel in) {
                return new QueuedDialogMessage(in);
            }

            @Override
            public QueuedDialogMessage[] newArray(int size) {
                return new QueuedDialogMessage[size];
            }
        };

        public int getTitleId() {
            return titleId;
        }

        public String getDetail() {
            return detail;
        }

        public boolean isCancellable() {
            return cancellable;
        }

        public int getPositiveButtonTextId() {
            return positiveButtonTextId;
        }

        public String getMessage() {
            return message;
        }

        public QuestionResultListener getListener() {
            return listener;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof UIHelper.QueuedDialogMessage)) {
                return false;
            }
            QueuedDialogMessage other = ((QueuedDialogMessage) obj);
            return titleId == other.titleId && message.equals(other.message);
        }

        public void populateCustomView(ViewGroup dialogView) {
            int layoutId = getLayoutId();

            if (layoutId == R.layout.layout_dialog_detailed) {
                final TextView detailView = dialogView.findViewById(R.id.list_item_details);
                detailView.setText(getDetail());

                ToggleButton detailsVisibleButton = dialogView.findViewById(R.id.details_toggle);
                detailsVisibleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        detailView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    }
                });
                detailsVisibleButton.toggle();
            } else {
                listener.onPopulateDialogView(dialogView, layoutId);
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public boolean isHasListener() {
            return hasListener;
        }

        protected int getLayoutId() {
            if (detail != null && !detail.trim().isEmpty()) {
                return R.layout.layout_dialog_detailed;
            } else {
                return View.NO_ID;
            }
        }
    }

    protected static class QueuedQuestionMessage extends QueuedDialogMessage {

        private final int negativeButtonTextId;
        private final int layoutId;
        private final int neutralButtonTextId;

        public QueuedQuestionMessage(Parcel in) {
            super(in);
            negativeButtonTextId = in.readInt();
            layoutId = in.readInt();
            neutralButtonTextId = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(negativeButtonTextId);
            dest.writeInt(layoutId);
            dest.writeInt(neutralButtonTextId);
        }

        public static final Creator<QueuedQuestionMessage> CREATOR = new Creator<QueuedQuestionMessage>() {
            @Override
            public QueuedQuestionMessage createFromParcel(Parcel in) {
                return new QueuedQuestionMessage(in);
            }

            @Override
            public QueuedQuestionMessage[] newArray(int size) {
                return new QueuedQuestionMessage[size];
            }
        };

        public QueuedQuestionMessage(int titleId, String message, int positiveButtonTextId, int negativeButtonTextId, QuestionResultListener listener) {
            this(titleId, message, null, View.NO_ID, positiveButtonTextId, negativeButtonTextId, listener);
        }

        public QueuedQuestionMessage(int titleId, String message, String detail, int positiveButtonTextId, int negativeButtonTextId, QuestionResultListener listener) {
            this(titleId, message, detail, View.NO_ID, positiveButtonTextId, negativeButtonTextId, listener);
        }

        public QueuedQuestionMessage(int titleId, String message, int layoutId, int positiveButtonTextId, int negativeButtonTextId, QuestionResultListener listener) {
            this(titleId, message, null, layoutId, positiveButtonTextId, negativeButtonTextId, View.NO_ID, listener);
        }

        public QueuedQuestionMessage(int titleId, String message, String detail, int layoutId, int positiveButtonTextId, int negativeButtonTextId, QuestionResultListener listener) {
            this(titleId, message, detail, layoutId, positiveButtonTextId, negativeButtonTextId, View.NO_ID, listener);
        }

        public QueuedQuestionMessage(int titleId, String message, String detail, int layoutId, int positiveButtonTextId, int negativeButtonTextId, int neutralButtonTextId, QuestionResultListener listener) {

            super(titleId, message, detail, positiveButtonTextId, false, listener);
            this.negativeButtonTextId = negativeButtonTextId;
            if(detail != null && !detail.trim().isEmpty() && layoutId == View.NO_ID) {
                this.layoutId = R.layout.layout_dialog_detailed;
            } else {
                this.layoutId = layoutId;
            }
            this.neutralButtonTextId = neutralButtonTextId;
        }

        public boolean isShowNegativeButton() {
            return negativeButtonTextId != View.NO_ID;
        }

        public boolean isShowNeutralButton() {
            return neutralButtonTextId != View.NO_ID;
        }

        public int getNeutralButtonTextId() {
            return neutralButtonTextId;
        }

        public int getNegativeButtonTextId() {
            return negativeButtonTextId;
        }

        public int getLayoutId() {
            return layoutId;
        }

    }

    private static class ActivityPermissionRequester implements PermissionRequester {
        private static final long serialVersionUID = -2215788562967783868L;
        private final Activity activity;

        public ActivityPermissionRequester(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void requestPermission(int requestId, final HashSet<String> permissionsNeeded) {
            ActivityCompat.requestPermissions(
                    activity,
                    permissionsNeeded.toArray(new String[0]),
                    requestId);
        }
    }

    protected class DismissListener implements DialogInterface.OnDismissListener {

        private QuestionResultListener listener;
        private boolean buildNewDialogOnDismiss;
        private boolean dialogClosingForUrgentMessage;

        public void setListener(QuestionResultListener listener) {
            this.listener = listener;
        }

        public void setDialogClosingForUrgentMessage(boolean dialogClosingForUrgentMessage) {
            this.dialogClosingForUrgentMessage = dialogClosingForUrgentMessage;
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            if (dialogClosingForUrgentMessage) {
                dialogClosingForUrgentMessage = false;
                if (buildNewDialogOnDismiss) {
                    // build a new dialog (needed if the view was altered)
                    buildAlertDialog();
                }
                return;
            }

            // remove the item we've just shown.
            if (dialogMessageQueue.size() > 0) {
                dialogMessageQueue.remove();
            } else {
                Log.w("UiHelper", "Message queue was empty - strange");
            }

            if (listener != null) {
                listener.onDismiss((AlertDialog) dialog);
            }

            if (buildNewDialogOnDismiss) {
                // build a new dialog (needed if the view was altered)
                buildAlertDialog();
            }

            if (canShowDialog()) {

                if (dialogMessageQueue.size() > 0 && canShowDialog()) {
                    QueuedDialogMessage nextMessage;
                    do {
                        nextMessage = dialogMessageQueue.peek();
                        if (nextMessage.isHasListener() && nextMessage.getListener() == null) {
                            Logging.log(Log.WARN, TAG, "Discarding corrupt message");
                            dialogMessageQueue.remove();
                            nextMessage = null;
                        }

                    } while (nextMessage == null && dialogMessageQueue.size() > 0);
                    if (nextMessage instanceof QueuedQuestionMessage) {
                        showDialog((QueuedQuestionMessage) nextMessage);
                    } else if (nextMessage != null) {
                        showDialog(nextMessage);
                    } else {
                        onNoDialogToShow();
                    }
                } else {
                    onNoDialogToShow();
                }
            }
        }

        protected void onNoDialogToShow() {
            if(!toastShowing) {
                showQueuedMsg();
            }
        }

        public void setBuildNewDialogOnDismiss(boolean buildNewDialogOnDismiss) {
            this.buildNewDialogOnDismiss = buildNewDialogOnDismiss;
        }
    }

    public static class Action<P extends UIHelper<T>, T, S extends PiwigoResponseBufferingHandler.Response> implements Parcelable {


        protected Action(Parcel in) {
        }

        public static final Creator<Action> CREATOR = new Creator<Action>() {
            @Override
            public Action createFromParcel(Parcel in) {
                return new Action(in);
            }

            @Override
            public Action[] newArray(int size) {
                return new Action[size];
            }
        };

        public Action() {
        }

        protected T getActionParent(P uiHelper) {
            return uiHelper.getParent();
        }

        /**
         * @param uiHelper
         * @param response
         * @return run normal listener code
         */
        public boolean onSuccess(P uiHelper, S response) {
            return true;
        }

        public boolean onFailure(P uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            return true;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
        }
    }

    private static class UnexpectedUriQuestionResult<Q extends UIHelper<T>,T>  extends QuestionResultAdapter<Q,T>implements Parcelable {


        public UnexpectedUriQuestionResult(Q uiHelper) {
            super(uiHelper);
        }

        protected UnexpectedUriQuestionResult(Parcel in) {
            super(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<UnexpectedUriQuestionResult> CREATOR = new Creator<UnexpectedUriQuestionResult>() {
            @Override
            public UnexpectedUriQuestionResult createFromParcel(Parcel in) {
                return new UnexpectedUriQuestionResult(in);
            }

            @Override
            public UnexpectedUriQuestionResult[] newArray(int size) {
                return new UnexpectedUriQuestionResult[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.FALSE.equals(positiveAnswer)) {
                ConnectionPreferences.getActiveProfile().setWarnInternalUriExposed(getUiHelper().getPrefs(), getUiHelper().getAppContext(), false);
            }
        }
    }
}
