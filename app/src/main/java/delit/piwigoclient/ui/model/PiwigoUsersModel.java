package delit.piwigoclient.ui.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import delit.piwigoclient.model.piwigo.PiwigoUsers;

public class PiwigoUsersModel extends ViewModel {
    private final MutableLiveData<PiwigoUsers> albumLiveData = new MutableLiveData<>();

    public PiwigoUsersModel() {
    }

    public LiveData<PiwigoUsers> getPiwigoUsers() {
        if (albumLiveData.getValue() == null) {
            albumLiveData.setValue(new PiwigoUsers());
        }
        return albumLiveData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        albumLiveData.getValue().clear();
        albumLiveData.setValue(null);
    }
}
