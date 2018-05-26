package delit.piwigoclient.ui.tags;

import android.app.AlertDialog;
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
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;

import java.util.HashSet;

import delit.piwigoclient.R;
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
import delit.piwigoclient.ui.common.EndlessRecyclerViewScrollListener;
import delit.piwigoclient.ui.common.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.trackable.TagSelectionCompleteEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class TagSelectFragment extends RecyclerViewLongSetSelectFragment<TagRecyclerViewAdapter, BaseRecyclerViewAdapterPreferences> {

    private static final String TAGS_MODEL = "tagsModel";
    private static final String TAGS_PAGE_BEING_LOADED = "tagsPageBeingLoaded";
    private PiwigoTags tagsModel = new PiwigoTags();
    private int pageToLoadNow = -1;

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
        outState.putInt(TAGS_PAGE_BEING_LOADED, pageToLoadNow);
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

        boolean editingEnabled = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
        if(!editingEnabled) {
            getViewPrefs().readonly();
        }

        TagRecyclerViewAdapter viewAdapter = new TagRecyclerViewAdapter(tagsModel, new TagRecyclerViewAdapter.MultiSelectStatusAdapter<Tag>() {
        }, getViewPrefs());
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
            pageToLoadNow = savedInstanceState.getInt(TAGS_PAGE_BEING_LOADED);
        }

        return v;
    }

    private void createNewTag(String tagname) {
        addActiveServiceCall(new TagAddResponseHandler(tagname).invokeAsync(this.getContext()));
    }

    private void addNewTag() {
        final View v = LayoutInflater.from(getContext()).inflate(R.layout.create_tag ,null);
        EditText tagNameEdit = (EditText)v.findViewById(R.id.tag_tagname);

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                AlertDialog alert = (AlertDialog)dialog;
                EditText tagNameEdit = (EditText)alert.findViewById(R.id.tag_tagname);
                String tagName = tagNameEdit.getText().toString();

                if(which == AlertDialog.BUTTON_POSITIVE) {
                    onPositiveButton(tagName);
                } else if(which == AlertDialog.BUTTON_NEUTRAL) {
                    onNeutralButton(tagName);
                }

            }

            private void onNeutralButton(String tagName) {
                if(PiwigoSessionDetails.isLoggedIn() && PiwigoSessionDetails.getInstance().isUseUserTagPluginForSearch()) {
                    addMatchingTagsForSelection(tagName);
                } else {
                    // sink this click - the user is trying to rush ahead before the UI has finished loading.
                }
            }

            private void onPositiveButton(String tagName) {
                if(PiwigoSessionDetails.isLoggedIn() && PiwigoSessionDetails.getInstance().isUseUserTagPluginForUpdate()) {
                    addNewTagForSelection(tagName);
                } else if(PiwigoSessionDetails.isAdminUser()) {
                    createNewTag(tagName);
                } else if(PiwigoSessionDetails.isLoggedIn() && !PiwigoSessionDetails.getInstance().isMethodsAvailableListAvailable()){
                    // sink this click - the user is trying to rush ahead before the UI has finished loading.
                }
            }
        };

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getContext()).setView(v)
                .setNegativeButton(R.string.button_cancel, listener);
        if(PiwigoSessionDetails.getInstance().isUseUserTagPluginForSearch()) {
            dialogBuilder.setNeutralButton(R.string.button_search, listener);
        }
        final AlertDialog dialog = dialogBuilder.setPositiveButton(R.string.button_ok, listener)
                .show();

        tagNameEdit.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String tagName = s.toString();
                if(((AlertDialog) dialog).getButton(
                        AlertDialog.BUTTON_NEUTRAL).isShown()) {
                    ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(tagName.length() > 0);
                }
                ((AlertDialog) dialog).getButton(
                        AlertDialog.BUTTON_POSITIVE).setEnabled(!tagsModel.containsTag(tagName));
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
    protected void setPageHeading(TextView headingField) {
        headingField.setText(R.string.tags_heading);
        headingField.setVisibility(View.VISIBLE);
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
        this.pageToLoadNow = pageToLoad;
        //NOTE: Paging not supported by API yet - so don't bother doing any. Note that the PiwigoTags object has been hacked to this effect.
//        int pageSize = prefs.getInt(getString(R.string.preference_tags_request_pagesize_key), getResources().getInteger(R.integer.preference_tags_request_pagesize_default));
        if(PiwigoSessionDetails.isLoggedIn() && !PiwigoSessionDetails.getInstance().isMethodsAvailableListAvailable()) {
            addActiveServiceCall(R.string.progress_loading_tags, new GetMethodsAvailableResponseHandler().invokeAsync(getContext()));
        }
        addActiveServiceCall(R.string.progress_loading_tags, new TagsGetListResponseHandler(pageToLoad, Integer.MAX_VALUE).invokeAsync(getContext()));
        if(PiwigoSessionDetails.isAdminUser()) {
            addActiveServiceCall(R.string.progress_loading_tags, new TagsGetAdminListResponseHandler(pageToLoad, Integer.MAX_VALUE).invokeAsync(getContext()));
        }
    }

    @Override
    protected void populateListWithItems() {
        if(pageToLoadNow > 0) {
            loadTagsPage(pageToLoadNow);
        }
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        TagRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<Long> tagsNeededToBeLoaded = listAdapter.getItemsSelectedButNotLoaded();
        if(tagsNeededToBeLoaded.size() > 0) {
            throw new UnsupportedOperationException("Paging not supported for tags");
//            //TODO what if there are more than the max page size?! Paging needed :-(
//            pageToLoadNow = Integer.MAX_VALUE; // flag that this is a special one off load.
//            addActiveServiceCall(R.string.progress_loading_tags, PiwigoAccessService.startActionGetTagsList(tagsNeededToBeLoaded, 0, tagsNeededToBeLoaded.size(), getContext()));
//            return;
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
                getViewPrefs().setInitialSelectionLocked(getViewPrefs().isAllowItemSelection() && !PiwigoSessionDetails.getInstance().isUseUserTagPluginForUpdate());
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
                onListItemLoadFailed();
            }
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
        synchronized (this) {
            pageToLoadNow = -1;
            boolean isAdminPage = response instanceof TagsGetAdminListResponseHandler.PiwigoGetTagsAdminListRetrievedResponse;
            boolean isUserTagPluginSearchResult = response instanceof PluginUserTagsGetListResponseHandler.PiwigoUserTagsPluginGetTagsListRetrievedResponse;
            int firstIndexInsertedAt = tagsModel.addItemPage(isAdminPage || isUserTagPluginSearchResult, response.getTags());
            HashSet<Long> selectedItemIds = getListAdapter().getSelectedItemIds();
            for (Long selectedItemId : selectedItemIds) {
                getListAdapter().setItemSelected(selectedItemId);
            }
            // can't do an incremental refresh as we sort the data and it could cause interleaving.
            getListAdapter().notifyDataSetChanged();;
            onListItemLoadSuccess();
            setAppropriateComponentState();
        }
    }
}
