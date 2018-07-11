package delit.piwigoclient.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.EulaAgreedEvent;
import delit.piwigoclient.ui.events.EulaNotAgreedEvent;
import delit.piwigoclient.util.ProjectUtils;

/**
 * Created by gareth on 07/06/17.
 */

public class EulaFragment extends MyFragment {
    public static EulaFragment newInstance() {
        return new EulaFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // ensure the dialog boxes are setup
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_eula, container, false);

        AdView adView = view.findViewById(R.id.eula_adView);
        if(AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        int agreedEulaVersion = prefs.getInt(getString(R.string.preference_agreed_eula_version_key), -1);
        int currentEulaVersion = getResources().getInteger(R.integer.eula_version);
        boolean agreedEula = agreedEulaVersion >= currentEulaVersion;
        Button cancelButton = view.findViewById(R.id.eula_cancel_button);
        Button agreeButton = view.findViewById(R.id.eula_agree_button);
        if(agreedEula) {
            cancelButton.setVisibility(View.GONE);
            agreeButton.setVisibility(View.GONE);
        } else {
            cancelButton.setVisibility(View.VISIBLE);
            agreeButton.setVisibility(View.VISIBLE);
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDontAgreeToEula();
                }
            });
            agreeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onAgreeToEula();
                }
            });
        }

        final TextView email = view.findViewById(R.id.eula_admin_email);

        email.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendEmail(((TextView)v).getText().toString());
            }
        });


        return view;
    }

    private void sendEmail(String email) {

        final String appVersion = ProjectUtils.getVersionName(getContext());

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain"); // send email as plain text
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
        intent.putExtra(Intent.EXTRA_SUBJECT, "PIWIGO Client");
        String serverVersion = "Unknown";
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if(sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
            serverVersion = sessionDetails.getPiwigoVersion();
        }
        intent.putExtra(Intent.EXTRA_TEXT, "Comments:\nFeature Request:\nBug Summary:\nBug Details:\nVersion of Piwigo Server Connected to: " + serverVersion + "\nVersion of PIWIGO Client: "+ appVersion +"\nType and model of Device Being Used:\n");
        getContext().startActivity(Intent.createChooser(intent, ""));
    }

    private void onDontAgreeToEula() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(getString(R.string.preference_agreed_eula_version_key));
        editor.commit();
        EventBus.getDefault().post(new EulaNotAgreedEvent());
    }

    private void onAgreeToEula() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(getString(R.string.preference_agreed_eula_version_key), getResources().getInteger(R.integer.eula_version));
        editor.commit();
        EventBus.getDefault().post(new EulaAgreedEvent());
    }

}
