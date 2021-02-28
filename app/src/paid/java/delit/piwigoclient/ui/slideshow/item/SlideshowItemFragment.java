package delit.piwigoclient.ui.slideshow.item;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;
import java.util.Set;

import delit.libs.ui.util.BundleUtils;
import delit.libs.util.SetUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.PluginUserTagsUpdateResourceTagsListResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.events.TagContentAlteredEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionNeededEvent;
import delit.piwigoclient.ui.slideshow.item.action.FavoriteCheckedListener;


public abstract class SlideshowItemFragment<F extends SlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends AbstractSlideshowItemFragment<F,FUIH,T> {

    private static final String STATE_UPDATED_TAGS_SET = "updatedTagSet";
    private static final String STATE_CHANGED_TAGS_SET = "changedTagSet";
    private HashSet<Tag> updatedTagsSet;
    private HashSet<TagContentAlteredEvent> changedTagsEvents;
    private CheckBox favoriteButton;

    @Override
    protected void setupImageDetailPopup(View v, Bundle savedInstanceState) {
        super.setupImageDetailPopup(v, savedInstanceState);

        getTagsField().setOnClickListener(v1 -> onShowTagsSelection());
    }

    private void onShowTagsSelection() {
        HashSet<Tag> currentSelection = getLatestTagListForResource();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean allowTagEdit = false;
        if(sessionDetails != null) {
            boolean allowFullEdit = !isAppInReadOnlyMode() && PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile());
            allowTagEdit = allowFullEdit || (!isAppInReadOnlyMode()  && sessionDetails.isUseUserTagPluginForUpdate());
            allowTagEdit &= isEditingItemDetails();
        }
        boolean lockInitialSelection = sessionDetails != null && !sessionDetails.isUseUserTagPluginForUpdate();
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

        setControlVisible(getEditButton(), (allowTagEdit || allowFullEdit) && !isEditingItemDetails());

    }

    protected void onResourceTagsUpdated(ResourceItem piwigoResource) {
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
            favoriteButton.setVisibility(View.VISIBLE);
        } else {
            favoriteButton.setVisibility(View.INVISIBLE);
        }


        setLinkedAlbumFieldText(getTagsField(), getLatestTagListForResource());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        BundleUtils.putSet(outState, STATE_UPDATED_TAGS_SET, updatedTagsSet);
        BundleUtils.putSet(outState, STATE_CHANGED_TAGS_SET, changedTagsEvents);
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
            addActiveServiceCall(R.string.progress_resource_details_updating, new PluginUserTagsUpdateResourceTagsListResponseHandler<>(model));
        }
        if(allowFullEdit) {
            if(model.getLinkedAlbums().isEmpty()) {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_item_must_belong_to_at_least_one_album));
            } else {
                addActiveServiceCall(R.string.progress_resource_details_updating, new ImageUpdateInfoResponseHandler<>(model, !allowTagEdit));
            }
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
    public void onImageDeleted(HashSet<? extends ResourceItem> deletedItems) {
        for (Tag tag : getModel().getTags()) {
            EventBus.getDefault().post(new TagContentAlteredEvent(tag.getId(), -1));
        }
        super.onImageDeleted(deletedItems);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        favoriteButton = v.findViewById(R.id.slideshow_image_favorite);
        return v;
    }

    public void onFavoriteUpdateFailed(boolean oldValue) {
        if (getModel().isFavorite() != favoriteButton.isChecked()) {
            // change the button to match the model.
            favoriteButton.setTag("noListener");
            favoriteButton.setChecked(oldValue);
        }
        favoriteButton.setEnabled(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        T model = getModel();
        if(model != null) {
            boolean favoritesSupported = model.hasFavoriteInfo();
            //PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile()).isPiwigoClientPluginInstalled();
            favoriteButton.setVisibility(favoritesSupported ? View.VISIBLE : View.INVISIBLE);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            //restore saved state
            updatedTagsSet = BundleUtils.getHashSet(savedInstanceState, STATE_UPDATED_TAGS_SET);
            changedTagsEvents = BundleUtils.getHashSet(savedInstanceState, STATE_CHANGED_TAGS_SET);
        }
        super.onViewCreated(view, savedInstanceState);
        favoriteButton.setOnCheckedChangeListener(new FavoriteCheckedListener<>(getUiHelper(), getModel()));
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
            setLinkedAlbumFieldText(getTagsField(), updatedTagsSet == null ? currentTags : updatedTagsSet);
        }
    }


    @Override
    public void onResourceInfoAltered(final T resourceItem) {
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
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new PaidSideshowPiwigoResponseListener<>();
    }

    @Override
    protected String getDisplayText(Object itemValue) {
        if(itemValue instanceof Tag) {
            return ((Tag)itemValue).getName();
        } else {
            return super.getDisplayText(itemValue);
        }
    }

    public void onFavoriteUpdateSucceeded(boolean newValue) {
        favoriteButton.setEnabled(true);
    }

}
