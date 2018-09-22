package delit.piwigoclient.ui;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.android.gms.ads.AdView;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.fragment.MyFragment;

/**
 * Created by gareth on 04/08/17.
 */

public class TopTipsFragment extends MyFragment {

    public static TopTipsFragment newInstance() {
        return new TopTipsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // ensure the dialog boxes are setup
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_top_tips, container, false);

        ListView plannedReleases = view.findViewById(R.id.toptips_list);
        String[] data = getResources().getStringArray(R.array.top_tips);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), R.layout.layout_top_tips_list_item, R.id.details, data);
        plannedReleases.setAdapter(adapter);

        AdView adView = view.findViewById(R.id.toptips_adView);
        if (AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }
        return view;
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.top_tips_heading);
    }
}
