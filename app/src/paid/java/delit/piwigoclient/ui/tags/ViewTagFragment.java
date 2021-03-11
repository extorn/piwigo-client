package delit.piwigoclient.ui.tags;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
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
import java.util.Objects;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.CustomClickTouchListener;
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
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetImagesBasicResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.PluginUserTagsUpdateResourceTagsListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagGetImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetAdminListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetListResponseHandler;
import delit.piwigoclient.ui.MainActivity;
import delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapter;
import delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.album.view.AlbumItemSpacingDecoration;
import delit.piwigoclient.ui.album.view.AlbumItemViewHolder;
import delit.piwigoclient.ui.album.view.BulkActionResourceProvider;
import delit.piwigoclient.ui.album.view.action.BulkResourceActionData;
import delit.piwigoclient.ui.album.view.action.DeleteResourcesForeverAction;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.TagContentAlteredEvent;
import delit.piwigoclient.ui.events.TagUpdatedEvent;
import delit.piwigoclient.ui.model.PiwigoTagModel;
import delit.piwigoclient.ui.tags.action.TagLoadedAction;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * A fragment representing a list of Items.
 */
public class ViewTagFragment<F extends ViewTagFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends MyFragment<F,FUIH> implements BulkActionResourceProvider {

    private static final String ARG_TAG = "tag";
    private static final String STATE_TAG_DIRTY = "isTagDirty";
    private static final String STATE_TAG_ACTIVE_LOAD_THREADS = "activeLoadingThreads";
    private static final String STATE_TAG_LOADS_TO_RETRY = "retryLoadList";
    private static final String STATE_DELETE_ACTION_DATA = "deleteActionData";
    private static final String STATE_USER_GUID = "userGuid";
    private static final String TAG = "ViewTagFrag";

    private AlbumItemRecyclerViewAdapter<?,ResourceItem,?,?,?>  viewAdapter;
    private ExtendedFloatingActionButton retryActionButton;
    private ConstraintLayout bulkActionsContainer;
    private ExtendedFloatingActionButton bulkActionButtonDelete;
    private ExtendedFloatingActionButton bulkActionButtonUnTag;
    // Start fields maintained in saved session state.
    private Tag currentTag;
    private PiwigoTag tagModel;
    private final HashMap<Long, String> loadingMessageIds = new HashMap<>(2);
    private final ArrayList<String> itemsToLoad = new ArrayList<>(0);
    private BulkResourceActionData bulkResourceActionData;
    private long userGuid;
    private View basketView;
    private RecyclerView tagListView;
    private TextView emptyTagLabel;
    private AlbumItemRecyclerViewAdapterPreferences viewPrefs;
    private boolean tagIsDirty;

