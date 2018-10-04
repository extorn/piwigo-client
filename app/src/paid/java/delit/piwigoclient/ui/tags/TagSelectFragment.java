package delit.piwigoclient.ui.tags;

import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;

import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoTags;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.GetMethodsAvailableResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.PluginUserTagsGetListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagAddResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetAdminListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetListResponseHandler;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.common.list.recycler.EndlessRecyclerViewScrollListener;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.trackable.TagSelectionCompleteEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class TagSelectFragment extends RecyclerViewLongSetSelectFragment<TagRecyclerViewAdapter, BaseRecyclerViewAdapterPreferences> {

    private static final String TAGS_MODEL = "tagsModel";
    private PiwigoTags tagsModel = new PiwigoTags();

    public static TagSelectFragment newInstance(BaseRecyclerViewAdapterPreferences prefs, int actionId, HashSet<Long> initialSelection) {
        TagSelectFragment fragment = new TagSelectFragment();
        fragment.setArguments(buildArgsBundle(prefs, actionId, initialSelection));
        return fragment;
    }

    @Override
    protected BaseRecyclerViewAdapterPreferences createEmptyPrefs() {
        return new BaseRecyclerViewAdapterPreferences();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(TAGS_MODEL, tagsModel);
    }

    private boolean isTagSelectionAllowed() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if(sessionDetails == null || !sessionDetails.isFullyLoggedIn()) {
            return false;
        }
        boolean allowFullEdit = !isAppInReadOnlyMode() && sessionDetails.isAdminUser();
        boolean allowTagEdit = allowFullEdit || (!isAppInReadOnlyMode() && sessionDetails.isUseUserTagPluginForUpdate());
        return allowTagEdit;
    }

    @Override
    protected boolean isNotAuthorisedToAlterState() {
        return !isTagSelectionAllowed();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        getAddListItemButton().setVisibility(View.VISIBLE);
        getAddListItemButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewTag();
            }
        });

        if(isServerConnectionChanged()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            return null;
        }

        if(isNotAuthorisedToAlterState()) {
            getViewPrefs().readonly();
        }

        TagRecyclerViewAdapter viewAdapter = new TagRecyclerViewAdapter(tagsModel, new TagRecyclerViewAdapter.MultiSelectStatusAdapter<Tag>() {
        }, getViewPrefs());
        /*if(!viewAdapter.isItemSelectionAllowed()) {
            viewAdapter.toggleItemSelection();
        }*/

        viewAdapter.setInitiallySelectedItems(getInitialSelection());
        viewAdapter.setSelectedItems(getInitialSelection());
        setListAdapter(viewAdapter);

        RecyclerView.LayoutManager layoutMan = new LinearLayoutManager(getContext());
        getList().setLayoutManager(layoutMan);
        getList().setAdapter(viewAdapter);


        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(layoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageToLoad = tagsModel.getPagesLoaded();
                if (pageToLoad == 0 || tagsModel.isFullyLoaded()) {
                    // already load this one by default so lets not double load it (or we've already loaded all items).
                    return;
                }
                loadTagsPage(pageToLoad);
            }
        };
        scrollListener.configure(tagsModel.getPagesLoaded(), tagsModel.getItemCount());
        getList().addOnScrollListener(scrollListener);

        if (savedInstanceState != null) {
            tagsModel = (PiwigoTags) savedInstanceState.getSerializable(TAGS_MODEL);
        }

        return v;
    }

    private void createNewTag(String tagname) {
        addActiveServiceCall(R.string.progress_creating_tag,new TagAddResponseHandler(tagname).invokeAsync(this.getContext()));
    }

    private void addNewTag() {
        final View v = LayoutInflater.from(getContext()).inflate(R.layout.create_tag ,null);
        EditText tagNameEdit = v.findViewById(R.id.tag_tagname);


        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getContext()).setView(v)
                .setNegativeButton(R.string.button_cancel, null);
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if(sessionDetails != null && sessionDetails.isUseUserTagPluginForSearch()) {
            dialogBuilder.setNeutralButton(R.string.button_search, null);
        }
        dialogBuilder.setPositiveButton(R.string.button_ok, null);
        final AlertDialog dialog = dialogBuilder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {

                View.OnClickListener listener = new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        AlertDialog alert = (AlertDialog)dialog;
                        EditText tagNameEdit = alert.findViewById(R.id.tag_tagname);
                        String tagName = tagNameEdit.getText().toString();
                        if(tagName.length() == 0) {
                            //do nothing.
                            return;
                        }

                        if(v == alert.getButton(AlertDialog.BUTTON_POSITIVE)) {
                            onPositiveButton(tagName);
                        } else if(v == alert.getButton(AlertDialog.BUTTON_NEUTRAL)) {
                            onNeutralButton(tagName);
                        }
                    }
                    private void onNeutralButton(String tagName) {
                        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                        if(sessionDetails != null && sessionDetails.isLoggedIn() && sessionDetails.isUseUserTagPluginForSearch()) {
                            addMatchingTagsForSelection(tagName);
                        } else {
                            // sink this click - the user is trying to rush ahead before the UI has finished loading.
                        }
                    }

                    private void onPositiveButton(String tagName) {
                        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                        if(sessionDetails != null && sessionDetails.isLoggedIn() && sessionDetails.isUseUserTagPluginForUpdate()) {
                            addNewTagForSelection(tagName);
                        } else if(sessionDetails != null && sessionDetails.isAdminUser()) {
                            createNewTag(tagName);
                        } else if(sessionDetails != null && sessionDetails.isLoggedIn() && !sessionDetails.isMethodsAvailableListAvailable()){
                            // sink this click - the user is trying to rush ahead before the UI has finished loading.
                        }
                    }
                };

                Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(listener);
                button.setEnabled(false);
                button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL);
                if(button != null) {
                    button.setOnClickListener(listener);
                    button.setEnabled(false);
                }
            }
        });

        dialog.show();

        tagNameEdit.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String tagName = s.toString();
                if(dialog.getButton(
                        AlertDialog.BUTTON_NEUTRAL).isShown()) {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(tagName.length() > 0);
                }
                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE).setEnabled(tagName.length() > 0 && !tagsModel.containsTag(tagName));
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }

    private void addMatchingTagsForSelection(String tagName) {
        addActiveServiceCall(R.string.progress_loading_tags, new PluginUserTagsGetListResponseHandler(tagName).invokeAsync(getContext()));
    }

    private void addNewTagForSelection(String tagName) {
        insertNewTagToList(new Tag(tagName));
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
        if(tagsModel.getPagesLoaded() == 0) {
            getListAdapter().notifyDataSetChanged();
            loadTagsPage(0);
        }
    }

    private void loadTagsPage(int pageToLoad) {
        tagsModel.acquirePageLoadLock();
        try {
            if(tagsModel.isPageLoadedOrBeingLoaded(pageToLoad)) {
                return;
            }
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
            //NOTE: Paging not supported by API yet - so don't bother doing any. Note that the PiwigoTags object has been hacked to this effect.
//        int pageSize = prefs.getInt(getString(R.string.preference_tags_request_pagesize_key), getResources().getInteger(R.integer.preference_tags_request_pagesize_default));
            if(sessionDetails != null && sessionDetails.isLoggedIn() && !sessionDetails.isMethodsAvailableListAvailable()) {
                tagsModel.recordPageBeingLoaded(addActiveServiceCall(R.string.progress_loading_tags, new GetMethodsAvailableResponseHandler().invokeAsync(getContext())), 0);
            }
            addActiveServiceCall(R.string.progress_loading_tags, new TagsGetListResponseHandler(pageToLoad, Integer.MAX_VALUE).invokeAsync(getContext()));
            if(PiwigoSessionDetails.isAdminUser(connectionPrefs)) {
                tagsModel.recordPageBeingLoaded(addActiveServiceCall(R.string.progress_loading_tags, new TagsGetAdminListResponseHandler(pageToLoad, Integer.MAX_VALUE).invokeAsync(getContext())), 0);
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
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        TagRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<Long> tagsNeededToBeLoaded = listAdapter.getItemsSelectedButNotLoaded();
        if(tagsNeededToBeLoaded.size() > 0) {
            throw new UnsupportedOperationException("Paging not supported for tags");
        }
        HashSet<Tag> selectedItems = listAdapter.getSelectedItems();
        EventBus.getDefault().post(new TagSelectionCompleteEvent(getActionId(), selectedIdsSet, selectedItems));
        // now pop this screen off the stack.
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if(response instanceof PiwigoResponseBufferingHandler.PiwigoGetMethodsAvailableResponse) {
                PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                getViewPrefs().setInitialSelectionLocked(getViewPrefs().isAllowItemSelection() && (sessionDetails == null || !sessionDetails.isUseUserTagPluginForUpdate()));
                if(getListAdapter() != null && getListAdapter().getItemCount() > 0) {
                    getListAdapter().notifyDataSetChanged();
                    // force redraw of either list.
                    getList().invalidate();
                }
            } else if(response instanceof TagAddResponseHandler.PiwigoAddTagResponse) {
                onTagCreated((TagAddResponseHandler.PiwigoAddTagResponse)response);
            } else if (response instanceof TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse) {
                onTagsLoaded((TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse) response);
            } else {
                onTagsLoadFailed(response);
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

    private void onTagCreated(TagAddResponseHandler.PiwigoAddTagResponse response) {
        Tag newTag = response.getTag();
        insertNewTagToList(newTag);
    }

    private void insertNewTagToList(Tag newTag) {
        tagsModel.addItem(newTag);
        tagsModel.sort();
        int firstIndexChanged = tagsModel.getItemIdx(newTag);
        getListAdapter().notifyItemRangeInserted(firstIndexChanged, 1);
        onListItemLoadSuccess();
        setAppropriateComponentState();
    }

    public void onTagsLoaded(final TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse response) {
        tagsModel.acquirePageLoadLock();
        try {
            tagsModel.recordPageLoadSucceeded(response.getMessageId());
            boolean isAdminPage = response instanceof TagsGetAdminListResponseHandler.PiwigoGetTagsAdminListRetrievedResponse;
            boolean isUserTagPluginSearchResult = response instanceof PluginUserTagsGetListResponseHandler.PiwigoUserTagsPluginGetTagsListRetrievedResponse;
            int firstIndexInsertedAt = tagsModel.addItemPage(isAdminPage || isUserTagPluginSearchResult, response.getTags());
            HashSet<Long> selectedItemIds = getListAdapter().getSelectedItemIds();
            for (Long selectedItemId : selectedItemIds) {
                getListAdapter().setItemSelected(selectedItemId);
            }
            // can't do an incremental refresh as we sort the data and it could cause interleaving.
            getListAdapter().notifyDataSetChanged();
            if(tagsModel.hasNoFailedPageLoads()) {
                onListItemLoadSuccess();
            }
            setAppropriateComponentState();
        } finally {
            tagsModel.releasePageLoadLock();
        }
    }
}
