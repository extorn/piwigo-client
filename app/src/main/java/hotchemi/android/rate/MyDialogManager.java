package hotchemi.android.rate;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import delit.libs.core.util.Logging;

import static hotchemi.android.rate.IntentHelper.createIntentForAmazonAppstore;
import static hotchemi.android.rate.IntentHelper.createIntentForGooglePlay;
import static hotchemi.android.rate.PreferenceHelper.setAgreeShowDialog;
import static hotchemi.android.rate.PreferenceHelper.setRemindInterval;

final class MyDialogManager {

    private MyDialogManager() {
    }

    static Dialog create(final Context context, final DialogOptions options) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setMessage(options.getMessageText(context));

        if (options.shouldShowTitle()) builder.setTitle(options.getTitleText(context));

        builder.setCancelable(options.getCancelable());

        View view = options.getView();
        if (view != null) builder.setView(view);

        final OnClickButtonListener listener = options.getListener();

        builder.setPositiveButton(options.getPositiveText(context), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final Intent intentToAppstore = options.getStoreType() == StoreType.GOOGLEPLAY ?
                        createIntentForGooglePlay(context) : createIntentForAmazonAppstore(context);
                try {
                    Logging.log(Log.DEBUG, "MyDialogMgr", String.format("Trying to start activity for result : %1$s", intentToAppstore.toString()));
                    context.startActivity(Intent.createChooser(intentToAppstore, ""));
                } catch (ActivityNotFoundException e) {
                    Logging.recordException(e);
                    // should never happen but will on an emulator almost certainly.
                    //Lets just sink the error for now and let the dialog close.
                    MaterialAlertDialogBuilder builder1 = new MaterialAlertDialogBuilder(context);
                    builder1.setCancelable(false);
                    builder1.setTitle(delit.piwigoclient.R.string.alert_error);
                    builder1.setMessage(delit.piwigoclient.R.string.alert_error_no_app_available_to_rate_app);
                    builder1.setPositiveButton(
                            delit.piwigoclient.R.string.button_close,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });
                    AlertDialog alertDialog = builder1.create();
                    alertDialog.show();
                }
                setAgreeShowDialog(context, false);
                if (listener != null) listener.onClickButton(which);
            }
        });

        if (options.shouldShowNeutralButton()) {
            builder.setNeutralButton(options.getNeutralText(context), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    setRemindInterval(context);
                    if (listener != null) listener.onClickButton(which);
                }
            });
        }

        if (options.shouldShowNegativeButton()) {
            builder.setNegativeButton(options.getNegativeText(context), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    setAgreeShowDialog(context, false);
                    if (listener != null) listener.onClickButton(which);
                }
            });
        }

        return builder.create();
    }

}