    public Tag getCurrentTag() {
        return currentTag;
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

    public static ViewTagFragment<?,?> newInstance(Tag tag) {
        ViewTagFragment<?,?> fragment = new ViewTagFragment<>();
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

    @Override
    public void onDestroy() {
        super.onDestroy();
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
        outState.putParcelable(STATE_DELETE_ACTION_DATA, bulkResourceActionData);
    }

    private void createOrUpdateViewPrefs() {

//        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());

        boolean showAlbumThumbnailsZoomed = AlbumViewPreferences.isShowAlbumThumbnailsZoomed(prefs, requireContext());

        String preferredThumbnailSize = AlbumViewPreferences.getPreferredResourceThumbnailSize(prefs, requireContext());
        boolean showResourceNames = AlbumViewPreferences.isShowResourceNames(prefs, requireContext());

        int recentlyAlteredThresholdAge = AlbumViewPreferences.getRecentlyAlteredMaxAgeMillis(prefs, requireContext());
        Date recentlyAlteredThresholdDate = new Date(System.currentTimeMillis() - recentlyAlteredThresholdAge);

        if(viewPrefs == null) {
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences();
        }

        viewPrefs.selectable(getMultiSelectionAllowed(), false);
        viewPrefs.setAllowItemSelection(false); // prevent selection until a long click enables it.
        viewPrefs.withPreferredThumbnailSize(preferredThumbnailSize);
        viewPrefs.withShowingResourceNames(showResourceNames);
        viewPrefs.withShowAlbumThumbnailsZoomed(showAlbumThumbnailsZoomed);
//        viewPrefs.withAlbumWidth(getScreenWidth() / albumsPerRow);
        viewPrefs.withRecentlyAlteredThresholdDate(recentlyAlteredThresholdDate);
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
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences(savedInstanceState);
            currentTag = savedInstanceState.getParcelable(ARG_TAG);
            reloadTagModel(currentTag);


            // if tagIsDirty then this fragment was updated while on the backstack - need to refresh it.
            userGuid = savedInstanceState.getLong(STATE_USER_GUID);
            tagIsDirty = tagIsDirty || PiwigoSessionDetails.getUserGuid(connectionPrefs) != userGuid;
            tagIsDirty = tagIsDirty || savedInstanceState.getBoolean(STATE_TAG_DIRTY);
            loadingMessageIds.clear();
            BundleUtils.readMap(savedInstanceState, STATE_TAG_ACTIVE_LOAD_THREADS, loadingMessageIds, null);
            CollectionUtils.addToCollectionNullSafe(itemsToLoad, savedInstanceState.getStringArrayList(STATE_TAG_LOADS_TO_RETRY));
            if (bulkResourceActionData != null && bulkResourceActionData.isEmpty()) {
                bulkResourceActionData = null;
            } else {
                bulkResourceActionData = savedInstanceState.getParcelable(STATE_DELETE_ACTION_DATA);
            }
        }

        TagUpdatedEvent tagUpdatedEvent = EventBus.getDefault().getStickyEvent(TagUpdatedEvent.class);
        if(tagUpdatedEvent != null && currentTag != null && currentTag.getId() == tagUpdatedEvent.getTag().getId()) {
            // updated the items tagged with this tag.
            // force reload of all tagged content.
            tagModel = null;
        }

        // Update the view preferences because though they can be loaded from saved state, some app preferences may have altered since
        createOrUpdateViewPrefs();

        userGuid = PiwigoSessionDetails.getUserGuid(ConnectionPreferences.getActiveProfile());
        if(tagModel == null) {
            tagIsDirty = true;
            reloadTagModel(currentTag);
        }
        tagIsDirty |= Objects.requireNonNull(tagModel).getImgResourceCount() < 0;

        if (isSessionDetailsChanged()) {

            if(!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile()) || (isSessionDetailsChanged() && !isServerConnectionChanged())){
                //trigger total screen refresh. Any errors will result in screen being closed.
                tagIsDirty = false;
                reloadTagModel();
            } else {
                // immediately leave this screen.
                Logging.log(Log.INFO, TAG, "Unable to show tag page - removing from activity");
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

//        viewInOrientation = getResources().getConfiguration().orientation;

        // Set the adapter
        tagListView = view.findViewById(R.id.tag_list);

        RecyclerView recyclerView = tagListView;

        if (!tagIsDirty) {
            emptyTagLabel.setVisibility(tagModel.getItemCount() == 0 ? VISIBLE : GONE);
        }

        // need to wait for the tag model to be initialised.
        int colsOnScreen = AlbumViewPreferences.getImagesToDisplayPerRow(getActivity(), prefs);
        GridLayoutManager gridLayoutMan = new GridLayoutManager(getContext(), colsOnScreen);
        recyclerView.setLayoutManager(gridLayoutMan);
        recyclerView.addItemDecoration(new AlbumItemSpacingDecoration(DisplayUtils.dpToPx(requireContext(), 1), DisplayUtils.dpToPx(requireContext(), 16)));


        viewAdapter = new AlbumItemRecyclerViewAdapter(requireContext(), PiwigoTagModel.class, tagModel, new TagViewAdapterListener<>(), viewPrefs);

        recyclerView.setAdapter(viewAdapter);


        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(gridLayoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, requireContext());
                int pageToActuallyLoad = getPageToActuallyLoad(page, pageSize);

                if (pageToActuallyLoad < 0 || tagModel.isPageLoadedOrBeingLoaded(pageToActuallyLoad) || tagModel.isFullyLoaded()) {
                    Integer missingPage = tagModel.getAMissingPage();
                    if(missingPage != null) {
                        pageToActuallyLoad = missingPage;
                    } else {
                        // already load this one by default so lets not double load it (or we've already loaded all items).
                        return;
                    }
                }
                loadAlbumResourcesPage(pageToActuallyLoad);
            }
        };
        scrollListener.configure(tagModel.getPagesLoadedIdxToSizeMap(), tagModel.getItemCount());
        recyclerView.addOnScrollListener(scrollListener);

        // basket depends on then adapter being available
        Basket basket = getBasket();
        initialiseBasketView(view);
        setupBulkActionsControls(basket);
        updateBasketDisplay(basket);
    }

