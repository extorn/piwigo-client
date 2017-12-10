package delit.piwigoclient.ui.slideshow;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.HashSet;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoAccessService;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.common.ControllableBottomSheetBehavior;
import delit.piwigoclient.ui.common.CustomImageButton;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.MyFragment;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.dialogs.SelectAlbumDialog;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AlbumItemDeletedEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.CancelDownloadEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionFinishedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionStartedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.AlbumSelectionNeededEvent;
import delit.piwigoclient.util.DisplayUtils;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;


public class SlideshowItemFragment<T extends ResourceItem> extends MyFragment {

    public static final String ARG_GALLERY_ITEM = "galleryItem";
    public static final String TAG = "SlideshowItemFragment";
    private static final String STATE_UPDATED_LINKED_ALBUM_SET = "updatedLinkedAlbumSet";
    private static final String STATE_ALBUMS_REQUIRING_UPDATE = "albumsRequiringUpdate";
    private static final String STATE_EDITING_ITEM_DETAILS = "editingItemDetails";
    private static final String STATE_INFORMATION_SHOWING = "informationShowing";
    private static final String ALLOW_DOWNLOAD = "allowDownload";
    private T model;
    private ControllableBottomSheetBehavior<View> bottomSheetBehavior;
    private RatingBar averageRatingsBar;
    private ProgressBar progressIndicator;
    private RatingBar ratingsBar;
    private Spinner privacyLevelSpinner;
    private EditText resourceDescriptionView;
    private EditText resourceNameView;
    private ImageButton saveButton;
    private ImageButton discardButton;
    private ImageButton editButton;
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

    public SlideshowItemFragment() {
    }

    public void setAllowDownload(boolean allowDownload) {
        this.allowDownload = allowDownload;
    }

    public boolean isAllowDownload() {
        return allowDownload;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            model = (T) getArguments().getSerializable(ARG_GALLERY_ITEM);
        }
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
    }

    public void addDownloadAction(long activeDownloadActionId) {
        this.activeDownloadActionId = activeDownloadActionId;
        getUiHelper().addBackgroundServiceCall(activeDownloadActionId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (savedInstanceState != null) {
            //restore saved state
            editingItemDetails = savedInstanceState.getBoolean(STATE_EDITING_ITEM_DETAILS);
            informationShowing = savedInstanceState.getBoolean(STATE_INFORMATION_SHOWING);
            allowDownload = savedInstanceState.getBoolean(ALLOW_DOWNLOAD);
            model = (T)savedInstanceState.getSerializable(ARG_GALLERY_ITEM);
            updatedLinkedAlbumSet = (HashSet<Long>) savedInstanceState.getSerializable(STATE_UPDATED_LINKED_ALBUM_SET);
            albumsRequiringReload = (HashSet<Long>) savedInstanceState.getSerializable(STATE_ALBUMS_REQUIRING_UPDATE);
        } else {
            // call this quietly in the background to avoid it ruining the slideshow experience.
            long messageId = PiwigoAccessService.startActionGetResourceInfo(model, getContext());
            getUiHelper().addBackgroundServiceCall(messageId);
//            if(proactivelyDownloadResourceInfo) {
//                EventBus.getDefault().post(new AlbumItemActionStartedEvent(model));
//                addActiveServiceCall(R.string.progress_loading_resource_details, PiwigoAccessService.startActionGetResourceInfo(model, getContext()));
//            }
        }

        final View v = inflater.inflate(R.layout.fragment_slideshow_item, container, false);

        boolean useDarkMode = prefs.getBoolean(getString(R.string.preference_gallery_use_dark_mode_key), false);

        if(useDarkMode) {
            v.setBackgroundColor(Color.BLACK);
        }

        RelativeLayout itemContentLayout = v.findViewById(R.id.slideshow_item_content_layout);
        progressIndicator = v.findViewById(R.id.slideshow_image_loadingIndicator);

        setAsAlbumThumbnail = v.findViewById(R.id.slideshow_resource_action_use_for_album_thumbnail);
        setAsAlbumThumbnail.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // Invoke call to retrieve all album names (will show a dialog once this is done).
                boolean recursive = true;
                addActiveServiceCall(R.string.progress_loading_albums, PiwigoAccessService.startActionGetSubCategoryNames(CategoryItem.ROOT_ALBUM.getId(), recursive, getContext()));
                return true;
            }
        });
        setAsAlbumThumbnail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getUiHelper().showOrQueueDialogQuestion(R.string.alert_title_set_album_thumbnail, getString(R.string.alert_message_set_album_thumbnail), R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultListener() {
                    @Override
                    public void onDismiss(AlertDialog dialog) {

                    }

                    @Override
                    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                        if(Boolean.TRUE == positiveAnswer) {
                            long albumId = model.getParentId();
                            Long albumParentId = model.getParentageChain().size() > 1? model.getParentageChain().get(model.getParentageChain().size() - 2) : null;
                            addActiveServiceCall(R.string.progress_resource_details_updating, PiwigoAccessService.startActionUpdateAlbumThubnail(albumId, albumParentId, model.getId(), getContext()));
                        }
                    }
                });
            }
        });

        View itemContent = createItemContent(inflater, itemContentLayout, savedInstanceState, model);
        if (itemContent != null) {
            // insert first to allow all others to be overlaid.
            itemContentLayout.addView(itemContent, 0);
        }

        averageRatingsBar = v.findViewById(R.id.slideshow_image_average_ratingBar);
        ratingsBar = v.findViewById(R.id.slideshow_image_ratingBar);
        ratingsBar.setRating(model.getYourRating());
        ratingsBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
                if(fromUser) {
                    onAlterRating(model, rating);
                }
            }
        });
        onRatingAltered(model);
