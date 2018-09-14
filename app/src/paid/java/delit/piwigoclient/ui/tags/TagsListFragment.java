package delit.piwigoclient.ui.tags;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
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

import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.concurrent.ConcurrentHashMap;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoTags;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.TagAddResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetAdminListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetListResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.common.list.recycler.EndlessRecyclerViewScrollListener;
import delit.piwigoclient.ui.common.list.recycler.RecyclerViewMargin;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.TagContentAlteredEvent;
import delit.piwigoclient.ui.events.TagUpdatedEvent;
import delit.piwigoclient.ui.events.ViewTagEvent;

/**
 * Created by gareth on 26/05/17.
 */
public class TagsListFragment extends MyFragment {

    private static final String GROUPS_MODEL = "tagsModel";
    private static final String GROUPS_PAGE_BEING_LOADED = "tagsPageBeingLoaded";
    private ConcurrentHashMap<Long, Tag> deleteActionsPending = new ConcurrentHashMap<>();
    private FloatingActionButton retryActionButton;
    private PiwigoTags tagsModel = new PiwigoTags();
    private TagRecyclerViewAdapter viewAdapter;
    private int pageToLoadNow = -1;
    private CustomImageButton addListItemButton;
    private AlertDialog addNewTagDialog;
    private BaseRecyclerViewAdapterPreferences viewPrefs;
    private RecyclerView recyclerView;

