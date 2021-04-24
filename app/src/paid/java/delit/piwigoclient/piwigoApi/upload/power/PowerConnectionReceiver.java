package delit.piwigoclient.piwigoApi.upload.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import delit.piwigoclient.piwigoApi.upload.UploadServiceNetworkListener;

public class PowerConnectionReceiver extends BroadcastReceiver {
    private final UploadServiceNetworkListener uploadEventListener;

    public PowerConnectionReceiver(UploadServiceNetworkListener uploadEventListener) {
        this.uploadEventListener = uploadEventListener;
    }

    public void registerBroadcastReceiver(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        context.registerReceiver(this, filter);
    }

    @Override
    public void onReceive(Context context, Intent batteryStatus) {
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        //TODO is the isCharging relevant? Needed?
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL;
        boolean pluggedIn = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        uploadEventListener.onHasExternalPowerChange(pluggedIn);
    }

    public void unregisterBroadcastReceiver(Context context) {
        context.unregisterReceiver(this);
    }
}
