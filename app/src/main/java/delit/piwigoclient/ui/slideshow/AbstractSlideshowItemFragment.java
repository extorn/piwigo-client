package delit.piwigoclient.ui.slideshow;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.view.recycler.MyFragmentRecyclerPagerAdapter;
import delit.libs.ui.view.slidingsheet.SlidingBottomSheet;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.video.RemoteAsyncFileCachingDataSource;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumThumbnailUpdatedResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageAlterRatingResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.dialogs.SelectAlbumDialog;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AlbumItemDeletedEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.DownloadFileRequestEvent;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.events.SlideshowSizeUpdateEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionFinishedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionStartedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.AlbumSelectionNeededEvent;
import delit.piwigoclient.ui.model.ViewModelContainer;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by gareth on 14/04/18.
 */

public abstract class AbstractSlideshowItemFragment<F extends AbstractSlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>,T extends ResourceItem> extends MyFragment<F,FUIH> implements MyFragmentRecyclerPagerAdapter.PagerItemFragment {

    private static final String TAG = "SlideshowItemFragment";

    private static final String ARG_GALLERY_TYPE = "containerModelType";
    private static final String ARG_GALLERY_ID = "containerId";
    private static final String ARG_GALLERY_ITEM_ID = "itemId";
    private static final String ARG_AND_STATE_ALBUM_ITEM_IDX = "albumItemIndex";
    private static final String ARG_AND_STATE_ALBUM_LOADED_RESOURCE_ITEM_COUNT = "albumLoadedResourceItemCount";
    private static final String ARG_AND_STATE_ALBUM_TOTAL_RESOURCE_ITEM_COUNT = "albumTotalResourceItemCount";
    private static final String STATE_UPDATED_LINKED_ALBUM_SET = "updatedLinkedAlbumSet";
    private static final String STATE_ALBUMS_REQUIRING_UPDATE = "albumsRequiringUpdate";
    private static final String STATE_EDITING_ITEM_DETAILS = "editingItemDetails";
    private static final String STATE_INFORMATION_SHOWING = "informationShowing";
    private static final String STATE_IS_PRIMARY_SLIDESHOW_ITEM = "isPrimarySlideshowItem";
    private static final String STATE_IS_ALLOW_DOWNLOAD = "allowDownload";

    private T model;
    private int albumItemIdx;
    private int albumLoadedItemCount;
    private long albumTotalItemCount;
    private HashSet<CategoryItemStub> updatedLinkedAlbumSet;
    private HashSet<Long> albumsRequiringReload;
    private boolean editingItemDetails;
    private boolean informationShowing;
    private boolean isPrimarySlideshowItem;
    private boolean allowDownload = true;

    private boolean doOnPageSelectedAndAddedRun; // reset to false with every object re-use and never tracked.

    private MaterialButton editButton;
    private TextView tagsField;
    private RatingBar averageRatingsBar;
    private ProgressBar progressIndicator;
    private RatingBar myRatingForThisResourceBar;
    private AppCompatSpinner privacyLevelSpinner;
    private EditText resourceDescriptionView;
    private EditText resourceNameView;
    private MaterialButton saveButton;
    private MaterialButton discardButton;
    private MaterialButton deleteButton;
    private MaterialButton copyButton;
    private MaterialButton moveButton;
    private MaterialButton downloadButton;
    private MaterialButton setAsAlbumThumbnail;
    private TextView linkedAlbumsField;
    private TextView itemPositionTextView;
    private SlidingBottomSheet bottomSheet;
    private View itemContent;
    private TextView resourceRatingScoreField;
    private ViewVisibleControl overlaysVisibilityControl;
    private TextView resourceTitleView;
    private TextView resourceDescTitleView;

    public static <S extends ResourceItem> Bundle buildArgs(Class<? extends ViewModelContainer> modelType, long albumId, long itemId, int albumItemIdx, int albumResourceItemCount, long totalResourceItemCount) {
        Bundle b = new Bundle();
        AbstractSlideshowFragment.storeGalleryModelClassToBundle(b, modelType);
        b.putLong(ARG_GALLERY_ID, albumId);
        b.putLong(ARG_GALLERY_ITEM_ID, itemId);
        b.putInt(ARG_AND_STATE_ALBUM_ITEM_IDX, albumItemIdx);
        b.putInt(ARG_AND_STATE_ALBUM_LOADED_RESOURCE_ITEM_COUNT, albumResourceItemCount);
        b.putLong(ARG_AND_STATE_ALBUM_TOTAL_RESOURCE_ITEM_COUNT, totalResourceItemCount);
        return b;
    }

