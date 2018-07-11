package delit.piwigoclient.ui.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.HttpConnectionCleanup;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.events.NewUnTrustedCaCertificateReceivedEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedRequestEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.X509Utils;

/**
 * Created by gareth on 13/10/17.
 */

public abstract class UIHelper<T> {

    private static final String TAG = "UiHelper";
    private static final String STATE_UIHELPER = "uiHelperState";
    private static final String ACTIVE_SERVICE_CALLS = "activeServiceCalls";
    private static final String STATE_TRACKED_REQUESTS = "trackedRequests";
    private static final String STATE_RUN_WITH_PERMS_LIST = "runWithPermsList";
    private static final String STATE_PERMS_FOR_REASON = "reasonForPermissionsRequired";
    private Context context;
    private final T parent;
    private final SharedPreferences prefs;
    private DismissListener dismissListener;
    private ProgressDialog progressDialog;
    private AlertDialog alertDialog;
    private final Queue<QueuedMessage> messageQueue = new LinkedBlockingQueue<>(100);
    private HashSet<Long> activeServiceCalls = new HashSet<>(3);
    private HashMap<Integer, PermissionsWantedRequestEvent> runWithPermissions = new HashMap<>();
    private int trackedRequest = -1;
    private BasicPiwigoResponseListener piwigoResponseListener;
    private int permissionsNeededReason;
    private NotificationManager notificationManager;

    public UIHelper(T parent, SharedPreferences prefs, Context context) {
        this.context = context;
        this.prefs = prefs;
        this.parent = parent;
        setupDialogBoxes();
        setupNotificationsManager();
    }

    public void swapToNewContext(Context context) {
        try {
            closeAllDialogs();
        } catch(RuntimeException e) {
            if(BuildConfig.DEBUG) {
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
            if(channel == null) {
                int importance = NotificationManager.IMPORTANCE_DEFAULT;
                channel = new NotificationChannel(getDefaultNotificationChannelId(), name, importance);
                notificationManager.createNotificationChannel(channel);
            }
        }

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

    public void showToast(@StringRes int messageResId) {
        Toast toast = Toast.makeText(getContext(), messageResId, Toast.LENGTH_SHORT);
        toast.show();
    }

    public void showToast(String message) {
        Toast toast = Toast.makeText(getContext(), message, Toast.LENGTH_SHORT);
        toast.show();
    }

    public void showLongToast(String message) {
        Toast toast = Toast.makeText(getContext(), message, Toast.LENGTH_LONG);
        toast.show();
    }

    public void showLongToast(@StringRes int messageResId) {
        Toast toast = Toast.makeText(getContext(), messageResId, Toast.LENGTH_LONG);
        toast.show();
    }

    private static class QueuedMessage implements Serializable {
        private final int titleId;
        private final String message;
        private final int positiveButtonTextId;
        private final boolean cancellable;
        private final QuestionResultListener listener;

        public QueuedMessage(int titleId, String message) {
            this(titleId, message, R.string.button_ok, true, null);
        }

        public QueuedMessage(int titleId, String message, QuestionResultListener listener) {
            this(titleId, message, R.string.button_ok, true, listener);
        }

        public QueuedMessage(int titleId, String message, int positiveButtonTextId) {
            this(titleId, message, positiveButtonTextId, true, null);
        }

        public QueuedMessage(int titleId, String message, int positiveButtonTextId, boolean cancellable, QuestionResultListener listener) {
            this.titleId = titleId;
            if(message == null) {
                throw new IllegalArgumentException("Message cannot be null");
            }
            this.message = message;
            this.positiveButtonTextId = positiveButtonTextId;
            this.listener = listener;
            this.cancellable = cancellable;
        }

        public int getTitleId() {
            return titleId;
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
            if (!(obj instanceof QueuedMessage)) {
                return false;
            }
            QueuedMessage other = ((QueuedMessage) obj);
            return titleId == other.titleId && message.equals(other.message);
        }
    }

    private static class QueuedQuestionMessage extends QueuedMessage {

        private final int negativeButtonTextId;
        private final int layoutId;
        private final int neutralButtonTextId;

        public QueuedQuestionMessage(int titleId, String message, int positiveButtonTextId, int negativeButtonTextId, QuestionResultListener listener) {
            this(titleId, message, Integer.MIN_VALUE, positiveButtonTextId, negativeButtonTextId, listener);
        }

        public QueuedQuestionMessage(int titleId, String message, int layoutId, int positiveButtonTextId, int negativeButtonTextId, QuestionResultListener listener) {
            this(titleId, message, layoutId, positiveButtonTextId, negativeButtonTextId, Integer.MIN_VALUE, listener);
        }

        public QueuedQuestionMessage(int titleId, String message, int layoutId, int positiveButtonTextId, int negativeButtonTextId, int neutralButtonTextId, QuestionResultListener listener) {

            super(titleId, message, positiveButtonTextId, false, listener);
            this.negativeButtonTextId = negativeButtonTextId;
            this.layoutId = layoutId;
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
    }

    public T getParent() {
        return parent;
    }

    public void setPiwigoResponseListener(BasicPiwigoResponseListener piwigoResponseListener) {
        this.piwigoResponseListener = piwigoResponseListener;
    }

    public void addBackgroundServiceCall(long messageId) {
        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, piwigoResponseListener);
    }

    /**
     * Called when retrying a failed call.
     */
    public void addActiveServiceCall(long messageId) {
        activeServiceCalls.add(messageId);
        if (progressDialog != null && !progressDialog.isShowing()) {
            // assume it still has the correct text... (fingers crossed)
            if(canShowDialog()) {
                showProgressDialog();
            }
        }
        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, piwigoResponseListener);
    }

    public void addActiveServiceCall(String titleString, long messageId) {
        activeServiceCalls.add(messageId);
        if (progressDialog != null && !progressDialog.isShowing()) {
            progressDialog.setTitle(titleString);
            if(canShowDialog()) {
                showProgressDialog();
            }
        }
        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, piwigoResponseListener);
    }

