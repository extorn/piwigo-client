package delit.piwigoclient.ui.tags;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.EndlessRecyclerViewScrollListener;
import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoTag;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetImagesBasicResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GetMethodsAvailableResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.PluginUserTagsUpdateResourceTagsListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagGetImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetAdminListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetListResponseHandler;
import delit.piwigoclient.ui.MainActivity;
import delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapter;
import delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.TagContentAlteredEvent;
import delit.piwigoclient.ui.events.TagUpdatedEvent;
import delit.piwigoclient.ui.model.PiwigoTagModel;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * A fragment representing a list of Items.
 */
public class ViewTagFragment<F extends ViewTagFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends MyFragment<F,FUIH> {

    private static final String ARG_TAG = "tag";
    private static final String STATE_TAG_DIRTY = "isTagDirty";
    private static final String STATE_TAG_ACTIVE_LOAD_THREADS = "activeLoadingThreads";
    private static final String STATE_TAG_LOADS_TO_RETRY = "retryLoadList";
    private static final String STATE_DELETE_ACTION_DATA = "deleteActionData";
    private static final String STATE_USER_GUID = "userGuid";
    private static final String TAG = "ViewTagFrag";

    private AlbumItemRecyclerViewAdapter viewAdapter;
    private ExtendedFloatingActionButton retryActionButton;
    private RelativeLayout bulkActionsContainer;
    private ExtendedFloatingActionButton bulkActionButtonDelete;
    // Start fields maintained in saved session state.
    private Tag currentTag;
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

    public Tag getCurrentTag() {
        return currentTag;
    }

    public DeleteActionData getDeleteActionData() {
        return deleteActionData;
    }

    public HashMap<Long, String> getLoadingMessageIds() {
        return loadingMessageIds;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ViewTagFragment() {
    }

    public static ViewTagFragment newInstance(Tag tag) {
        ViewTagFragment fragment = new ViewTagFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_TAG, tag);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            if (getArguments().containsKey(ARG_TAG)) {
                currentTag = getArguments().getParcelable(ARG_TAG);
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
    protected String buildPageHeading() {
        return getString(R.string.tag_heading_pattern, currentTag.getName());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewPrefs != null) {
            viewPrefs.storeToBundle(outState);
        }
        outState.putParcelable(ARG_TAG, currentTag);
        BundleUtils.writeMap(outState, STATE_TAG_ACTIVE_LOAD_THREADS, loadingMessageIds);
        outState.putStringArrayList(STATE_TAG_LOADS_TO_RETRY, itemsToLoad);
        outState.putParcelable(STATE_DELETE_ACTION_DATA, deleteActionData);
    }

    private AlbumItemRecyclerViewAdapterPreferences updateViewPrefs() {

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());

        boolean showAlbumThumbnailsZoomed = AlbumViewPreferences.isShowAlbumThumbnailsZoomed(prefs, requireContext());

        boolean showResourceNames = AlbumViewPreferences.isShowResourceNames(prefs, requireContext());

        String preferredThumbnailSize = AlbumViewPreferences.getPreferredResourceThumbnailSize(prefs, requireContext());

        String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, requireContext());

        int recentlyAlteredThresholdAge = AlbumViewPreferences.getRecentlyAlteredMaxAgeMillis(prefs, requireContext());
        Date recentlyAlteredThresholdDate = new Date(System.currentTimeMillis() - recentlyAlteredThresholdAge);

        if(viewPrefs == null) {
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences();
        }