    public static TagsListFragment newInstance(BaseRecyclerViewAdapterPreferences viewPrefs) {
        TagsListFragment fragment = new TagsListFragment();
        fragment.setArguments(viewPrefs.storeToBundle(new Bundle()));
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDetach() {
        EventBus.getDefault().unregister(this);
        super.onDetach();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        if(getArguments() != null) {
            viewPrefs = new BaseRecyclerViewAdapterPreferences().loadFromBundle(getArguments());
            setArguments(null);
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(GROUPS_MODEL, tagsModel);
        outState.putInt(GROUPS_PAGE_BEING_LOADED, pageToLoadNow);
        viewPrefs.storeToBundle(outState);
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.tags_heading);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        if (savedInstanceState != null) {
            if(!isSessionDetailsChanged()) {
                tagsModel = (PiwigoTags) savedInstanceState.getSerializable(GROUPS_MODEL);
                pageToLoadNow = savedInstanceState.getInt(GROUPS_PAGE_BEING_LOADED);
            }
            viewPrefs = new BaseRecyclerViewAdapterPreferences().loadFromBundle(savedInstanceState);
        }

        View view = inflater.inflate(R.layout.layout_fullsize_recycler_list, container, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if(AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        TextView heading = view.findViewById(R.id.heading);
        heading.setVisibility(View.INVISIBLE);

        Button cancelButton = view.findViewById(R.id.list_action_cancel_button);
        cancelButton.setVisibility(View.GONE);

        Button toggleAllButton = view.findViewById(R.id.list_action_toggle_all_button);
        toggleAllButton.setVisibility(View.GONE);

        Button saveButton = view.findViewById(R.id.list_action_save_button);
        saveButton.setVisibility(View.GONE);

        addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewTag();
            }
        });

        retryActionButton = view.findViewById(R.id.list_retryAction_actionButton);
        retryActionButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                retryActionButton.setVisibility(View.GONE);
                loadTagsPage(pageToLoadNow);
            }
        });

        recyclerView = view.findViewById(R.id.list);

        RecyclerView.LayoutManager layoutMan = new LinearLayoutManager(getContext()); //new GridLayoutManager(getContext(), 1);

        recyclerView.setLayoutManager(layoutMan);

        viewAdapter = new TagRecyclerViewAdapter(tagsModel, new TagListSelectListener(), viewPrefs);

        recyclerView.setAdapter(viewAdapter);
        recyclerView.addItemDecoration(new RecyclerViewMargin(getContext(), RecyclerViewMargin.DEFAULT_MARGIN_DP, 1));

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
        recyclerView.addOnScrollListener(scrollListener);

        setViewControlStatusBasedOnSessionState();

        return view;
    }

    private void setViewControlStatusBasedOnSessionState() {
        if(isAppInReadOnlyMode() && addNewTagDialog != null && addNewTagDialog.isShowing()) {
            addNewTagDialog.cancel();
        }
        addListItemButton.setVisibility(!viewPrefs.isAllowItemAddition() || isAppInReadOnlyMode()?View.GONE:View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if(tagsModel.getPagesLoaded() == 0 && viewAdapter != null) {
            viewAdapter.notifyDataSetChanged();
            loadTagsPage(0);
        }
    }

    private void loadTagsPage(int pageToLoad) {
        this.pageToLoadNow = pageToLoad;
//        int pageSize = prefs.getInt(getString(R.string.preference_tags_request_pagesize_key), getResources().getInteger(R.integer.preference_tags_request_pagesize_default));
        addActiveServiceCall(R.string.progress_loading_tags,new TagsGetListResponseHandler(pageToLoad, Integer.MAX_VALUE).invokeAsync(getContext()));
        if(PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
            addActiveServiceCall(R.string.progress_loading_tags, new TagsGetAdminListResponseHandler(pageToLoad, Integer.MAX_VALUE).invokeAsync(getContext()));
        }
    }

    private void addNewTag() {
        final View v = LayoutInflater.from(getContext()).inflate(R.layout.create_tag ,null);
        EditText tagNameEdit = v.findViewById(R.id.tag_tagname);

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if(which != AlertDialog.BUTTON_POSITIVE) {
                    return;
                }
                AlertDialog alert = (AlertDialog)dialog;
                EditText tagNameEdit = alert.findViewById(R.id.tag_tagname);
                String tagName = tagNameEdit.getText().toString();
                createNewTag(tagName);
            }
        };

        addNewTagDialog = new AlertDialog.Builder(getContext()).setView(v)
                .setNegativeButton(R.string.button_cancel, listener)
                .setPositiveButton(R.string.button_ok, listener)
                .show();

        tagNameEdit.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String tagName = s.toString();
                addNewTagDialog.getButton(
                        AlertDialog.BUTTON_POSITIVE).setEnabled(!tagsModel.containsTag(tagName));
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }

    private void onTagSelected(Tag selectedTag) {
        EventBus.getDefault().post(new ViewTagEvent(selectedTag));
    }

    public void onDeleteTag(final Tag thisItem) {
        String message = getString(R.string.alert_confirm_really_delete_tag);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultAdapter() {

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if(Boolean.TRUE == positiveAnswer) {
                    deleteTagNow(thisItem);
                }
            }
        });
    }

    private void createNewTag(String tagname) {
        addActiveServiceCall(new TagAddResponseHandler(tagname).invokeAsync(this.getContext()));
    }

    private void deleteTagNow(Tag thisItem) {
        throw new UnsupportedOperationException("Not supported in Piwigo API");
//        long deleteActionId = PiwigoAccessService.startActionDeleteTag(thisItem.getId(), this.getContext());
//        this.deleteActionsPending.put(deleteActionId, thisItem);
//        addActiveServiceCall(R.string.progress_delete_tag,deleteActionId);
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if(isVisible()) {
                updateActiveSessionDetails();
            }
            super.onBeforeHandlePiwigoResponse(response);
        }

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            /*if (response instanceof PiwigoResponseBufferingHandler.PiwigoDeleteTagResponse) {
                onTagDeleted((PiwigoResponseBufferingHandler.PiwigoDeleteTagResponse) response);
            } else*/ if(response instanceof TagAddResponseHandler.PiwigoAddTagResponse) {
                onTagCreated((TagAddResponseHandler.PiwigoAddTagResponse)response);
            } else if (response instanceof TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse) {
                onTagsLoaded((TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse) response);
            } else if(response instanceof PiwigoResponseBufferingHandler.ErrorResponse){
//                if(deleteActionsPending.size() == 0) {
//                    // assume this to be a list reload that's required.
//                    retryActionButton.setVisibility(View.VISIBLE);
//                }
            }
        }

        private void onTagCreated(TagAddResponseHandler.PiwigoAddTagResponse response) {
            Tag newTag = response.getTag();
            tagsModel.addItem(newTag);
            tagsModel.sort();
            int firstIndexChanged = tagsModel.getItemIdx(newTag);
            viewAdapter.notifyItemRangeInserted(firstIndexChanged, 1);
        }

        @Override
        protected void handlePiwigoHttpErrorResponse(PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse msg) {
            /*if (deleteActionsPending.containsKey(msg.getMessageId())) {
                onTagDeleteFailed(msg.getMessageId());
            } else*/ {
                super.handlePiwigoHttpErrorResponse(msg);
            }
        }

        @Override
        protected void handlePiwigoServerErrorResponse(PiwigoResponseBufferingHandler.PiwigoServerErrorResponse msg) {
            /*if (deleteActionsPending.containsKey(msg.getMessageId())) {
                onTagDeleteFailed(msg.getMessageId());
            } else*/ {
                super.handlePiwigoServerErrorResponse(msg);
            }
        }

        @Override
        protected void handlePiwigoUnexpectedReplyErrorResponse(PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse msg) {
            /*if (deleteActionsPending.containsKey(msg.getMessageId())) {
                onTagDeleteFailed(msg.getMessageId());
            } else*/ {
                super.handlePiwigoUnexpectedReplyErrorResponse(msg);
            }
        }
    }

    public void onTagsLoaded(final TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse response) {
        synchronized (this) {
            pageToLoadNow = -1;
            retryActionButton.setVisibility(View.GONE);
            boolean isAdminPage = response instanceof TagsGetAdminListResponseHandler.PiwigoGetTagsAdminListRetrievedResponse;
            int firstIdxAdded = tagsModel.addItemPage(isAdminPage, response.getTags());
            // can't do an incremental refresh as we sort the data and it could cause interleaving.
            viewAdapter.notifyDataSetChanged();
        }
    }

