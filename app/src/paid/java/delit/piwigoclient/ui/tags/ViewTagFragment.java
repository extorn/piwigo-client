package delit.piwigoclient.ui.tags;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoTag;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagGetImagesResponseHandler;
import delit.piwigoclient.ui.MainActivity;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapter;
import delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.album.view.ViewAlbumFragment;
import delit.piwigoclient.ui.common.CustomImageButton;
import delit.piwigoclient.ui.common.EndlessRecyclerViewScrollListener;
import delit.piwigoclient.ui.common.MyFragment;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.TagContentAlteredEvent;
import delit.piwigoclient.ui.events.TagUpdatedEvent;
import delit.piwigoclient.util.SetUtils;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * A fragment representing a list of Items.
 */
public class ViewTagFragment extends MyFragment {

    private static final String ARG_TAG = "tag";
    private static final String STATE_TAG_MODEL = "TagModel";
    private static final String STATE_TAG_DIRTY = "isTagDirty";
    private static final String STATE_TAG_ACTIVE_LOAD_THREADS = "activeLoadingThreads";
    private static final String STATE_TAG_LOADS_TO_RETRY = "retryLoadList";
    private static final String STATE_DELETE_ACTION_DATA = "deleteActionData";
    private static final String STATE_USER_GUID = "userGuid";

    private AlbumItemRecyclerViewAdapter viewAdapter;
    private FloatingActionButton retryActionButton;
    private TextView tagNameHeader;
    private RelativeLayout bulkActionsContainer;
    private FloatingActionButton bulkActionButtonDelete;
    // Start fields maintained in saved session state.
    private Tag tag;
    private PiwigoTag tagModel;
    private final HashMap<Long, String> loadingMessageIds = new HashMap<>(2);
    private final ArrayList<String> itemsToLoad = new ArrayList<>(0);
    private int colsOnScreen;
    private DeleteActionData deleteActionData;
    private long userGuid;
    private AppCompatImageView actionIndicatorImg;
    private RecyclerView tagListView;
    private TextView emptyTagLabel;
    private boolean tagIsDirty;
    private AlbumItemRecyclerViewAdapterPreferences viewPrefs;


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ViewTagFragment() {
    }

