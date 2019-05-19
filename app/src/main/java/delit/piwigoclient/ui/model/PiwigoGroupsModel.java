package delit.piwigoclient.ui.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import delit.piwigoclient.model.piwigo.PiwigoGroups;

public class PiwigoGroupsModel extends ViewModel {
    private final MutableLiveData<PiwigoGroups> albumLiveData = new MutableLiveData<>();

    public PiwigoGroupsModel() {
    }

    public LiveData<PiwigoGroups> getPiwigoGroups() {
        if (albumLiveData.getValue() == null) {
            albumLiveData.setValue(new PiwigoGroups());
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
