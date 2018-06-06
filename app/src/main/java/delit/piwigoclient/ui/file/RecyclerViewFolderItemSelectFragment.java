package delit.piwigoclient.ui.file;

import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.common.util.ArrayUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.BackButtonHandler;
import delit.piwigoclient.ui.common.LongSetSelectFragment;
import delit.piwigoclient.ui.common.MappedArrayAdapter;
import delit.piwigoclient.ui.common.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;

public class RecyclerViewFolderItemSelectFragment extends RecyclerViewLongSetSelectFragment<FolderItemRecyclerViewAdapter, FolderItemViewAdapterPreferences> implements BackButtonHandler {
    private static final String ACTIVE_FOLDER = "activeFolder";
    private LinearLayout folderPathView;
    private Spinner spinner;
    private MappedArrayAdapter<String, File> folderRootsAdapter;

    public static RecyclerViewFolderItemSelectFragment newInstance(FolderItemViewAdapterPreferences prefs, int actionId) {
        RecyclerViewFolderItemSelectFragment fragment = new RecyclerViewFolderItemSelectFragment();
        fragment.setArguments(RecyclerViewFolderItemSelectFragment.buildArgsBundle(prefs, actionId));
        return fragment;
    }

    public static Bundle buildArgsBundle(FolderItemViewAdapterPreferences prefs, int actionId) {
        Bundle args = LongSetSelectFragment.buildArgsBundle(prefs, actionId, null);
        return args;
    }

    @Override
    @LayoutRes
    protected int getViewId() {
        return R.layout.layout_file_selection_recycler_list;
    }

    @Override
    protected FolderItemViewAdapterPreferences createEmptyPrefs() {
        return new FolderItemViewAdapterPreferences();
    }

    @Override
    protected boolean isNotAuthorisedToAlterState() {
        return isAppInReadOnlyMode(); // Non admin users can alter this since this may be for another profile entirely.
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(ACTIVE_FOLDER, getListAdapter().getActiveFolder());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        if(isNotAuthorisedToAlterState()) {
            getViewPrefs().readonly();
        }

        folderRootsAdapter = buildFolderRootsAdapter();

        spinner = v.findViewById(R.id.folder_root_spinner);
        spinner.setAdapter(folderRootsAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(id > 0) {
                    MappedArrayAdapter<String, File> adapter = (MappedArrayAdapter) parent.getAdapter();
                    File newRoot = adapter.getItemValue(position);
                    getListAdapter().updateContent(newRoot);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        folderPathView = v.findViewById(R.id.folder_path);

        FolderItemRecyclerViewAdapter.NavigationListener navListener = new FolderItemRecyclerViewAdapter.NavigationListener() {

            @Override
            public void onFolderOpened(File oldFolder, File newFolder) {
                String item = folderRootsAdapter.getItemByValue(newFolder);
                if(item == null) {
                    // reset the selection
                    if(spinner.getSelectedItemId() >= 0) {
                        spinner.setAdapter(folderRootsAdapter);
                    }
                } else {
                    spinner.setSelection(folderRootsAdapter.getPosition(item), false);
                }

                File f = newFolder;
                folderPathView.removeAllViews();

                while(!f.getName().isEmpty()) {
                    TextView pathItem = new TextView(getContext());

                    folderPathView.addView(pathItem, 0);
                    pathItem.setText('/'+f.getName());
                    final File thisFile = f;

                    pathItem.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            TextView tv = (TextView)v;
                            getListAdapter().updateContent(thisFile);
                        }
                    });
                    f = f.getParentFile();
                }
            }
        };

        final FolderItemRecyclerViewAdapter viewAdapter = new FolderItemRecyclerViewAdapter(navListener, new FolderItemRecyclerViewAdapter.MultiSelectStatusAdapter<File>() {
        }, getViewPrefs());
        if(!viewAdapter.isItemSelectionAllowed()) {
            viewAdapter.toggleItemSelection();
        }

        if (savedInstanceState != null) {
            File activeFolder = (File) savedInstanceState.getSerializable(ACTIVE_FOLDER);
            viewAdapter.setActiveFolder(activeFolder);
        }

        // will restore previous selection from state if any
        setListAdapter(viewAdapter);

        GridLayoutManager layoutMan = new GridLayoutManager(getContext(),6);
        layoutMan.setSpanSizeLookup(new SpanSizeLookup(viewAdapter, 6));
        getList().setLayoutManager(layoutMan);
        getList().setAdapter(viewAdapter);



        return v;
    }

    @Override
    public boolean onBackButton() {
        File parent = getListAdapter().getActiveFolder().getParentFile();
        if(parent.getName().isEmpty()) {
            return false;
        } else {
            getListAdapter().updateContent(parent);
            return true;
        }
    }

    private static class SpanSizeLookup extends GridLayoutManager.SpanSizeLookup {

        private final int spanCount;
        private final FolderItemRecyclerViewAdapter viewAdapter;

        public SpanSizeLookup(FolderItemRecyclerViewAdapter viewAdapter, int spanCount) {
            this.viewAdapter = viewAdapter;
            this.spanCount = spanCount;
        }

        @Override
        public int getSpanSize(int position) {
            int itemType = viewAdapter.getItemViewType(position);
            switch(itemType) {
                case FolderItemRecyclerViewAdapter.VIEW_TYPE_FOLDER:
                    return spanCount / 3;
                case FolderItemRecyclerViewAdapter.VIEW_TYPE_FILE:
                    return spanCount / 2;
                default:
                    return spanCount / 3;
            }
        }
    }

    private MappedArrayAdapter<String,File> buildFolderRootsAdapter() {
        List<String> rootLabels = ArrayUtils.toArrayList(new String[]{getString(R.string.folder_root_root), getString(R.string.folder_root_userdata), getString(R.string.folder_extstorage)});

        List<File> rootPaths = ArrayUtils.toArrayList(new File[]{Environment.getRootDirectory(), Environment.getDataDirectory(), Environment.getExternalStorageDirectory()});

        if(getViewPrefs().getInitialFolderAsFile() != null && !rootPaths.contains(getViewPrefs().getInitialFolderAsFile())) {
            rootPaths.add(0, getViewPrefs().getInitialFolderAsFile());
            rootLabels.add(0, getString(R.string.folder_default));
        }

        rootLabels.add(0, "");
        rootPaths.add(0, null);

        return new MappedArrayAdapter<>(getContext(), R.layout.support_simple_spinner_dropdown_item, rootLabels, rootPaths);
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        // heading field not used (missing from layout)
//        headingField.setText(R.string.file_selection_heading);
//        headingField.setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void populateListWithItems() {
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        FolderItemRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<File> selectedItems = listAdapter.getSelectedItems();
        EventBus.getDefault().post(new FileSelectionCompleteEvent(getActionId(), new ArrayList<>(selectedItems)));
        // now pop this screen off the stack.
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    @Override
    public void onCancelChanges() {
        EventBus.getDefault().post(new FileSelectionCompleteEvent(getActionId(), null));
        super.onCancelChanges();
    }

}