    public static ViewTagFragment newInstance(Tag tag) {
        ViewTagFragment fragment = new ViewTagFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_TAG, tag);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            if (getArguments().containsKey(ARG_TAG)) {
                tag = (Tag) getArguments().getSerializable(ARG_TAG);
            }
        }
    }

    private float getScreenWidth() {
        DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
        return (float)dm.widthPixels / dm.xdpi;
    }

    private int getDefaultImagesColumnCount() {
        float screenWidth = getScreenWidth();
        int columnsToShow = Math.round(screenWidth - (screenWidth % 1)); // allow 1 inch per column
        return Math.max(1,columnsToShow);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        UIHelper.recycleImageViewContent(actionIndicatorImg);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        viewPrefs.storeToBundle(outState);
        outState.putSerializable(ARG_TAG, tag);
        outState.putSerializable(STATE_TAG_MODEL, tagModel);
        outState.putSerializable(STATE_TAG_ACTIVE_LOAD_THREADS, loadingMessageIds);
        outState.putSerializable(STATE_TAG_LOADS_TO_RETRY, itemsToLoad);
        outState.putSerializable(STATE_DELETE_ACTION_DATA, deleteActionData);
    }

    private AlbumItemRecyclerViewAdapterPreferences updateViewPrefs() {
        boolean captureActionClicks = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();

        boolean useDarkMode = prefs.getBoolean(getString(R.string.preference_gallery_use_dark_mode_key), getResources().getBoolean(R.bool.preference_gallery_use_dark_mode_default));
        boolean showAlbumThumbnailsZoomed = prefs.getBoolean(getString(R.string.preference_gallery_show_album_thumbnail_zoomed_key), getResources().getBoolean(R.bool.preference_gallery_show_album_thumbnail_zoomed_default));

        boolean showLargeAlbumThumbnails = prefs.getBoolean(getString(R.string.preference_gallery_show_large_thumbnail_key), getResources().getBoolean(R.bool.preference_gallery_show_large_thumbnail_default));

        boolean useMasonryStyle = prefs.getBoolean(getString(R.string.preference_gallery_masonry_view_key), getResources().getBoolean(R.bool.preference_gallery_masonry_view_default));

        boolean showResourceNames = prefs.getBoolean(getString(R.string.preference_gallery_show_image_name_key), getResources().getBoolean(R.bool.preference_gallery_show_image_name_default));

        int recentlyAlteredThresholdAge = prefs.getInt(getString(R.string.preference_gallery_recentlyAlteredAgeMillis_key), getResources().getInteger(R.integer.preference_gallery_recentlyAlteredAgeMillis_default));
        Date recentlyAlteredThresholdDate = new Date(System.currentTimeMillis() - recentlyAlteredThresholdAge);

        if(viewPrefs == null) {
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences();
        }

        viewPrefs.selectable(captureActionClicks, false);
        viewPrefs.setAllowItemSelection(false); // prevent selection until a long click enables it.
        viewPrefs.withDarkMode(useDarkMode);
        viewPrefs.withLargeAlbumThumbnails(showLargeAlbumThumbnails);
        viewPrefs.withMasonryStyle(useMasonryStyle);
        viewPrefs.withShowingAlbumNames(showResourceNames);
        viewPrefs.withShowAlbumThumbnailsZoomed(showAlbumThumbnailsZoomed);
//        viewPrefs.withAlbumWidth(getScreenWidth() / albumsPerRow);
        viewPrefs.withRecentlyAlteredThresholdDate(recentlyAlteredThresholdDate);
        return viewPrefs;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        return inflater.inflate(R.layout.fragment_tag, container, false);

    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if(!PiwigoSessionDetails.isFullyLoggedIn()) {
            // force a reload of the tag if the session has been destroyed.
            tagIsDirty = true;
        } else if (savedInstanceState != null) {
            //restore saved state
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences();
            viewPrefs.loadFromBundle(savedInstanceState);
            tagModel = (PiwigoTag) savedInstanceState.getSerializable(STATE_TAG_MODEL);
            tag = (Tag) savedInstanceState.getSerializable(ARG_TAG);
            // if tagIsDirty then this fragment was updated while on the backstack - need to refresh it.
            userGuid = savedInstanceState.getLong(STATE_USER_GUID);
            tagIsDirty = tagIsDirty || PiwigoSessionDetails.getUserGuid() != userGuid;
            tagIsDirty = tagIsDirty || savedInstanceState.getBoolean(STATE_TAG_DIRTY);
            SetUtils.setNotNull(loadingMessageIds,(HashMap<Long,String>)savedInstanceState.getSerializable(STATE_TAG_ACTIVE_LOAD_THREADS));
            SetUtils.setNotNull(itemsToLoad,(ArrayList<String>)savedInstanceState.getSerializable(STATE_TAG_LOADS_TO_RETRY));
            if(deleteActionData != null && deleteActionData.isEmpty()) {
                deleteActionData = null;
            } else {
                deleteActionData = (DeleteActionData) savedInstanceState.getSerializable(STATE_DELETE_ACTION_DATA);
            }
        }

        TagUpdatedEvent tagUpdatedEvent = EventBus.getDefault().getStickyEvent(TagUpdatedEvent.class);
        if(tagUpdatedEvent != null && tag != null && tag.getId() == tagUpdatedEvent.getTag().getId()) {
            // updated the items tagged with this tag.
            // force reload of all tagged content.
            tagModel = null;
        }

        updateViewPrefs();

        userGuid = PiwigoSessionDetails.getUserGuid();
        if(tagModel == null) {
            tagIsDirty = true;
            tagModel = new PiwigoTag(tag);
        }

        if(tagListView != null && isSessionDetailsChanged()) {
            // If the page has been initialised already (not first visit), and the session token has changed, tag may not be valid for current user.
            getFragmentManager().popBackStack();
            return;
        }

        if(viewPrefs.isUseDarkMode()) {
            view.setBackgroundColor(Color.BLACK);
        }

        retryActionButton = view.findViewById(R.id.tag_retryAction_actionButton);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_refresh_black_24dp).into(retryActionButton);

        retryActionButton.setVisibility(GONE);
        retryActionButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                onReloadAlbum();
            }
        });

        emptyTagLabel = view.findViewById(R.id.tag_empty_content);
        emptyTagLabel.setText(R.string.tag_empty_text);
        emptyTagLabel.setVisibility(GONE);

        bulkActionsContainer = view.findViewById(R.id.tag_actions_bulk_container);

        tagNameHeader = view.findViewById(R.id.tag_details_name_header);

        setTagHeadings();

        int imagesOnScreen = selectBestColumnCountForScreenSize();
        colsOnScreen = imagesOnScreen;


