package delit.piwigoclient.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdView;
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;

/**
 * Created by gareth on 07/06/17.
 */

public class LicencesFragment<F extends LicencesFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> {
    public static LicencesFragment newInstance() {
        LicencesFragment fragment = new LicencesFragment();
        fragment.setTheme(R.style.Theme_App_EditPages);
        return fragment;
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.licences_heading);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // ensure the dialog boxes are setup
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_licences_view, container, false);

        Button button = view.findViewById(R.id.other_oss_licences_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //                OssLicensesMenuActivity.setActivityTitle(getString(R.string.custom_license_title));
                startActivity(new Intent(getActivity(), OssLicensesMenuActivity.class));
            }
        });

        AdView adView = view.findViewById(R.id.licences_adView);
        if(AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        return view;
    }
}
