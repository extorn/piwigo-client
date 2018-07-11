package delit.piwigoclient.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.ArrayRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.ads.AdView;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.fragment.MyFragment;

/**
 * Created by gareth on 07/06/17.
 */

public class AboutFragment extends MyFragment {
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
        if(AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        ListView plannedReleases = view.findViewById(R.id.about_planned_releases);
        plannedReleases.setAdapter(new ReleaseListAdapter(getContext(), R.layout.simple_list_item_layout, R.array.planned_releases));

        ListView releaseHistory = view.findViewById(R.id.about_release_history);
        releaseHistory.setAdapter(new ReleaseListAdapter(getContext(), R.layout.simple_list_item_layout, R.array.release_history));

        return view;
    }

    class ReleaseListAdapter extends PairedArrayAdapter {

        public ReleaseListAdapter(@NonNull Context context, int itemLayout, @NonNull String[] data) {
            super(context, itemLayout, data);
        }

        public ReleaseListAdapter(@NonNull Context context, int itemLayout, int dataResource) {
            super(context, itemLayout, dataResource);
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

    abstract class PairedArrayAdapter extends BaseAdapter {

        private final Context context;
        private final String[] data;
        private final int itemLayout;

        public PairedArrayAdapter(@NonNull Context context, @LayoutRes int itemLayout, @ArrayRes int dataResource) {
            this(context, itemLayout, context.getResources().getStringArray(dataResource));
        }

        public PairedArrayAdapter(@NonNull Context context, @LayoutRes int itemLayout, @NonNull String[] data) {
            this.context = context;
            this.data = data;
            this.itemLayout = itemLayout;
        }

        @Override
        public int getCount() {
            return data.length / 2;
        }

        /**
         * Will retrieve the headings
         * @param position
         * @return
         */
        @Override
        public Object getItem(int position) {
            return data[position * 2];
        }

        public String getItemData(int position) {
            return data[1 +  (position * 2)];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View view = convertView; // re-use an existing view, if one is supplied
            if (view == null) {
                // otherwise create a pkg one
                view = LayoutInflater.from(context).inflate(itemLayout, parent, false);
            }
            // set view properties to reflect data for the given row

            String heading = (String)getItem(position);
            String data = getItemData(position);

            populateView(view, heading, data);

            // return the view, populated with data, for display
            return view;
        }

        public abstract void populateView(View view, String heading, String data);
    }
}