//        viewInOrientation = getResources().getConfiguration().orientation;

        // Set the adapter
        tagListView = view.findViewById(R.id.tag_list);

        RecyclerView recyclerView = tagListView;

        if (!tagIsDirty) {
            emptyTagLabel.setVisibility(tagModel.getItemCount() == 0 ? VISIBLE : GONE);
        }

        // need to wait for the tag model to be initialised.
        RecyclerView.LayoutManager gridLayoutMan;
        if(viewPrefs.isUseMasonryStyle()) {
            gridLayoutMan = new StaggeredGridLayoutManager(colsOnScreen, StaggeredGridLayoutManager.VERTICAL);
        } else {
            gridLayoutMan = new GridLayoutManager(getContext(), colsOnScreen);
        }

        recyclerView.setLayoutManager(gridLayoutMan);

        if(!viewPrefs.isUseMasonryStyle()) {
            int colsPerImage = colsOnScreen / imagesOnScreen;
            ((GridLayoutManager)gridLayoutMan).setSpanSizeLookup(new SpanSizeLookup(tagModel, colsPerImage));
        }

        viewAdapter = new AlbumItemRecyclerViewAdapter(getContext(), tagModel, new AlbumViewAdapterListener(), viewPrefs);

        bulkActionsContainer.setVisibility(viewAdapter.isItemSelectionAllowed()?VISIBLE:GONE);

        bulkActionButtonDelete = bulkActionsContainer.findViewById(R.id.tag_action_delete_bulk);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_delete_black_24px).into(bulkActionButtonDelete);
        bulkActionButtonDelete.setVisibility(viewAdapter.isItemSelectionAllowed()?VISIBLE:GONE);
        bulkActionButtonDelete.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getActionMasked() == MotionEvent.ACTION_UP) {
                    onBulkActionDeleteButtonPressed();
                }
                return true; // consume the event
            }
        });

        recyclerView.setAdapter(viewAdapter);


        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(gridLayoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageToLoad = tagModel.getPagesLoaded();
                if (pageToLoad == 0 || tagModel.isFullyLoaded()) {
                    // already load this one by default so lets not double load it (or we've already loaded all items).
                    return;
                }
                loadAlbumResourcesPage(pageToLoad);
            }
        };
        scrollListener.configure(tagModel.getPagesLoaded(), tagModel.getItemCount());
        recyclerView.addOnScrollListener(scrollListener);
    }

    private void onBulkActionDeleteButtonPressed() {
        boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
        if(bulkActionsAllowed) {
            HashSet<Long> selectedItemIds = viewAdapter.getSelectedItemIds();
            if(deleteActionData != null && selectedItemIds.equals(deleteActionData.getSelectedItemIds())) {
                //continue with previous action
                onDeleteResources(deleteActionData);
            } else if(selectedItemIds.size() > 0) {
                HashSet<ResourceItem> selectedItems = viewAdapter.getSelectedItems();
                DeleteActionData deleteActionData = new DeleteActionData(selectedItemIds, selectedItems);
                if(!deleteActionData.isResourceInfoAvailable()) {
                    this.deleteActionData = deleteActionData;
                }
                onDeleteResources(deleteActionData);
            }
        }
    }

    private Basket getBasket() {
        MainActivity activity = (MainActivity)getActivity();
        return activity.getBasket();
    }

    private static class DeleteActionData implements Serializable {
        final HashSet<Long> selectedItemIds;
        final HashSet<Long> itemsUpdated;
        final HashSet<ResourceItem> selectedItems;
        boolean resourceInfoAvailable;
        private ResourceItem[] itemsWithoutLinkedAlbumData;

        public DeleteActionData(HashSet<Long> selectedItemIds, HashSet<ResourceItem> selectedItems) {
            this.selectedItemIds = selectedItemIds;
            this.selectedItems = selectedItems;
            this.resourceInfoAvailable = false; //FIXME when Piwigo provides this info as standard, this can be removed and the method simplified.
            itemsUpdated = new HashSet<>(selectedItemIds.size());
        }

        public void updateLinkedAlbums(ResourceItem item) {
            itemsUpdated.add(item.getId());
            if(itemsUpdated.size() == selectedItemIds.size()) {
                resourceInfoAvailable = true;
            }
            selectedItems.add(item); // will replace the previous with this one.
        }

        public boolean isResourceInfoAvailable() {
            return resourceInfoAvailable;
        }

        public HashSet<Long> getSelectedItemIds() {
            return selectedItemIds;
        }

        public HashSet<ResourceItem> getSelectedItems() {
            return selectedItems;
        }

        public void clear() {
            selectedItemIds.clear();
            selectedItems.clear();
            itemsUpdated.clear();
        }

        public Set<ResourceItem> getItemsWithoutLinkedAlbumData() {
            if(itemsUpdated.size() == 0) {
                return selectedItems;
            }
            Set<ResourceItem> itemsWithoutLinkedAlbumData = new HashSet<>();
            for(ResourceItem r : selectedItems) {
                if(!itemsUpdated.contains(r.getId())) {
                    itemsWithoutLinkedAlbumData.add(r);
                }
            }
            return itemsWithoutLinkedAlbumData;
        }

        public boolean removeProcessedResource(ResourceItem resource) {
            selectedItemIds.remove(resource.getId());
            selectedItems.remove(resource);
            itemsUpdated.remove(resource.getId());
            return selectedItemIds.size() == 0;
        }

        public boolean removeProcessedResources(HashSet<Long> deletedItemIds) {
            for(Long deletedResourceId : deletedItemIds) {
                selectedItemIds.remove(deletedResourceId);
                itemsUpdated.remove(deletedResourceId);
            }
            for (Iterator<ResourceItem> it = selectedItems.iterator(); it.hasNext(); ) {
                ResourceItem r = it.next();
                if(deletedItemIds.contains(r.getId())) {
                    it.remove();
                }
            }
            return selectedItemIds.size() == 0;
        }

        public boolean isEmpty() {
            return selectedItemIds.isEmpty();
        }
    }

    private boolean isDeTaggingSupported() {
        return !PiwigoSessionDetails.getInstance().isUseUserTagPluginForUpdate();
    }

    private void onDeleteResources(final DeleteActionData deleteActionData) {
        if (!deleteActionData.isResourceInfoAvailable()) {
            String multimediaExtensionList = prefs.getString(getString(R.string.preference_piwigo_playable_media_extensions_key), getString(R.string.preference_piwigo_playable_media_extensions_default));
            for (ResourceItem item : deleteActionData.getItemsWithoutLinkedAlbumData()) {
                addActiveServiceCall(R.string.progress_loading_resource_details, new ImageGetInfoResponseHandler(item, multimediaExtensionList).invokeAsync(getContext()));
            }
            return;
        }

        UIHelper.QuestionResultListener dialogListener = new UIHelper.QuestionResultListener() {
            @Override
            public void onDismiss(AlertDialog dialog) {

            }

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {

                HashSet<Long> itemIdsForPermanentDelete = new HashSet<>(deleteActionData.getSelectedItemIds());
                HashSet<ResourceItem> itemsForPermanentDelete = new HashSet<>(deleteActionData.getSelectedItems());

                if (Boolean.TRUE == positiveAnswer) {
                    deleteResourcesFromServerForever(itemIdsForPermanentDelete, itemsForPermanentDelete);
                } else if (Boolean.FALSE == positiveAnswer) { // Negative answer
                    for (ResourceItem item : itemsForPermanentDelete) {
                        itemIdsForPermanentDelete.remove(item.getId());
                        itemsForPermanentDelete.remove(item);
                        item.getTags().remove(tag);
                        //FIXME call correct service to remove tag! Note: user tags plugin needed - warn user
                        addActiveServiceCall(getString(R.string.progress_untag_resources_pattern,tag.getName()), new ImageUpdateInfoResponseHandler(item).invokeAsync(getContext()));
                    }
                } else {
                    // Neutral (cancel button) - do nothing
                }
            }
        };

        if(isDeTaggingSupported()) {
            String msg = getString(R.string.alert_confirm_delete_items_from_server_or_just_unlink_them_from_this_tag_pattern, deleteActionData.getSelectedItemIds().size());
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, Integer.MIN_VALUE, R.string.button_untag, R.string.button_cancel, R.string.button_delete, dialogListener);
        } else {
            String msg = getString(R.string.alert_confirm_delete_items_from_server_no_unlinking_from_tags_possible_pattern, deleteActionData.getSelectedItemIds().size());
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, Integer.MIN_VALUE, R.string.button_cancel, R.string.button_delete, dialogListener);
        }

    }

    private void deleteResourcesFromServerForever(final HashSet<Long> selectedItemIds, final HashSet<? extends ResourceItem> selectedItems) {
        String msg = getString(R.string.alert_confirm_really_delete_items_from_server);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultListener() {
            @Override
            public void onDismiss(AlertDialog dialog) {

            }

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if(Boolean.TRUE == positiveAnswer) {
                    addActiveServiceCall(R.string.progress_delete_resources, new ImageDeleteResponseHandler(selectedItemIds).invokeAsync(getContext()));
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if(tagListView == null) {
            //Resumed, but fragment initialisation cancelled for whatever reason.
            return;
        }

        if (tagIsDirty) {
            reloadAlbumContent();
        } else if(itemsToLoad.size() > 0) {
            onReloadAlbum();
        }
    }

    private void reloadAlbumContent() {
        if(tagIsDirty) {
            tagIsDirty = false;
            if(loadingMessageIds.size() > 0) {
                // already a load in progress - ignore this call.
                //TODO be cleverer - check the time the call was invoked and queue another if needed.
                return;
            }
            tagModel.clear();
            viewAdapter.notifyDataSetChanged();
            loadAlbumResourcesPage(0);

        }
    }

    private void loadAlbumResourcesPage(int pageToLoad) {
        synchronized (loadingMessageIds) {
            Set<String> activeCalls = new HashSet<>(loadingMessageIds.values());
            if (activeCalls.contains(String.valueOf(pageToLoad))) {
                // already loading this page, ignore the request.
                return;
            }
            String sortOrder = prefs.getString(getString(R.string.preference_gallery_sortOrder_key), getString(R.string.preference_gallery_sortOrder_default));
            String multimediaExtensionList = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(getString(R.string.preference_piwigo_playable_media_extensions_key), getString(R.string.preference_piwigo_playable_media_extensions_default));
            int pageSize = prefs.getInt(getString(R.string.preference_album_request_pagesize_key), getResources().getInteger(R.integer.preference_album_request_pagesize_default));
            long loadingMessageId = new TagGetImagesResponseHandler(tag, sortOrder, pageToLoad, pageSize, getContext(), multimediaExtensionList).invokeAsync(getContext());
            loadingMessageIds.put(loadingMessageId, String.valueOf(pageToLoad));
            addActiveServiceCall(R.string.progress_loading_album_content, loadingMessageId);
        }
    }

    private void displayControlsBasedOnSessionState() {
    }

    private void updateBasketDisplay(Basket basket) {
        if(viewPrefs.isAllowItemSelection()) {
            bulkActionButtonDelete.setVisibility(VISIBLE);
        } else {
            bulkActionButtonDelete.setVisibility(GONE);
        }
    }

    private void setTagHeadings() {
        tagNameHeader.setText(getString(R.string.tag_heading_pattern, tag.getName()));
        tagNameHeader.setVisibility(View.VISIBLE);
    }

    private int selectBestColumnCountForScreenSize() {
        int mColumnCount = getDefaultImagesColumnCount();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mColumnCount = prefs.getInt(getString(R.string.preference_gallery_images_preferredColumnsLandscape_key), mColumnCount);
        } else {
            mColumnCount = prefs.getInt(getString(R.string.preference_gallery_images_preferredColumnsPortrait_key), mColumnCount);
        }
        return Math.max(1,mColumnCount);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        EventBus.getDefault().unregister(this);
        tag = null;
        tagModel = null;
        if(tagListView != null) {
            tagListView.setAdapter(null);
        }
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
            synchronized (loadingMessageIds) {

                if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse) {
                    onGetResources((PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoAlbumDeletedResponse) {
                    onAlbumDeleted((PiwigoResponseBufferingHandler.PiwigoAlbumDeletedResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse) {
                    onResourceInfoRetrieved((PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse) {
                    //TODO handle resource removed from current tag.
                } else {
                    String failedCall = loadingMessageIds.get(response.getMessageId());
                    synchronized (itemsToLoad) {
                        itemsToLoad.add(failedCall);
                        emptyTagLabel.setText(R.string.tag_content_load_failed_text);
                        if (itemsToLoad.size() > 0) {
                            emptyTagLabel.setVisibility(VISIBLE);
                            retryActionButton.setVisibility(VISIBLE);
                        }
                    }
                }
                loadingMessageIds.remove(response.getMessageId());
            }
        }
    }

    private void onReloadAlbum() {
        retryActionButton.setVisibility(GONE);
        emptyTagLabel.setVisibility(GONE);
        synchronized (itemsToLoad) {
            while (itemsToLoad.size() > 0) {
                String itemToLoad = itemsToLoad.remove(0);
                switch (itemToLoad) {
                    default:
                        int page = Integer.valueOf(itemToLoad);
                        loadAlbumResourcesPage(page);
                        break;
                }
            }
        }
    }

    private void onGetResources(final PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse response) {
        synchronized (this) {
            tagModel.addItemPage(response.getPage(), response.getPageSize(), response.getResources());
            viewAdapter.notifyDataSetChanged();
        }
    }

    private void onAlbumDeleted(PiwigoResponseBufferingHandler.PiwigoAlbumDeletedResponse response) {
        tagIsDirty = true;
        if(isResumed()) {
            reloadAlbumContent();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AppLockedEvent event) {
        if(isResumed()) {
            displayControlsBasedOnSessionState();
            /*boolean captureActionClicks = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
            viewAdapter.setCaptureActionClicks(captureActionClicks);*/
        } else {
            // if not showing, just flush the state and rebuild the page
            tagIsDirty = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AppUnlockedEvent event) {
        if(isResumed()) {
            displayControlsBasedOnSessionState();
            /*boolean captureActionClicks = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
            viewAdapter.setCaptureActionClicks(captureActionClicks);*/
        } else {
            // if not showing, just flush the state and rebuild the page
            tagIsDirty = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumAlteredEvent albumAlteredEvent) {
        // This is needed because a slideshow item currently only fires these events (tag unaware).
        if (tag != null && tag.getId() == albumAlteredEvent.id) {
            tagIsDirty = true;
            if(isResumed()) {
                reloadAlbumContent();
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TagContentAlteredEvent event) {
        if (tag != null && tag.getId() == event.getId()) {
            tagIsDirty = true;
            if(isResumed()) {
                reloadAlbumContent();
            }
        }
    }

    private class SpanSizeLookup extends GridLayoutManager.SpanSizeLookup {

        private final int colsPerImage;
        private final PiwigoTag tagModel;

        public SpanSizeLookup(PiwigoTag tagModel, int colsPerImage) {
            this.colsPerImage = colsPerImage;
            this.tagModel = tagModel;
        }

        @Override
        public int getSpanSize(int position) {
            // ensure that app cannot crash due to position being out of bounds.
            //FIXME - why would position be outside model size? What happens next now it doesn't crash here?
            if(position < 0 || tagModel.getItemCount() <= position) {
                return 1;
            }

            int itemType = tagModel.getItemByIdx(position).getType();
            switch(itemType) {
                case GalleryItem.PICTURE_RESOURCE_TYPE:
                    return colsPerImage;
                case GalleryItem.VIDEO_RESOURCE_TYPE:
                    return colsPerImage;
                default:
                    return colsOnScreen;
            }
        }
    }

    private void onResourceInfoRetrieved(PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse response) {
        this.deleteActionData.updateLinkedAlbums(response.getResource());
        this.deleteActionData.isResourceInfoAvailable();
        onDeleteResources(deleteActionData);
    }

    private class AlbumViewAdapterListener extends AlbumItemRecyclerViewAdapter.MultiSelectStatusAdapter {

        @Override
        public void onMultiSelectStatusChanged(BaseRecyclerViewAdapter adapter, boolean multiSelectEnabled) {
//            bulkActionsContainer.setVisibility(multiSelectEnabled?VISIBLE:GONE);
        }

        @Override
        public void onItemSelectionCountChanged(BaseRecyclerViewAdapter adapter, int size) {
            bulkActionsContainer.setVisibility(size > 0?VISIBLE:GONE);
//            bulkActionsContainer.setVisibility(size > 0 || getBasket().getItemCount() > 0 ?VISIBLE:GONE);
            updateBasketDisplay(getBasket());
        }

        @Override
        public void onCategoryLongClick(CategoryItem album) {
        }

        @Override
        public void notifyAlbumThumbnailInfoLoadNeeded(CategoryItem mItem) {
        }


    }

}
