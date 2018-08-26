package delit.piwigoclient.ui.slideshow;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.wunderlist.slidinglayer.SlidingLayer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumThumbnailUpdatedResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageAlterRatingResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.common.list.recycler.MyFragmentRecyclerPagerAdapter;
import delit.piwigoclient.ui.dialogs.SelectAlbumDialog;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AlbumItemDeletedEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.CancelDownloadEvent;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.events.SlideshowSizeUpdateEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionFinishedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionStartedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.AlbumSelectionNeededEvent;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by gareth on 14/04/18.
 */

public abstract class AbstractSlideshowItemFragment<T extends ResourceItem> extends MyFragment implements MyFragmentRecyclerPagerAdapter.PagerItemFragment {

    private static final String ARG_GALLERY_ITEM = "galleryItem";
    private static final String ARG_ALBUM_ITEM_IDX = "albumItemIndex";
    private static final String ARG_ALBUM_LOADED_RESOURCE_ITEM_COUNT = "albumLoadedResourceItemCount";
    private static final String ARG_ALBUM_TOTAL_RESOURCE_ITEM_COUNT = "albumTotalResourceItemCount";
    private static final String TAG = "SlideshowItemFragment";
    private static final String STATE_UPDATED_LINKED_ALBUM_SET = "updatedLinkedAlbumSet";
    private static final String STATE_ALBUMS_REQUIRING_UPDATE = "albumsRequiringUpdate";
    private static final String STATE_EDITING_ITEM_DETAILS = "editingItemDetails";
    private static final String STATE_INFORMATION_SHOWING = "informationShowing";
    private static final String ALLOW_DOWNLOAD = "allowDownload";
    protected ImageButton editButton;
    protected TextView tagsField;
    private T model;
    private RatingBar averageRatingsBar;
    private ProgressBar progressIndicator;
    private RatingBar ratingsBar;
    private Spinner privacyLevelSpinner;
    private EditText resourceDescriptionView;
    private EditText resourceNameView;
    private ImageButton saveButton;
    private ImageButton discardButton;
    private ImageButton deleteButton;
    private ImageButton copyButton;
    private ImageButton moveButton;
    private boolean editingItemDetails;
    private boolean informationShowing;
    private RatingBar ratingBar;
    private ImageButton downloadButton;
    private boolean allowDownload = true;
    private Long activeDownloadActionId;
    private CustomImageButton setAsAlbumThumbnail;
    private TextView linkedAlbumsField;
    private HashSet<Long> updatedLinkedAlbumSet;
    private HashSet<Long> albumsRequiringReload;
    private int albumItemIdx;
    private int albumLoadedItemCount;
    private TextView itemPositionTextView;
    private long albumTotalItemCount;
    private SlidingLayer bottomSheet;
    private View itemContent;

    public static <S extends ResourceItem> Bundle buildArgs(S model, int albumResourceItemIdx, int albumResourceItemCount, long totalResourceItemCount) {
        Bundle b = new Bundle();
        b.putSerializable(ARG_GALLERY_ITEM, model);
        b.putInt(ARG_ALBUM_ITEM_IDX, albumResourceItemIdx);
        b.putInt(ARG_ALBUM_LOADED_RESOURCE_ITEM_COUNT, albumResourceItemCount);
        b.putLong(ARG_ALBUM_TOTAL_RESOURCE_ITEM_COUNT, totalResourceItemCount);
        return b;
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
    }