//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onEvent(TagDeletedEvent event) {
//        viewAdapter.remove(event.getTag());
//        getUiHelper().showOrQueueMessage(R.string.alert_information, String.format(getString(R.string.alert_tag_delete_success_pattern), event.getTag().getName()));
//    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TagUpdatedEvent event) {
        viewAdapter.replaceOrAddItem(event.getTag());
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TagContentAlteredEvent event) {
        Tag item = viewAdapter.getItemById(event.getId());
        item.setUsageCount(item.getUsageCount() + event.getContentChange());
        viewAdapter.notifyItemChanged(viewAdapter.getItemPosition(item));
    }

//    public void onTagDeleted(final PiwigoResponseBufferingHandler.PiwigoDeleteTagResponse response) {
//        Tag tag = deleteActionsPending.remove(response.getMessageId());
//        viewAdapter.remove(tag);
//        getUiHelper().showOrQueueMessage(R.string.alert_information, String.format(getString(R.string.alert_tag_delete_success_pattern), tag.getName()));
//    }

//    public void onTagDeleteFailed(final long messageId) {
//        Tag tag = deleteActionsPending.remove(messageId);
//        getUiHelper().showOrQueueMessage(R.string.alert_information, String.format(getString(R.string.alert_tag_delete_failed_pattern), tag.getName()));
//    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AppLockedEvent event) {
        setViewControlStatusBasedOnSessionState();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AppUnlockedEvent event) {
        setViewControlStatusBasedOnSessionState();
    }

    class TagListSelectListener extends TagRecyclerViewAdapter.MultiSelectStatusAdapter<Tag> {

        @Override
        public <A extends BaseRecyclerViewAdapter> void onItemDeleteRequested(A adapter, Tag item) {
            onDeleteTag(item);
        }

        @Override
        public <A extends BaseRecyclerViewAdapter> void onItemClick(A adapter, Tag item) {
            onTagSelected(item);
        }

    }
}
