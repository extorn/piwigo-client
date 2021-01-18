package delit.piwigoclient.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdView;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.installations.FirebaseInstallations;

import java.util.Map;
import java.util.Objects;

import delit.libs.ui.view.list.StringMapExpandableListAdapterBuilder;
import delit.libs.util.ArrayUtils;
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
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        ExpandableListView plannedReleasesListView = view.findViewById(R.id.about_planned_releases);
        Map<String,String> futureReleasesData = ArrayUtils.toMap(view.getContext().getResources().getStringArray(R.array.planned_releases));
        SimpleExpandableListAdapter futureReleasesListAdapter = new StringMapExpandableListAdapterBuilder().build(view.getContext(), futureReleasesData);
        plannedReleasesListView.setAdapter(futureReleasesListAdapter);
        if(futureReleasesData.size() == 1) {
            plannedReleasesListView.expandGroup(0);
        }

        ExpandableListView historicalReleasesListView = view.findViewById(R.id.about_release_history);
        Map<String,String> historicalReleasesData = ArrayUtils.toMap(view.getContext().getResources().getStringArray(R.array.release_history));
        SimpleExpandableListAdapter historicalReleasesListAdapter = new StringMapExpandableListAdapterBuilder().build(view.getContext(), historicalReleasesData);
        historicalReleasesListView.setAdapter(historicalReleasesListAdapter);
        if(futureReleasesData.size() >= 1) {
            historicalReleasesListView.expandGroup(0);
        }

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setClickable(false);
        Task<String> idTask = FirebaseInstallations.getInstance().getId(); //This is a globally unique id for the app installation instance.
        idTask.addOnCompleteListener(this::withInstallGuid);
    }

    private void withInstallGuid(Task<String> uuidTask) {
        String installGuid;
        TextView uuidField = requireView().findViewById(R.id.install_guid_field);
        try {
            if (uuidTask.isSuccessful()) {
                installGuid = uuidTask.getResult();
                uuidField.setText(installGuid);
                uuidField.setOnClickListener(v -> {
                    Context context = v.getContext();
                    ClipboardManager mgr = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (mgr != null) {
                        ClipData clipData = ClipData.newPlainText(getString(R.string.uuid_title), ((TextView) v).getText());
                        mgr.setPrimaryClip(clipData);
                        getUiHelper().showShortMsg(R.string.copied_to_clipboard);
                    } else {
                        FirebaseAnalytics.getInstance(context).logEvent("NoClipMgr", null);
                    }
                });
            }
        } finally {
            uuidField.setClickable(true);
        }
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.about_heading);
    }

}