    private class DismissListener implements DialogInterface.OnDismissListener {

        private QuestionResultListener listener;
        private boolean buildNewDialogOnDismiss;

        public void setListener(QuestionResultListener listener) {
            this.listener = listener;
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            // remove the item we've just shown.
            if (messageQueue.size() > 0) {
                messageQueue.remove();
            } else {
                Log.w("UiHelper", "Message queue was empty - strange");
            }

            if(listener != null) {
                listener.onDismiss((AlertDialog) dialog);
            }

            if(buildNewDialogOnDismiss) {
                // build a new dialog (needed if the view was altered)
                buildAlertDialog();
            }

            if (messageQueue.size() > 0 && canShowDialog()) {
                QueuedMessage nextMessage = messageQueue.peek();
                if(nextMessage instanceof QueuedQuestionMessage) {
                    showDialog((QueuedQuestionMessage)nextMessage);
                } else if(nextMessage != null) {
                    showDialog(nextMessage);
                }
            }
        }

        public void setBuildNewDialogOnDismiss(boolean buildNewDialogOnDismiss) {
            this.buildNewDialogOnDismiss = buildNewDialogOnDismiss;
        }
    }

    private void setupDialogBoxes() {
        progressDialog = new ProgressDialog(context);
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        buildAlertDialog();
    }

