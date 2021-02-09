package hotchemi.android.rate;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import delit.libs.core.util.Logging;
import delit.piwigoclient.R;

import static hotchemi.android.rate.IntentHelper.createIntentForAmazonAppstore;
import static hotchemi.android.rate.IntentHelper.createIntentForGooglePlay;
import static hotchemi.android.rate.PreferenceHelper.setAgreeShowDialog;
import static hotchemi.android.rate.PreferenceHelper.setRemindInterval;

final class MyRatingDialogManager {

    private MyRatingDialogManager() {
    }

    static Dialog create(final Context context, final DialogOptions options, int onNoSuitableActivityErrorMsg) {
        Context dialogContext = new ContextThemeWrapper(context, R.style.Theme_App_EditPages);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(dialogContext);
        builder.setMessage(options.getMessageText(dialogContext));

        if (options.shouldShowTitle()) builder.setTitle(options.getTitleText(dialogContext));

        builder.setCancelable(options.getCancelable());

        View view = options.getView();
        if (view != null) builder.setView(view);

        final OnClickButtonListener listener = options.getListener();

        builder.setPositiveButton(options.getPositiveText(dialogContext), (dialog, which) -> {
            final Intent intentToAppstore = options.getStoreType() == StoreType.GOOGLEPLAY ?
                    createIntentForGooglePlay(dialogContext) : createIntentForAmazonAppstore(dialogContext);
            try {
                Logging.log(Log.DEBUG, "MyDialogMgr", String.format("Trying to start activity for result : %1$s", intentToAppstore.toString()));
                dialogContext.startActivity(Intent.createChooser(intentToAppstore, ""));
            } catch (ActivityNotFoundException e) {
                Logging.recordException(e);
                // should never happen but will on an emulator almost certainly.
                //Lets just sink the error for now and let the dialog close.
                MaterialAlertDialogBuilder builder1 = new MaterialAlertDialogBuilder(dialogContext);
                builder1.setCancelable(false);
                builder1.setTitle(R.string.alert_error);
                builder1.setMessage(onNoSuitableActivityErrorMsg);
                builder1.setPositiveButton(
                        R.string.button_close,
                        (dialog1, id) -> dialog1.cancel());
                AlertDialog alertDialog = builder1.create();
                alertDialog.show();
            }
            setAgreeShowDialog(dialogContext, false);
            if (listener != null) listener.onClickButton(which);
        });

        if (options.shouldShowNeutralButton()) {
            builder.setNeutralButton(options.getNeutralText(dialogContext), (dialog, which) -> {
                setRemindInterval(dialogContext);
                if (listener != null) listener.onClickButton(which);
            });
        }

        if (options.shouldShowNegativeButton()) {
            builder.setNegativeButton(options.getNegativeText(dialogContext), (dialog, which) -> {
                setAgreeShowDialog(dialogContext, false);
                if (listener != null) listener.onClickButton(which);
            });
        }

        return builder.create();
    }

}