    protected void saveExtraArgsToBundle(@NonNull Bundle b) {
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_EDITING_ITEM_DETAILS, editingItemDetails);
        outState.putBoolean(STATE_INFORMATION_SHOWING, informationShowing);
        outState.putBoolean(STATE_IS_ALLOW_DOWNLOAD, isAllowDownload());
        BundleUtils.putSet(outState, STATE_UPDATED_LINKED_ALBUM_SET, updatedLinkedAlbumSet);
        BundleUtils.putLongHashSet(outState, STATE_ALBUMS_REQUIRING_UPDATE, albumsRequiringReload);
        outState.putInt(ARG_AND_STATE_ALBUM_ITEM_IDX, albumItemIdx);
        outState.putInt(ARG_AND_STATE_ALBUM_LOADED_RESOURCE_ITEM_COUNT, albumLoadedItemCount);
        outState.putLong(ARG_AND_STATE_ALBUM_TOTAL_RESOURCE_ITEM_COUNT, albumTotalItemCount);
        outState.putBoolean(STATE_IS_PRIMARY_SLIDESHOW_ITEM, isPrimarySlideshowItem);
        saveExtraArgsToBundle(outState);
        if (BuildConfig.DEBUG) {
            BundleUtils.logSize("AbstractSlideshowItemFragment", outState);
        }
    }

    public boolean isAllowDownload() {
        return allowDownload;
    }

    public void setAllowDownload(boolean allowDownload) {
        this.allowDownload = allowDownload;
    }

    private void intialiseFields() {
        model = null;
        editingItemDetails = false;
        informationShowing = false;
        allowDownload = true;
        albumItemIdx = -1;
        albumLoadedItemCount = -1;
        albumTotalItemCount = -1;
        doOnPageSelectedAndAddedRun = false;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            long galleryItemId = args.getLong(ARG_GALLERY_ITEM_ID);
            if (model == null || (model != null && model.getId() != galleryItemId)) {
                intialiseFields(); // if we are opening this page for the first time with new data, wipe any old values.
                loadArgsFromBundle(args);
            }
        }
        // override page default values with any saved state
        restoreSavedInstanceState(savedInstanceState);

        setAsAlbumThumbnail.setOnClickListener(v -> {
            if (model.getParentId() != null) {
                onUseAsAlbumThumbnailForParent();
            } else {
                onUseAsAlbumThumbnailSelectAlbum();
            }
        });
        myRatingForThisResourceBar.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
            if (fromUser) {
                onAlterRating(model, rating);
            }
        });

        updateItemPositionText();
        setupImageDetailPopup(view, savedInstanceState);
        // updates the detail popup and main image rating
        processModelRatings(model);
        // show information panel if wanted.
        updateInformationShowingStatus();

        super.onViewCreated(view, savedInstanceState);

        configureItemContent(itemContent, model, savedInstanceState);

        if(model == null) {
            Logging.log(Log.ERROR, TAG, "Resource Item unavailable to process multimedia info from");
        } else if (model.isResourceDetailsLikelyOutdated() || model.isLikelyOutdated()) {
            // call this quietly in the background to avoid it ruining the slideshow experience.
            Set<String> multimediaExtensionList = ConnectionPreferences.getActiveProfile().getKnownMultimediaExtensions(prefs, requireContext());
            long messageId = new ImageGetInfoResponseHandler<>(model, multimediaExtensionList).invokeAsync(getContext());
            getUiHelper().addBackgroundServiceCall(messageId);
        }
    }

    private void loadArgsFromBundle(Bundle b) throws ModelUnavailableException {
        if (b == null) {
            return;
        }
        Class<? extends ViewModelContainer> galleryModelClass = AbstractSlideshowFragment.loadGalleryModelClassFromBundle(b);
        long galleryModelId = b.getLong(ARG_GALLERY_ID);
        ResourceContainer<?, T> modelStore = null;
        if (galleryModelClass != null) {
            ViewModelContainer viewModelContainer = new ViewModelProvider(requireActivity()).get("" + galleryModelId, galleryModelClass);
            modelStore = ((ResourceContainer<?, T>) viewModelContainer.getModel());
        }
        if (modelStore == null) {
            Bundle errBundle = new Bundle();
            String modelClassname = galleryModelClass == null ? null : galleryModelClass.getName();
            errBundle.putString("class", modelClassname);
            errBundle.putLong("modelId", galleryModelId);
            FirebaseAnalytics.getInstance(requireContext()).logEvent("modelNull", errBundle);
            Logging.log(Log.ERROR, TAG, String.format(Locale.UK, "slideshow model is null - %1$s(%2$d)", modelClassname, galleryModelId));
            throw new ModelUnavailableException();
        }
        long galleryItemId = b.getLong(ARG_GALLERY_ITEM_ID);
        try {
            model = modelStore.getItemById(galleryItemId);
        } catch (ClassCastException e) {
            Bundle errBundle = new Bundle();
            errBundle.putString("error", e.getMessage());
            errBundle.putString("galleryModelClass", galleryModelClass.getName());
            errBundle.putString("modelStoreClass", modelStore.getClass().getName());
            errBundle.putLong("galleryItemId", galleryItemId);
            errBundle.putLong("modelId", galleryModelId);
            errBundle.putLong("itemCount", modelStore.getItemCount());
            errBundle.putLong("indexOfItem", modelStore.getItemIdx(modelStore.getItemById(galleryItemId)));

            for (int i = 0; i < modelStore.getItemCount(); i++) {
                GalleryItem item = modelStore.getItemById(i);
                if (item.getId() == galleryItemId) {
                    errBundle.putInt("correctIndexOfItem", i);
                }
            }

            errBundle.putInt("sortOrder", modelStore.isRetrieveItemsInReverseOrder() ? 1 : 0);
            FirebaseAnalytics.getInstance(requireContext()).logEvent("modelClassWrong", null);
            String errMsg = String.format(Locale.UK, "slideshow model is wrong type - %1$s(%2$d)\n%3$s", galleryModelClass.getName(), galleryModelId, errBundle.toString());
            Logging.log(Log.ERROR, TAG, errMsg);
            ModelUnavailableException er = new ModelUnavailableException(errMsg, e);
            Logging.recordException(er);
            throw er;
        } catch (IllegalArgumentException e) {
            String errMsg = String.format(Locale.UK, "slideshow galleryItem could not be found in model - %1$s(%2$d)", galleryModelClass.getName(), galleryModelId);
            Logging.log(Log.ERROR, TAG, errMsg);
            ModelUnavailableException er = new ModelUnavailableException(errMsg, e);
            Logging.recordException(er);
        }
        albumItemIdx = b.getInt(ARG_AND_STATE_ALBUM_ITEM_IDX);
        albumLoadedItemCount = b.getInt(ARG_AND_STATE_ALBUM_LOADED_RESOURCE_ITEM_COUNT);
        albumTotalItemCount = b.getLong(ARG_AND_STATE_ALBUM_TOTAL_RESOURCE_ITEM_COUNT);
        loadExtraArgsFromBundle(b);
    }

    protected void loadExtraArgsFromBundle(@NonNull Bundle b) {
    }

    private void restoreSavedInstanceState(Bundle b) {
        if (b == null) {
            return;
        }
        editingItemDetails = b.getBoolean(STATE_EDITING_ITEM_DETAILS);
        informationShowing = b.getBoolean(STATE_INFORMATION_SHOWING);
        allowDownload = b.getBoolean(STATE_IS_ALLOW_DOWNLOAD);
        updatedLinkedAlbumSet = BundleUtils.getHashSet(b, STATE_UPDATED_LINKED_ALBUM_SET);
        albumsRequiringReload = BundleUtils.getLongHashSet(b, STATE_ALBUMS_REQUIRING_UPDATE);
        isPrimarySlideshowItem = b.getBoolean(STATE_IS_PRIMARY_SLIDESHOW_ITEM);

        albumItemIdx = b.getInt(ARG_AND_STATE_ALBUM_ITEM_IDX);
        albumLoadedItemCount = b.getInt(ARG_AND_STATE_ALBUM_LOADED_RESOURCE_ITEM_COUNT);
        albumTotalItemCount = b.getLong(ARG_AND_STATE_ALBUM_TOTAL_RESOURCE_ITEM_COUNT);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            loadArgsFromBundle(getArguments());
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        // need to do this here because text fields don't update correctly when set in onCreateView / onViewCreated
        if(model != null) {
            fillHeaderFields();
            fillResourceEditFields();
        }
    }

    private void fillHeaderFields() {
        resourceTitleView.setText(model.getName());
        String desc = model.getDescription();
        resourceDescTitleView.setText(PiwigoUtils.getSpannedHtmlText(desc));
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        View v = inflater.inflate(getLayoutId(), container, false);

        bottomSheet = v.findViewById(R.id.slideshow_image_bottom_sheet);
        bottomSheet.setOnInteractListener(new SlidingBottomSheet.OnInteractListener() {
            @Override
            public void onOpened() {
                getUiHelper().showUserHint(TAG, 1, R.string.hint_slideshow_item_view_1);
            }
            @Override
            public void onClosed() {
            }
        });

        itemPositionTextView = v.findViewById(R.id.slideshow_resource_item_x_of_y_text);
        progressIndicator = v.findViewById(R.id.slideshow_image_loadingIndicator);
        setAsAlbumThumbnail = v.findViewById(R.id.slideshow_resource_action_use_for_album_thumbnail);
        setAsAlbumThumbnail.setOnLongClickListener(v13 -> onUseAsAlbumThumbnailSelectAlbum());

        resourceTitleView = v.findViewById(R.id.slideshow_resource_item_title);
        resourceDescTitleView = v.findViewById(R.id.slideshow_resource_item_desc_title);
        resourceTitleView.setOnClickListener(v12 -> openInformationPanelIfAppropriate());
        resourceDescTitleView.setOnClickListener(v1 -> openInformationPanelIfAppropriate());
        v.findViewById(R.id.show_information_action_button).setOnClickListener(v12 -> openInformationPanelIfAppropriate());

        averageRatingsBar = v.findViewById(R.id.slideshow_image_average_ratingBar);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            LayerDrawable layerDrawable = (LayerDrawable) averageRatingsBar.getProgressDrawable();
            Drawable progressDrawable = layerDrawable.findDrawableByLayerId(android.R.id.progress).mutate();
            progressDrawable.setColorFilter(ContextCompat.getColor(requireContext(), R.color.rating_indicator), PorterDuff.Mode.SRC_IN);
        }
        myRatingForThisResourceBar = v.findViewById(R.id.slideshow_image_ratingBar);


        overlaysVisibilityControl = new ViewVisibleControl(v.findViewById(R.id.item_overlay_details_panel));
        overlaysVisibilityControl.setDelayMillis(AlbumViewPreferences.getAutoHideItemDetailDelayMillis(getPrefs(),requireContext()));
        overlaysVisibilityControl.setVisibility(View.VISIBLE);

        RelativeLayout itemContentLayout = v.findViewById(R.id.slideshow_item_content_layout);
        itemContent = createItemContent(inflater, itemContentLayout, savedInstanceState);

        if (itemContent != null) {
            // insert first to allow all others to be overlaid.
            int children = itemContentLayout.getChildCount();
            itemContentLayout.addView(itemContent, 0);
        }

        return v;
    }

    private void openInformationPanelIfAppropriate() {
        if(!bottomSheet.isEnabled()) {
            bottomSheet.setEnabled(true);
            bottomSheet.setVisibility(VISIBLE);
        }
        bottomSheet.open();
    }

    protected void addViewVisibleControl(View v) {
        overlaysVisibilityControl.addView(v);
    }

    public ViewVisibleControl getOverlaysVisibilityControl() {
        return overlaysVisibilityControl;
    }

    protected void doOnceOnPageSelectedAndAdded() {
        FragmentUIHelper uiHelper = getUiHelper();
        uiHelper.registerToActiveServiceCalls();
        uiHelper.setBlockDialogsFromShowing(false);
        uiHelper.handleAnyQueuedPiwigoMessages();
//        setTitleBar();
        overlaysVisibilityControl.setVisibility(View.VISIBLE);
        if (getParentFragment() != null) {
            getOverlaysVisibilityControl().runWithDelay(getParentFragment().getView());
        }
    }

    protected @LayoutRes
    int getLayoutId() {
        return R.layout.fragment_slideshow_item;
    }

    private void onUseAsAlbumThumbnailForParent() {
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_title_set_album_thumbnail, getString(R.string.alert_message_set_album_thumbnail), R.string.button_cancel, R.string.button_ok, new UseAsAlbumThumbnailForParentAction<>(getUiHelper()));
    }

    private boolean onUseAsAlbumThumbnailSelectAlbum() {
        // Invoke call to retrieve all album names (will show a dialog once this is done).
        addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId(), true));
        return true;
    }

    private void onAlterRating(T model, float rating) {
        AlbumItemActionStartedEvent event = new AlbumItemActionStartedEvent(model);
        getUiHelper().setTrackingRequest(event.getActionId());
        EventBus.getDefault().post(event);
        addActiveServiceCall(R.string.progress_resource_details_updating, new ImageAlterRatingResponseHandler(model, rating));
    }

    protected boolean isEditingItemDetails() {
        return editingItemDetails;
    }

    private void updateItemPositionText() {
        if (albumLoadedItemCount == 1 && albumItemIdx == albumLoadedItemCount && albumTotalItemCount == albumLoadedItemCount) {
            itemPositionTextView.setVisibility(GONE);
        } else {
            itemPositionTextView.setVisibility(VISIBLE);
            if(albumLoadedItemCount < albumTotalItemCount) {
                itemPositionTextView.setText(String.format(Locale.getDefault(), "%1$d/%2$d[%3$d]", albumItemIdx + 1, albumLoadedItemCount, albumTotalItemCount));
            } else {
                itemPositionTextView.setText(String.format(Locale.getDefault(), "%1$d/%2$d", albumItemIdx + 1, albumTotalItemCount));
            }
        }
    }

    protected SlidingBottomSheet getBottomSheet() {
        return bottomSheet;
    }

    private void updateInformationShowingStatus() {
        if (informationShowing) {
            bottomSheet.open();
        } else {
            bottomSheet.close();
        }
    }

    public void hideProgressIndicator() {
        progressIndicator.setVisibility(GONE);
    }

    public void showProgressIndicator() {
        progressIndicator.setVisibility(VISIBLE);
    }

    protected View createItemContent(LayoutInflater inflater, @NonNull final ViewGroup container, @Nullable Bundle savedInstanceState) {
        return null;
    }

    protected void configureItemContent(@Nullable View itemContentView, final T model, @Nullable Bundle savedInstanceState) {
    }

    protected View createCustomItemDetail(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState, T model) {
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        if(isPrimarySlideshowItem) {
            doOnPageSelectedAndAdded();
        }
    }

    protected boolean isPrimarySlideshowItem() {
        return isPrimarySlideshowItem;
    }

    protected void setupImageDetailPopup(View v, Bundle savedInstanceState) {

        resourceNameView = v.findViewById(R.id.slideshow_image_details_name);
        resourceDescriptionView = v.findViewById(R.id.slideshow_image_details_description);
        resourceRatingScoreField = v.findViewById(R.id.slideshow_image_details_rating_score);

        linkedAlbumsField = v.findViewById(R.id.slideshow_image_details_linked_albums);
        linkedAlbumsField.setOnClickListener(v1 -> showLinkedAlbumsView());

        tagsField = v.findViewById(R.id.slideshow_image_details_tags);

        privacyLevelSpinner = v.findViewById(R.id.privacy_level);
// Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> privacyLevelOptionsAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.privacy_levels_groups_array, android.R.layout.simple_spinner_item);
// Specify the layout to use when the list of choices appears
        privacyLevelOptionsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
// Apply the adapter to the spinner
        privacyLevelSpinner.setAdapter(privacyLevelOptionsAdapter);

        saveButton = v.findViewById(R.id.slideshow_resource_action_save_button);
        saveButton.setOnClickListener(v14 -> onSaveChangesButtonClicked());
        discardButton = v.findViewById(R.id.slideshow_resource_action_discard_button);
        discardButton.setOnClickListener(v12 -> onDiscardChanges());

        editButton = v.findViewById(R.id.slideshow_resource_action_edit_button);
        getEditButton().setOnClickListener(v13 -> {
            toggleEditingItemDetails();
            fillResourceEditFields();
        });


        downloadButton = v.findViewById(R.id.slideshow_resource_action_download);
        downloadButton.setOnClickListener(v15 -> onDownloadItem(model));
        deleteButton = v.findViewById(R.id.slideshow_resource_action_delete);
        deleteButton.setOnClickListener(v18 -> onDeleteItem(model));
        moveButton = v.findViewById(R.id.slideshow_resource_action_move);
        moveButton.setOnClickListener(v16 -> onMoveItem(model));
        copyButton = v.findViewById(R.id.slideshow_resource_action_copy);
        copyButton.setOnClickListener(v17 -> onCopyItem(model));
    }

    private void showLinkedAlbumsView() {
        HashSet<Long> currentSelection = PiwigoUtils.toSetOfIds(updatedLinkedAlbumSet);
        if (currentSelection == null) {
            currentSelection = new HashSet<>(model.getLinkedAlbums());
        }

        boolean allowFullEdit = !isAppInReadOnlyMode() && PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile());

        AlbumSelectionNeededEvent albumSelectEvent = new AlbumSelectionNeededEvent(true, allowFullEdit && editingItemDetails, currentSelection);
        getUiHelper().setTrackingRequest(albumSelectEvent.getActionId());
        EventBus.getDefault().post(albumSelectEvent);
    }

    protected void onSaveChangesButtonClicked() {
        updateModelFromFields();
        if(getUiHelper().getActiveServiceCallCount() > 0) {
            getUiHelper().showDetailedMsg(R.string.alert_information, R.string.alert_server_call_in_progress_please_wait);
            return;
        }
        onSaveModelChanges(model);
    }

    protected abstract void onSaveModelChanges(T model);

    protected void updateModelFromFields() {
        model.setName(resourceNameView.getText().toString());
        model.setDescription(resourceDescriptionView.getText().toString());
        model.setPrivacyLevel(getPrivacyLevelValue());
        if (updatedLinkedAlbumSet != null) {
            model.setLinkedAlbums(PiwigoUtils.toSetOfIds(updatedLinkedAlbumSet));
        }
    }

    protected void onDiscardChanges() {
        toggleEditingItemDetails();
        updatedLinkedAlbumSet = null;
        fillResourceEditFields();
    }

    protected void toggleEditingItemDetails() {
        editingItemDetails = !editingItemDetails;
    }

    protected void setControlVisible(View v, boolean visible) {
        v.setVisibility(visible ? VISIBLE : GONE);
    }

    public void displayItemDetailsControlsBasedOnSessionState() {

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean allowTagEdit = !isAppInReadOnlyMode() && sessionDetails != null && sessionDetails.isLoggedIn() && sessionDetails.isUseUserTagPluginForUpdate();
        boolean allowFullEdit = !isAppInReadOnlyMode() && sessionDetails != null && sessionDetails.isAdminUser();

        setControlVisible(saveButton, allowFullEdit || allowTagEdit);
        setControlVisible(discardButton, allowFullEdit || allowTagEdit);
        setControlVisible(getEditButton(), allowFullEdit || allowTagEdit);
        setControlVisible(deleteButton, allowFullEdit);
        //TODO make visible once functionality written.
        setControlVisible(copyButton, false);
        setControlVisible(moveButton, false);

        myRatingForThisResourceBar.setEnabled(!isAppInReadOnlyMode());
        downloadButton.setEnabled(allowDownload);
    }

    private void fillResourceEditFields() {

        if (model.getName() == null) {
            resourceNameView.setText("");
        } else {
            resourceNameView.setText(model.getName());
            resourceNameView.invalidate();
        }

        if (model.getDescription() == null) {
            resourceDescriptionView.setText("");
        } else {
            resourceDescriptionView.setText(model.getDescription());
        }

        resourceRatingScoreField.setText(getString(R.string.rating_score_pattern, model.getScore(), model.getRatingsGiven()));

        populateResourceExtraFields();

        displayItemDetailsControlsBasedOnSessionState();
        setEditItemDetailsControlsStatus();

    }

    protected void setEditItemDetailsControlsStatus() {

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean allowTagEdit = !isAppInReadOnlyMode() && sessionDetails != null && sessionDetails.isUseUserTagPluginForUpdate();
        boolean allowFullEdit = !isAppInReadOnlyMode() && sessionDetails != null && sessionDetails.isAdminUser();

        resourceNameView.setEnabled(allowFullEdit && editingItemDetails);
        resourceDescriptionView.setEnabled(allowFullEdit && editingItemDetails);
        privacyLevelSpinner.setEnabled(allowFullEdit && editingItemDetails);

        setControlVisible(setAsAlbumThumbnail, allowFullEdit && !editingItemDetails);
        setControlVisible(getEditButton(), (allowTagEdit || allowFullEdit) && !editingItemDetails);
        setControlVisible(saveButton, editingItemDetails);
        saveButton.setEnabled(editingItemDetails);
        setControlVisible(discardButton, editingItemDetails);
        discardButton.setEnabled(editingItemDetails);
    }

    private byte getPrivacyLevelValue() {
        int selectedIdx = privacyLevelSpinner.getSelectedItemPosition();
        return (byte) requireContext().getResources().getIntArray(R.array.privacy_levels_values_array)[selectedIdx];
    }

    private int getPrivacyLevelIndexPositionFromValue(int value) {

        int[] values = getResources().getIntArray(R.array.privacy_levels_values_array);
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private void onMoveItem(T model) {
        //TODO implement this
        getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_error_unimplemented));
    }

    private void onCopyItem(T model) {
        //TODO implement this
        getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_error_unimplemented));
    }

    protected void onDownloadItem(T model) {
        AlbumItemActionStartedEvent event = new AlbumItemActionStartedEvent(model);
        getUiHelper().setTrackingRequest(event.getActionId());
        EventBus.getDefault().post(event);
    }

    void onGetSubAlbumNames(AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse response) {
        Activity activity = getActivity();
        ArrayList<CategoryItemStub> albumNames = response.getAlbumNames();
        if (albumNames == null || albumNames.isEmpty()) {
            // should never occur, but to be sure...
            return;
        }

        Long defaultAlbumSelectionId = model.getParentId();
        if (defaultAlbumSelectionId == null) {
            if (BuildConfig.DEBUG) {
                Log.e(getTag(), "ERROR: No parent id available for resource!");
            }
            defaultAlbumSelectionId = albumNames.get(0).getId();
        }
        final SelectAlbumDialog dialogFact = new SelectAlbumDialog(activity, defaultAlbumSelectionId);
        AlertDialog dialog = dialogFact.buildDialog(albumNames, CategoryItem.ROOT_ALBUM, (dialog1, which) -> {
            long selectedAlbumId = dialogFact.getSelectedAlbumId();
            Long selectedAlbumParentId = dialogFact.getSelectedAlbumParentId();
            addActiveServiceCall(R.string.progress_resource_details_updating, new AlbumThumbnailUpdatedResponseHandler(selectedAlbumId, selectedAlbumParentId, model.getId()));
        });
        dialog.show();
    }

    private void onDeleteItem(final T model) {
        String message = getString(R.string.alert_confirm_really_delete_from_server);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new OnDeleteItemAction<>(getUiHelper()));
    }

    protected MaterialButton getEditButton() {
        return editButton;
    }

    protected TextView getTagsField() {
        return tagsField;
    }

    private static class UseAsAlbumThumbnailForParentAction<F extends AbstractSlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>,T extends ResourceItem> extends UIHelper.QuestionResultAdapter<FUIH,F> implements Parcelable {

        public UseAsAlbumThumbnailForParentAction(FUIH uiHelper) {
            super(uiHelper);
        }

        protected UseAsAlbumThumbnailForParentAction(Parcel in) {
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

        public static final Creator<UseAsAlbumThumbnailForParentAction> CREATOR = new Creator<UseAsAlbumThumbnailForParentAction>() {
            @Override
            public UseAsAlbumThumbnailForParentAction createFromParcel(Parcel in) {
                return new UseAsAlbumThumbnailForParentAction<>(in);
            }

            @Override
            public UseAsAlbumThumbnailForParentAction[] newArray(int size) {
                return new UseAsAlbumThumbnailForParentAction[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                F parent = getUiHelper().getParent();
                ResourceItem model = parent.getModel();
                long albumId = model.getParentId();
                Long albumParentId = model.getParentageChain().size() > 1 ? model.getParentageChain().get(model.getParentageChain().size() - 2) : null;
                getUiHelper().addActiveServiceCall(R.string.progress_resource_details_updating, new AlbumThumbnailUpdatedResponseHandler(albumId, albumParentId, model.getId()));
            }
        }
    }

    public T getModel() {
        return model;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    @Override
    public void updatePageTitle() {
        // Do nothing ( called in resume).
    }

    @Override
    public void onPageSelected() {
        if(isPrimarySlideshowItem) {
            return;
        }
        isPrimarySlideshowItem = true;
        if (isAdded()) {
            doOnPageSelectedAndAdded();
        }
    }

    protected void doOnPageSelectedAndAdded() {
        if(!doOnPageSelectedAndAddedRun) {
            doOnPageSelectedAndAddedRun = true;
            doOnceOnPageSelectedAndAdded();
        }
    }

    public static class ViewVisibleControl implements Runnable {

        private static final long DEFAULT_DELAY_MILLIS = 2000;
        private long delayMillis = DEFAULT_DELAY_MILLIS;
        private List<View> views;
        private int visibilityOnRun = View.INVISIBLE;
        private long timerStarted;

        public ViewVisibleControl(View... views) {
            this.views = new ArrayList<>(Arrays.asList(views));
        }

        public void setDelayMillis(long delayMillis) {
            this.delayMillis = delayMillis;
        }

        public void setVisibilityOnRun(int visibility) {
            this.visibilityOnRun = visibility;
        }

        private void setVisibility(int visibility) {
            for (View v : views) {
                v.setVisibility(visibility);
            }
        }

        @Override
        public synchronized void run() {
            if (timerStarted + delayMillis - System.currentTimeMillis() > 0) {
                // another trigger has been added.
                return;
            }
            setVisibility(visibilityOnRun);
        }

        public synchronized void runWithDelay(View v) {
            if (v != null) {
                timerStarted = System.currentTimeMillis();
                setVisibility(visibilityOnRun == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
                v.postDelayed(this, delayMillis);
            }
        }

        public synchronized void addView(View v) {
            views.add(v);
            v.setVisibility(visibilityOnRun == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
        }

    }

    private void setTitleBar() {
        /*ToolbarEvent event = new ToolbarEvent(getActivity());
        event.setTitle(model.getName());
        EventBus.getDefault().post(event);*/
    }

    @Override
    public void onPageDeselected() {
        if(!isPrimarySlideshowItem) {
            return;
        }
        doOnPageSelectedAndAddedRun = false;
                // pick up responses when the page is visible again.
        isPrimarySlideshowItem = false;
        FragmentUIHelper uiHelper = getUiHelper();
        if(uiHelper != null) {
            uiHelper.setBlockDialogsFromShowing(true);
            uiHelper.deregisterFromActiveServiceCalls();
        }
    }

    void onAlbumThumbnailUpdated(AlbumThumbnailUpdatedResponseHandler.PiwigoAlbumThumbnailUpdatedResponse response) {
        EventBus.getDefault().post(new AlbumAlteredEvent(response.getAlbumParentIdAltered(), response.getAlbumIdAltered()));
    }

    void onResourceInfoRetrieved(final BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<T> response) {
        model = response.getResource();
        populateResourceExtraFields();
    }

    void processModelRatings(ResourceItem resource) {
        if(resource == null) {
            Logging.log(Log.ERROR, TAG, "Resource Item unavailable to process model ratings from");
            return;
        }
        if (resource.getRatingsGiven() > 0) {
            averageRatingsBar.setRating(resource.getAverageRating());
            averageRatingsBar.setVisibility(VISIBLE);
        } else {
            averageRatingsBar.setVisibility(View.INVISIBLE);
        }
        resourceRatingScoreField.setText(getString(R.string.rating_score_pattern, model.getScore(), model.getRatingsGiven()));

        myRatingForThisResourceBar.setRating(resource.getMyRating());
        DrawableCompat.setTint(myRatingForThisResourceBar.getProgressDrawable(), ContextCompat.getColor(requireContext(), R.color.app_secondary));
    }

    protected void onImageDeleted(HashSet<? extends ResourceItem> deletedItems) {
        List<Long> resourceItemParentChain = model.getParentageChain();
        EventBus.getDefault().post(new AlbumItemDeletedEvent<>(model, albumItemIdx, albumLoadedItemCount));
        for (int i = 0; i < resourceItemParentChain.size() - 1; i++) {
            // update all albums except the direct parent of the resource deleted
            EventBus.getDefault().post(new AlbumAlteredEvent(resourceItemParentChain.get(i), resourceItemParentChain.get(i+1)));
        }
    }

    private static class OnDeleteItemAction<F extends AbstractSlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends UIHelper.QuestionResultAdapter<FUIH,F> implements Parcelable {

        public OnDeleteItemAction(FUIH uiHelper) {
            super(uiHelper);
        }

        protected OnDeleteItemAction(Parcel in) {
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

        public static final Creator<OnDeleteItemAction> CREATOR = new Creator<OnDeleteItemAction>() {
            @Override
            public OnDeleteItemAction createFromParcel(Parcel in) {
                return new OnDeleteItemAction<>(in);
            }

            @Override
            public OnDeleteItemAction[] newArray(int size) {
                return new OnDeleteItemAction[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                F fragment = getUiHelper().getParent();
                ResourceItem model = fragment.getModel();
                AlbumItemActionStartedEvent event = new AlbumItemActionStartedEvent(model);
                getUiHelper().setTrackingRequest(event.getActionId());
                EventBus.getDefault().post(event);
                getUiHelper().addActiveServiceCall(R.string.progress_delete_resource, new ImageDeleteResponseHandler<>(model));
            }
        }
    }

    protected void populateResourceExtraFields() {

        privacyLevelSpinner.setSelection(getPrivacyLevelIndexPositionFromValue(model.getPrivacyLevel()));

        HashSet<?> currentLinkedAlbumsSet = updatedLinkedAlbumSet != null ? updatedLinkedAlbumSet : model.getLinkedAlbums();
        setLinkedAlbumFieldText(linkedAlbumsField, currentLinkedAlbumsSet);
    }

    protected String getDisplayText(Object itemValue) {
        if(itemValue instanceof String) {
            return itemValue.toString();
        }
        if(itemValue instanceof CategoryItemStub) {
            return ((CategoryItemStub) itemValue).getName();
        }
        return null;
    }

    public void setLinkedAlbumFieldText(TextView textView, HashSet<?> linkedSelectedItems) {
        if(linkedSelectedItems == null) {
            textView.setText(getString(R.string.click_to_view));
        } else {
            Iterator<?> itemIter = linkedSelectedItems.iterator();

            Object itemValue = itemIter.hasNext()?itemIter.next():null;
            if(getDisplayText(itemValue) != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(linkedSelectedItems.size());
                sb.append(" - ");
                do {
                    sb.append(getDisplayText(itemValue));
                    itemValue = null;
                    if (itemIter.hasNext()) {
                        sb.append(", ");
                        itemValue = itemIter.next();
                    }
                } while (itemValue != null);
                textView.setText(sb.toString());
            } else {
                textView.setText(getString(R.string.click_to_view_pattern, linkedSelectedItems.size()));
            }
        }
    }

    protected void onResourceInfoAltered(final T resourceItem) {

        getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.resource_details_updated_message));

        model = resourceItem;
        if (editingItemDetails) {
            editingItemDetails = false;
            fillResourceEditFields();
            fillHeaderFields();
        }
        if (albumsRequiringReload != null) {
            // ensure all necessary albums are updated.
            List<Long> notifiedAlbums = new ArrayList<>(albumsRequiringReload.size() + model.getParentageChain().size());
            for (Long albumAltered : albumsRequiringReload) {
                if(!notifiedAlbums.contains(albumAltered)) {
                    notifiedAlbums.add(albumAltered);
                    EventBus.getDefault().post(new AlbumAlteredEvent(albumAltered, resourceItem.getId()));
                    List<Long> parentageChain = model.getParentageChain();
                    int parentChainIdx = parentageChain.indexOf(albumAltered);
                    if (parentChainIdx >= 0) {
                        for(int i = parentChainIdx + 1; i < parentageChain.size(); i++) {
                            long notifyAlbum = parentageChain.get(parentChainIdx);
                            if(!notifiedAlbums.contains(notifyAlbum)) {
                                notifiedAlbums.add(notifyAlbum);
                                EventBus.getDefault().post(new AlbumAlteredEvent(notifyAlbum, parentageChain.get(parentChainIdx - 1)));
                            }
                        }
                    }
                }
            }
            notifiedAlbums.clear();
            albumsRequiringReload.clear();
            albumsRequiringReload = null;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PiwigoLoginSuccessEvent event) {
        displayItemDetailsControlsBasedOnSessionState();
        setEditItemDetailsControlsStatus();
    }

    public void onPagerIndexChangedTo(int newPagerIndex) {
        albumItemIdx -= 1;
        updateItemPositionText();
    }

    @Override
    public int getPagerIndex() {
        return albumItemIdx;
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppLockedEvent event) {
        if (editingItemDetails) {
            discardButton.callOnClick();
        } else {
            displayItemDetailsControlsBasedOnSessionState();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(SlideshowSizeUpdateEvent event) {
        this.albumLoadedItemCount = event.getLoadedResources();
        this.albumTotalItemCount = event.getTotalResources();
        updateItemPositionText();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppUnlockedEvent event) {
        displayItemDetailsControlsBasedOnSessionState();
        setEditItemDetailsControlsStatus();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumSelectionCompleteEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            if (event.getCurrentSelection().size() != event.getSelectedItems().size()) {
                // action was cancelled due to inability to load the albums
                return;
            }
            updatedLinkedAlbumSet = event.getSelectedItems();
            setLinkedAlbumFieldText(linkedAlbumsField, updatedLinkedAlbumSet);

            // see what has changed and alter fields as needed.
            // Update the linked album list if required.
            HashSet<Long> currentAlbums = model.getLinkedAlbums();
            HashSet<Long> newAlbums = event.getCurrentSelection();

            // check the original and new set aren't the same before updating the server.
            HashSet<Long> albumsResourceRemovedFrom = new HashSet<>(currentAlbums);
            albumsResourceRemovedFrom.removeAll(newAlbums); // to give those status altered in the old set (removed).
            HashSet<Long> albumsResourceAddedTo = new HashSet<>(newAlbums);
            albumsResourceAddedTo.removeAll(currentAlbums); // to give those status altered in the new set (added).
            HashSet<Long> changedAlbums = new HashSet<>();
            changedAlbums.addAll(albumsResourceAddedTo);
            changedAlbums.addAll(albumsResourceRemovedFrom);

            if (changedAlbums.size() == 0) {
                // no changes
                updatedLinkedAlbumSet = null;
            } else {
                if (albumsRequiringReload == null) {
                    albumsRequiringReload = new HashSet<>();
                } else {
                    albumsRequiringReload.clear();
                }
                // add all the changed albums
                albumsRequiringReload.addAll(changedAlbums);
            }
        }

    }

    public final void onGalleryItemActionFinished() {
        getUiHelper().hideProgressIndicator();
        EventBus.getDefault().post(new AlbumItemActionFinishedEvent(getUiHelper().getTrackedRequest(), model));
    }

    protected static class CustomPiwigoResponseListener<F extends AbstractSlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends BasicPiwigoResponseListener<FUIH,F> {

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            EventBus.getDefault().post(new PiwigoSessionTokenUseNotificationEvent(PiwigoSessionDetails.getActiveSessionToken(ConnectionPreferences.getActiveProfile())));
        }


        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {

            if (response instanceof ImageAlterRatingResponseHandler.PiwigoRatingAlteredResponse) {
                getParent().processModelRatings(((ImageAlterRatingResponseHandler.PiwigoRatingAlteredResponse) response).getPiwigoResource());
            } else if (response instanceof ImageDeleteResponseHandler.PiwigoDeleteImageResponse) {
                getParent().onImageDeleted(((ImageDeleteResponseHandler.PiwigoDeleteImageResponse) response).getDeletedItems());
            } else if (response instanceof BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse) {
                getParent().onResourceInfoRetrieved((BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<T>) response);
            } else if (response instanceof BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse) {
                BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse<T> r = ((BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse<T>) response);
                getParent().onResourceInfoAltered(r.getPiwigoResource());
            } else if (response instanceof AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) {
                getParent().onGetSubAlbumNames((AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else if (response instanceof AlbumThumbnailUpdatedResponseHandler.PiwigoAlbumThumbnailUpdatedResponse) {
                getParent().onAlbumThumbnailUpdated((AlbumThumbnailUpdatedResponseHandler.PiwigoAlbumThumbnailUpdatedResponse) response);
            }
            getParent().onGalleryItemActionFinished();
        }
    }

    public static class BaseDownloadQuestionResult<F extends MyFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends UIHelper.QuestionResultAdapter<FUIH,F> implements Parcelable {

        public BaseDownloadQuestionResult(FUIH uiHelper) {
            super(uiHelper);
        }

        protected BaseDownloadQuestionResult(Parcel in) {
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

        public static final Creator<BaseDownloadQuestionResult> CREATOR = new Creator<BaseDownloadQuestionResult>() {
            @Override
            public BaseDownloadQuestionResult createFromParcel(Parcel in) {
                return new BaseDownloadQuestionResult(in);
            }

            @Override
            public BaseDownloadQuestionResult[] newArray(int size) {
                return new BaseDownloadQuestionResult[size];
            }
        };

        public void doDownloadAction(Set<ResourceItem> items, String selectedPiwigoFilesizeName, boolean shareWithOtherAppsAfterDownload) {
            Iterator<ResourceItem> itemsIterator = items.iterator();
            DownloadFileRequestEvent evt = new DownloadFileRequestEvent(shareWithOtherAppsAfterDownload);
            while(itemsIterator.hasNext()) {
                ResourceItem item = itemsIterator.next();
                if (item instanceof VideoResourceItem) {
                    File localCache = RemoteAsyncFileCachingDataSource.getFullyLoadedCacheFile(getContext(), Uri.parse(item.getFileUrl(item.getFullSizeFile().getName())));
                    if (localCache != null) {
                        String downloadFilename = item.getDownloadFileName(item.getFullSizeFile());
                        String remoteUri = item.getFileUrl(item.getFullSizeFile().getName());
                        evt.addFileDetail(item.getName(), remoteUri, downloadFilename, Uri.fromFile(localCache));
                    }
                } else {
                    String downloadFilename = item.getDownloadFileName(item.getFile(selectedPiwigoFilesizeName));
                    String remoteUri = item.getFileUrl(selectedPiwigoFilesizeName);
                    evt.addFileDetail(item.getName(), remoteUri, downloadFilename);
                }
            }
            EventBus.getDefault().post(evt);
            if(items.size() == 1) {
                EventBus.getDefault().post(new AlbumItemActionFinishedEvent(getUiHelper().getTrackedRequest(), items.iterator().next()));
            }
        }
    }

    public static class SelectionContainsUnsuitableFilesQuestionResult<F extends MyFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends BaseDownloadQuestionResult<F,FUIH,T> {

        private final Set<ResourceItem> items;
        private final String selectedPiwigoFilesizeName;

        public SelectionContainsUnsuitableFilesQuestionResult(FUIH uiHelper, Set<ResourceItem> items, String selectedPiwigoFilesizeName) {
            super(uiHelper);
            this.items = items;
            this.selectedPiwigoFilesizeName = selectedPiwigoFilesizeName;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            doDownloadAction(items, selectedPiwigoFilesizeName, false);
        }
    }

}