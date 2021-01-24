package delit.piwigoclient.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdView;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;

/**
 * Created by gareth on 04/08/17.
 */

public class TopTipsFragment<F extends TopTipsFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> {

    public static TopTipsFragment newInstance() {
        TopTipsFragment fragment = new TopTipsFragment();
        fragment.setTheme(R.style.Theme_App_EditPages);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // ensure the dialog boxes are setup
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_top_tips_view, container, false);

        ListView plannedReleases = view.findViewById(R.id.toptips_list);
        String[] data = getResources().getStringArray(R.array.top_tips);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), R.layout.layout_top_tips_list_item, R.id.list_item_details, data);
        plannedReleases.setAdapter(adapter);

        AdView adView = view.findViewById(R.id.toptips_adView);
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
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
