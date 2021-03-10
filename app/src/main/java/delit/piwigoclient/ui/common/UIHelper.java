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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

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
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

import delit.libs.core.util.Logging;
import delit.libs.ui.events.NewUnTrustedCaCertificateReceivedEvent;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.ProgressIndicator;
import delit.libs.util.CustomSnackbar;
import delit.libs.util.ObjectUtils;
import delit.libs.util.SafeRunnable;
import delit.libs.util.Utils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.ErrorRetryQuestionResultHandler;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.ui.common.dialogmessage.NewUnTrustedCaCertificateReceivedAction;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultListener;
import delit.piwigoclient.ui.common.dialogmessage.QueuedDialogMessage;
import delit.piwigoclient.ui.common.dialogmessage.QueuedQuestionMessage;
import delit.piwigoclient.ui.common.dialogmessage.QueuedSimpleMessage;
import delit.piwigoclient.ui.common.dialogmessage.ReleaseNotesMessage;
import delit.piwigoclient.ui.common.dialogmessage.UnexpectedUriQuestionResult;
import delit.piwigoclient.ui.events.BadRequestExposesInternalServerEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedRequestEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.ui.util.TransientMsgUtils;

import static android.content.Context.CLIPBOARD_SERVICE;

/**
 * Created by gareth on 13/10/17.
 */

public abstract class UIHelper<UIH extends UIHelper<UIH, OWNER>, OWNER> {

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
    private final WeakReference<OWNER> parent;
    private final SharedPreferences prefs;
    private final Deque<QueuedDialogMessage<UIH, OWNER>> dialogMessageQueue = new LinkedBlockingDeque<>(20);
    private final Queue<QueuedSimpleMessage> simpleMessageQueue = new LinkedBlockingQueue<>(50);
    private boolean toastShowing = false;
    private Context appContext;
    private DismissListener dismissListener;
    private AlertDialog alertDialog;
    private final Map<Long, String> activeServiceCalls = Collections.synchronizedMap(new HashMap<>(3));
    private HashMap<Integer, PermissionsWantedRequestEvent> runWithPermissions = new HashMap<>();
    private int trackedRequest = -1;
    private BasicPiwigoResponseListener<UIH, OWNER> piwigoResponseListener;
    private int permissionsNeededReason;
    private NotificationManagerCompat notificationManager;
    private WeakReference<ProgressIndicator> progressIndicator;
    private ConcurrentHashMap<Long, Action<UIH, OWNER,? extends PiwigoResponseBufferingHandler.Response>> actionOnServerCallComplete = new ConcurrentHashMap<>();

    public UIHelper(OWNER parent, SharedPreferences prefs, Context context) {
        this(parent, prefs, context, DisplayUtils.getActivity(context).getWindow().getDecorView());
    }

