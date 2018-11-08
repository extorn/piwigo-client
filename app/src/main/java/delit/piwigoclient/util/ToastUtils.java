package delit.piwigoclient.util;

import android.content.Context;
import androidx.core.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import delit.piwigoclient.R;

public class ToastUtils {
    public static Toast makeDetailedToast(Context c, int titleResId, String message, int duration) {
        Toast toast = new Toast(c.getApplicationContext());
        LayoutInflater inflater = (LayoutInflater)c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.toast_detailed_notification, null);
        toast.setView(v);
        toast.setDuration(duration);
        toast.setText(message);
        ((TextView)v.findViewById(R.id.toast_title)).setText(titleResId);
        ImageView icon = v.findViewById(R.id.toast_icon);
        switch(titleResId) {
            case R.string.alert_error:
                icon.setImageDrawable(ContextCompat.getDrawable(c,android.R.drawable.stat_notify_error));
                icon.setVisibility(View.VISIBLE);
                break;
            case R.string.alert_information:
                icon.setImageDrawable(ContextCompat.getDrawable(c, android.R.drawable.ic_menu_info_details));
                icon.setVisibility(View.VISIBLE);
                break;
            case R.string.alert_success:
                icon.setImageDrawable(ContextCompat.getDrawable(c,android.R.drawable.btn_star_big_on));
                icon.setVisibility(View.VISIBLE);
                break;
            default:
                icon.setVisibility(View.GONE);
        }
        return toast;
    }
}