    protected void buildAlertDialog() {
        AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
        builder1.setCancelable(true);
        dismissListener = new DismissListener();
        builder1.setOnDismissListener(dismissListener);
        alertDialog = builder1.create();
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
        if(nextMessage.isShowNeutralButton()) {
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
    }

    protected void showDialog(final QueuedMessage nextMessage) {
        buildAlertDialog();
        alertDialog.setCancelable(nextMessage.isCancellable());
        alertDialog.setTitle(nextMessage.getTitleId());
        alertDialog.setMessage(nextMessage.getMessage());
        Button b = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if(b != null) {
            b.setVisibility(View.GONE);
        }
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(nextMessage.getPositiveButtonTextId()), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                QuestionResultListener l = nextMessage.getListener();
                if(l != null) {
                    l.onResult(alertDialog, true);
                }
            }
        });
        dismissListener.setListener(nextMessage.getListener());
        alertDialog.show();
    }


    public void addActiveServiceCall(int titleStringId, long messageId) {
        addActiveServiceCall(context.getString(titleStringId), messageId);
    }

    public BasicPiwigoResponseListener getPiwigoResponseListener() {
        return piwigoResponseListener;
    }

    public boolean isServiceCallInProgress() {
        return activeServiceCalls.size() > 0;
    }

    public void deregisterFromActiveServiceCalls() {

        EventBus.getDefault().unregister(this);
        for (long activeCall : activeServiceCalls) {
            PiwigoResponseBufferingHandler.getDefault().deRegisterResponseHandler(activeCall);
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        Bundle thisBundle = new Bundle();
        thisBundle.putSerializable(ACTIVE_SERVICE_CALLS, activeServiceCalls);
        thisBundle.putInt(STATE_TRACKED_REQUESTS, trackedRequest);
        thisBundle.putSerializable(STATE_RUN_WITH_PERMS_LIST, runWithPermissions);
        thisBundle.putInt(STATE_PERMS_FOR_REASON, permissionsNeededReason);
        piwigoResponseListener.onSaveInstanceState(thisBundle);
        outState.putBundle(STATE_UIHELPER, thisBundle);
    }

    public void onRestoreSavedInstanceState(Bundle savedInstanceState) {
        if(savedInstanceState != null) {
            Bundle thisBundle = savedInstanceState.getBundle(STATE_UIHELPER);
            if (thisBundle != null) {
                activeServiceCalls = (HashSet<Long>) thisBundle.getSerializable(ACTIVE_SERVICE_CALLS);
                trackedRequest = thisBundle.getInt(STATE_TRACKED_REQUESTS);
                runWithPermissions = (HashMap<Integer, PermissionsWantedRequestEvent>) thisBundle.getSerializable(STATE_RUN_WITH_PERMS_LIST);
                permissionsNeededReason = thisBundle.getInt(STATE_PERMS_FOR_REASON);
                piwigoResponseListener.onRestoreInstanceState(thisBundle);
            }
        }
    }

    public void setPermissionsNeededReason(int permissionsNeededReason) {
        this.permissionsNeededReason = permissionsNeededReason;
    }

    public int getPermissionsNeededReason() {
        return permissionsNeededReason;
    }

    public void showNextQueuedMessage() {
        if(messageQueue.size() > 0 && (alertDialog != null && !alertDialog.isShowing())) {
            // show the dialog now we're able.
            QueuedMessage nextMessage = messageQueue.peek();
            if(nextMessage instanceof QueuedQuestionMessage) {
                showDialog((QueuedQuestionMessage)nextMessage);
            } else if(nextMessage != null) {
                showDialog(nextMessage);
            }
        }
    }

    public int getActiveServiceCallCount() {
        return activeServiceCalls.size();
    }

    public void closeAllDialogs() {
        alertDialog.dismiss();
        dismissProgressDialog();
    }

    public int runWithExtraPermissions(final Fragment fragment, int sdkVersionRequiredFrom, int sdkVersionRequiredUntil, final String permissionNeeded, String permissionJustificationString) {
        PermissionsWantedRequestEvent event = new PermissionsWantedRequestEvent();
        event.addPermissionNeeded(permissionNeeded);
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

        final PermissionRequester requester =new ActivityPermissionRequester(activity);
        if(activity instanceof FragmentActivity) {
            event.setActionId(event.getActionId() & 0xffff);
        }
        runWithPermissions.put(event.getActionId(), event);

        final HashSet<String> permissionsWanted = event.getPermissionsWanted();

        final HashSet<String> permissionsNeeded = new HashSet<>(permissionsWanted.size());
        if(Build.VERSION.SDK_INT <= sdkVersionRequiredUntil && Build.VERSION.SDK_INT >= sdkVersionRequiredFrom) {
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
            for(String permissionNeeded : permissionsNeeded) {
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
                        onRequestPermissionsResult(activity, event.getActionId(), permissionsNeeded.toArray(new String[permissionsNeeded.size()]), new int[0]);
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
            onRequestPermissionsResult(activity, event.getActionId(), permissionsWanted.toArray(new String[permissionsWanted.size()]), permissionResponses);
        }
        return event.getActionId();
    }

    public void onRequestPermissionsResult(Activity activity, int requestCode, String[] permissions, int[] grantResults) {
        int actionId = requestCode;
        if(activity instanceof FragmentActivity) {
            actionId &= 0xffff;
        }
        // If request is cancelled, the result arrays are empty.
        PermissionsWantedResponse event = new PermissionsWantedResponse(actionId, permissions, grantResults);
        EventBus.getDefault().post(event);
    }

    public boolean messagesQueuedOrShowing() {
        return messageQueue.size() > 0;
    }


    public static void recycleImageViewContent(ImageView imgView) {
        if(imgView != null) {
            Bitmap img = imgView.getDrawingCache();
            if(img != null) {
                img.recycle();
            }
        }

    }

    protected abstract boolean canShowDialog();

    public void showProgressDialog(int titleId) {
        progressDialog.setTitle(titleId);
        progressDialog.show();
    }

    public void showProgressDialog() {
        progressDialog.show();
    }

    public void dismissProgressDialog() {
        progressDialog.dismiss();
    }

    public interface QuestionResultListener extends Serializable {
        void onDismiss(AlertDialog dialog);
        void onResult(AlertDialog dialog, Boolean positiveAnswer);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final NewUnTrustedCaCertificateReceivedEvent event) {
        if(!canShowDialog()) {
            return;
        }
        if(event.isHandled()) {
            return;
        }
        final Set<String> preNotifiedCerts = prefs.getStringSet(context.getString(R.string.preference_pre_user_notified_certificates_key), new HashSet<String>());
        if(preNotifiedCerts.containsAll(event.getUntrustedCerts().keySet())) {
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
        for(X509Certificate cert : event.getUntrustedCerts().values()) {

            if(untrustedCertCount == 1 && cert.equals(event.getEndCertificate())) {
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

        showOrQueueDialogQuestion(R.string.alert_information, message, R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultListener() {

            @Override
            public void onDismiss(AlertDialog dialog){}

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if(Boolean.TRUE == positiveAnswer) {
                    KeyStore trustStore = X509Utils.loadTrustedCaKeystore(context);
                    try {
                        for(Map.Entry<String,X509Certificate> entry : event.getUntrustedCerts().entrySet()) {
                            trustStore.setCertificateEntry(entry.getKey(), entry.getValue());
                        }
                        X509Utils.saveTrustedCaKeystore(getContext(), trustStore);
                    } catch (KeyStoreException e) {
                        showOrQueueDialogMessage(R.string.alert_error, context.getString(R.string.alert_error_adding_certificate_to_truststore));
                    }
                    preNotifiedCerts.addAll(event.getUntrustedCerts().keySet());
                    prefs.edit().putStringSet(context.getString(R.string.preference_pre_user_notified_certificates_key), preNotifiedCerts).commit();
                    long messageId = new HttpConnectionCleanup(ConnectionPreferences.getActiveProfile(), context).start();
                    PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, new BasicPiwigoResponseListener() {
                        @Override
                        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
                            showOrQueueMessage(R.string.alert_information, getContext().getString(R.string.alert_http_engine_shutdown));
                        }
                    });
                }
            }
        });
    }

    public void showOrQueueDialogQuestion(int titleId, String message, int negativeButtonTextId, int positiveButtonTextId, final QuestionResultListener listener) {
        showOrQueueDialogMessage(new QueuedQuestionMessage(titleId, message, positiveButtonTextId, negativeButtonTextId, listener));
    }

    public void showOrQueueDialogQuestion(int titleId, String message, int layoutId, int negativeButtonTextId, int neutralButtonTextId, int positiveButtonTextId, final QuestionResultListener listener) {
        showOrQueueDialogMessage(new QueuedQuestionMessage(titleId, message, layoutId, positiveButtonTextId, negativeButtonTextId, neutralButtonTextId, listener));
    }

    public void showOrQueueDialogQuestion(int titleId, String message, int layoutId, int negativeButtonTextId, int positiveButtonTextId, final QuestionResultListener listener) {
        showOrQueueDialogMessage(new QueuedQuestionMessage(titleId, message, layoutId, positiveButtonTextId, negativeButtonTextId, listener));
    }

    public void showOrQueueDialogMessage(int titleId, String message, int positiveButtonTextId) {
        showOrQueueDialogMessage(new QueuedMessage(titleId, message, positiveButtonTextId));
    }

    public void showOrQueueDialogMessage(int titleId, String message, int positiveButtonTextId, boolean cancellable, QuestionResultListener listener) {
        showOrQueueDialogMessage(new QueuedMessage(titleId, message, positiveButtonTextId, cancellable, listener));
    }

    public void showOrQueueDialogMessage(int titleId, String message, QuestionResultListener listener) {
        showOrQueueDialogMessage(new QueuedMessage(titleId, message, listener));
    }

    public void showOrQueueDialogMessage(int titleId, String message) {
        showOrQueueDialogMessage(new QueuedMessage(titleId, message));
    }

    public <S extends QueuedMessage> void showOrQueueDialogMessage(S message) {
        if (alertDialog != null && !alertDialog.isShowing() && canShowDialog()) {
            QueuedMessage nextMessage = messageQueue.peek();
            if(nextMessage instanceof QueuedQuestionMessage) {
                showDialog((QueuedQuestionMessage)nextMessage);
            } else if(nextMessage != null) {
                showDialog(nextMessage);
            }
        }

        if(!messageQueue.contains(message)) {
            messageQueue.add(message);
        }
        if (alertDialog != null && !alertDialog.isShowing() && canShowDialog()) {
            QueuedMessage nextMessage = messageQueue.peek();
            if(nextMessage instanceof QueuedQuestionMessage) {
                showDialog((QueuedQuestionMessage)nextMessage);
            } else if(nextMessage != null) {
                showDialog(nextMessage);
            }
        }
    }

    public void registerToActiveServiceCalls() {
        if(!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        for (long activeCall : activeServiceCalls) {
            PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(activeCall, piwigoResponseListener);
        }
    }

    public void onServiceCallComplete(long messageId) {
        activeServiceCalls.remove(messageId);
        if (activeServiceCalls.size() == 0) {
            progressDialog.dismiss();
        }
    }

    public void onServiceCallComplete(PiwigoResponseBufferingHandler.Response response) {
        if(response.isEndResponse()) {
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
        if(trackedRequest == requestId) {
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

    private static class ActivityPermissionRequester implements PermissionRequester {
        private final Activity activity;

        public ActivityPermissionRequester(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void requestPermission(int requestId, final HashSet<String> permissionsNeeded) {
            ActivityCompat.requestPermissions(
                    activity,
                    permissionsNeeded.toArray(new String[permissionsNeeded.size()]),
                    requestId);
        }
    }

    public boolean completePermissionsWantedRequest(PermissionsWantedResponse response) {
        PermissionsWantedRequestEvent request = runWithPermissions.remove(response.getActionId());
        if(request != null) {
            response.addAllPermissionsAlreadyHaveFromRequest(request);
            return true;
        }
        return false;
    }
}
