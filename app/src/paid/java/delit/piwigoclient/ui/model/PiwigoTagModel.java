package delit.piwigoclient.ui.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.drew.lang.annotations.NotNull;

import delit.piwigoclient.model.piwigo.PiwigoTag;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.Tag;

public class PiwigoTagModel extends ViewModelContainer {
    private final MutableLiveData<PiwigoTag> albumLiveData = new MutableLiveData<>();

    public PiwigoTagModel() {
    }

    public LiveData<PiwigoTag> getPiwigoTag() {
        return albumLiveData;
    }

    public LiveData<PiwigoTag> updatePiwigoTag(@NotNull Tag tag) {
        albumLiveData.setValue(new PiwigoTag(tag));
        return albumLiveData;
    }

    public LiveData<PiwigoTag> getPiwigoTag(@NotNull Tag tag) {
        if (albumLiveData.getValue() == null) {
            albumLiveData.setValue(new PiwigoTag(tag));
        }
        return albumLiveData;
    }

    @Override
    public ResourceContainer getModel() {
        return getPiwigoTag().getValue();
    }
}