    private boolean showBulkActionsContainer(Basket basket) {
        return viewAdapter != null && (viewAdapter.isItemSelectionAllowed() || !getBasket().isEmpty());
    }

    private void initialiseBasketView(View v) {
        basketView = v.findViewById(R.id.basket);

        basketView.setOnTouchListener((v12, event) -> {
            // sink events without action.
            return true;
        });

//        AppCompatImageView basketImage = basketView.findViewById(R.id.basket_image);

        AppCompatImageView clearButton = basketView.findViewById(R.id.basket_clear_button);
        CustomClickTouchListener.callClickOnTouch(clearButton, (cb)->onClickClearBasketButton());
    }

    private void onClickClearBasketButton() {
        Basket basket = getBasket();
        basket.clear();
        updateBasketDisplay(basket);
    }

    protected void setupBulkActionsControls(Basket basket) {

        bulkActionsContainer.setVisibility(showBulkActionsContainer(basket) ? VISIBLE : GONE);
        bulkActionButtonUnTag = bulkActionsContainer.findViewById(R.id.tag_action_untag_bulk);
        bulkActionButtonDelete = bulkActionsContainer.findViewById(R.id.tag_action_delete_bulk);
        //NOTE this touch listener is needed because we use a touch listener in the images below and they'll pick up the clicks if we don't
        CustomClickTouchListener.callClickOnTouch(bulkActionButtonUnTag, (v)->onClickBulkActionUnTagButton());
        CustomClickTouchListener.callClickOnTouch(bulkActionButtonDelete, (v)->onClickBulkActionDeleteButton());
    }

    public PiwigoTag getTagModel() {
        return tagModel;
    }

    public void reloadTagModel(@Nullable Tag tag) {
        if(tag == null) {
            //TODO is this even logical?
            PiwigoTagModel tagViewModel = obtainActivityViewModel(requireActivity(), PiwigoTagModel.class);
            tagModel = tagViewModel.getPiwigoTag().getValue();
        } else {
            PiwigoTagModel tagViewModel = obtainActivityViewModel(requireActivity(), "" + tag.getId(), PiwigoTagModel.class);
            tagModel = tagViewModel.updatePiwigoTag(tag).getValue();
        }
        loadAlbumResourcesPage(0);
    }

    private void reloadTagModel() {
        if(PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
            getUiHelper().invokeActiveServiceCall(R.string.progress_loading_tags, new TagsGetAdminListResponseHandler(1, Integer.MAX_VALUE), new TagLoadedAction<>());
        } else {
            getUiHelper().invokeActiveServiceCall(R.string.progress_loading_tags, new TagsGetListResponseHandler(0, Integer.MAX_VALUE), new TagLoadedAction<>());
        }
    }

    private void onClickBulkActionButton(int action, BulkResourceActionData.Caller actionCode) {
        boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
        if (bulkActionsAllowed) {
            HashSet<Long> selectedItemIds = viewAdapter.getSelectedItemIds();
            if (bulkResourceActionData != null && selectedItemIds.equals(bulkResourceActionData.getSelectedItemIds())) {
                //continue with previous action
                actionCode.run(bulkResourceActionData);
            } else if (selectedItemIds.size() > 0) {
                HashSet<ResourceItem> selectedItems = viewAdapter.getSelectedItemsOfType(ResourceItem.class);
                BulkResourceActionData bulkActionData = new BulkResourceActionData(selectedItemIds, selectedItems, action);
                if (!bulkActionData.isResourceInfoAvailable()) {
                    this.bulkResourceActionData = bulkActionData;
                }
                actionCode.run(bulkActionData);
            }
        }
    }

