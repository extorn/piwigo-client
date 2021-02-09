package delit.piwigoclient.ui.util;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import delit.libs.util.CustomSnackbar;
import delit.piwigoclient.R;

public class TransientMsgUtils {

    public static Toast makeDetailedToast(Context c, int titleResId, final String message, int duration) {
        ContextThemeWrapper toastContext = new ContextThemeWrapper(c, R.style.Theme_App_EditPages);
        final Toast toast = new Toast(toastContext);
        LayoutInflater inflater = LayoutInflater.from(toastContext);
        View v = inflater.inflate(R.layout.toast_detailed_notification, null);
        toast.setView(v);
        toast.setDuration(duration);
        try {
            toast.setText(message);
        } catch (IllegalStateException e) {
            // on Android 11
            TextView textView = v.findViewById(android.R.id.message);
            textView.setText(message);
        }
        ((TextView)v.findViewById(R.id.toast_title)).setText(titleResId);
        ImageView icon = v.findViewById(R.id.toast_icon);
        if (titleResId == R.string.alert_error) {
            icon.setImageDrawable(AppCompatResources.getDrawable(toastContext, android.R.drawable.stat_notify_error));
            icon.setVisibility(View.VISIBLE);
        } else if (titleResId == R.string.alert_warning) {
            icon.setImageDrawable(AppCompatResources.getDrawable(toastContext, R.drawable.ic_warning_black_24dp));
            icon.setVisibility(View.VISIBLE);
        } else if (titleResId == R.string.alert_information) {
            icon.setImageDrawable(AppCompatResources.getDrawable(toastContext, android.R.drawable.ic_menu_info_details));
            icon.setVisibility(View.VISIBLE);
        } else if (titleResId == R.string.alert_success) {
            icon.setImageDrawable(AppCompatResources.getDrawable(toastContext, android.R.drawable.btn_star_big_on));
            icon.setVisibility(View.VISIBLE);
        } else {
            icon.setVisibility(View.GONE);
        }
        return toast;
    }

    public static CustomSnackbar makeSnackbar(@NonNull View parent, int titleResId, String message, int duration) {
        Context snackbarContext = new ContextThemeWrapper(parent.getContext(), R.style.Theme_App_EditPages);
        CustomSnackbar snackbar = CustomSnackbar.make(snackbarContext, parent, duration);
        if(message == null) {
            snackbar.setTitle(null);
            snackbar.setText(snackbar.getContext().getString(titleResId));
        } else {
            snackbar.setTitle(snackbar.getContext().getString(titleResId));
            snackbar.setText(message);
        }
        if (titleResId == R.string.alert_error) {
            snackbar.setIcon(R.drawable.ic_error_outline_black_24dp);
        } else if (titleResId == R.string.alert_failure) {
            snackbar.setIcon(R.drawable.ic_error_outline_black_24dp);
        } else if (titleResId == R.string.alert_warning) {
            snackbar.setIcon(R.drawable.ic_warning_black_24dp);
        } else if (titleResId == R.string.alert_information) {
            snackbar.setIcon(R.drawable.ic_info_outline_black_24dp);
        } else if (titleResId == R.string.alert_success) {
            snackbar.setIcon(android.R.drawable.btn_star_big_on);
        }
        return snackbar;
    }
}
