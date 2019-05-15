package delit.piwigoclient.ui.common;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.Serializable;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.HttpConnectionCleanup;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.ui.common.util.ParcelUtils;
import delit.piwigoclient.ui.events.NewUnTrustedCaCertificateReceivedEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedRequestEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.CustomSnackbar;
import delit.piwigoclient.util.ObjectUtils;
import delit.piwigoclient.util.TransientMsgUtils;
import delit.piwigoclient.util.X509Utils;

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
    private final T parent;
    private final SharedPreferences prefs;
    private final Queue<QueuedDialogMessage> dialogMessageQueue = new LinkedBlockingQueue<>(20);
    private final Queue<QueuedSimpleMessage> simpleMessageQueue = new LinkedBlockingQueue<>(50);
    private boolean toastShowing = false;
    private Context context;
    private DismissListener dismissListener;
    private AlertDialog alertDialog;
    private Map<Long, String> activeServiceCalls = Collections.synchronizedMap(new HashMap<Long, String>(3));
    private HashMap<Integer, PermissionsWantedRequestEvent> runWithPermissions = new HashMap<>();
    private int trackedRequest = -1;
    private BasicPiwigoResponseListener piwigoResponseListener;
    private int permissionsNeededReason;
    private NotificationManager notificationManager;
    ProgressIndicator progressIndicator;
    private ConcurrentHashMap<Long, Action> actionOnServerCallComplete = new ConcurrentHashMap();

    public UIHelper(T parent, SharedPreferences prefs, Context context) {
        this.context = context;
        this.prefs = prefs;
        this.parent = parent;
        setupDialogBoxes();
        setupNotificationsManager();
    }

    public static void recycleImageViewContent(ImageView imgView) {
        if (imgView != null) {
            Bitmap img = imgView.getDrawingCache();
            if (img != null) {
                img.recycle();
            }
        }

    }

    public void swapToNewContext(Context context) {
        try {
            closeAllDialogs();
        } catch (RuntimeException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "unable to flush old dialogs", e);
            }
        }
        this.context = context;
        setupDialogBoxes();
        setupNotificationsManager();
    }

    private void setupNotificationsManager() {
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String name = context.getString(R.string.app_name);
            NotificationChannel channel = notificationManager.getNotificationChannel(getDefaultNotificationChannelId());
            if (channel == null) {
                int importance = NotificationManager.IMPORTANCE_DEFAULT;
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

    public void showNotification(String source, int notificationId, Notification notification) {
        // Builds the notification and issues it.
        notificationManager.notify(source, notificationId, notification);
    }

    public String getDefaultNotificationChannelId() {
        return context.getString(R.string.app_name) + "_Misc";
    }

    public boolean isContextOutOfSync(Context context) {
        return this.context != context;
    }

    public void showDetailedMsg(@StringRes int titleResId, @StringRes int messageResId) {
        showDetailedMsg(titleResId, getContext().getString(messageResId), Toast.LENGTH_LONG);
    }

    public void showDetailedShortMsg(@StringRes int titleResId, @StringRes int messageResId) {
        showDetailedMsg(titleResId, getContext().getString(messageResId), Toast.LENGTH_LONG);
    }

    public void showDetailedShortMsg(@StringRes int titleResId, String message) {
        showDetailedMsg(titleResId, message, Toast.LENGTH_SHORT);
    }

    public void showDetailedMsg(@StringRes int titleResId, String message) {
        showDetailedMsg(titleResId, message, Toast.LENGTH_LONG);
    }

    public synchronized void showDetailedMsg(@StringRes int titleResId, String message, int duration) {
        QueuedSimpleMessage newItem = new QueuedSimpleMessage(titleResId, message, duration);
        if(!toastShowing) {
            // do this here in case the queue is already full (will only occur if there is a bug in the display logic really).
            showQueuedMsg();
        }
        if(!simpleMessageQueue.contains(newItem)) {
            try {
                simpleMessageQueue.add(newItem);
            } catch(IllegalStateException e) {
                StringBuilder sb = new StringBuilder();
                sb.append("SimpleMessageQueue Full : \n");
                for(QueuedSimpleMessage item : simpleMessageQueue) {
                    sb.append("title : ");
                    sb.append(getContext().getString(item.titleResId));
                    sb.append('\n');
                    sb.append("msg : ");
                    sb.append(item.message);
                    sb.append('\n');
                    sb.append("duration : ");
                    sb.append(item.duration);
                    sb.append('\n');
                }
                Crashlytics.log(Log.ERROR, TAG, sb.toString());
                Crashlytics.logException(e);
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
        if(simpleMessageQueue.isEmpty()) {
            return;
        }
        final CustomSnackbar snackbar;
        toastShowing = true;
        QueuedSimpleMessage toastMsg = simpleMessageQueue.remove();
        final String message;
        if(toastMsg.message == null) {
            message = getContext().getString(toastMsg.titleResId);
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
                ClipboardManager clipboardService = (ClipboardManager) v.getContext().getApplicationContext().getSystemService(CLIPBOARD_SERVICE);
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
                return false;
            }
        });
        snackbar.show();
    }

    protected abstract View getParentView();

    public T getParent() {
        return parent;
    }

    public long invokeSilentServiceCall(AbstractPiwigoDirectResponseHandler worker) {
        worker.setRunInBackground(true);
        long msgId = worker.getMessageId();
        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(msgId, piwigoResponseListener);
        worker.invokeAsync(context);
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
        if(progressIndicator == null) {
            loadProgressIndicatorIfPossible();
        }
        return progressIndicator != null && progressIndicator.getVisibility() == View.VISIBLE;
    }

    private void loadProgressIndicatorIfPossible() {
        try {
            progressIndicator = ActivityCompat.requireViewById((Activity) context, R.id.progressIndicator);
        } catch (IllegalArgumentException e) {
            if(BuildConfig.DEBUG) {
                Crashlytics.log(Log.ERROR, TAG, "Progress indicator not available in " + ((Activity) context).getLocalClassName());
            }
        }
    }

    /**
     * Called when retrying a failed call.
     */
    public long addActiveServiceCall(AbstractPiwigoDirectResponseHandler handler) {
        long messageId = handler.invokeAsync(getContext());
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

    public static <T extends AsyncTask, S extends Object> T submitAsyncTask(T task, S... params) {
        if (executors == null) {
            executors = Executors.newCachedThreadPool();
        }
        task.executeOnExecutor(executors, params);
        return task;
    }

    public long addActiveServiceCall(String titleString, AbstractPiwigoDirectResponseHandler handler) {
        long messageId = handler.invokeAsync(getContext());
        synchronized (activeServiceCalls) {
            activeServiceCalls.put(messageId, handler.getTag());
        }
        if (!isProgressIndicatorVisible()) {
            if (progressIndicator == null) {
                Crashlytics.log(Log.ERROR, TAG, "The current activity does not have a progress indicator.");
            } else {
                progressIndicator.showProgressIndicator(titleString, -1);
            }
        }
        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, piwigoResponseListener);
        return messageId;
    }

    private void setupDialogBoxes() {
        buildAlertDialog();
        loadProgressIndicatorIfPossible();
    }

    protected void buildAlertDialog() {
        AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
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

        if (nextMessage.getLayoutId() != Integer.MIN_VALUE) {
            LayoutInflater inflater = LayoutInflater.from(alertDialog.getContext());
            final LinearLayout dialogView = (LinearLayout) inflater.inflate(nextMessage.getLayoutId(), null, false);
            alertDialog.setView(dialogView);
            nextMessage.populateCustomView(dialogView);
        }

        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(nextMessage.getPositiveButtonTextId()), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                nextMessage.getListener().onResult(alertDialog, true);
            }
        });
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(nextMessage.getNegativeButtonTextId()), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                nextMessage.getListener().onResult(alertDialog, false);
            }
        });
        if (nextMessage.isShowNeutralButton()) {
            alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(nextMessage.getNeutralButtonTextId()), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    nextMessage.getListener().onResult(alertDialog, null);
                }
            });
        }
        dismissListener.setListener(nextMessage.getListener());
        dismissListener.setBuildNewDialogOnDismiss(nextMessage.getLayoutId() != Integer.MIN_VALUE);
        alertDialog.show();
        nextMessage.getListener().onShow(alertDialog);
    }

    protected void showDialog(final QueuedDialogMessage nextMessage) {
        buildAlertDialog();
        alertDialog.setCancelable(nextMessage.isCancellable());
        alertDialog.setTitle(nextMessage.getTitleId());
        alertDialog.setMessage(nextMessage.getMessage());
        Button b = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (b != null) {
            b.setVisibility(View.GONE);
        }
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(nextMessage.getPositiveButtonTextId()), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                QuestionResultListener l = nextMessage.getListener();
                if (l != null) {
                    l.onResult(alertDialog, true);
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
        worker.invokeAsync(context);
    }

    public void invokeActiveServiceCall(String progressMsg, AbstractPiwigoDirectResponseHandler worker, Action actionOnResponse) {
        addActionOnResponse(worker.getMessageId(), actionOnResponse);
        addActiveServiceCall(progressMsg, worker);
        worker.invokeAsync(context);
    }

    public void invokeActiveServiceCall(int progressMsgId, AbstractPiwigoDirectResponseHandler worker, Action actionOnResponse) {
        invokeActiveServiceCall(context.getString(progressMsgId), worker, actionOnResponse);
    }

    public void addActiveServiceCall(int titleStringId, AbstractPiwigoDirectResponseHandler worker) {
        addActiveServiceCall(context.getString(titleStringId), worker);
    }

    public BasicPiwigoResponseListener getPiwigoResponseListener() {
        return piwigoResponseListener;
    }

    public void setPiwigoResponseListener(BasicPiwigoResponseListener piwigoResponseListener) {
        this.piwigoResponseListener = piwigoResponseListener;
    }

    public boolean isServiceCallInProgress() {
        synchronized (activeServiceCalls) {
            return activeServiceCalls.size() > 0;
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
        BundleUtils.writeMap(thisBundle, ACTIVE_SERVICE_CALLS, activeServiceCalls);
        thisBundle.putInt(STATE_TRACKED_REQUESTS, trackedRequest);
        thisBundle.putSerializable(STATE_RUN_WITH_PERMS_LIST, runWithPermissions);
        thisBundle.putSerializable(STATE_ACTIONS_ON_RESPONSES, actionOnServerCallComplete);
        thisBundle.putInt(STATE_PERMS_FOR_REASON, permissionsNeededReason);
        BundleUtils.writeQueue(thisBundle, STATE_SIMPLE_MESSAGE_QUEUE, simpleMessageQueue);
        BundleUtils.writeQueue(thisBundle, STATE_DIALOG_MESSAGE_QUEUE, dialogMessageQueue);

        piwigoResponseListener.onSaveInstanceState(thisBundle);
        outState.putBundle(STATE_UIHELPER, thisBundle);

    }

    public void onRestoreSavedInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            Bundle thisBundle = savedInstanceState.getBundle(STATE_UIHELPER);
            if (thisBundle != null) {
                activeServiceCalls = Collections.synchronizedMap(BundleUtils.<Long, String>readMap(thisBundle, ACTIVE_SERVICE_CALLS, getClass().getClassLoader()));
                trackedRequest = thisBundle.getInt(STATE_TRACKED_REQUESTS);
                runWithPermissions = BundleUtils.getSerializable(thisBundle, STATE_RUN_WITH_PERMS_LIST, HashMap.class);
                try {
                    actionOnServerCallComplete = BundleUtils.getSerializable(thisBundle, STATE_ACTIONS_ON_RESPONSES, ConcurrentHashMap.class);
                } catch(IllegalStateException e) {
                    Map<Long, Action> map = BundleUtils.getSerializable(thisBundle, STATE_ACTIONS_ON_RESPONSES, HashMap.class);
                    actionOnServerCallComplete = new ConcurrentHashMap<>(map);
                }
                permissionsNeededReason = thisBundle.getInt(STATE_PERMS_FOR_REASON);
                piwigoResponseListener.onRestoreInstanceState(thisBundle);

                BundleUtils.readQueue(savedInstanceState, STATE_SIMPLE_MESSAGE_QUEUE, simpleMessageQueue);
                BundleUtils.readQueue(savedInstanceState, STATE_DIALOG_MESSAGE_QUEUE, dialogMessageQueue);
                for(QueuedDialogMessage message : dialogMessageQueue) {
                    if(message.getListener() != null) {
                        message.getListener().setUiHelper(this);
                    }
                }
            }
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

    public int getActiveServiceCallCount() {
        return activeServiceCalls.size();
    }

    public void closeAllDialogs() {
        alertDialog.dismiss();
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
                if (ContextCompat.checkSelfPermission(context,
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
                final AlertDialog.Builder alert = new AlertDialog.Builder(this.context);
                alert.setTitle(context.getString(R.string.alert_title_permissions_needed));
                alert.setMessage(event.getJustification());

                alert.setPositiveButton(context.getString(R.string.button_ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
//                        EventBus.getDefault().post(event);
                        requester.requestPermission(event.getActionId(), permissionsNeeded);
                    }
                });
                alert.setNegativeButton(context.getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
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

    public void showProgressIndicator() {
        if(progressIndicator == null) {
            Crashlytics.log(Log.ERROR, TAG, "The current activity does not have a progress indicator.");
        } else {
            progressIndicator.setVisibility(View.VISIBLE);
        }
    }

    public void hideProgressIndicator() {
        if(progressIndicator != null) {
            progressIndicator.setVisibility(View.GONE);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final NewUnTrustedCaCertificateReceivedEvent event) {

        if (event.isHandled()) {
            return;
        }
        final Set<String> preNotifiedCerts = prefs.getStringSet(context.getString(R.string.preference_pre_user_notified_certificates_key), new HashSet<String>());
        if (preNotifiedCerts.containsAll(event.getUntrustedCerts().keySet())) {
            // already dealt with this
            return;
        }

        DateFormat sdf = SimpleDateFormat.getDateInstance();
        StringBuilder sb = new StringBuilder();
        sb.append(context.getString(R.string.alert_add_cert_to_truststore_pattern));

        String subjectName = event.getEndCertificate().getSubjectX500Principal().getName();
        Date validFrom = event.getEndCertificate().getNotBefore();
        Date validTo = event.getEndCertificate().getNotAfter();
        BigInteger serialNumber = event.getEndCertificate().getSerialNumber();
        sb.append(context.getString(R.string.certificate_summary_pattern, subjectName, sdf.format(validFrom), sdf.format(validTo), serialNumber.toString()));

        int untrustedCertCount = event.getUntrustedCerts().size();
        for (X509Certificate cert : event.getUntrustedCerts().values()) {

            if (untrustedCertCount == 1 && cert.equals(event.getEndCertificate())) {
                // don't add the self signed cert to a list of itself
                break;
            }

            sb.append(context.getString(R.string.certificate_chain_seperator));

            subjectName = cert.getSubjectX500Principal().getName();
            validFrom = cert.getNotBefore();
            validTo = cert.getNotAfter();
            serialNumber = cert.getSerialNumber();
            sb.append(context.getString(R.string.certificate_summary_pattern, subjectName, sdf.format(validFrom), sdf.format(validTo), serialNumber.toString()));
        }
        String message = sb.toString();

        showOrQueueDialogQuestion(R.string.alert_information, message, R.string.button_no, R.string.button_yes, new NewUnTrustedCaCertificateReceivedAction(this, event.getUntrustedCerts()));
    }

    public long addActiveServiceCall(String titleString, long messageId, String serviceDesc) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, titleString);
        }
        synchronized (activeServiceCalls) {
            activeServiceCalls.put(messageId, serviceDesc);
        }
        if (!isProgressIndicatorVisible()) {
            if (progressIndicator == null) {
                Crashlytics.log(Log.ERROR, TAG, "The current activity does not have a progress indicator.");
            } else {
                progressIndicator.showProgressIndicator(titleString, -1);
            }
        }
        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, piwigoResponseListener);
        return messageId;
    }

    private static class NewUnTrustedCaCertificateReceivedAction extends UIHelper.QuestionResultAdapter {
        private final HashMap<String, X509Certificate> untrustedCerts;

        public NewUnTrustedCaCertificateReceivedAction(UIHelper uiHelper, HashMap<String, X509Certificate> untrustedCerts) {
            super(uiHelper);
            this.untrustedCerts = untrustedCerts;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {

                final Set<String> preNotifiedCerts = getUiHelper().prefs.getStringSet(getContext().getString(R.string.preference_pre_user_notified_certificates_key), new HashSet<String>());
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
                    Crashlytics.logException(e);
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getContext().getString(R.string.alert_error_adding_certificate_to_truststore));
                }
                preNotifiedCerts.addAll(untrustedCerts.keySet());
                getUiHelper().prefs.edit().putStringSet(getContext().getString(R.string.preference_pre_user_notified_certificates_key), preNotifiedCerts).commit();
                long messageId = new HttpConnectionCleanup(ConnectionPreferences.getActiveProfile(), getContext()).start();
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
        showOrQueueDialogMessage(new QueuedQuestionMessage(titleId, message, null, Integer.MIN_VALUE, positiveButtonTextId, negativeButtonTextId, cancellableButtonTextId, listener));
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

    public <S extends QueuedDialogMessage> void showOrQueueDialogMessage(S message) {
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
        actionOnServerCallComplete.remove(messageId);
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

    public Context getContext() {
        return context;
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
            loadProgressIndicatorIfPossible();
        }
        return progressIndicator;
    }

    public Action getActionOnResponse(PiwigoResponseBufferingHandler.Response response) {
        return actionOnServerCallComplete.get(response.getMessageId());
    }

    public void addActionOnResponse(long msgId, Action loginAction) {
        actionOnServerCallComplete.put(msgId, loginAction);
    }

    public static abstract class QuestionResultAdapter implements QuestionResultListener {

        public QuestionResultAdapter(UIHelper uiHelper) {
            this.uiHelper = uiHelper;
        }

        private UIHelper uiHelper;

        @Override
        public void setUiHelper(UIHelper uiHelper) {
            this.uiHelper = uiHelper;
        }

        public UIHelper getUiHelper() {
            return uiHelper;
        }

        public Context getContext() {
            return uiHelper.getContext();
        }

        @Override
        public void onShow(AlertDialog alertDialog) {

        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {

        }

        @Override
        public void onDismiss(AlertDialog dialog) {

        }
    }

    public interface QuestionResultListener extends Serializable {
        void onDismiss(AlertDialog dialog);

        void onResult(AlertDialog dialog, Boolean positiveAnswer);

        void onShow(AlertDialog alertDialog);

        void setUiHelper(UIHelper uiHelper);
    }

    private static class QueuedSimpleMessage implements Parcelable {
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
            return duration == other.duration && titleResId == other.titleResId && ObjectUtils.areEqual(message, other.message);
        }
    }

    private static class QueuedDialogMessage implements Parcelable {
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
            message = ParcelUtils.readString(in);
            positiveButtonTextId = in.readInt();
            cancellable = ParcelUtils.readBool(in);
            detail = ParcelUtils.readString(in);
            listener = ParcelUtils.readValue(in, QuestionResultListener.class.getClassLoader(), QuestionResultListener.class);
            hasListener = ParcelUtils.readBool(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(id);
            dest.writeInt(titleId);
            dest.writeValue(message);
            dest.writeInt(positiveButtonTextId);
            ParcelUtils.writeBool(dest, cancellable);
            dest.writeValue(detail);
            try {
                dest.writeSerializable(listener);
            } catch(RuntimeException e) {
                dest.writeSerializable(null); // so we can still read the non serializable object in (as null)
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

        public void populateCustomView(LinearLayout dialogView) {
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public boolean isHasListener() {
            return hasListener;
        }
    }

    private static class QueuedQuestionMessage extends QueuedDialogMessage {

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
            this(titleId, message, null, Integer.MIN_VALUE, positiveButtonTextId, negativeButtonTextId, listener);
        }

        public QueuedQuestionMessage(int titleId, String message, String detail, int positiveButtonTextId, int negativeButtonTextId, QuestionResultListener listener) {
            this(titleId, message, detail, Integer.MIN_VALUE, positiveButtonTextId, negativeButtonTextId, listener);
        }

        public QueuedQuestionMessage(int titleId, String message, int layoutId, int positiveButtonTextId, int negativeButtonTextId, QuestionResultListener listener) {
            this(titleId, message, null, layoutId, positiveButtonTextId, negativeButtonTextId, Integer.MIN_VALUE, listener);
        }

        public QueuedQuestionMessage(int titleId, String message, String detail, int layoutId, int positiveButtonTextId, int negativeButtonTextId, QuestionResultListener listener) {
            this(titleId, message, detail, layoutId, positiveButtonTextId, negativeButtonTextId, Integer.MIN_VALUE, listener);
        }

        public QueuedQuestionMessage(int titleId, String message, String detail, int layoutId, int positiveButtonTextId, int negativeButtonTextId, int neutralButtonTextId, QuestionResultListener listener) {

            super(titleId, message, detail, positiveButtonTextId, false, listener);
            this.negativeButtonTextId = negativeButtonTextId;
            if(detail != null && !detail.trim().isEmpty() && layoutId == Integer.MIN_VALUE) {
                this.layoutId = R.layout.layout_dialog_detailed;
            } else {
                this.layoutId = layoutId;
            }
            this.neutralButtonTextId = neutralButtonTextId;
        }

        public boolean isShowNeutralButton() {
            return neutralButtonTextId != Integer.MIN_VALUE;
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

        @Override
        public void populateCustomView(LinearLayout dialogView) {
            if(layoutId == R.layout.layout_dialog_detailed) {
                final TextView detailView = dialogView.findViewById(R.id.details);
                detailView.setText(getDetail());

                ToggleButton detailsVisibleButton = dialogView.findViewById(R.id.details_toggle);
                detailsVisibleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        detailView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    }
                });
                detailsVisibleButton.toggle();
            }
            super.populateCustomView(dialogView);
        }
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

        private QuestionResultListener listener;
        private boolean buildNewDialogOnDismiss;

        public void setListener(QuestionResultListener listener) {
            this.listener = listener;
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
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

            if (dialogMessageQueue.size() > 0 && canShowDialog()) {
                QueuedDialogMessage nextMessage;
                do {
                    nextMessage = dialogMessageQueue.peek();
                    if (nextMessage.isHasListener() && nextMessage.getListener() == null) {
                        Crashlytics.log(Log.WARN, TAG, "Discarding corrupt message");
                        dialogMessageQueue.remove();
                        nextMessage = null;
                    }
                } while(nextMessage == null && dialogMessageQueue.size() > 0);
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

        protected void onNoDialogToShow() {
            if(!toastShowing) {
                showQueuedMsg();
            }
        }

        public void setBuildNewDialogOnDismiss(boolean buildNewDialogOnDismiss) {
            this.buildNewDialogOnDismiss = buildNewDialogOnDismiss;
        }
    }

    public static class Action<T,S extends PiwigoResponseBufferingHandler.Response> implements Serializable {

        protected T getActionParent(UIHelper<T> uiHelper) {
            return uiHelper.getParent();
        }

        public boolean onSuccess(UIHelper<T> uiHelper, S response){
            return true;
        }

        public boolean onFailure(UIHelper<T> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response){
            return true;
        }
    }
}