    private void onClickBulkActionUnTagButton() {
        onClickBulkActionButton(BulkResourceActionData.ACTION_UNTAG, this::onActionUnTagResources);
    }

    private void onClickBulkActionDeleteButton() {
        onClickBulkActionButton(BulkResourceActionData.ACTION_DELETE, this::onActionDeleteResources);
    }

    private Basket getBasket() {
        MainActivity<?> activity = (MainActivity<?>)requireActivity();
        return activity.getBasket();
    }

    @Override
    public BulkResourceActionData getBulkResourceActionData() {
        return bulkResourceActionData;
    }

    public void loadAlbumResourcesPage(int pageToLoad) {
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


                long loadingMessageId = addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, new TagGetImagesResponseHandler(currentTag, sortOrder, pageToActuallyLoad, pageSize));
                tagModel.recordPageBeingLoaded(loadingMessageId, pageToActuallyLoad);
                loadingMessageIds.put(loadingMessageId, String.valueOf(pageToActuallyLoad));
            } finally {
                tagModel.releasePageLoadLock();
            }
        }
    }

    private int getPageToActuallyLoad(int pageRequested, int pageSize) {
        if(tagModel.getImgResourceCount() < 0) {
            //FIXME Have to load this to get the number of pages.
            return 0;
        }
        boolean invertResourceSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, requireContext());
        boolean reversed;
        try {
            reversed = tagModel.setRetrieveResourcesInReverseOrder(invertResourceSortOrder);
            //need to refresh this page as the sort order was just flipped.
        } catch(IllegalStateException e) {
            tagModel.removeAllResources();
            reversed = tagModel.setRetrieveResourcesInReverseOrder(invertResourceSortOrder);
        }
        int pageToActuallyLoad = pageRequested;
        if (invertResourceSortOrder) {
            pageToActuallyLoad = tagModel.getContainerDetails().getPagesOfPhotos(pageSize) - 1;
            if (tagModel.getResourcesCount() > 0) {
                pageToActuallyLoad -= pageRequested;
            }
            if(tagModel.isPageLoaded(pageToActuallyLoad)) {
                pageToActuallyLoad = pageRequested;
            }
        }
        return pageToActuallyLoad;
    }

    public AlbumItemRecyclerViewAdapter getViewAdapter() {
        return viewAdapter;
    }

    protected void actionDeleteResourcesFromServerForever(final HashSet<Long> selectedItemIds, final HashSet<? extends ResourceItem> selectedItems) {
        String msg = getString(R.string.alert_confirm_really_delete_items_from_server);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_cancel, R.string.button_ok, new DeleteResourcesForeverAction<>(getUiHelper(), selectedItemIds, selectedItems));
    }

    @Override
    public void onResume() {
        super.onResume();

        if(tagListView == null) {
            //Resumed, but fragment initialisation cancelled for whatever reason.
            return;
        }

        adjustSortOrderAsNeeded();

        if (tagIsDirty) {
            reloadAlbumContent();
        } else {
            if(itemsToLoad.size() > 0) {
                onReloadAlbum();
            }
        }
    }

    private void adjustSortOrderAsNeeded() {
        if(tagModel.getImgResourceCount() < 0) {
            // don't change the order - will force a reload of all pages in right order if needs inverting.
            return;
        }
        boolean invertResourceSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, requireContext());
        boolean reversed;
        try {
            reversed = tagModel.setRetrieveResourcesInReverseOrder(invertResourceSortOrder);
            // need to refresh this page as the sort order was just flipped.
        } catch(IllegalStateException e) {
            tagModel.removeAllResources();
            reversed = tagModel.setRetrieveResourcesInReverseOrder(invertResourceSortOrder);
            tagIsDirty = true;
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

    private void displayControlsBasedOnSessionState() {
    }

    protected boolean isPreventItemSelection() {
        if (isAppInReadOnlyMode() || !PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
            return true;
        }
        return false;
    }

    private void updateBasketDisplay(Basket basket) {
        if (viewAdapter != null && viewAdapter.isMultiSelectionAllowed() && isPreventItemSelection()) {
            viewPrefs.withAllowMultiSelect(false);
            viewPrefs.setAllowItemSelection(false);
            DisplayUtils.runOnUiThread(()->viewAdapter.notifyDataSetChanged()); //TODO check this works (refresh the whole list, redrawing all with/without select box as appropriate)
        }

        int basketItemCount = basket.getItemCount();
        if (basketItemCount == 0) {
            basketView.setVisibility(GONE);
        } else {
            basketView.setVisibility(VISIBLE);
        }

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean isTagAlterationSupported = sessionDetails != null && (sessionDetails.isUseUserTagPluginForUpdate() || sessionDetails.isAdminUser());
        if(viewPrefs.isAllowItemSelection()) {
            bulkActionButtonDelete.show();
            if(isTagAlterationSupported) {
                bulkActionButtonUnTag.show();
            } else {
                bulkActionButtonUnTag.hide();
            }
        } else {
            bulkActionButtonDelete.hide();
            bulkActionButtonUnTag.hide();
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

    protected void onPiwigoResponseTagUpdate(PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse rsp) {
        if(rsp.hasError()) {
            String errorMsg = getString(R.string.error_updating_tag_pattern, rsp.getPiwigoResource().getName(), rsp.getError());
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, errorMsg);
            rsp.getPiwigoResource().getTags().add(currentTag); // removal failed, re-add this tag.
        } else {
            ResourceItem r = rsp.getPiwigoResource();
            onActionItemRemovedFromTag(r);
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
                    getParent().onPiwigoResponseGetResources((AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse) response);
                } else if (response instanceof BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse) {
                    getParent().onPiwigoResponseResourceInfoRetrieved((BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<?>) response);
                } else if(response instanceof ImageDeleteResponseHandler.PiwigoDeleteImageResponse) {
                    for(ResourceItem r : ((ImageDeleteResponseHandler.PiwigoDeleteImageResponse) response).getDeletedItems()) {
                        getParent().onActionItemRemovedFromServer(r);
                    }
                } else if (response instanceof BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse) {
                    ResourceItem r = ((BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse<?>) response).getPiwigoResource();
                    getParent().onActionItemRemovedFromTag(r);
                } else if (response instanceof PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse) {
                    getParent().onPiwigoResponseTagUpdate(((PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse) response));
                } else {
                    getParent().withUnexpectedPiwigoResponse(response);
                }
                getParent().getLoadingMessageIds().remove(response.getMessageId());
            }
        }
    }

    void onActionItemRemovedFromServer(ResourceItem r) {
        onActionItemRemovedFromTag(r);
        for(Long albumId : r.getLinkedAlbums()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(albumId, r.getId(), true));
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
                tagModel.acquirePageLoadLock();
                try {
                    tagModel.recordPageLoadFailed(response.getMessageId());
                } finally {
                    tagModel.releasePageLoadLock();
                }
            }
        }
    }

    protected void onActionItemRemovedFromTag(ResourceItem r) {
        int itemIdx = tagModel.getItemIdx(r);
        tagModel.remove(itemIdx);
        viewAdapter.notifyItemRemoved(itemIdx);
        if(bulkResourceActionData.removeProcessedResource(r)) {
            bulkResourceActionData = null;
        }
        for(Long albumId : r.getLinkedAlbums()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(albumId, r.getId(), true));
        }
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
//                          break;
//                    }
                } else {
                    Logging.log(Log.ERROR, getTag(), "User told tag page failed to load, but nothing to retry loading!");
                }
            }
        }
    }

    void onPiwigoResponseGetResources(final AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse response) {
        synchronized (this) {
            tagModel.getContainerDetails().setPhotoCount(response.getTotalResourceCount());
            tagModel.addItemPage(response.getPage(), response.getPageSize(), response.getResources());
            if (tagModel.isFullyLoaded() && tagModel.getItemCount() == 0) {
                emptyTagLabel.setText(R.string.favorites_empty_text);
                emptyTagLabel.setVisibility(VISIBLE);
            }
            viewAdapter.notifyDataSetChanged();
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

    private void onActionUnTagResources(final BulkResourceActionData bulkActionData) {
        if(!bulkActionData.isResourceInfoAvailable()){
            bulkResourceActionData.getResourcesInfoIfNeeded(this);
            return;
        }

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean allowTagEdit = !isAppInReadOnlyMode() && sessionDetails != null && sessionDetails.isUseUserTagPluginForUpdate();
        for (ResourceItem item : bulkActionData.getSelectedItems()) {
//            getUiHelper().showDetailedShortMsg(R.string.alert_information, "removing item from tag " + item.getName());
            item.getTags().remove(getCurrentTag());
            if (allowTagEdit) {
                getUiHelper().addActiveServiceCall(R.string.progress_untag_resources_pattern, new PluginUserTagsUpdateResourceTagsListResponseHandler<>(item));
            } else {
                getUiHelper().addActiveServiceCall(R.string.progress_untag_resources_pattern, new ImageUpdateInfoResponseHandler<>(item, true));
            }
        }
    }

    private void onActionDeleteResources(final BulkResourceActionData bulkActionData) {
        actionDeleteResourcesFromServerForever(bulkActionData.getSelectedItemIds(), bulkActionData.getSelectedItems());
    }

    protected void onPiwigoResponseResourceInfoRetrieved(BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<?> response) {
        if (bulkResourceActionData != null && bulkResourceActionData.isTrackingMessageId(response.getMessageId())) {
            this.bulkResourceActionData.updateLinkedAlbums(response.getResource());
            if (this.bulkResourceActionData.isResourceInfoAvailable()) {
                switch (bulkResourceActionData.getAction()) {
                    case BulkResourceActionData.ACTION_DELETE:
                        onActionDeleteResources(bulkResourceActionData);/*
                        break;
                    case BulkResourceActionData.ACTION_UPDATE_PERMISSIONS:
                        onUserActionUpdateImagePermissions(bulkResourceActionData);*/
                        break;
                    case BulkResourceActionData.ACTION_UNTAG:
                        onActionUnTagResources(bulkResourceActionData);
                }

            } else {
                // this will load the next batch of resource infos...
                bulkResourceActionData.getResourcesInfoIfNeeded(this);
            }
        }
    }

//FIXME this might need processing, but at the moment it causes itself to update and that isn't great. Maybe make the event clearer
    /*@Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumAlteredEvent albumAlteredEvent) {
        // This is needed because a slideshow item currently only fires these events (tag unaware).
        if (currentTag != null && currentTag.getId() == albumAlteredEvent.getAlbumAltered()) {
            tagIsDirty = true;
            if(isResumed()) {
                reloadAlbumContent();
            }
        }
    }*/

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(TagContentAlteredEvent event) {
        if (currentTag != null && currentTag.getId() == event.getId()) {
            tagIsDirty = true;
            if(isResumed()) {
                reloadAlbumContent();
            }
        }
    }

    private class TagViewAdapterListener<MSL extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter<MSL,LVA,VH,RC,T>,LVA extends AlbumItemRecyclerViewAdapter<LVA, T, MSL, VH, RC> , VH extends AlbumItemViewHolder<VH, LVA, T, MSL, RC>, RC extends ResourceContainer<?, T>, T extends GalleryItem> extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter<MSL,LVA,VH,RC,T> {

        @Override
        public void onMultiSelectStatusChanged(LVA adapter, boolean multiSelectEnabled) {
//            bulkActionsContainer.setVisibility(multiSelectEnabled?VISIBLE:GONE);
        }

        @Override
        public void onItemSelectionCountChanged(LVA adapter, int size) {
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