//            CheckBox favoriteButton = (CheckBox)v.findViewById(R.id.slideshow_image_favorite);
//            favoriteButton.setOnCheckedChangeListener(pkg CompoundButton.OnCheckedChangeListener() {
//                @Override
//                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//                    PiwigoAccessService.startActionSetIsFavorite(model, isChecked, this);
//                }
//            });
        FloatingActionButton actionButton = v.findViewById(R.id.slideshow_image_actionButton_details);

        LinearLayout bottomSheetLayout = v.findViewById(R.id.slideshow_image_bottom_sheet);
        bottomSheetBehavior = ControllableBottomSheetBehavior.from((View) bottomSheetLayout);

        int bottomSheetOffsetDp = prefs.getInt(getString(R.string.preference_gallery_detail_sheet_offset_key), getResources().getInteger(R.integer.preference_gallery_detail_sheet_offset_default));
        bottomSheetBehavior.setPeekHeight(DisplayUtils.dpToPx(getContext(), bottomSheetOffsetDp));

        setupImageDetailPopup(v, inflater, container, savedInstanceState);

        View itemDetail = createCustomItemDetail(inflater, itemContentLayout, savedInstanceState, model);
        if (itemDetail != null) {
            bottomSheetLayout.addView(itemDetail, bottomSheetLayout.getChildCount());
        }

        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                informationShowing = !informationShowing;
                updateInformationShowingStatus();
            }
        });

        // show information panel if wanted.
        updateInformationShowingStatus();

        return v;
    }

    private void updateInformationShowingStatus() {
        if (informationShowing) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    public void hideProgressIndicator() {
        progressIndicator.setVisibility(GONE);
    }

    public void showProgressIndicator() {
        progressIndicator.setVisibility(VISIBLE);
    }

    protected View createItemContent(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState, T model) {
        return null;
    }

    protected View createCustomItemDetail(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState, T model) {
        return null;
    }

    private void setupImageDetailPopup(View v, LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        resourceNameView = v.findViewById(R.id.slideshow_image_details_name);
        resourceNameView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (resourceNameView.getLineCount() > resourceNameView.getMaxLines()) {
                    bottomSheetBehavior.setAllowUserDragging(event.getActionMasked() == MotionEvent.ACTION_UP);
                }
                return false;
            }
        });
        resourceDescriptionView = v.findViewById(R.id.slideshow_image_details_description);
        resourceDescriptionView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (resourceDescriptionView.getLineCount() > resourceDescriptionView.getMaxLines()) {
                    bottomSheetBehavior.setAllowUserDragging(event.getActionMasked() == MotionEvent.ACTION_UP);
                }
                return false;
            }
        });

        linkedAlbumsField = v.findViewById(R.id.slideshow_image_details_linked_albums);
        linkedAlbumsField.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            HashSet<Long> currentSelection = updatedLinkedAlbumSet;
            if(currentSelection == null) {
                currentSelection = new HashSet<>(model.getLinkedAlbums());
            }
            AlbumSelectionNeededEvent albumSelectEvent = new AlbumSelectionNeededEvent(true, editingItemDetails, currentSelection);
            getUiHelper().setTrackingRequest(albumSelectEvent.getActionId());
            EventBus.getDefault().post(albumSelectEvent);
            }
        });

        privacyLevelSpinner = v.findViewById(R.id.privacy_level);
// Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> privacyLevelOptionsAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.privacy_levels_groups_array, R.layout.dark_spinner_item);
// Specify the layout to use when the list of choices appears
        privacyLevelOptionsAdapter.setDropDownViewResource(R.layout.dark_spinner_item);
// Apply the adapter to the spinner
        privacyLevelSpinner.setAdapter(privacyLevelOptionsAdapter);

        saveButton = v.findViewById(R.id.slideshow_resource_action_save_button);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateModelFromFields();
                addActiveServiceCall(R.string.progress_resource_details_updating, PiwigoAccessService.startActionUpdateResourceInfo(model, getContext()));
            }
        });
        discardButton = v.findViewById(R.id.slideshow_resource_action_discard_button);
        discardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editingItemDetails = !editingItemDetails;
                updatedLinkedAlbumSet = null;
                fillResourceEditFields();
            }
        });

        editButton = v.findViewById(R.id.slideshow_resource_action_edit_button);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editingItemDetails = !editingItemDetails;
                fillResourceEditFields();
            }
        });


        downloadButton = v.findViewById(R.id.slideshow_resource_action_download);
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDownloadItem(model);
            }
        });
        deleteButton = v.findViewById(R.id.slideshow_resource_action_delete);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDeleteItem(model);
            }
        });
        moveButton = v.findViewById(R.id.slideshow_resource_action_move);
        moveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMoveItem(model);
            }
        });
        copyButton = v.findViewById(R.id.slideshow_resource_action_copy);
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCopyItem(model);
            }
        });

        ratingBar = v.findViewById(R.id.slideshow_image_ratingBar);

        fillResourceEditFields();
    }

    private void updateModelFromFields() {
        model.setName(resourceNameView.getText().toString());
        model.setDescription(resourceDescriptionView.getText().toString());
        model.setPrivacyLevel(getPrivacyLevelValue());
        if(updatedLinkedAlbumSet != null) {
            model.setLinkedAlbums(updatedLinkedAlbumSet);
        }
    }

    public void displayItemDetailsControlsBasedOnSessionState() {
        if (PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode()) {
            saveButton.setVisibility(VISIBLE);
            discardButton.setVisibility(VISIBLE);
            editButton.setVisibility(VISIBLE);
            deleteButton.setVisibility(VISIBLE);
            //TODO make visible once functionality written.
            copyButton.setVisibility(GONE);
            moveButton.setVisibility(GONE);
        } else {
            saveButton.setVisibility(GONE);
            discardButton.setVisibility(GONE);
            editButton.setVisibility(GONE);
            deleteButton.setVisibility(GONE);
            copyButton.setVisibility(GONE);
            moveButton.setVisibility(GONE);
        }
        ratingBar.setEnabled(!isAppInReadOnlyMode());
        if(allowDownload) {
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
        }

        if (model.getDescription() == null) {
            resourceDescriptionView.setText("");
        } else {
            resourceDescriptionView.setText(model.getDescription());
        }
        privacyLevelSpinner.setSelection(getPrivacyLevelIndexPositionFromValue(model.getPrivacyLevel()));

        HashSet<Long> currentLinkedAlbumsSet = updatedLinkedAlbumSet != null ? updatedLinkedAlbumSet : model.getLinkedAlbums();
        linkedAlbumsField.setText((currentLinkedAlbumsSet == null ? '?' : currentLinkedAlbumsSet.size()) +" ("+getString(R.string.click_to_view)+')');

        displayItemDetailsControlsBasedOnSessionState();
        setEditItemDetailsControlsStatus();
    }

    private void setEditItemDetailsControlsStatus() {
        resourceNameView.setEnabled(editingItemDetails);
        resourceDescriptionView.setEnabled(editingItemDetails);
        privacyLevelSpinner.setEnabled(editingItemDetails);
        setAsAlbumThumbnail.setVisibility(PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode() && !editingItemDetails ? VISIBLE : GONE);
        editButton.setVisibility(PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode() && !editingItemDetails ? VISIBLE : GONE);
        saveButton.setVisibility(editingItemDetails ? VISIBLE : GONE);
        saveButton.setEnabled(editingItemDetails);
        discardButton.setVisibility(editingItemDetails ? VISIBLE : GONE);
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

    protected void onAlterRating(T model, float rating) {
        AlbumItemActionStartedEvent event = new AlbumItemActionStartedEvent(model);
        getUiHelper().setTrackingRequest(event.getActionId());
        EventBus.getDefault().post(event);
        addActiveServiceCall(R.string.progress_resource_details_updating, PiwigoAccessService.startActionChangeRating(model, rating, getContext()));
    }

    protected void onDeleteItem(final T model) {
        String message = getString(R.string.alert_confirm_really_delete_from_server);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultListener() {
            @Override
            public void onDismiss(AlertDialog dialog) {
            }

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if(Boolean.TRUE == positiveAnswer) {
                    AlbumItemActionStartedEvent event = new AlbumItemActionStartedEvent(model);
                    getUiHelper().setTrackingRequest(event.getActionId());
                    EventBus.getDefault().post(event);
                    addActiveServiceCall(R.string.progress_delete_resource, PiwigoAccessService.startActionDeleteGalleryItemFromServer(model.getId(), getContext()));
                }
            }
        });
    }

    public T getModel() {
        return model;
    }

    private void notifyUserFileDownloadComplete(File downloadedFile) {

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
        // N.B.this only works with a very select few android apps - folder browsing seeminly isnt a standard thing in android.
//            notificationIntent = pkg Intent(Intent.ACTION_VIEW);
//            Uri selectedUri = Uri.fromFile(downloadedFile.getParentFile());
//            notificationIntent.setDataAndType(selectedUri, "resource/folder");
//        }

        PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 0,
                notificationIntent, 0);

        Bitmap largeIcon = BitmapFactory.decodeResource(getContext().getResources(),
                R.drawable.ic_notifications_black_24dp);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getContext(), getUiHelper().getDefaultNotificationChannelId())
                .setLargeIcon(largeIcon)
                .setContentTitle(getString(R.string.notification_download_event))
                .setContentText(downloadedFile.getAbsolutePath())
                .setCategory(Notification.CATEGORY_EVENT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // this is not a vector graphic
            mBuilder.setSmallIcon(R.drawable.ic_notifications_black);
        } else {
            mBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
        }

        getUiHelper().clearNotification(TAG, 1);
        getUiHelper().showNotification(TAG, 1, mBuilder.build());

    }

    public final void onGalleryItemActionFinished() {
        getUiHelper().dismissProgressDialog();

        if (determinateProgressDialog.isShowing()) {
            determinateProgressDialog.dismiss();
        }
        EventBus.getDefault().post(new AlbumItemActionFinishedEvent(getUiHelper().getTrackedRequest(), model));
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDetach() {
        if(activeDownloadActionId != null) {
            EventBus.getDefault().post(new CancelDownloadEvent(activeDownloadActionId));
        }
        super.onDetach();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    public void onPageSelected() {
        if(isAdded()) {
            FragmentUIHelper uiHelper = getUiHelper();
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

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
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
                onResourceInfoAltered(((PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse<T>) response).getResource());
            } else if(response instanceof  PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) {
                onGetSubAlbumNames((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse)response);
            } else if(response instanceof  PiwigoResponseBufferingHandler.PiwigoAlbumThumbnailUpdatedResponse) {
                onAlbumThumbnailUpdated((PiwigoResponseBufferingHandler.PiwigoAlbumThumbnailUpdatedResponse)response);
            }

            if(finishedOperation) {
                onGalleryItemActionFinished();
            }
        }
    }

    private void onAlbumThumbnailUpdated(PiwigoResponseBufferingHandler.PiwigoAlbumThumbnailUpdatedResponse response) {
        EventBus.getDefault().post(new AlbumAlteredEvent(response.getAlbumParentIdAltered()));
    }

    private void onGetSubAlbumNames(PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse response) {
        final SelectAlbumDialog dialogFact = new SelectAlbumDialog(getActivity(), model.getParentId());
        AlertDialog dialog = dialogFact.buildDialog(response.getAlbumNames(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                long selectedAlbumId = dialogFact.getSelectedAlbumId();
                Long selectedAlbumParentId = dialogFact.getSelectedAlbumParentId();
                addActiveServiceCall(R.string.progress_resource_details_updating, PiwigoAccessService.startActionUpdateAlbumThubnail(selectedAlbumId, selectedAlbumParentId, model.getId(), getContext()));
            }
        });
        dialog.show();
    }

    public void onGetResource(final PiwigoResponseBufferingHandler.UrlToFileSuccessResponse response) {
        activeDownloadActionId = null;
        notifyUserFileDownloadComplete(response.getFile());
    }



    public void onProgressUpdate(final PiwigoResponseBufferingHandler.UrlProgressResponse response) {
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

    public void onRatingAltered(ResourceItem resource) {
        if (resource.getRatingsGiven() > 0) {
            averageRatingsBar.setRating(resource.getAverageRating());
            averageRatingsBar.setVisibility(VISIBLE);
        }
    }

    public void onImageDeleted() {
        for(Long itemParent : model.getParentageChain()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(itemParent));
        }
        EventBus.getDefault().post(new AlbumItemDeletedEvent(model));
    }

    public void onGetResourceCancelled(PiwigoResponseBufferingHandler.UrlCancelledResponse response) {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_image_download_cancelled_message));
    }

    public void onResourceInfoRetrieved(final PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse response) {
        model = (T)response.getResource();
        onRatingAltered(response.getResource());
        ratingsBar.setRating(response.getResource().getYourRating());
        privacyLevelSpinner.setSelection(getPrivacyLevelIndexPositionFromValue(model.getPrivacyLevel()));

        HashSet<Long> currentLinkedAlbumsSet = updatedLinkedAlbumSet != null ? updatedLinkedAlbumSet : model.getLinkedAlbums();
        linkedAlbumsField.setText((currentLinkedAlbumsSet == null ? '?' : currentLinkedAlbumsSet.size()) +" ("+getString(R.string.click_to_view)+')');
    }

    public void onResourceInfoAltered(final T categoryItem) {
        model = categoryItem;
        if (editingItemDetails) {
            editingItemDetails = !editingItemDetails;
            fillResourceEditFields();
        }
        if(albumsRequiringReload != null) {
            // ensure all necessary albums are updated.
            for (Long albumsAltered : albumsRequiringReload) {
                EventBus.getDefault().post(new AlbumAlteredEvent(albumsAltered));
            }
            albumsRequiringReload = null;
        }
        getUiHelper().dismissProgressDialog();
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
    public void onEvent(AppUnlockedEvent event) {
        displayItemDetailsControlsBasedOnSessionState();
        setEditItemDetailsControlsStatus();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumSelectionCompleteEvent event) {
        if(getUiHelper().isTrackingRequest(event.getActionId())) {
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

            if(changedAlbums.size() == 0) {
                // no changes
                updatedLinkedAlbumSet = null;
            } else {
                if(albumsRequiringReload == null) {
                    albumsRequiringReload = new HashSet<>();
                } else {
                    albumsRequiringReload.clear();
                }
                // add all the changed albums
                albumsRequiringReload.addAll(changedAlbums);
                // now add the parentage for all changed albums (sadly won't include the deselected ones).
                for(CategoryItemStub selectedItem : event.getSelectedItems()) {
                    if(changedAlbums.contains(selectedItem.getId())) {
                        albumsRequiringReload.addAll(selectedItem.getParentageChain());
                    }
                }
                // If we remove an album, update the parent chain from here (to ensure things are in-sync if inefficiently)
                if(albumsRemoved.size() > 0) {
                    List<Long> parentageOfItem = model.getParentageChain();
                    HashSet<Long> albumsToNotify = new HashSet<>();
                    for(int i = parentageOfItem.size() - 1; i >= 0; i--) {
                        if(albumsRemoved.contains(parentageOfItem.get(i))) {
                            albumsToNotify.addAll(parentageOfItem.subList(0, i));
                            break;
                        }
                    }
                    albumsRequiringReload.addAll(albumsToNotify);
                }
            }
        }

    }


}