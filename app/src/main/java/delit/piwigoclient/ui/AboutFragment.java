package delit.piwigoclient.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdView;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.common.list.PairedArrayAdapter;

/**
 * Created by gareth on 07/06/17.
 */

public class AboutFragment extends MyFragment<AboutFragment> {
    public static AboutFragment newInstance() {
        return new AboutFragment();
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
        plannedReleases.setAdapter(new ReleaseListAdapter(getContext(), R.layout.layout_simple_list_item, R.array.planned_releases));

        ListView releaseHistory = view.findViewById(R.id.about_release_history);
        releaseHistory.setAdapter(new ReleaseListAdapter(getContext(), R.layout.layout_simple_list_item, R.array.release_history));

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
            TextView headingText = view.findViewById(R.id.name);
            headingText.setText(heading);

            data = data.replaceAll("\\n[\\s]*(\\s-|\\w)", "\n$1");

            TextView detailsText = view.findViewById(R.id.details);
            detailsText.setText(data);
        }
    }

}
