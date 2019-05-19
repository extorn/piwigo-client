package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.FavoritesAddImageResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.FavoritesRemoveImageResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.PluginUserTagsUpdateResourceTagsListResponseHandler;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.ui.events.FavoritesUpdatedEvent;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.events.TagContentAlteredEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionNeededEvent;
import delit.piwigoclient.util.SetUtils;


public abstract class SlideshowItemFragment<T extends ResourceItem> extends AbstractSlideshowItemFragment<T> {

    private static final String STATE_UPDATED_TAGS_SET = "updatedTagSet";
    private static final String STATE_CHANGED_TAGS_SET = "changedTagSet";
    private HashSet<Tag> updatedTagsSet;
    private HashSet<TagContentAlteredEvent> changedTagsEvents;
    private CheckBox favoriteButton;

    @Override
    protected void setupImageDetailPopup(View v, Bundle savedInstanceState) {
        super.setupImageDetailPopup(v, savedInstanceState);

        tagsField.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onShowTagsSelection();
            }
        });
    }

    private void onShowTagsSelection() {
        HashSet<Tag> currentSelection = getLatestTagListForResource();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean allowFullEdit = !isAppInReadOnlyMode() && sessionDetails != null && PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile());
        boolean allowTagEdit = allowFullEdit || (!isAppInReadOnlyMode() && sessionDetails != null && sessionDetails.isUseUserTagPluginForUpdate());
        allowTagEdit &= isEditingItemDetails();
        boolean lockInitialSelection = !sessionDetails.isUseUserTagPluginForUpdate();
        //disable tag deselection if user tags plugin is not present but allow editing if is admin user. (bug in PIWIGO API)
        HashSet<Long> selectedTagIds = PiwigoUtils.toSetOfIds(currentSelection);
        HashSet<Tag> customTags = new HashSet<>();
        for(Tag t : currentSelection) {
            if(t.getId() < 0) {
                customTags.add(t);
            }
        }
        TagSelectionNeededEvent tagSelectEvent = new TagSelectionNeededEvent(true, allowTagEdit, lockInitialSelection, selectedTagIds);
        tagSelectEvent.setNewUnsavedTags(customTags);
        getUiHelper().setTrackingRequest(tagSelectEvent.getActionId());
        EventBus.getDefault().post(tagSelectEvent);
    }

    @Override
    protected void setEditItemDetailsControlsStatus() {

        super.setEditItemDetailsControlsStatus();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean allowTagEdit = !isAppInReadOnlyMode() && sessionDetails != null && sessionDetails.isUseUserTagPluginForUpdate();
        boolean allowFullEdit = !isAppInReadOnlyMode() && sessionDetails != null && sessionDetails.isAdminUser();

        setControlVisible(editButton, (allowTagEdit || allowFullEdit) && !isEditingItemDetails());

    }

    private void onResourceTagsUpdated(ResourceItem piwigoResource) {
        getModel().setTags(piwigoResource.getTags());
        populateResourceExtraFields();
    }


    @Override
    protected void populateResourceExtraFields() {

        super.populateResourceExtraFields();

        if(getModel().hasFavoriteInfo()) {
            boolean modelVal = getModel().hasFavoriteInfo() && getModel().isFavorite();
            if (modelVal != favoriteButton.isChecked()) {
                favoriteButton.setTag("noListener");
                favoriteButton.setChecked(modelVal);
            }
        }

        setLinkedAlbumFieldText(tagsField, getLatestTagListForResource());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        BundleUtils.putHashSet(outState, STATE_UPDATED_TAGS_SET, updatedTagsSet);
        BundleUtils.putHashSet(outState, STATE_CHANGED_TAGS_SET, changedTagsEvents);
        if (BuildConfig.DEBUG) {
            BundleUtils.logSize("SlideshowItemFragment", outState);
        }
    }

    @Override
    protected void onSaveModelChanges(T model) {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean allowTagEdit = !isAppInReadOnlyMode() && sessionDetails != null && sessionDetails.isUseUserTagPluginForUpdate();
        boolean allowFullEdit = !isAppInReadOnlyMode() && sessionDetails != null && sessionDetails.isAdminUser();

        if (allowTagEdit) {
            addActiveServiceCall(R.string.progress_resource_details_updating, new PluginUserTagsUpdateResourceTagsListResponseHandler(model));
        }
        if(allowFullEdit) {
            addActiveServiceCall(R.string.progress_resource_details_updating, new ImageUpdateInfoResponseHandler(model, !allowTagEdit));
        }
    }

    @Override
    protected void onDiscardChanges() {
        updatedTagsSet = null;
        super.onDiscardChanges();
    }

    protected HashSet<Tag> getLatestTagListForResource() {
        HashSet<Tag> currentSelection = updatedTagsSet;
        if (currentSelection == null) {
            if(getModel().getTags() != null) {
                return new HashSet<>(getModel().getTags());
            }
            return new HashSet<>();
        }
        return currentSelection;
    }

    @Override
    public void onImageDeleted(HashSet<Long> deletedItemIds) {
        for (Tag tag : getModel().getTags()) {
            EventBus.getDefault().post(new TagContentAlteredEvent(tag.getId(), -1));
        }
        super.onImageDeleted(deletedItemIds);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        favoriteButton = v.findViewById(R.id.slideshow_image_favorite);
        addViewVisibleControl(favoriteButton);
        return v;
    }

    private void onFavoriteUpdateFailed() {
        if (getModel().isFavorite() != favoriteButton.isChecked()) {
            // change the button to match the model.
            favoriteButton.setTag("noListener");
            favoriteButton.setChecked(!getModel().isFavorite());
        }
        favoriteButton.setEnabled(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean favoritesSupported = getModel().hasFavoriteInfo();
        //PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile()).isPiwigoClientPluginInstalled();
        favoriteButton.setVisibility(favoritesSupported?View.VISIBLE:View.GONE);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            //restore saved state
            updatedTagsSet = BundleUtils.getHashSet(savedInstanceState, STATE_UPDATED_TAGS_SET);
            changedTagsEvents = BundleUtils.getHashSet(savedInstanceState, STATE_CHANGED_TAGS_SET);
        }
        super.onViewCreated(view, savedInstanceState);
        favoriteButton.setOnCheckedChangeListener(new SlideshowItemFragment.FavoriteCheckedListener(getUiHelper(), getModel()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(TagSelectionCompleteEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            updatedTagsSet = event.getSelectedItems();
            HashSet<Tag> currentTags = getModel().getTags();
            HashSet<Tag> newTags = updatedTagsSet;
            if (newTags.size() == currentTags.size() && newTags.containsAll(currentTags)) {
                // no changes
                updatedTagsSet = null;
            } else {
                Set<Tag> added = SetUtils.difference(newTags, currentTags);
                Set<Tag> deleted = SetUtils.difference(currentTags, newTags);
                changedTagsEvents = new HashSet<>(added.size() + deleted.size());
                for(Tag newTag : added) {
                    changedTagsEvents.add(new TagContentAlteredEvent(newTag.getId(), 1));
                }
                for(Tag newTag : deleted) {
                    changedTagsEvents.add(new TagContentAlteredEvent(newTag.getId(), -1));
                }
            }
            setLinkedAlbumFieldText(tagsField, updatedTagsSet == null ? currentTags : updatedTagsSet);
        }
    }


    @Override
    protected void onResourceInfoAltered(final T resourceItem) {
        if (BuildConfig.PAID_VERSION && PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile()).isUseUserTagPluginForUpdate() && getUiHelper().getActiveServiceCallCount() == 0) {
            // tags have been updated already so we need to keep the existing ones.
            resourceItem.setTags(getModel().getTags());
        }
        super.onResourceInfoAltered(resourceItem);

        if (changedTagsEvents != null) {
            // ensure all necessary tags are updated.
            for (TagContentAlteredEvent tagEvent : changedTagsEvents) {
                EventBus.getDefault().post(tagEvent);
            }
            changedTagsEvents.clear();
            changedTagsEvents = null;
        }
    }

    @Override
    protected void updateModelFromFields() {
        super.updateModelFromFields();
        if (updatedTagsSet != null) {
            getModel().setTags(updatedTagsSet);
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new PaidPiwigoResponseListener();
    }

    @Override
    protected String getDisplayText(Object itemValue) {
        if(itemValue instanceof Tag) {
            return ((Tag)itemValue).getName();
        } else {
            return super.getDisplayText(itemValue);
        }
    }

    private class PaidPiwigoResponseListener extends CustomPiwigoResponseListener {

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            EventBus.getDefault().post(new PiwigoSessionTokenUseNotificationEvent(PiwigoSessionDetails.getActiveSessionToken(ConnectionPreferences.getActiveProfile())));
        }

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse) {
                if(((PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse) response).hasError()) {
                    showOrQueueMessage(R.string.alert_error, ((PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse) response).getError());
                } else {
                    onResourceTagsUpdated(((PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse) response).getPiwigoResource());
                }
                onGalleryItemActionFinished();
            } else if(response instanceof BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse) {
                getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.resource_details_updated_message));
            } else {
                super.onAfterHandlePiwigoResponse(response);
            }
        }
    }

    private static class FavoriteAction<T extends ResourceItem, S extends PiwigoResponseBufferingHandler.Response> extends UIHelper.Action<SlideshowItemFragment<T>, S> {
        @Override
        public boolean onFailure(UIHelper<SlideshowItemFragment<T>> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            getActionParent(uiHelper).onFavoriteUpdateFailed();
            return super.onFailure(uiHelper, response);
        }

        @Override
        public boolean onSuccess(UIHelper<SlideshowItemFragment<T>> uiHelper, S response) {
            getActionParent(uiHelper).onFavoriteUpdateSucceeded();
            return super.onSuccess(uiHelper, response);
        }
    }

    private static class FavoriteRemoveAction<T extends ResourceItem> extends FavoriteAction<T, FavoritesRemoveImageResponseHandler.PiwigoRemoveFavoriteResponse> {
        @Override
        public boolean onSuccess(UIHelper<SlideshowItemFragment<T>> uiHelper, FavoritesRemoveImageResponseHandler.PiwigoRemoveFavoriteResponse response) {
            if(EventBus.getDefault().getStickyEvent(FavoritesUpdatedEvent.class) == null) {
                EventBus.getDefault().postSticky(new FavoritesUpdatedEvent());
            }
            return super.onSuccess(uiHelper, response);
        }
    }

    private static class FavoriteAddAction<T extends ResourceItem> extends FavoriteAction<T, FavoritesAddImageResponseHandler.PiwigoAddFavoriteResponse> {
        @Override
        public boolean onSuccess(UIHelper<SlideshowItemFragment<T>> uiHelper, FavoritesAddImageResponseHandler.PiwigoAddFavoriteResponse response) {
            if(EventBus.getDefault().getStickyEvent(FavoritesUpdatedEvent.class) == null) {
                EventBus.getDefault().postSticky(new FavoritesUpdatedEvent());
            }
            return super.onSuccess(uiHelper, response);
        }
    }

    private void onFavoriteUpdateSucceeded() {
        favoriteButton.setEnabled(true);
    }

    private static class FavoriteCheckedListener implements CompoundButton.OnCheckedChangeListener {

        private static final String TAG = "FavoriteCheckedListener";
        private final @NonNull
        UIHelper helper;
        private final @NonNull
        ResourceItem item;

        public FavoriteCheckedListener(@NonNull UIHelper helper, @NonNull ResourceItem item) {
            this.helper = helper;
            this.item = item;
            if (item == null) {
                Crashlytics.log(Log.ERROR, TAG, "Model item is null");
            }
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if ("noListener".equals(buttonView.getTag())) {
                buttonView.setTag(null);
                return;
            }
            buttonView.setEnabled(false);
            if (item.hasFavoriteInfo()) {
                if (!item.isFavorite()) {
                    helper.invokeActiveServiceCall(R.string.adding_favorite, new FavoritesAddImageResponseHandler(item), new FavoriteAddAction());
                } else {
                    helper.invokeActiveServiceCall(R.string.removing_favorite, new FavoritesRemoveImageResponseHandler(item), new FavoriteRemoveAction());
                }
            }
        }
    }
}
