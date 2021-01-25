package delit.piwigoclient.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;

import delit.libs.util.ProjectUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.EulaAgreedEvent;
import delit.piwigoclient.ui.events.EulaNotAgreedEvent;

/**
 * Created by gareth on 07/06/17.
 */

public class EulaFragment<F extends EulaFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> {
    public static EulaFragment newInstance() {
        EulaFragment fragment = new EulaFragment();
        fragment.setTheme(R.style.Theme_App_EditPages);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // ensure the dialog boxes are setup
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_eula_view, container, false);

        AdView adView = view.findViewById(R.id.eula_adView);
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        int agreedEulaVersion = prefs.getInt(getString(R.string.preference_agreed_eula_version_key), -1);
        int currentEulaVersion = getResources().getInteger(R.integer.eula_version);
        boolean agreedEula = agreedEulaVersion >= currentEulaVersion;
        Button cancelButton = view.findViewById(R.id.eula_cancel_button);
        Button agreeButton = view.findViewById(R.id.eula_agree_button);
        if (agreedEula) {
            cancelButton.setVisibility(View.GONE);
            agreeButton.setVisibility(View.GONE);
        } else {
            cancelButton.setVisibility(View.VISIBLE);
            agreeButton.setVisibility(View.VISIBLE);
            cancelButton.setOnClickListener(v -> onDontAgreeToEula());
            agreeButton.setOnClickListener(v -> onAgreeToEula());
        }

        final TextView email = view.findViewById(R.id.eula_admin_email);

        email.setOnClickListener(v -> sendEmail(((TextView) v).getText().toString()));


        return view;
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.app_licence_heading);
    }

    private void sendEmail(String email) {

        final String appVersion = ProjectUtils.getVersionName(requireContext());

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain"); // send email as plain text
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
        intent.putExtra(Intent.EXTRA_SUBJECT, "PIWIGO Client");
        String serverVersion = "Unknown";
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
            serverVersion = sessionDetails.getPiwigoVersion();
        }
        intent.putExtra(Intent.EXTRA_TEXT, "Comments:\nFeature Request:\nBug Summary:\nBug Details:\nVersion of Piwigo Server Connected to: " + serverVersion + "\nVersion of PIWIGO Client: " + appVersion + "\nType and model of Device Being Used:\n");
        requireContext().startActivity(Intent.createChooser(intent, getString(R.string.create_email_using_app)));
    }

    private void onDontAgreeToEula() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(getString(R.string.preference_agreed_eula_version_key));
        editor.commit();
        EventBus.getDefault().post(new EulaNotAgreedEvent());
    }

    private void onAgreeToEula() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(getString(R.string.preference_agreed_eula_version_key), getResources().getInteger(R.integer.eula_version));
        editor.commit();
        EventBus.getDefault().post(new EulaAgreedEvent());
    }

}
