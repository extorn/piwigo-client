package delit.piwigoclient.ui.model;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.ResourceContainer;

public class PiwigoAlbumModel extends ViewModelContainer {
    private final MutableLiveData<PiwigoAlbum> albumLiveData = new MutableLiveData<>();

    public PiwigoAlbumModel() {
    }

    public LiveData<PiwigoAlbum> getPiwigoAlbum() {
        return albumLiveData;
    }

    public LiveData<PiwigoAlbum> getPiwigoAlbum(@NonNull CategoryItem categoryItem) {
        if (albumLiveData.getValue() == null) {
            albumLiveData.setValue(new PiwigoAlbum(categoryItem));
        }
        return albumLiveData;
    }

    @Override
    public <T extends ResourceContainer<?, ?>> T getModel() {
        return (T) getPiwigoAlbum().getValue();
    }
}
