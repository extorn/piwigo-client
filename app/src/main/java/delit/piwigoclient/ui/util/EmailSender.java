package delit.piwigoclient.ui.util;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import delit.libs.util.ProjectUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;

public class EmailSender implements OnCompleteListener<String> {

    private final String toEmailAddress;
    private final Context context;

    public EmailSender(Context context, String toEmailAddress) {
        this.toEmailAddress = toEmailAddress;
        this.context = context;
    }

    @Override
    public void onComplete(@NonNull Task<String> uuidTask) {
        String uuid = null;
        if(uuidTask.isSuccessful()) {
            uuid = uuidTask.getResult();
        }
        final String appVersion = ProjectUtils.getVersionName(context);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain"); // send email as plain text
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{toEmailAddress});
        intent.putExtra(Intent.EXTRA_SUBJECT, "PIWIGO Client");
        String serverVersion = "Unknown";
        String activePluginSummary = "Unknown";
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
            serverVersion = sessionDetails.getPiwigoVersion();
            activePluginSummary = sessionDetails.getActivePluginSummary().replaceAll("},", "},\n");
        }
        String emailContent = context.getString(R.string.support_email_pattern, context.getString(R.string.localizedEmailHelp),  serverVersion, appVersion, Build.VERSION.CODENAME + "(" + Build.VERSION.SDK_INT + ")", uuid, activePluginSummary);
        intent.putExtra(Intent.EXTRA_TEXT, emailContent);
        context.startActivity(Intent.createChooser(intent, "Email Text"));
    }


}
