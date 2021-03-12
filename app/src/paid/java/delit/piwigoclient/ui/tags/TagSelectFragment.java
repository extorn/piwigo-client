package delit.piwigoclient.ui.tags;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;

import java.util.HashSet;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.recycler.EndlessRecyclerViewScrollListener;
import delit.libs.ui.view.recycler.RecyclerViewMargin;
import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoTags;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.PluginUserTagsGetListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagAddResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetAdminListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetListResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.events.trackable.TagSelectionCompleteEvent;
import delit.piwigoclient.ui.model.PiwigoTagModel;

/**
 * Created by gareth on 26/05/17.
 */

public class TagSelectFragment<F extends TagSelectFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends RecyclerViewLongSetSelectFragment<F,FUIH,TagRecyclerViewAdapter<?,?,?>, TagRecyclerViewAdapter.TagViewAdapterPreferences,Tag> {

    private static final String TAGS_MODEL = "tagsModel";
    private static final String ARGS_UNSAVED_TAGS = "unsavedTags";
    private static final String TAG = "TagSelFrag";
    private PiwigoTags tagsModel;

    public static TagSelectFragment<?,?> newInstance(TagRecyclerViewAdapter.TagViewAdapterPreferences prefs, int actionId, HashSet<Long> initialSelection, HashSet<Tag> unsavedNewTags) {
        TagSelectFragment<?,?> fragment = new TagSelectFragment<>();
        Bundle b = buildArgsBundle(prefs, actionId, initialSelection);
        BundleUtils.putSet(b, ARGS_UNSAVED_TAGS, unsavedNewTags);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    protected TagRecyclerViewAdapter.TagViewAdapterPreferences loadPreferencesFromBundle(Bundle bundle) {
        return new TagRecyclerViewAdapter.TagViewAdapterPreferences(bundle);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(TAGS_MODEL, tagsModel);
    }

    private boolean isTagSelectionAllowed() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if(sessionDetails == null || !sessionDetails.isFullyLoggedIn()) {
            return false;
        }
        boolean allowFullEdit = !isAppInReadOnlyMode() && sessionDetails.isAdminUser();
        return allowFullEdit || (!isAppInReadOnlyMode() && sessionDetails.isUseUserTagPluginForUpdate());
    }

    @Override
    protected boolean isNotAuthorisedToAlterState() {
        return !isTagSelectionAllowed();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        HashSet<Tag> unsavedTags = null;
        if (savedInstanceState != null) {
            if(!isSessionDetailsChanged()) {
                tagsModel = savedInstanceState.getParcelable(TAGS_MODEL);
            }
        } else {
            Bundle args = getArguments();
            if (args != null) {
                unsavedTags = BundleUtils.getHashSet(args, ARGS_UNSAVED_TAGS);
            }
        }

        if(tagsModel == null) {
            tagsModel = new PiwigoTags();
            setTagsModelPageSourceCount();
        }

        if(unsavedTags != null) {
            for (Tag unsavedTag : unsavedTags) {
                tagsModel.addItem(unsavedTag);
            }
        }

        if(getAddListItemButton() != null) {
            getAddListItemButton().setVisibility(View.VISIBLE);
            getAddListItemButton().setOnClickListener(v1 -> addNewTag());
        }

        if(isServerConnectionChanged()) {
            // immediately leave this screen.
            Logging.log(Log.INFO, TAG, "removing from activity as server connection changed");
            getParentFragmentManager().popBackStack();
            return null;
        }

        if(isNotAuthorisedToAlterState()) {
            getViewPrefs().readonly();
        }

        TagRecyclerViewAdapter viewAdapter = new TagRecyclerViewAdapter(requireContext(), PiwigoTagModel.class, tagsModel, new TagRecyclerViewAdapter.MultiSelectStatusAdapter() {
        }, getViewPrefs());
        /*if(!viewAdapter.isItemSelectionAllowed()) {
            viewAdapter.toggleItemSelection();
        }*/

        // need to load this before the list adapter is added else will load from the list adapter which hasn't been inited yet!
        HashSet<Long> currentSelection = getCurrentSelection();

        // will restore previous selection from state if any
        setListAdapter(viewAdapter);


        // select the items to view.
        viewAdapter.setInitiallySelectedItems(getInitialSelection());
        viewAdapter.setSelectedItems(currentSelection);

        RecyclerView.LayoutManager layoutMan = new LinearLayoutManager(getContext());
        getList().setLayoutManager(layoutMan);
        getList().setAdapter(viewAdapter);
        getList().addItemDecoration(new RecyclerViewMargin(getContext(), RecyclerViewMargin.DEFAULT_MARGIN_DP, 1));


        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(layoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageToLoad = page * tagsModel.getPageSources();
                if (tagsModel.isPageLoadedOrBeingLoaded(pageToLoad) || tagsModel.isFullyLoaded()) {
                    Integer missingPage = tagsModel.getAMissingPage();
                    if(missingPage != null) {
                        pageToLoad = missingPage;
                    } else {
                        // already load this one by default so lets not double load it (or we've already loaded all items).
                        return;
                    }
                }
                loadTagsPage(pageToLoad);
            }
        };
        scrollListener.configure(tagsModel.getPagesLoadedIdxToSizeMap(), tagsModel.getItemCount());
        getList().addOnScrollListener(scrollListener);

