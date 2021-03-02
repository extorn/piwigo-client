package delit.piwigoclient.ui.model;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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

    public LiveData<PiwigoTag> updatePiwigoTag(@NonNull Tag tag) {
        albumLiveData.postValue(new PiwigoTag(tag));
        return albumLiveData;
    }

    public LiveData<PiwigoTag> getPiwigoTag(@NonNull Tag tag) {
        if (albumLiveData.getValue() == null) {
            albumLiveData.setValue(new PiwigoTag(tag));
        } else {
            albumLiveData.getValue().setContainerDetails(tag);
        }
        return albumLiveData;
    }

    @Override
    public <T extends ResourceContainer<?, ?>> T getModel() {
        return (T) getPiwigoTag().getValue();
    }

}