    @Override
    protected void doInOnCreateView() {
        // Do nothing (don't want to register for service calls. we'll do that as the fragment is displayed).
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_EDITING_ITEM_DETAILS, editingItemDetails);
        outState.putBoolean(STATE_INFORMATION_SHOWING, informationShowing);
        outState.putSerializable(ARG_GALLERY_ITEM, model);
        outState.putBoolean(ALLOW_DOWNLOAD, isAllowDownload());
        outState.putSerializable(STATE_UPDATED_LINKED_ALBUM_SET, updatedLinkedAlbumSet);
        outState.putSerializable(STATE_ALBUMS_REQUIRING_UPDATE, albumsRequiringReload);
        outState.putInt(ARG_ALBUM_ITEM_IDX, albumItemIdx);
        outState.putInt(ARG_ALBUM_LOADED_RESOURCE_ITEM_COUNT, albumLoadedItemCount);
        outState.putLong(ARG_ALBUM_TOTAL_RESOURCE_ITEM_COUNT, albumTotalItemCount);
    }

    public void addDownloadAction(long activeDownloadActionId) {
        this.activeDownloadActionId = activeDownloadActionId;
        getUiHelper().addBackgroundServiceCall(activeDownloadActionId);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            intialiseFields();
            model = (T) getArguments().getSerializable(ARG_GALLERY_ITEM);
            albumItemIdx = getArguments().getInt(ARG_ALBUM_ITEM_IDX);
            albumLoadedItemCount = getArguments().getInt(ARG_ALBUM_LOADED_RESOURCE_ITEM_COUNT);
            albumTotalItemCount = getArguments().getLong(ARG_ALBUM_TOTAL_RESOURCE_ITEM_COUNT);
            setArguments(null);
        }
        if (savedInstanceState != null) {
            //restore saved state
            editingItemDetails = savedInstanceState.getBoolean(STATE_EDITING_ITEM_DETAILS);
            informationShowing = savedInstanceState.getBoolean(STATE_INFORMATION_SHOWING);
            allowDownload = savedInstanceState.getBoolean(ALLOW_DOWNLOAD);
            updatedLinkedAlbumSet = (HashSet<Long>) savedInstanceState.getSerializable(STATE_UPDATED_LINKED_ALBUM_SET);
            albumsRequiringReload = (HashSet<Long>) savedInstanceState.getSerializable(STATE_ALBUMS_REQUIRING_UPDATE);
        }

        boolean useDarkMode = prefs.getBoolean(getString(R.string.preference_gallery_use_dark_mode_key), false);
        if (useDarkMode) {
            view.setBackgroundColor(Color.BLACK);
            itemPositionTextView.setBackgroundColor(getResources().getColor(R.color.black_overlay));
            itemPositionTextView.setTextColor(Color.WHITE);
        } else {
            view.setBackgroundColor(Color.WHITE);
            itemPositionTextView.setBackgroundColor(getResources().getColor(R.color.white_overlay));
            itemPositionTextView.setTextColor(Color.BLACK);
        }
        setAsAlbumThumbnail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (model.getParentId() != null) {
                    onUseAsAlbumThumbnailForParent();
                } else {
                    onUseAsAlbumThumbnailSelectAlbum();
                }
            }
        });
        ratingsBar.setRating(model.getYourRating());
        ratingsBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
                if (fromUser) {
                    onAlterRating(model, rating);
                }
            }
        });

        updateItemPositionText();
        onRatingAltered(model);
        setupImageDetailPopup(view, savedInstanceState);
        // show information panel if wanted.
        updateInformationShowingStatus();

        configureItemContent(itemContent, model, savedInstanceState);

        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState == null) {
            // call this quietly in the background to avoid it ruining the slideshow experience.
            String multimediaExtensionList = prefs.getString(getString(R.string.preference_piwigo_playable_media_extensions_key), getString(R.string.preference_piwigo_playable_media_extensions_default));
            long messageId = new ImageGetInfoResponseHandler(model, multimediaExtensionList).invokeAsync(getContext());
            getUiHelper().addBackgroundServiceCall(messageId);
//            if(proactivelyDownloadResourceInfo) {
//                EventBus.getDefault().post(new AlbumItemActionStartedEvent(model));
//                addActiveServiceCall(R.string.progress_loading_resource_details, PiwigoAccessService.startActionGetResourceInfo(model, getContext()));
//            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // need to do this here because text fields don't update correctly when set in onCreateView / onViewCreated
        fillResourceEditFields();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = getView();
        if (v != null) {
            return v;
        }
        super.onCreateView(inflater, container, savedInstanceState);

        v = inflater.inflate(getLayoutId(), container, false);

        itemPositionTextView = v.findViewById(R.id.slideshow_resource_item_x_of_y_text);
        progressIndicator = v.findViewById(R.id.slideshow_image_loadingIndicator);
        setAsAlbumThumbnail = v.findViewById(R.id.slideshow_resource_action_use_for_album_thumbnail);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_wallpaper_black_24dp).into(setAsAlbumThumbnail);
        setAsAlbumThumbnail.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return onUseAsAlbumThumbnailSelectAlbum();
            }
        });

        averageRatingsBar = v.findViewById(R.id.slideshow_image_average_ratingBar);
        ratingsBar = v.findViewById(R.id.slideshow_image_ratingBar);

        RelativeLayout itemContentLayout = v.findViewById(R.id.slideshow_item_content_layout);
        itemContent = createItemContent(inflater, itemContentLayout, savedInstanceState);
        if (itemContent != null) {
            // insert first to allow all others to be overlaid.
            int children = itemContentLayout.getChildCount();
            itemContentLayout.addView(itemContent, 0);
        }
        //            CheckBox favoriteButton = (CheckBox)v.findViewById(R.id.slideshow_image_favorite);