    public UIHelper(OWNER parent, SharedPreferences prefs, Context context, @Nullable View attachedView) {
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
            importance = NotificationManager.IMPORTANCE_DEFAULT; // makes a noise
            if (channel == null || channel.getImportance() != importance) {
                channel = new NotificationChannel(getDefaultNotificationChannelId(), name, importance);
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
            DisplayUtils.postOnUiThread(() -> showDetailedMsg(newItem));
            return;
        }
        if(!toastShowing) {
            // do this here in case the queue is already full (will only occur if there is a bug in the display logic really).
            showQueuedToastMsg();
        }
        if (newItem.getId() >= 0) {
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
                    item.toString(getAppContext());
                }
                Logging.log(Log.ERROR, TAG, sb.toString());
                Logging.recordException(e);
            }
        }
        if(!toastShowing) {
            showQueuedToastMsg();
        }
    }

    public void showShortMsg(@StringRes int messageResId) {
        showDetailedShortMsg(messageResId, null);
    }

    protected void showQueuedToastMsg() {
        synchronized(simpleMessageQueue) {
            if (simpleMessageQueue.isEmpty()) {
                return;
            }
            View parentView = getParentView();
            if (parentView == null || !canShowDialog()) {
                String parentId = Utils.getId(getParent());
                Logging.log(Log.WARN, TAG, "Unable to show message, parent has no view. Parent : " + parentId);
                return;
            }

            final CustomSnackbar snackbar;
            toastShowing = true;
            QueuedSimpleMessage toastMsg = simpleMessageQueue.remove();
            final String message;
            if (toastMsg.getMessage() == null) {
                message = getAppContext().getString(toastMsg.getTitleResId());
                snackbar = TransientMsgUtils.makeSnackbar(parentView, toastMsg.getTitleResId(), null, toastMsg.getSnackbarDuration());
            } else {
                message = toastMsg.getMessage();
                snackbar = TransientMsgUtils.makeSnackbar(parentView, toastMsg.getTitleResId(), toastMsg.getMessage(), toastMsg.getSnackbarDuration());
            }
            snackbar.addCallback(new MySnackbarCallback(message, snackbar));
            snackbar.show();
        }
    }


    protected abstract View getParentView();

    public OWNER getParent() {
        return parent.get();
    }

    public long invokeSilentServiceCall(AbstractPiwigoDirectResponseHandler worker) {
        worker.setRunInBackground(true);
        long msgId = worker.getMessageId();
        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(msgId, piwigoResponseListener);
        worker.invokeAsync(appContext);
        return msgId;
    }

    public long invokeSilentServiceCall(AbstractPiwigoDirectResponseHandler worker, Action<UIH, OWNER,? extends PiwigoResponseBufferingHandler.Response> actionOnResponse) {
        addActionOnResponse(worker.getMessageId(), actionOnResponse);
        return invokeSilentServiceCall(worker);
    }

    public void addBackgroundServiceCall(long messageId) {
        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, piwigoResponseListener);
    }



    private boolean isProgressIndicatorVisible() {
        ProgressIndicator progressIndicator = getProgressIndicator();
        return progressIndicator != null && progressIndicator.getVisibility() == View.VISIBLE;
    }

    private void loadProgressIndicatorIfPossible(View view) {
        if(view != null) {
            try {
                progressIndicator = new WeakReference<>(ViewCompat.requireViewById(view.getRootView(), R.id.progressIndicator));
            } catch(IllegalArgumentException e) {
                Logging.recordException(new Exception().fillInStackTrace());
                Logging.log(Log.ERROR, TAG, "Progress indicator not available in current view graph " + Utils.getId(getParent()));
            }
        } else {
            if (BuildConfig.DEBUG) {
                Logging.recordException(new Exception().fillInStackTrace());
                Logging.log(Log.ERROR, TAG, "Progress indicator not available in " + Utils.getId(getParent()));
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

    public static <AST extends AsyncTask<PRM, ?,?>,PRM> AST submitAsyncTask(AST task, PRM... params) {
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
        Context context = getAppContext();
        if(getParentView() != null) {
            context = getParentView().getContext();
        } else {
            Logging.log(Log.WARN, TAG,"Unable to use view context for dialog boxes - view not available");
        }
        buildAlertDialog(context);
    }

    protected void buildAlertDialog(Context c) {
        MaterialAlertDialogBuilder builder1 = new MaterialAlertDialogBuilder(new android.view.ContextThemeWrapper(c, R.style.Theme_App_EditPages));
        builder1.setCancelable(true);
        dismissListener = buildDialogDismissListener();
        builder1.setOnDismissListener(dismissListener);
        alertDialog = builder1.create();
    }

    protected DismissListener buildDialogDismissListener() {
        return new DismissListener();
    }

    protected void showDialog(final QueuedQuestionMessage<UIH,OWNER> nextMessage) {
        buildAlertDialog(getParentView().getContext());
        alertDialog.setCancelable(nextMessage.isCancellable());
        alertDialog.setTitle(nextMessage.getTitleId());

        ViewGroup dialogView = null;
        if (nextMessage.getLayoutId() != View.NO_ID) {
            LayoutInflater inflater = LayoutInflater.from(alertDialog.getContext());
            dialogView = (ViewGroup) inflater.inflate(nextMessage.getLayoutId(), null, false);
            alertDialog.setView(dialogView);
            nextMessage.populateCustomView(dialogView);
        } else {
            // WARNING:  If you set the message and the view, the alert dialog layout silently fails to measure the components!
            alertDialog.setMessage(nextMessage.getMessage());
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
            nextMessage.getListener().onBeforeShow(alertDialog);
            WindowManager.LayoutParams layoutParams = null;
            if (nextMessage.getLayoutId() != View.NO_ID) {
                layoutParams = nextMessage.showWithDialogLayoutParams(alertDialog, dialogView);
            }
            alertDialog.show();
            if(layoutParams != null) {
                Objects.requireNonNull(alertDialog.getWindow()).setAttributes(layoutParams);
                //dialogView.getParent().requestLayout(); // this might be needed, but tweaking the layout params isn't needed at the moment
            }
            nextMessage.getListener().onShow(alertDialog);
        } catch(WindowManager.BadTokenException e) {
            Logging.recordException(e);
            Logging.log(Log.ERROR, TAG, "Unable to show dialog as window is detached from parent : " + getParent());
        }
    }

    protected void showDialog(final QueuedDialogMessage<UIH,OWNER> nextMessage) {
        buildAlertDialog(getParentView().getContext());
        alertDialog.setCancelable(nextMessage.isCancellable());
        alertDialog.setTitle(nextMessage.getTitleId());

        ViewGroup dialogView = null;
        if (nextMessage.getLayoutId() != View.NO_ID) {
            LayoutInflater inflater = LayoutInflater.from(alertDialog.getContext());
            dialogView = (ViewGroup) inflater.inflate(nextMessage.getLayoutId(), null, false);
            alertDialog.setView(dialogView);
            nextMessage.populateCustomView(dialogView);
        } else {
            // WARNING: If you set the message and the view, the alert dialog layout silently fails to measure the components!
            alertDialog.setMessage(nextMessage.getMessage());
        }

        Button b = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (b != null) {
            b.setVisibility(View.GONE);
        }
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, appContext.getString(nextMessage.getPositiveButtonTextId()), (dialog, which) -> {
            QuestionResultListener<UIH,OWNER> l = nextMessage.getListener();
            if (l != null) {
                l.onResultInternal(alertDialog, true);
            }
        });
        dismissListener.setListener(nextMessage.getListener());
        WindowManager.LayoutParams layoutParams = null;
        if (nextMessage.getLayoutId() != View.NO_ID) {
            layoutParams = nextMessage.showWithDialogLayoutParams(alertDialog, dialogView);
        }
        alertDialog.show();
        if(layoutParams != null) {
            Objects.requireNonNull(alertDialog.getWindow()).setAttributes(layoutParams);
            //dialogView.getParent().requestLayout(); // this might be needed, but tweaking the layout params isn't needed at the moment
        }
        QuestionResultListener<UIH,OWNER> l = nextMessage.getListener();
        if (l != null) {
            l.onShow(alertDialog);
        }
    }

    public void invokeActiveServiceCall(String progressMsg, AbstractPiwigoDirectResponseHandler worker) {
        addActiveServiceCall(progressMsg, worker);
        worker.invokeAsync(appContext);
    }

    public long invokeActiveServiceCall(String progressMsg, AbstractPiwigoDirectResponseHandler worker, Action<UIH,OWNER,?> actionOnResponse) {
        addActionOnResponse(worker.getMessageId(), actionOnResponse);
        addActiveServiceCall(progressMsg, worker);
        return worker.getMessageId();
    }

    public void invokeActiveServiceCall(int progressMsgId, AbstractPiwigoDirectResponseHandler worker, Action<UIH,OWNER,?> actionOnResponse) {
        invokeActiveServiceCall(appContext.getString(progressMsgId), worker, actionOnResponse);
    }

    public long addActiveServiceCall(int titleStringId, AbstractPiwigoDirectResponseHandler worker) {
        return addActiveServiceCall(appContext.getString(titleStringId), worker);
    }

    public BasicPiwigoResponseListener<UIH, OWNER> getPiwigoResponseListener() {
        return piwigoResponseListener;
    }

    public void setPiwigoResponseListener(BasicPiwigoResponseListener<UIH, OWNER> piwigoResponseListener) {
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

                    for (QueuedDialogMessage<UIH,OWNER> message : dialogMessageQueue) {
                        if (message.getListener() != null) {
                            message.getListener().setUiHelper((UIH)this);
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
                QueuedDialogMessage<UIH,OWNER> nextMessage = dialogMessageQueue.peek();
                if (nextMessage instanceof QueuedQuestionMessage) {
                    showDialog((QueuedQuestionMessage<UIH,OWNER>) nextMessage);
                } else if (nextMessage != null) {
                    showDialog(nextMessage);
                }
            } else if(simpleMessageQueue.size() > 0 && !toastShowing) {
                showQueuedToastMsg();
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

    public int runWithExtraPermissions(@NonNull final Fragment fragment, int sdkVersionRequiredFrom, int sdkVersionRequiredUntil, @Nullable final String permissionNeeded, @NonNull String permissionJustificationString) {
        return runWithExtraPermissions(fragment, sdkVersionRequiredFrom, sdkVersionRequiredUntil, new String[]{permissionNeeded}, permissionJustificationString);
    }

    public int runWithExtraPermissions(@NonNull final Fragment fragment, int sdkVersionRequiredFrom, int sdkVersionRequiredUntil, @NonNull final String[] permissionsNeeded, @NonNull String permissionJustificationString) {
        PermissionsWantedRequestEvent event = new PermissionsWantedRequestEvent();
        for(String permissionNeeded : permissionsNeeded) {
            if(permissionNeeded != null) {
                event.addPermissionNeeded(permissionNeeded);
            }
        }
        event.setJustification(permissionJustificationString);
        return runWithExtraPermissions(fragment.requireActivity(), sdkVersionRequiredFrom, sdkVersionRequiredUntil, event);
    }

    public int runWithExtraPermissions(@NonNull final Activity activity, int sdkVersionRequiredFrom, int sdkVersionRequiredUntil, final String permissionNeeded, String permissionJustificationString) {
        PermissionsWantedRequestEvent event = new PermissionsWantedRequestEvent();
        event.addPermissionNeeded(permissionNeeded);
        event.setJustification(permissionJustificationString);
        return runWithExtraPermissions(activity, sdkVersionRequiredFrom, sdkVersionRequiredUntil, event);
    }

    private int runWithExtraPermissions(@NonNull final Activity activity, int sdkVersionRequiredFrom, int sdkVersionRequiredUntil, @NonNull final PermissionsWantedRequestEvent event) {

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
                final MaterialAlertDialogBuilder alert = new MaterialAlertDialogBuilder(new android.view.ContextThemeWrapper(activity, R.style.Theme_App_EditPages));
                alert.setTitle(appContext.getString(R.string.alert_title_permissions_needed));
                alert.setMessage(event.getJustification());

                alert.setPositiveButton(appContext.getString(R.string.button_ok), (dialog, whichButton) -> {
//                        EventBus.getDefault().post(event);
                    requester.requestPermission(event.getActionId(), permissionsNeeded);
                });
                alert.setNegativeButton(appContext.getString(R.string.button_cancel), (dialog, whichButton) -> {
                    // Do nothing, automatically the dialog is going to be closed.
                    onRequestPermissionsResult(activity, event.getActionId(), permissionsNeeded.toArray(new String[0]), new int[0]);
                });
                if(DisplayUtils.canShowDialog(activity)) {
                    alert.create().show();
                }
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
        return getParentView() != null && getParentView().getContext() != null; // if no context, then unable to build the view let alone show it.
    }

    public void showProgressIndicator(final String titleString, final int progress) {
        ProgressIndicator progressIndicator = getProgressIndicator();
        if (progressIndicator == null) {
            Logging.log(Log.ERROR, TAG, "The current activity does not have a progress indicator.");
        } else {
            DisplayUtils.runOnUiThread(()->progressIndicator.showProgressIndicator(titleString, progress));
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
                progressIndicator.post(new SafeRunnable(() -> progressIndicator.setVisibility(View.VISIBLE)));
            }
        }
    }

    public void hideProgressIndicator() {
        ProgressIndicator progressIndicator = getProgressIndicator();
        if(progressIndicator != null && progressIndicator.isVisible()) {
            if (DisplayUtils.isRunningOnUIThread()) {
                progressIndicator.setVisibility(View.GONE);
            } else {
                // publish on the main thread
                progressIndicator.post(new SafeRunnable(() -> progressIndicator.setVisibility(View.GONE)));
            }
        }
    }

    public boolean showMessageImmediatelyIfPossible(QueuedDialogMessage<UIH, OWNER> message) {
        if (!canShowDialog()) {
            return false;
        }
        synchronized (dialogMessageQueue) {
            if (!dialogMessageQueue.contains(message)) {
                if(message.getListener() instanceof ErrorRetryQuestionResultHandler) {
                    // this is needed because the detail will likely be the same (for connection failed) but the message different due to different server method being called.
                    for (QueuedDialogMessage<UIH, OWNER> queuedDialogMessage : dialogMessageQueue) {
                        QuestionResultListener<UIH, OWNER> queuedDialogMessageListener = queuedDialogMessage.getListener();
                        if (queuedDialogMessageListener instanceof ErrorRetryQuestionResultHandler) {
                            if(Objects.equals(queuedDialogMessage.getDetail(), message.getDetail())){
                                queuedDialogMessageListener.chainResult(message.getListener());
                                Logging.log(Log.INFO,TAG, "Chained retry of %1$s and %2$s", message, queuedDialogMessage);
                            }
                        }
                    }
                }
                if (isDialogShowing()) {
                    dismissListener.setDialogClosingForUrgentMessage(true);
                    alertDialog.dismiss();
                }
                dialogMessageQueue.addFirst(message);
                showNextQueuedMessage();
            } else {
                QueuedDialogMessage<UIH, OWNER> msg = dialogMessageQueue.peek();
                if(msg != null) {
                    QuestionResultListener<UIH, OWNER> listener = msg.getListener();
                    if (listener != null) {
                        listener.chainResult(message.getListener());
                    }
                }
            }
            return true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final BadRequestExposesInternalServerEvent event) {
        if (event.isHandled()) {
            return;
        }
        String msg = getAppContext().getString(R.string.alert_internal_server_exposed_pattern, event.getOldAuthority(), event.getNewAuthority());
        showOrQueueDialogQuestion(R.string.alert_warning, msg, R.string.button_stop_warning_me, R.string.button_ok, new UnexpectedUriQuestionResult<>((UIH) this));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final NewUnTrustedCaCertificateReceivedEvent event) {

        if (event.isHandled()) {
            return;
        }
        final Set<String> preNotifiedCerts = new HashSet<>(Objects.requireNonNull(prefs.getStringSet(appContext.getString(R.string.preference_pre_user_notified_certificates_key), new HashSet<>())));
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

        showOrQueueDialogQuestion(R.string.alert_information, message, R.string.button_no, R.string.button_yes, new NewUnTrustedCaCertificateReceivedAction<>((UIH)this, event.getUntrustedCerts()));
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

    public <S extends QueuedDialogMessage<UIH, OWNER>> void showOrQueueDialogMessage(S message) {
        synchronized (dialogMessageQueue) {
            if (!isDialogShowing() && canShowDialog()) {
                QueuedDialogMessage<UIH,OWNER> nextMessage = dialogMessageQueue.peek();
                if (nextMessage instanceof QueuedQuestionMessage) {
                    showDialog((QueuedQuestionMessage<UIH,OWNER>)nextMessage);
                } else if (nextMessage != null) {
                    showDialog(nextMessage);
                }
            }

            if (!dialogMessageQueue.contains(message)) {
                dialogMessageQueue.add(message);
            }
            if (!isDialogShowing() && canShowDialog()) {
                QueuedDialogMessage<UIH,OWNER> nextMessage = dialogMessageQueue.peek();
                if (nextMessage instanceof QueuedQuestionMessage) {
                    showDialog((QueuedQuestionMessage<UIH,OWNER>)nextMessage);
                } else if (nextMessage != null) {
                    showDialog(nextMessage);
                }
            }
        }
    }

    public <QRL extends QuestionResultListener<UIH, OWNER>> void showOrQueueEnhancedDialogQuestion(@StringRes int titleId, String message, String detail, @StringRes int negativeButtonTextId, @StringRes int positiveButtonTextId, final QRL listener) {
        showOrQueueDialogMessage(new QueuedQuestionMessage<>(titleId, message, detail, positiveButtonTextId, negativeButtonTextId, listener));
    }

    public <QRL extends QuestionResultListener<UIH, OWNER>> void showOrQueueDialogQuestion(@StringRes int titleId, String message, @StringRes int negativeButtonTextId, @StringRes int positiveButtonTextId, final QRL listener) {
        showOrQueueDialogMessage(new QueuedQuestionMessage<>(titleId, message, positiveButtonTextId, negativeButtonTextId, listener));
    }

    public <QRL extends QuestionResultListener<UIH, OWNER>> void showOrQueueTriButtonDialogQuestion(@StringRes int titleId, String message, @LayoutRes int layoutId, @StringRes int negativeButtonTextId, @StringRes int neutralButtonTextId, @StringRes int positiveButtonTextId, final QRL listener) {
        showOrQueueDialogMessage(new QueuedQuestionMessage<>(titleId, message, null, layoutId, positiveButtonTextId, negativeButtonTextId, neutralButtonTextId, listener));
    }

    public <QRL extends QuestionResultListener<UIH, OWNER>> void showOrQueueTriButtonDialogQuestion(@StringRes int titleId, String message, @StringRes int negativeButtonTextId, @StringRes int neutralButtonTextId, @StringRes int positiveButtonTextId, final QRL listener) {
        showOrQueueDialogMessage(new QueuedQuestionMessage<>(titleId, message, null, View.NO_ID, positiveButtonTextId, negativeButtonTextId, neutralButtonTextId, listener));
    }

    public <QRL extends QuestionResultListener<UIH, OWNER>> void showOrQueueDialogQuestion(@StringRes int titleId, String message, @LayoutRes int layoutId, @StringRes int negativeButtonTextId, @StringRes int positiveButtonTextId, final QRL listener) {
        showOrQueueDialogMessage(new QueuedQuestionMessage<>(titleId, message, layoutId, positiveButtonTextId, negativeButtonTextId, listener));
    }

    public void showOrQueueDialogMessage(int titleId, String message, int positiveButtonTextId) {
        showOrQueueDialogMessage(new QueuedDialogMessage<>(titleId, message, null, positiveButtonTextId));
    }

    public <QRL extends QuestionResultListener<UIH, OWNER>> void showOrQueueDialogMessage(@StringRes int titleId, String message, @StringRes int positiveButtonTextId, boolean cancellable, QRL listener) {
        showOrQueueDialogMessage(new QueuedDialogMessage<>(titleId, message, null, positiveButtonTextId, cancellable, listener));
    }

    public <QRL extends QuestionResultListener<UIH, OWNER>> void showOrQueueDialogMessage(@StringRes int titleId, String message, QRL listener) {
        showOrQueueDialogMessage(new QueuedDialogMessage<>(titleId, message, null, listener));
    }

    public void showOrQueueDialogMessage(@StringRes int titleId, String message) {
        showOrQueueDialogMessage(new QueuedDialogMessage<>(titleId, message, null));
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
        Set<String> hintsShown = new HashSet<>(Objects.requireNonNull(getPrefs().getStringSet(hintsKey, new HashSet<>())));
        if (hintsShown.add(tag + '_' + hintId)) {
            int userHintDuration = Toast.LENGTH_LONG; //TODO use custom toast impl so I can set other duration perhaps. - AppPreferences.getUserHintDuration(getPrefs(), context);
            TransientMsgUtils.makeDetailedToast(appContext, R.string.usage_hint_title, appContext.getString(hintStrResId), userHintDuration).show();
            SharedPreferences.Editor editor = getPrefs().edit();
            editor.putStringSet(hintsKey, hintsShown);
            editor.apply();
        }
    }

    public ConnectionPreferences.ResumeActionPreferences getResumePrefs() {
        return ConnectionPreferences.getActiveProfile().getResumeActionPreferences(getPrefs(), getAppContext());
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

    public void showReleaseNotesIfNewlyUpgradedOrInstalled(String currentAppVersion) {
        String lastShownReleaseNotes = AppPreferences.getLatestReleaseNotesVersionShown(getPrefs(), getAppContext());
        if(!ObjectUtils.areEqual(currentAppVersion, lastShownReleaseNotes)) {
            Map<String, String> releaseNotes = AppPreferences.getAppReleaseHistory(getAppContext(), lastShownReleaseNotes);
            if (releaseNotes.size() > 0) {
                showOrQueueDialogMessage(new ReleaseNotesMessage<>(getAppContext(), releaseNotes, new QuestionResultAdapter<UIH, OWNER>((UIH) this) {
                    @Override
                    public void onDismiss(AlertDialog dialog) {
                        super.onDismiss(dialog);
                        AppPreferences.setLatestReleaseNotesShown(getPrefs(), dialog.getContext(), currentAppVersion);
                    }
                }));
            }
        }
    }

    public Action<UIH, OWNER, PiwigoResponseBufferingHandler.Response> getActionOnResponse(PiwigoResponseBufferingHandler.Response response) {
        return (Action<UIH, OWNER, PiwigoResponseBufferingHandler.Response>) actionOnServerCallComplete.get(response.getMessageId());
    }

    public void addActionOnResponse(long msgId, Action<UIH,OWNER,?> action) {
        actionOnServerCallComplete.put(msgId, action);
    }

    private static class ActivityPermissionRequester implements PermissionRequester {
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

        private QuestionResultListener<?,?> listener;
        private boolean buildNewDialogOnDismiss;
        private boolean dialogClosingForUrgentMessage;

        public void setListener(QuestionResultListener<?,?> listener) {
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
                    Context c;
                    if(getParentView() != null) {
                        c = getParentView().getContext();
                    } else {
                        c = ((AlertDialog)dialog).getContext();
                    }
                    buildAlertDialog(c);
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
                Context c;
                if(getParentView() != null) {
                    c = getParentView().getContext();
                } else {
                    c = ((AlertDialog)dialog).getContext();
                }
                buildAlertDialog(c);
            }

            if (canShowDialog()) {

                if (dialogMessageQueue.size() > 0 && canShowDialog()) {
                    QueuedDialogMessage<UIH,OWNER> nextMessage;
                    do {
                        nextMessage = dialogMessageQueue.peek();
                        if (nextMessage != null && nextMessage.isHasListener() && nextMessage.getListener() == null) {
                            Logging.log(Log.WARN, TAG, "Discarding corrupt message");
                            dialogMessageQueue.remove();
                            nextMessage = null;
                        }
                    } while (nextMessage == null && dialogMessageQueue.size() > 0);
                    if (nextMessage instanceof QueuedQuestionMessage) {
                        showDialog((QueuedQuestionMessage<UIH,OWNER>)nextMessage);
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
                showQueuedToastMsg();
            }
        }

        public void setBuildNewDialogOnDismiss(boolean buildNewDialogOnDismiss) {
            this.buildNewDialogOnDismiss = buildNewDialogOnDismiss;
        }
    }

    public static class Action<P extends UIHelper<P,T>, T, S extends PiwigoResponseBufferingHandler.Response> implements Parcelable {


        protected Action(Parcel in) {
        }

        public static final Creator<Action<?,?,?>> CREATOR = new Creator<Action<?,?,?>>() {
            @Override
            public Action<?,?,?> createFromParcel(Parcel in) {
                return new Action<>(in);
            }

            @Override
            public Action<?,?,?>[] newArray(int size) {
                return new Action<?,?,?>[size];
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

    private class MySnackbarCallback extends CustomSnackbar.BaseCallback {
        private final String message;
        private final CustomSnackbar snackBar;
        private boolean dismissHandled;

        public MySnackbarCallback(String message, CustomSnackbar snackbar) {
            this.message = message;
            this.snackBar = snackbar;
        }

        @Override
        public void onDismissed(CustomSnackbar transientBottomBar, int event) {
            super.onDismissed(transientBottomBar, event);
            if(!dismissHandled) {
                toastShowing = false;
                DisplayUtils.postOnUiThread(UIHelper.this::showQueuedToastMsg);
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
                snackBar.dismiss();
                showDetailedMsg(R.string.copied_to_clipboard, null, CustomSnackbar.LENGTH_SHORT);
            }
            return false;
        }
    }
}