        return v;
    }

    /**
     *
     * @return if changed
     */
    private boolean setTagsModelPageSourceCount() {
        int pageSources = 1;
        if(PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
            pageSources = 2;
        }
        int currentValue = tagsModel.getPageSources();
        tagsModel.setPageSources(pageSources);
        return currentValue != pageSources;
    }

    private void createNewTag(String tagname) {
        addActiveServiceCall(R.string.progress_creating_tag, new TagAddResponseHandler(tagname));
    }

    private void addNewTag() {

        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(new android.view.ContextThemeWrapper(getContext(), R.style.Theme_App_EditPages));

        final View v = LayoutInflater.from(dialogBuilder.getContext()).inflate(R.layout.dialog_layout_create_tag,null);
        EditText tagNameEdit = v.findViewById(R.id.tag_tagname);
        dialogBuilder.setView(v);

        dialogBuilder.setNegativeButton(R.string.button_cancel, null);
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if(sessionDetails != null && sessionDetails.isUseUserTagPluginForSearch()) {
            dialogBuilder.setNeutralButton(R.string.button_search, null);
        }
        dialogBuilder.setPositiveButton(R.string.button_ok, null);
        final AlertDialog dialog = dialogBuilder.create();
        dialog.setOnShowListener(dialog1 -> {

            View.OnClickListener listener = new View.OnClickListener() {

                @Override
                public void onClick(View v1) {
                    AlertDialog alert = (AlertDialog) dialog1;
                    EditText tagNameEdit1 = alert.findViewById(R.id.tag_tagname);
                    String tagName = tagNameEdit1.getText().toString();
                    if(tagName.length() == 0) {
                        //just close the window.
                        DisplayUtils.hideKeyboardFrom(getContext(), dialog1);
                        dialog1.dismiss();
                        return;
                    }

                    if(v1 == alert.getButton(AlertDialog.BUTTON_POSITIVE)) {
                        onPositiveButton(tagName);
                        DisplayUtils.hideKeyboardFrom(getContext(), dialog1);
                        dialog1.dismiss();
                    } else if(v1 == alert.getButton(AlertDialog.BUTTON_NEUTRAL)) {
                        onNeutralButton(tagName);
                        DisplayUtils.hideKeyboardFrom(getContext(), dialog1);
                        dialog1.dismiss();
                    }
                }
                private void onNeutralButton(String tagName) {
                    PiwigoSessionDetails sessionDetails1 = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                    if(sessionDetails1 != null && sessionDetails1.isLoggedIn() && sessionDetails1.isUseUserTagPluginForSearch()) {
                        addMatchingTagsForSelection(tagName);
                    } else {
                        // sink this click - the user is trying to rush ahead before the UI has finished loading.
                    }
                }

                private void onPositiveButton(String tagName) {
                    PiwigoSessionDetails sessionDetails1 = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                    if(sessionDetails1 != null && sessionDetails1.isUseUserTagPluginForUpdate()) {
                        addNewTagForSelection(tagName);
                    } else if(sessionDetails1 != null && sessionDetails1.isAdminUser()) {
                        createNewTag(tagName);
                    } else if(sessionDetails1 != null && !sessionDetails1.isMethodsAvailableListAvailable()){
                        // sink this click - the user is trying to rush ahead before the UI has finished loading.
                    }
                }
            };

            Button button = ((AlertDialog) dialog1).getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(listener);
            button.setEnabled(false);
            button = ((AlertDialog) dialog1).getButton(AlertDialog.BUTTON_NEUTRAL);
            if(button != null) {
                button.setOnClickListener(listener);
                button.setEnabled(false);
            }
        });

        dialog.show();

        tagNameEdit.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    String tagName = s.toString();
                    if (dialog.getButton(
                            AlertDialog.BUTTON_NEUTRAL).isShown()) {
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(tagName.length() > 0);
                    }
                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE).setEnabled(tagName.length() > 0 && !tagsModel.containsTag(tagName));
                } catch (RuntimeException e) {
                    Logging.log(Log.ERROR, getTag(), "Error in on tag name change");
                    Logging.recordException(e);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }

    private void addMatchingTagsForSelection(String tagName) {
        addActiveServiceCall(R.string.progress_loading_tags, new PluginUserTagsGetListResponseHandler(tagName));
    }

    private void addNewTagForSelection(String tagName) {

        Tag t = new Tag(tagsModel.findAnIdNotYetPresentInTheList(), tagName);
        insertNewTagToList(t);
        getUiHelper().showDetailedShortMsg(R.string.alert_warning, R.string.tag_will_be_created_when_resource_is_saved);
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.tags_heading);
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        headingField.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();

        if(isServerConnectionChanged()) {
            return;
        }
        if(!tagsModel.isPageLoadedOrBeingLoaded(0)) {
            getListAdapter().notifyDataSetChanged();
            loadTagsPage(0);
        }
    }

    private void loadTagsPage(int pageToLoad) {
        tagsModel.acquirePageLoadLock();
        int basePageToLoad = pageToLoad % 2 == 0 ? pageToLoad : pageToLoad -1;
        try {
            if(!tagsModel.isPageLoadedOrBeingLoaded(basePageToLoad)) {
                addActiveServiceCall(R.string.progress_loading_tags, new TagsGetListResponseHandler(basePageToLoad, Integer.MAX_VALUE));
            }
            if(!tagsModel.isPageLoadedOrBeingLoaded(basePageToLoad + 1)) {
                if(PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
                    addActiveServiceCall(R.string.progress_loading_tags, new TagsGetAdminListResponseHandler(basePageToLoad + 1, Integer.MAX_VALUE));
                }
            }
        } finally {
            tagsModel.releasePageLoadLock();
        }
    }

    @Override
    protected void rerunRetrievalForFailedPages() {
        tagsModel.acquirePageLoadLock();
        try {
            for(Integer reloadPageNum = tagsModel.getNextPageToReload(); reloadPageNum != null; reloadPageNum = tagsModel.getNextPageToReload()) {
                loadTagsPage(reloadPageNum);
            }

        } finally {
            tagsModel.releasePageLoadLock();
        }
    }

    @Override
    protected void onClickCancelFileSelectionButton() {
        // reset the selection to default.
        getListAdapter().setSelectedItems(null);
        onSelectActionComplete(getListAdapter().getSelectedItemIds());
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        TagRecyclerViewAdapter listAdapter = getListAdapter();
        // in fact given the tags list isn't pages, non loaded means that it doesn't exist any longer on the server.
        HashSet<Long> tagsNeededToBeLoaded = listAdapter.getItemsSelectedButNotLoaded();
        if(tagsNeededToBeLoaded.size() > 0) {
            Bundle b = new Bundle();
            b.putLongArray("tagIds", CollectionUtils.asLongArray(tagsNeededToBeLoaded));
            Logging.logAnalyticEvent(requireContext(),"non_existent_tags", b);
        }
        for (Long tagId : tagsNeededToBeLoaded) {
            listAdapter.deselectItem(tagId, true);
        }
        if(tagsNeededToBeLoaded.size() > 0) {
            getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.warning_missing_tags_links_removed_from_resource, tagsNeededToBeLoaded.size()));
        }
        HashSet<Tag> selectedItems = listAdapter.getSelectedItems();
        EventBus.getDefault().post(new TagSelectionCompleteEvent(getActionId(), selectedIdsSet, selectedItems));
        // now pop this screen off the stack.
        if(isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately as tags selected");
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    private static class CustomPiwigoResponseListener<F extends TagSelectFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends BasicPiwigoResponseListener<FUIH,F> {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if(response instanceof TagAddResponseHandler.PiwigoAddTagResponse) {
                getParent().onTagCreated((TagAddResponseHandler.PiwigoAddTagResponse)response);
            } else if (response instanceof TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse) {
                getParent().onTagsLoaded((TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse) response);
            } else {
                getParent().onTagsLoadFailed(response);
            }
        }
    }

    protected void onTagsLoadFailed(PiwigoResponseBufferingHandler.Response response) {
        tagsModel.acquirePageLoadLock();
        try {
            tagsModel.recordPageLoadFailed(response.getMessageId());
            onListItemLoadFailed();
        } finally {
            tagsModel.releasePageLoadLock();
        }
    }

    protected void onTagCreated(TagAddResponseHandler.PiwigoAddTagResponse response) {
        Tag newTag = response.getTag();
        insertNewTagToList(newTag);
    }

    private void insertNewTagToList(Tag newTag) {
        tagsModel.addItem(newTag);
        int firstIndexChanged = tagsModel.getItemIdx(newTag);
        getListAdapter().setItemSelected(newTag.getId());
        getListAdapter().notifyItemRangeInserted(firstIndexChanged, 1);
        onListItemLoadSuccess();
        setAppropriateComponentState();
    }

    public void onTagsLoaded(final TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse response) {
        tagsModel.acquirePageLoadLock();
        try {
            boolean isAdminPage = response instanceof TagsGetAdminListResponseHandler.PiwigoGetTagsAdminListRetrievedResponse;
            if(!isAdminPage && tagsModel.getPagesLoadedIdxToSizeMap() == 0) {
                boolean needToLoadAdminList = setTagsModelPageSourceCount();
                if(needToLoadAdminList) {
                    loadTagsPage(response.getPage());
                }
            }
            boolean isUserTagPluginSearchResult = response instanceof PluginUserTagsGetListResponseHandler.PiwigoUserTagsPluginGetTagsListRetrievedResponse;
            if(isUserTagPluginSearchResult) {
                tagsModel.addRandomItems(response.getTags(), false);
            } else {
                tagsModel.addItemPage(isAdminPage?1:0, isAdminPage, response.getPage(), response.getPageSize(), response.getTags());
                // Will this code play nicely with the tags plugin? Testing needed
//                if(tagsModel.getPageSources() == tagsModel.getPagesLoaded()) {
//                    // this is okay because there is no paging
//                    tagsModel.markAsFullyLoaded();
//                }
            }
            HashSet<Long> selectedItemIds = getListAdapter().getSelectedItemIds();
            for (Long selectedItemId : selectedItemIds) {
                getListAdapter().setItemSelected(selectedItemId);
            }
            // can't do an incremental refresh as we sort the data and it could cause interleaving.
            getListAdapter().notifyDataSetChanged();
            getList().requestLayout();
            if(tagsModel.hasNoFailedPageLoads()) {
                onListItemLoadSuccess();
            }
            setAppropriateComponentState();
        } finally {
            tagsModel.releasePageLoadLock();
        }
    }
}