//            favoriteButton.setOnCheckedChangeListener(pkg CompoundButton.OnCheckedChangeListener() {
//                @Override
//                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//                    PiwigoAccessService.startActionSetIsFavorite(model, isChecked, this);
//                }
//            });
        bottomSheet = v.findViewById(R.id.slideshow_image_bottom_sheet);
        LinearLayout bottomSheetContent = bottomSheet.findViewById(R.id.slideshow_image_bottom_sheet_content);
        View itemDetail = createCustomItemDetail(inflater, itemContentLayout, savedInstanceState, model);
        if (itemDetail != null) {
            bottomSheetContent.addView(itemDetail, bottomSheetContent.getChildCount());
        }

        return v;
    }

    protected @LayoutRes
    int getLayoutId() {
        return R.layout.fragment_slideshow_item;
    }

    private void onUseAsAlbumThumbnailForParent() {
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_title_set_album_thumbnail, getString(R.string.alert_message_set_album_thumbnail), R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultAdapter() {

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if (Boolean.TRUE == positiveAnswer) {
                    long albumId = model.getParentId();
                    Long albumParentId = model.getParentageChain().size() > 1 ? model.getParentageChain().get(model.getParentageChain().size() - 2) : null;
                    addActiveServiceCall(R.string.progress_resource_details_updating, new AlbumThumbnailUpdatedResponseHandler(albumId, albumParentId, model.getId()).invokeAsync(getContext()));
                }
            }
        });
    }

    private boolean onUseAsAlbumThumbnailSelectAlbum() {
        // Invoke call to retrieve all album names (will show a dialog once this is done).
        addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId(), true).invokeAsync(getContext()));
        return true;
    }

    protected boolean isEditingItemDetails() {
        return editingItemDetails;
    }

    private void updateItemPositionText() {
        if (albumLoadedItemCount == 1 && albumItemIdx == albumLoadedItemCount && albumTotalItemCount == albumLoadedItemCount) {
            itemPositionTextView.setVisibility(GONE);
        } else {
            itemPositionTextView.setVisibility(VISIBLE);
            itemPositionTextView.setText(String.format(Locale.getDefault(), "%1$d/%2$d[%3$d]", albumItemIdx + 1, albumLoadedItemCount, albumTotalItemCount));
        }
    }

    private void updateInformationShowingStatus() {
        if (informationShowing) {
            bottomSheet.openLayer(false);
        } else {
            bottomSheet.closeLayer(false);
        }
    }

    public void hideProgressIndicator() {
        progressIndicator.setVisibility(GONE);
    }

    public void showProgressIndicator() {
        progressIndicator.setVisibility(VISIBLE);
    }

    protected View createItemContent(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {
        return null;
    }

    protected void configureItemContent(@Nullable View itemContentView, final T model, @Nullable Bundle savedInstanceState) {
    }

    protected View createCustomItemDetail(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState, T model) {
        return null;
    }

    protected void setupImageDetailPopup(View v, Bundle savedInstanceState) {
        resourceNameView = v.findViewById(R.id.slideshow_image_details_name);
//        resourceNameView.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//
//                if (resourceNameView.getLineCount() > resourceNameView.getMaxLines()) {
//                    bottomSheetBehavior.setAllowUserDragging(event.getActionMasked() == MotionEvent.ACTION_UP);
//                }
//                return false;
//            }
//        });
        resourceDescriptionView = v.findViewById(R.id.slideshow_image_details_description);
//        resourceDescriptionView.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                if (resourceDescriptionView.getLineCount() > resourceDescriptionView.getMaxLines()) {
//                    bottomSheetBehavior.setAllowUserDragging(event.getActionMasked() == MotionEvent.ACTION_UP);
//                }
//                return false;
//            }
//        });

        linkedAlbumsField = v.findViewById(R.id.slideshow_image_details_linked_albums);
        linkedAlbumsField.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HashSet<Long> currentSelection = updatedLinkedAlbumSet;
                if (currentSelection == null) {
                    currentSelection = new HashSet<>(model.getLinkedAlbums());
                }

                boolean allowFullEdit = !isAppInReadOnlyMode() && PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile());

                AlbumSelectionNeededEvent albumSelectEvent = new AlbumSelectionNeededEvent(true, allowFullEdit && editingItemDetails, currentSelection);
                getUiHelper().setTrackingRequest(albumSelectEvent.getActionId());
                EventBus.getDefault().post(albumSelectEvent);
            }
        });

        tagsField = v.findViewById(R.id.slideshow_image_details_tags);
