package delit.piwigoclient.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdView;

import java.util.Map;

import delit.libs.ui.view.list.PairedArrayAdapter;
import delit.libs.ui.view.list.StringMapExpandableListAdapterBuilder;
import delit.libs.util.ArrayUtils;
import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.fragment.MyFragment;

/**
 * Created by gareth on 07/06/17.
 */

public class AboutFragment extends MyFragment<AboutFragment> {
    public static AboutFragment newInstance() {
        AboutFragment fragment = new AboutFragment();
        fragment.setTheme(R.style.Theme_App_EditPages);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // ensure the dialog boxes are setup
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_about, container, false);

        AdView adView = view.findViewById(R.id.about_adView);
        if (AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        ListView plannedReleases = view.findViewById(R.id.about_planned_releases);
        plannedReleases.setAdapter(new ReleaseListAdapter(view.getContext(), R.layout.layout_list_item_simple, R.array.planned_releases));

        ExpandableListView exifDataList = view.findViewById(R.id.about_release_history);
        Map<String,String> data = ArrayUtils.toMap(view.getContext().getResources().getStringArray(R.array.release_history));
        SimpleExpandableListAdapter expandingListAdapter = new StringMapExpandableListAdapterBuilder().build(view.getContext(), data);
        exifDataList.setAdapter(expandingListAdapter);
        exifDataList.setOnGroupCollapseListener(groupPosition -> exifDataList.getParent().requestLayout());
        exifDataList.setOnGroupExpandListener(groupPosition -> exifDataList.getParent().requestLayout());

        return view;
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.about_heading);
    }

    class ReleaseListAdapter extends PairedArrayAdapter<String> {

        public ReleaseListAdapter(@NonNull Context context, int itemLayout, @NonNull String[] data) {
            super(context, itemLayout, data);
        }

        public ReleaseListAdapter(@NonNull Context context, int itemLayout, int dataResource) {
            super(context, itemLayout, context.getResources().getStringArray(dataResource));
        }

        @Override
        public void populateView(View view, String heading, String data) {
            TextView headingText = view.findViewById(R.id.list_item_name);
            headingText.setText(heading);

            data = data.replaceAll("\\n[\\s]*(\\s-|\\w)", "\n$1");

            TextView detailsText = view.findViewById(R.id.list_item_details);
            detailsText.setText(data);
        }
    }

}