        viewPrefs.selectable(getMultiSelectionAllowed(), false);
        viewPrefs.setAllowItemSelection(false); // prevent selection until a long click enables it.
        viewPrefs.withPreferredThumbnailSize(preferredThumbnailSize);
        viewPrefs.withPreferredAlbumThumbnailSize(preferredAlbumThumbnailSize);
        viewPrefs.withShowingAlbumNames(showResourceNames);
        viewPrefs.withShowAlbumThumbnailsZoomed(showAlbumThumbnailsZoomed);
//        viewPrefs.withAlbumWidth(getScreenWidth() / albumsPerRow);
        viewPrefs.withRecentlyAlteredThresholdDate(recentlyAlteredThresholdDate);
        return viewPrefs;
    }

    protected boolean getMultiSelectionAllowed() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean captureActionClicks = sessionDetails != null && sessionDetails.isAdminUser();
        captureActionClicks |= (sessionDetails != null && sessionDetails.isFullyLoggedIn() && sessionDetails.isUseUserTagPluginForUpdate());
        captureActionClicks &= !isAppInReadOnlyMode();
        return captureActionClicks;
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
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        if(sessionDetails == null || !sessionDetails.isFullyLoggedIn()) {
            // force a reload of the tag if the session has been destroyed.
            tagIsDirty = true;
        } else if (savedInstanceState != null) {
            //restore saved state
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences();
            viewPrefs.loadFromBundle(savedInstanceState);
            currentTag = savedInstanceState.getParcelable(ARG_TAG);
            tagModel = new ViewModelProvider(requireActivity()).get(PiwigoTagModel.class).getPiwigoTag().getValue();
            // if tagIsDirty then this fragment was updated while on the backstack - need to refresh it.
            userGuid = savedInstanceState.getLong(STATE_USER_GUID);
            tagIsDirty = tagIsDirty || PiwigoSessionDetails.getUserGuid(connectionPrefs) != userGuid;
            tagIsDirty = tagIsDirty || savedInstanceState.getBoolean(STATE_TAG_DIRTY);
            loadingMessageIds.clear();
            BundleUtils.readMap(savedInstanceState, STATE_TAG_ACTIVE_LOAD_THREADS, loadingMessageIds, null);
            CollectionUtils.addToCollectionNullSafe(itemsToLoad, savedInstanceState.getStringArrayList(STATE_TAG_LOADS_TO_RETRY));
            if(deleteActionData != null && deleteActionData.isEmpty()) {
                deleteActionData = null;
            } else {
                deleteActionData = savedInstanceState.getParcelable(STATE_DELETE_ACTION_DATA);
            }
        }

        TagUpdatedEvent tagUpdatedEvent = EventBus.getDefault().getStickyEvent(TagUpdatedEvent.class);
        if(tagUpdatedEvent != null && currentTag != null && currentTag.getId() == tagUpdatedEvent.getTag().getId()) {
            // updated the items tagged with this tag.
            // force reload of all tagged content.
            tagModel = null;
        }

        updateViewPrefs();

        userGuid = PiwigoSessionDetails.getUserGuid(ConnectionPreferences.getActiveProfile());
        if(tagModel == null) {
            tagIsDirty = true;
            tagModel = new ViewModelProvider(requireActivity()).get("" + currentTag.getId(), PiwigoTagModel.class).getPiwigoTag(currentTag).getValue();
        }

        if (isSessionDetailsChanged()) {

            if(!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile()) || (isSessionDetailsChanged() && !isServerConnectionChanged())){
                //trigger total screen refresh. Any errors will result in screen being closed.
                tagIsDirty = false;
                reloadTagModel();
            } else {
                // immediately leave this screen.
                Logging.log(Log.INFO, TAG, "removing from activity as no longer logged in");
                getParentFragmentManager().popBackStack();
            }
        }

        retryActionButton = view.findViewById(R.id.tag_retryAction_actionButton);

        retryActionButton.hide();
        retryActionButton.setOnClickListener(v -> onReloadAlbum());

        emptyTagLabel = view.findViewById(R.id.tag_empty_content);
        emptyTagLabel.setText(R.string.tag_empty_text);
        emptyTagLabel.setVisibility(GONE);

        bulkActionsContainer = view.findViewById(R.id.tag_actions_bulk_container);

        int imagesOnScreen = AlbumViewPreferences.getImagesToDisplayPerRow(getActivity(), prefs);
        colsOnScreen = imagesOnScreen;


