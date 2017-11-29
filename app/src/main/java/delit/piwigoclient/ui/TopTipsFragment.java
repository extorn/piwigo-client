package delit.piwigoclient.ui;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.MyFragment;

/**
 * Created by gareth on 04/08/17.
 */

public class TopTipsFragment extends MyFragment {

    public static TopTipsFragment newInstance(MainActivity mainActivity) {
        TopTipsFragment fragment = new TopTipsFragment();
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // ensure the dialog boxes are setup
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_top_tips, container, false);

        AdView adView = view.findViewById(R.id.toptips_adView);
        if(AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(View.VISIBLE);
        } else {
            adView.setVisibility(View.GONE);
        }
        return view;
    }
}