//        if (model.getTags() == null) {
//            tagsField.setVisibility(GONE);
//        } else {
//            tagsField.setVisibility(VISIBLE);
//        }

        privacyLevelSpinner = v.findViewById(R.id.privacy_level);
// Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> privacyLevelOptionsAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.privacy_levels_groups_array, R.layout.dark_spinner_item);
// Specify the layout to use when the list of choices appears
        privacyLevelOptionsAdapter.setDropDownViewResource(R.layout.dark_spinner_item);
// Apply the adapter to the spinner
        privacyLevelSpinner.setAdapter(privacyLevelOptionsAdapter);

        saveButton = v.findViewById(R.id.slideshow_resource_action_save_button);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_save_black_24dp).into(saveButton);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateModelFromFields();
                onSaveModelChanges(model);
            }
        });
        discardButton = v.findViewById(R.id.slideshow_resource_action_discard_button);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_undo_black_24dp).into(discardButton);
        discardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDiscardChanges();
            }
        });

        editButton = v.findViewById(R.id.slideshow_resource_action_edit_button);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_mode_edit_black_24dp).into(editButton);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editingItemDetails = !editingItemDetails;
                fillResourceEditFields();
            }
        });


        downloadButton = v.findViewById(R.id.slideshow_resource_action_download);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_file_download_black_24px).into(downloadButton);
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDownloadItem(model);
            }
        });
        deleteButton = v.findViewById(R.id.slideshow_resource_action_delete);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_delete_black_24px).into(deleteButton);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDeleteItem(model);
            }
        });
        moveButton = v.findViewById(R.id.slideshow_resource_action_move);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_content_cut_black_24px).into(moveButton);
        moveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMoveItem(model);
            }
        });
        copyButton = v.findViewById(R.id.slideshow_resource_action_copy);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_content_copy_black_24px).into(copyButton);
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCopyItem(model);
            }
        });

        ratingBar = v.findViewById(R.id.slideshow_image_ratingBar);
    }

    protected abstract void onSaveModelChanges(T model);

    protected void updateModelFromFields() {
        model.setName(resourceNameView.getText().toString());
        model.setDescription(resourceDescriptionView.getText().toString());
        model.setPrivacyLevel(getPrivacyLevelValue());
        if (updatedLinkedAlbumSet != null) {
            model.setLinkedAlbums(updatedLinkedAlbumSet);
        }
    }

    protected void onDiscardChanges() {
        editingItemDetails = !editingItemDetails;
        updatedLinkedAlbumSet = null;
        fillResourceEditFields();
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
        setControlVisible(editButton, allowFullEdit || allowTagEdit);
        setControlVisible(deleteButton, allowFullEdit);
        //TODO make visible once functionality written.
        setControlVisible(copyButton, false);
        setControlVisible(moveButton, false);

        ratingBar.setEnabled(!isAppInReadOnlyMode());
        if (allowDownload) {
            downloadButton.setVisibility(VISIBLE);
        } else {
            // can't use gone as this button is an anchor for other ui components
            downloadButton.setVisibility(View.INVISIBLE);
        }
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
        setControlVisible(editButton, (allowTagEdit || allowFullEdit) && !editingItemDetails);
        setControlVisible(saveButton, editingItemDetails);
        saveButton.setEnabled(editingItemDetails);
        setControlVisible(discardButton, editingItemDetails);
        discardButton.setEnabled(editingItemDetails);
    }

    private int getPrivacyLevelValue() {
        int selectedIdx = privacyLevelSpinner.getSelectedItemPosition();
        return getContext().getResources().getIntArray(R.array.privacy_levels_values_array)[selectedIdx];
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
        getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_unimplemented));
    }

    private void onCopyItem(T model) {
        //TODO implement this
        getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_unimplemented));
    }

    protected void onDownloadItem(T model) {
        AlbumItemActionStartedEvent event = new AlbumItemActionStartedEvent(model);
        getUiHelper().setTrackingRequest(event.getActionId());
        EventBus.getDefault().post(event);
    }

    private void onAlterRating(T model, float rating) {
        AlbumItemActionStartedEvent event = new AlbumItemActionStartedEvent(model);
        getUiHelper().setTrackingRequest(event.getActionId());
        EventBus.getDefault().post(event);
        addActiveServiceCall(R.string.progress_resource_details_updating, new ImageAlterRatingResponseHandler(model, rating).invokeAsync(getContext()));
    }

    private void onDeleteItem(final T model) {
        String message = getString(R.string.alert_confirm_really_delete_from_server);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultAdapter() {

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if (Boolean.TRUE == positiveAnswer) {
                    AlbumItemActionStartedEvent event = new AlbumItemActionStartedEvent(model);
                    getUiHelper().setTrackingRequest(event.getActionId());
                    EventBus.getDefault().post(event);
                    addActiveServiceCall(R.string.progress_delete_resource, new ImageDeleteResponseHandler(model.getId()).invokeAsync(getContext()));
                }
            }
        });
    }

    public T getModel() {
        return model;
    }

    private void notifyUserFileDownloadComplete(final File downloadedFile) {

        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_notifications_black_24dp).into(new Target() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                Intent notificationIntent;

//        if(openImageNotFolder) {
                notificationIntent = new Intent(Intent.ACTION_VIEW);
                // Action on click on notification
                Uri selectedUri = Uri.fromFile(downloadedFile);
                MimeTypeMap map = MimeTypeMap.getSingleton();
                String ext = MimeTypeMap.getFileExtensionFromUrl(selectedUri.toString());
                String mimeType = map.getMimeTypeFromExtension(ext);
                //notificationIntent.setDataAndType(selectedUri, mimeType);

                Uri apkURI = FileProvider.getUriForFile(
                        getContext(),
                        getContext().getApplicationContext()
                                .getPackageName() + ".provider", downloadedFile);
                notificationIntent.setDataAndType(apkURI, mimeType);
                notificationIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

//        } else {
                // N.B.this only works with a very select few android apps - folder browsing seeminly isn't a standard thing in android.
//            notificationIntent = pkg Intent(Intent.ACTION_VIEW);
//            Uri selectedUri = Uri.fromFile(downloadedFile.getParentFile());
//            notificationIntent.setDataAndType(selectedUri, "resource/folder");
//        }

                PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 0,
                        notificationIntent, 0);

                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getContext(), getUiHelper().getDefaultNotificationChannelId())
                        .setLargeIcon(bitmap)
                        .setContentTitle(getString(R.string.notification_download_event))
                        .setContentText(downloadedFile.getAbsolutePath())
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true);

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    // this is not a vector graphic
                    mBuilder.setSmallIcon(R.drawable.ic_notifications_black);
                    mBuilder.setCategory("event");
                } else {
                    mBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
                    mBuilder.setCategory(Notification.CATEGORY_EVENT);
                }

                getUiHelper().clearNotification(TAG, 1);
                getUiHelper().showNotification(TAG, 1, mBuilder.build());
            }

            @Override
            public void onBitmapFailed(Drawable errorDrawable) {
                //Do nothing... Should never ever occur
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                // Don't need to do anything before loading image
            }

        });

    }

    public final void onGalleryItemActionFinished() {

        if (determinateProgressDialog.isShowing()) {
            determinateProgressDialog.dismiss();
        }
        EventBus.getDefault().post(new AlbumItemActionFinishedEvent(getUiHelper().getTrackedRequest(), model));
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        if (activeDownloadActionId != null) {
            EventBus.getDefault().post(new CancelDownloadEvent(activeDownloadActionId));
        }
        super.onDetach();
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    public void onPageSelected() {
        if (isAdded()) {
            FragmentUIHelper uiHelper = getUiHelper();
            uiHelper.registerToActiveServiceCalls();
            uiHelper.setBlockDialogsFromShowing(false);
            uiHelper.handleAnyQueuedPiwigoMessages();
        }
    }

    public void onPageDeselected() {
        // pick up responses when the page is visible again.
        FragmentUIHelper uiHelper = getUiHelper();
        uiHelper.setBlockDialogsFromShowing(true);
        uiHelper.deregisterFromActiveServiceCalls();
    }

    public void updateSlideshowPositionDetails(int position, int albumLoadedItemCount, int albumTotalItemCount) {
        this.albumItemIdx = position;
        this.albumLoadedItemCount = albumLoadedItemCount;
        this.albumTotalItemCount = albumTotalItemCount;
        updateItemPositionText();
    }

    private void onAlbumThumbnailUpdated(PiwigoResponseBufferingHandler.PiwigoAlbumThumbnailUpdatedResponse response) {
        EventBus.getDefault().post(new AlbumAlteredEvent(response.getAlbumParentIdAltered()));
    }

    private void onGetSubAlbumNames(AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse response) {
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
        AlertDialog dialog = dialogFact.buildDialog(albumNames, CategoryItem.ROOT_ALBUM, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                long selectedAlbumId = dialogFact.getSelectedAlbumId();
                Long selectedAlbumParentId = dialogFact.getSelectedAlbumParentId();
                addActiveServiceCall(R.string.progress_resource_details_updating, new AlbumThumbnailUpdatedResponseHandler(selectedAlbumId, selectedAlbumParentId, model.getId()).invokeAsync(getContext()));
            }
        });
        dialog.show();
    }

    public void onGetResource(final PiwigoResponseBufferingHandler.UrlToFileSuccessResponse response) {
        activeDownloadActionId = null;
        notifyUserFileDownloadComplete(response.getFile());
    }

    private void onProgressUpdate(final PiwigoResponseBufferingHandler.UrlProgressResponse response) {
        if (response.getProgress() < 0) {
            getUiHelper().showProgressDialog();
        } else {
            if (response.getProgress() == 0) {
                determinateProgressDialog.setTitle(R.string.progress_downloading);

                determinateProgressDialog.setCanceledOnTouchOutside(false);
                determinateProgressDialog.setCancelable(true);
                determinateProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        activeDownloadActionId = null;
                        EventBus.getDefault().post(new CancelDownloadEvent(response.getMessageId()));
                    }
                });
                determinateProgressDialog.show();
                determinateProgressDialog.setProgress(response.getProgress());
            } else if (determinateProgressDialog.isShowing()) {
                determinateProgressDialog.setProgress(response.getProgress());
            }
        }
    }

    private void onRatingAltered(ResourceItem resource) {
        if (resource.getRatingsGiven() > 0) {
            averageRatingsBar.setRating(resource.getAverageRating());
            averageRatingsBar.setVisibility(VISIBLE);
        }
    }

    protected void onImageDeleted() {
        for (Long itemParent : model.getParentageChain()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(itemParent));
        }
        EventBus.getDefault().post(new AlbumItemDeletedEvent(model, albumItemIdx, albumLoadedItemCount));
    }

    private void onGetResourceCancelled(PiwigoResponseBufferingHandler.UrlCancelledResponse response) {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_image_download_cancelled_message));
    }

    private void onResourceInfoRetrieved(final PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse response) {
        model = (T) response.getResource();
        populateResourceExtraFields();
    }

    protected void populateResourceExtraFields() {
        onRatingAltered(model);

        ratingsBar.setRating(model.getYourRating());

        privacyLevelSpinner.setSelection(getPrivacyLevelIndexPositionFromValue(model.getPrivacyLevel()));

        HashSet<Long> currentLinkedAlbumsSet = updatedLinkedAlbumSet != null ? updatedLinkedAlbumSet : model.getLinkedAlbums();
        linkedAlbumsField.setText((currentLinkedAlbumsSet == null ? '?' : currentLinkedAlbumsSet.size()) + " (" + getString(R.string.click_to_view) + ')');

        tagsField.setText(R.string.paid_feature_only);
    }

    protected void onResourceInfoAltered(final T resourceItem) {
        model = resourceItem;
        if (editingItemDetails) {
            editingItemDetails = !editingItemDetails;
            fillResourceEditFields();
        }
        if (albumsRequiringReload != null) {
            // ensure all necessary albums are updated.
            for (Long albumsAltered : albumsRequiringReload) {
                EventBus.getDefault().post(new AlbumAlteredEvent(albumsAltered));
            }
            albumsRequiringReload = null;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PiwigoLoginSuccessEvent event) {
        displayItemDetailsControlsBasedOnSessionState();
        setEditItemDetailsControlsStatus();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumItemDeletedEvent event) {
        Long albumId = event.item.getParentId();
        if (albumId == null && model.getParentId() == null || albumId != null && albumId.equals(model.getParentId())) {
            //Need to update page as an item was deleted from the currently displayed album
            albumLoadedItemCount = event.getAlbumResourceItemCount() - 1;
            if (albumItemIdx > event.getAlbumResourceItemIdx()) {
                albumItemIdx -= 1;
            }
            updateItemPositionText();
        }
    }

    @Override
    public int getPagerIndex() {
        return albumItemIdx;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AppLockedEvent event) {
        if (editingItemDetails) {
            discardButton.callOnClick();
        } else {
            displayItemDetailsControlsBasedOnSessionState();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(SlideshowSizeUpdateEvent event) {
        this.albumLoadedItemCount = event.getLoadedResources();
        this.albumTotalItemCount = event.getTotalResources();
        updateItemPositionText();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AppUnlockedEvent event) {
        displayItemDetailsControlsBasedOnSessionState();
        setEditItemDetailsControlsStatus();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumSelectionCompleteEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            updatedLinkedAlbumSet = event.getCurrentSelection();

            // see what has changed and alter fields as needed.
            // Update the linked album list if required.
            HashSet<Long> currentAlbums = model.getLinkedAlbums();
            HashSet<Long> newAlbums = updatedLinkedAlbumSet;

            // check the original and new set aren't the same before updating the server.
            HashSet<Long> albumsRemoved = new HashSet<>(currentAlbums);
            albumsRemoved.removeAll(newAlbums); // to give those status altered in the old set (removed).
            HashSet<Long> albumsAdded = new HashSet<>(newAlbums);
            albumsAdded.removeAll(currentAlbums); // to give those status altered in the new set (added).
            HashSet<Long> changedAlbums = new HashSet<>();
            changedAlbums.addAll(albumsAdded);
            changedAlbums.addAll(albumsRemoved);

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
                // now add the parentage for all changed albums (sadly won't include the deselected ones).
                for (CategoryItemStub selectedItem : event.getSelectedItems()) {
                    if (changedAlbums.contains(selectedItem.getId())) {
                        albumsRequiringReload.addAll(selectedItem.getParentageChain());
                    }
                }
                // If we remove an album, update the parent chain from here (to ensure things are in-sync if inefficiently)
                if (albumsRemoved.size() > 0) {
                    List<Long> parentageOfItem = model.getParentageChain();
                    HashSet<Long> albumsToNotify = new HashSet<>();
                    for (int i = parentageOfItem.size() - 1; i >= 0; i--) {
                        if (albumsRemoved.contains(parentageOfItem.get(i))) {
                            albumsToNotify.addAll(parentageOfItem.subList(0, i));
                            break;
                        }
                    }
                    albumsRequiringReload.addAll(albumsToNotify);
                }
            }
        }

    }

    protected class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            EventBus.getDefault().post(new PiwigoSessionTokenUseNotificationEvent(PiwigoSessionDetails.getActiveSessionToken(ConnectionPreferences.getActiveProfile())));
        }

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            boolean finishedOperation = true;

            if (response instanceof PiwigoResponseBufferingHandler.UrlToFileSuccessResponse) {
                onGetResource((PiwigoResponseBufferingHandler.UrlToFileSuccessResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.UrlProgressResponse) {
                onProgressUpdate((PiwigoResponseBufferingHandler.UrlProgressResponse) response);
                finishedOperation = false;
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoRatingAlteredResponse) {
                onRatingAltered(((PiwigoResponseBufferingHandler.PiwigoRatingAlteredResponse) response).getPiwigoResource());
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoDeleteImageResponse) {
                onImageDeleted();
            } else if (response instanceof PiwigoResponseBufferingHandler.UrlCancelledResponse) {
                onGetResourceCancelled((PiwigoResponseBufferingHandler.UrlCancelledResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse) {
                onResourceInfoRetrieved((PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse) {
                PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse<T> r = ((PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse<T>) response);
                onResourceInfoAltered((T) r.getPiwigoResource());
            } else if (response instanceof AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) {
                onGetSubAlbumNames((AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoAlbumThumbnailUpdatedResponse) {
                onAlbumThumbnailUpdated((PiwigoResponseBufferingHandler.PiwigoAlbumThumbnailUpdatedResponse) response);
            }

            if (finishedOperation) {
                onGalleryItemActionFinished();
            }
        }
    }

}