//        viewInOrientation = getResources().getConfiguration().orientation;

        // Set the adapter
        tagListView = view.findViewById(R.id.tag_list);

        RecyclerView recyclerView = tagListView;

        if (!tagIsDirty) {
            emptyTagLabel.setVisibility(tagModel.getItemCount() == 0 ? VISIBLE : GONE);
        }

        // need to wait for the tag model to be initialised.
        GridLayoutManager gridLayoutMan = new GridLayoutManager(getContext(), colsOnScreen);
        int colsPerImage = colsOnScreen / imagesOnScreen;
        gridLayoutMan.setSpanSizeLookup(new SpanSizeLookup(tagModel, colsPerImage));

        recyclerView.setLayoutManager(gridLayoutMan);


        viewAdapter = new AlbumItemRecyclerViewAdapter(requireContext(), PiwigoTagModel.class, tagModel, new AlbumViewAdapterListener(), viewPrefs);

        bulkActionsContainer.setVisibility(viewAdapter.isItemSelectionAllowed()?VISIBLE:GONE);

        bulkActionButtonDelete = bulkActionsContainer.findViewById(R.id.tag_action_delete_bulk);
        if(viewAdapter.isItemSelectionAllowed()) {
            bulkActionButtonDelete.show();
        } else {
            bulkActionButtonDelete.hide();
        }
        bulkActionButtonDelete.setOnTouchListener((v, event) -> {
            if(event.getActionMasked() == MotionEvent.ACTION_UP) {
                onBulkActionDeleteButtonPressed();
            }
            return true; // consume the event
        });

        recyclerView.setAdapter(viewAdapter);


        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(gridLayoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageToLoad = page;

                int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, requireContext());
                int pageToActuallyLoad = getPageToActuallyLoad(pageToLoad, pageSize);

                if (tagModel.isPageLoadedOrBeingLoaded(pageToActuallyLoad) || tagModel.isFullyLoaded()) {
                    Integer missingPage = tagModel.getAMissingPage();
                    if(missingPage != null) {
                        pageToLoad = missingPage;
                    } else {
                        // already load this one by default so lets not double load it (or we've already loaded all items).
                        return;
                    }
                }
                loadAlbumResourcesPage(pageToLoad);
            }
        };
        scrollListener.configure(tagModel.getPagesLoadedIdxToSizeMap(), tagModel.getItemCount());
        recyclerView.addOnScrollListener(scrollListener);
    }

    protected void reloadTagModel(Tag tag) {
        tagModel = new ViewModelProvider(requireActivity()).get("" + tag.getId(), PiwigoTagModel.class).updatePiwigoTag(tag).getValue();
    }

    protected PiwigoTag getTagModel() {
        return tagModel;
    }

    private void reloadTagModel() {
        if(PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
            getUiHelper().invokeActiveServiceCall(R.string.progress_loading_tags, new TagsGetAdminListResponseHandler(1, Integer.MAX_VALUE), new TagLoadedAction<>());
        } else {
            getUiHelper().invokeActiveServiceCall(R.string.progress_loading_tags, new TagsGetListResponseHandler(0, Integer.MAX_VALUE), new TagLoadedAction<>());
        }
    }

    protected void onGetResources(final AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse response) {
        synchronized (this) {
            tagModel.addItemPage(response.getPage(), response.getPageSize(), response.getResources());
            if (tagModel.isFullyLoaded() && tagModel.getItemCount() == 0) {
                emptyTagLabel.setText(R.string.tag_empty_text);
                emptyTagLabel.setVisibility(VISIBLE);
            }
            viewAdapter.notifyDataSetChanged();
        }
    }

    private void onBulkActionDeleteButtonPressed() {
        if(!isAppInReadOnlyMode()) {
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
        MainActivity activity = (MainActivity)requireActivity();
        return activity.getBasket();
    }

    private static class DeleteActionData implements Parcelable {
        final HashSet<Long> selectedItemIds;
        final HashSet<Long> itemsUpdated;
        final HashSet<ResourceItem> selectedItems;
        boolean resourceInfoAvailable;
        private ArrayList<Long> trackedMessageIds = new ArrayList<>();

        public DeleteActionData(HashSet<Long> selectedItemIds, HashSet<ResourceItem> selectedItems) {
            this.selectedItemIds = selectedItemIds;
            this.selectedItems = selectedItems;
            this.resourceInfoAvailable = false; //FIXME when Piwigo provides this info as standard, this can be removed and the method simplified.
            itemsUpdated = new HashSet<>(selectedItemIds.size());
        }

        public DeleteActionData(Parcel in) {
            selectedItemIds = ParcelUtils.readLongSet(in);
            itemsUpdated = ParcelUtils.readLongSet(in);
            selectedItems = ParcelUtils.readHashSet(in, getClass().getClassLoader());
            resourceInfoAvailable = ParcelUtils.readBool(in);
            trackedMessageIds = ParcelUtils.readLongArrayList(in);
        }

        public static final Creator<DeleteActionData> CREATOR = new Creator<DeleteActionData>() {
            @Override
            public DeleteActionData createFromParcel(Parcel in) {
                return new DeleteActionData(in);
            }

            @Override
            public DeleteActionData[] newArray(int size) {
                return new DeleteActionData[size];
            }
        };

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelUtils.writeLongSet(dest, selectedItemIds);
            ParcelUtils.writeLongSet(dest, itemsUpdated);
            ParcelUtils.writeSet(dest, selectedItems);
            ParcelUtils.writeBool(dest, resourceInfoAvailable);
            ParcelUtils.writeLongArrayList(dest, trackedMessageIds);
        }

        public void updateLinkedAlbums(ResourceItem item) {
            itemsUpdated.add(item.getId());
            if (itemsUpdated.size() == selectedItemIds.size()) {
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
            if (itemsUpdated.size() == 0) {
                return selectedItems;
            }
            Set<ResourceItem> itemsWithoutLinkedAlbumData = new HashSet<>();
            for (ResourceItem r : selectedItems) {
                if (!itemsUpdated.contains(r.getId())) {
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
            for (Long deletedResourceId : deletedItemIds) {
                selectedItemIds.remove(deletedResourceId);
                itemsUpdated.remove(deletedResourceId);
            }
            PiwigoUtils.removeAll(selectedItems, deletedItemIds);
            return selectedItemIds.size() == 0;
        }

        public boolean isEmpty() {
            return selectedItemIds.isEmpty();
        }

        public void trackMessageId(long messageId) {
            trackedMessageIds.add(messageId);
        }

        public boolean isTrackingMessageId(long messageId) {
            return trackedMessageIds.remove(messageId);
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }
    
    private void onDeleteResources(final DeleteActionData deleteActionData) {

        if (!deleteActionData.isResourceInfoAvailable()) {
            Set<String> multimediaExtensionList = ConnectionPreferences.getActiveProfile().getKnownMultimediaExtensions(prefs, getContext());
            for (ResourceItem item : deleteActionData.getItemsWithoutLinkedAlbumData()) {
                deleteActionData.trackMessageId(addActiveServiceCall(R.string.progress_loading_resource_details, new ImageGetInfoResponseHandler(item, multimediaExtensionList)));
            }
            return;
        }

        UIHelper.QuestionResultListener dialogListener = new OnDeleteTagsAction<>(getUiHelper());

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean isTagAlterationSupported = sessionDetails != null && (sessionDetails.isUseUserTagPluginForUpdate() || sessionDetails.isAdminUser());

        if(isTagAlterationSupported) {
            String msg = getString(R.string.alert_confirm_delete_items_from_server_or_just_unlink_them_from_this_tag_pattern, deleteActionData.getSelectedItemIds().size());
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, View.NO_ID, R.string.button_untag, R.string.button_cancel, R.string.button_delete, dialogListener);
        } else {
            String msg = getString(R.string.alert_confirm_delete_items_from_server_no_unlinking_from_tags_possible_pattern, deleteActionData.getSelectedItemIds().size());
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, View.NO_ID, R.string.button_cancel, R.string.button_delete, dialogListener);
        }

    }

    protected void loadAlbumResourcesPage(int pageToLoad) {
        synchronized (loadingMessageIds) {
            tagModel.acquirePageLoadLock();
            try {
                int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, requireContext());
                int pageToActuallyLoad = getPageToActuallyLoad(pageToLoad, pageSize);

                if (pageToActuallyLoad < 0) {
                    // the sort order is inverted so we know for a fact this page is invalid.
                    return;
                }
                if (tagModel.isPageLoadedOrBeingLoaded(pageToActuallyLoad)) {
                    return;
                }


                String sortOrder = AlbumViewPreferences.getResourceSortOrder(prefs, requireContext());
                Set<String> multimediaExtensionList = ConnectionPreferences.getActiveProfile().getKnownMultimediaExtensions(prefs, requireContext());

                long loadingMessageId = addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, new TagGetImagesResponseHandler(currentTag, sortOrder, pageToActuallyLoad, pageSize, multimediaExtensionList));
                tagModel.recordPageBeingLoaded(loadingMessageId, pageToLoad);
                loadingMessageIds.put(loadingMessageId, String.valueOf(pageToLoad));
            } finally {
                tagModel.releasePageLoadLock();
            }
        }
    }

    private int getPageToActuallyLoad(int pageRequested, int pageSize) {
//        boolean invertSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, getContext());
//        tagModel.setRetrieveItemsInReverseOrder(invertSortOrder);
        int pageToActuallyLoad = pageRequested;
//        if (invertSortOrder) {
//            int pagesOfPhotos = tagModel.getContainerDetails().getPagesOfPhotos(pageSize);
//            pageToActuallyLoad = pagesOfPhotos - pageRequested;
//        }
        return pageToActuallyLoad;
    }

    protected void deleteResourcesFromServerForever(final HashSet<Long> selectedItemIds, final HashSet<? extends ResourceItem> selectedItems) {
        String msg = getString(R.string.alert_confirm_really_delete_items_from_server);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_cancel, R.string.button_ok, new OnDeleteTagsForeverAction<>(getUiHelper(), selectedItemIds, selectedItems));
    }

    private static class OnDeleteTagsAction<F extends ViewTagFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends UIHelper.QuestionResultAdapter<FUIH,F> implements Parcelable {

        public OnDeleteTagsAction(FUIH uiHelper) {
            super(uiHelper);
        }

        protected OnDeleteTagsAction(Parcel in) {
            super(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<OnDeleteTagsAction> CREATOR = new Creator<OnDeleteTagsAction>() {
            @Override
            public OnDeleteTagsAction createFromParcel(Parcel in) {
                return new OnDeleteTagsAction<>(in);
            }

            @Override
            public OnDeleteTagsAction[] newArray(int size) {
                return new OnDeleteTagsAction[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            F fragment = getUiHelper().getParent();
            fragment.getViewAdapter().toggleItemSelection();
            if (Boolean.TRUE == positiveAnswer) {
                HashSet<Long> itemIdsForPermanentDelete = new HashSet<>(fragment.getDeleteActionData().getSelectedItemIds());
                HashSet<ResourceItem> itemsForPermanentDelete = new HashSet<>(fragment.getDeleteActionData().getSelectedItems());
                fragment.deleteResourcesFromServerForever(itemIdsForPermanentDelete, itemsForPermanentDelete);
            } else if (Boolean.FALSE == positiveAnswer) { // Negative answer
                PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                boolean allowTagEdit = !fragment.isAppInReadOnlyMode() && sessionDetails != null && sessionDetails.isUseUserTagPluginForUpdate();
                for (ResourceItem item : fragment.getDeleteActionData().getSelectedItems()) {
                    item.getTags().remove(fragment.getCurrentTag());
                    if (allowTagEdit) {
                        getUiHelper().addActiveServiceCall(R.string.progress_untag_resources_pattern, new PluginUserTagsUpdateResourceTagsListResponseHandler(item));
                    } else {
                        getUiHelper().addActiveServiceCall(R.string.progress_untag_resources_pattern, new ImageUpdateInfoResponseHandler(item, true));
                    }
                }
            } else {
                // Neutral (cancel button) - do nothing
            }
        }
    }

    protected AlbumItemRecyclerViewAdapter getViewAdapter() {
        return viewAdapter;
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

    private static class OnDeleteTagsForeverAction<F extends ViewTagFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends UIHelper.QuestionResultAdapter<FUIH, F> implements Parcelable {

        private final HashSet<Long> selectedItemIds;
        private final HashSet<? extends ResourceItem> selectedItems;

        public OnDeleteTagsForeverAction(FUIH uiHelper, HashSet<Long> selectedItemIds, HashSet<? extends ResourceItem> selectedItems) {
            super(uiHelper);
            this.selectedItemIds = selectedItemIds;
            this.selectedItems = selectedItems;
        }

        protected OnDeleteTagsForeverAction(Parcel in) {
            super(in);
            selectedItemIds = ParcelUtils.readLongSet(in);
            selectedItems = ParcelUtils.readHashSet(in, ResourceItem.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            ParcelUtils.writeLongSet(dest, selectedItemIds);
            ParcelUtils.writeSet(dest, selectedItems);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<OnDeleteTagsForeverAction> CREATOR = new Creator<OnDeleteTagsForeverAction>() {
            @Override
            public OnDeleteTagsForeverAction createFromParcel(Parcel in) {
                return new OnDeleteTagsForeverAction<>(in);
            }

            @Override
            public OnDeleteTagsForeverAction[] newArray(int size) {
                return new OnDeleteTagsForeverAction[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                getUiHelper().addActiveServiceCall(R.string.progress_delete_resources, new ImageDeleteResponseHandler(selectedItemIds, selectedItems));
            }
        }
    }

    private void displayControlsBasedOnSessionState() {
    }

    private void updateBasketDisplay(Basket basket) {
        if(viewPrefs.isAllowItemSelection()) {
            bulkActionButtonDelete.show();
        } else {
            bulkActionButtonDelete.hide();
        }
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
        currentTag = null;
        tagModel = null;
        if(tagListView != null) {
            tagListView.setAdapter(null);
        }
    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    protected void onTagUpdateResponse(PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse rsp) {
        if(rsp.hasError()) {
            String errorMsg = getString(R.string.error_updating_tag_pattern, rsp.getPiwigoResource().getName(), rsp.getError());
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, errorMsg);
            rsp.getPiwigoResource().getTags().add(currentTag); // removal failed, re-add this tag.
        } else {
            ResourceItem r = rsp.getPiwigoResource();
            onItemRemovedFromTag(r);
        }
    }

    private static class CustomPiwigoResponseListener<F extends ViewTagFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends BasicPiwigoResponseListener<FUIH,F> {

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if(getParent().isVisible()) {
                getParent().updateActiveSessionDetails();
            }
            super.onBeforeHandlePiwigoResponse(response);
        }

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            synchronized (getParent().getLoadingMessageIds()) {

                if (response instanceof AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse) {
                    getParent().onGetResources((AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse) response);
                } else if (response instanceof BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse) {
                    getParent().onResourceInfoRetrieved((BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse) response);
                } else if(response instanceof ImageDeleteResponseHandler.PiwigoDeleteImageResponse) {
                    for(ResourceItem r : ((ImageDeleteResponseHandler.PiwigoDeleteImageResponse) response).getDeletedItems()) {
                        getParent().onItemRemovedFromServer(r);
                    }
                } else if (response instanceof BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse) {
                    ResourceItem r = ((BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse) response).getPiwigoResource();
                    getParent().onItemRemovedFromTag(r);
                } else if (response instanceof PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse) {
                    getParent().onTagUpdateResponse(((PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse) response));
                } else if (response instanceof GetMethodsAvailableResponseHandler.PiwigoGetMethodsAvailableResponse) {
                    getParent().getViewPrefs().withAllowMultiSelect(getParent().getMultiSelectionAllowed());
                } else {
                    getParent().withUnexpectedPiwigoResponse(response);
                }
                getParent().getLoadingMessageIds().remove(response.getMessageId());
            }
        }
    }

    protected void withUnexpectedPiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
        String failedCall = loadingMessageIds.get(response.getMessageId());
        synchronized (itemsToLoad) {
            tagModel.recordPageLoadFailed(response.getMessageId());
            itemsToLoad.add(failedCall);
            emptyTagLabel.setText(R.string.tag_content_load_failed_text);
            if (itemsToLoad.size() > 0) {
                emptyTagLabel.setVisibility(VISIBLE);
                retryActionButton.show();
            }
        }
    }

    protected AlbumItemRecyclerViewAdapterPreferences getViewPrefs() {
        return viewPrefs;
    }

    protected void onItemRemovedFromServer(ResourceItem r) {
        onItemRemovedFromTag(r);
        for(Long albumId : r.getLinkedAlbums()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(albumId, r.getId(), true));
        }
    }

    protected void onItemRemovedFromTag(ResourceItem r) {
        int itemIdx = tagModel.getItemIdx(r);
        tagModel.remove(itemIdx);
        viewAdapter.notifyItemRemoved(itemIdx);
        if (deleteActionData != null && deleteActionData.removeProcessedResource(r)) {
            deleteActionData = null;
        }
        EventBus.getDefault().post(new TagContentAlteredEvent(currentTag.getId(), -1));
    }

    private void onReloadAlbum() {
        retryActionButton.hide();
        emptyTagLabel.setVisibility(GONE);
        synchronized (itemsToLoad) {
            while (itemsToLoad.size() > 0) {
                String itemToLoad = itemsToLoad.remove(0);
                if(itemToLoad != null) {
//                    switch (itemToLoad) {
//                        default:
                            int page = Integer.parseInt(itemToLoad);
                            loadAlbumResourcesPage(page);
//                            break;
//                    }
                } else {
                    Logging.log(Log.ERROR, getTag(), "User told tag page failed to load, but nothing to retry loading!");
                }
            }
        }
    }

    static class TagLoadedAction<F extends ViewTagFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends UIHelper.Action<FUIH, F, TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse> implements Parcelable {

        TagLoadedAction(){}

        protected TagLoadedAction(Parcel in) {
            super(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<TagLoadedAction> CREATOR = new Creator<TagLoadedAction>() {
            @Override
            public TagLoadedAction createFromParcel(Parcel in) {
                return new TagLoadedAction<>(in);
            }

            @Override
            public TagLoadedAction[] newArray(int size) {
                return new TagLoadedAction[size];
            }
        };

        @Override
        public boolean onSuccess(FUIH uiHelper, TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse response) {
            boolean updated = false;
            F fragment = uiHelper.getParent();
            for (Tag t : response.getTags()) {
                if (t.getId() == fragment.getTagModel().getId()) {
                    // tag has been located!
                    fragment.reloadTagModel(t);
                    updated = true;
                }
            }
            if (!updated) {
                //Something wierd is going on - this should never happen
                Logging.log(Log.ERROR, TAG, "Closing tag - tag was not available after refreshing session");
                fragment.getParentFragmentManager().popBackStack();
                return false;
            }
            fragment.loadAlbumResourcesPage(0);
            return false;
        }

        @Override
        public boolean onFailure(FUIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            F fragment = uiHelper.getParent();
            Logging.log(Log.INFO, TAG, "removing from activity on piwigo error");
            fragment.getParentFragmentManager().popBackStack();
            return false;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppLockedEvent event) {
        if(isResumed()) {
            displayControlsBasedOnSessionState();
            /*boolean captureActionClicks = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
            viewAdapter.setCaptureActionClicks(captureActionClicks);*/
        } else {
            // if not showing, just flush the state and rebuild the page
            tagIsDirty = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppUnlockedEvent event) {
        if(isResumed()) {
            displayControlsBasedOnSessionState();
            /*boolean captureActionClicks = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
            viewAdapter.setCaptureActionClicks(captureActionClicks);*/
        } else {
            // if not showing, just flush the state and rebuild the page
            tagIsDirty = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumAlteredEvent albumAlteredEvent) {
        // This is needed because a slideshow item currently only fires these events (tag unaware).
        if (currentTag != null && currentTag.getId() == albumAlteredEvent.getAlbumAltered()) {
            tagIsDirty = true;
            if(isResumed()) {
                reloadAlbumContent();
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(TagContentAlteredEvent event) {
        if (currentTag != null && currentTag.getId() == event.getId()) {
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

    protected void onResourceInfoRetrieved(BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse response) {
        if(this.deleteActionData.isTrackingMessageId(response.getMessageId())) {
            this.deleteActionData.updateLinkedAlbums(response.getResource());
            if (this.deleteActionData.isResourceInfoAvailable()) {
                onDeleteResources(deleteActionData);
            }
        }
    }

    private class AlbumViewAdapterListener extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter {

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
        protected void onCategoryClick(CategoryItem item) {
            if (viewAdapter.isItemSelectionAllowed()) {
                viewAdapter.toggleItemSelection();
            }
        }

        @Override
        public void onCategoryLongClick(CategoryItem album) {
        }

        @Override
        public void notifyAlbumThumbnailInfoLoadNeeded(CategoryItem mItem) {
        }


    }

}
