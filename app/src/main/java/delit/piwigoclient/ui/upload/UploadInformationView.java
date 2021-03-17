package delit.piwigoclient.ui.upload;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;

public class UploadInformationView extends DialogFragment {
    private final ConnectionPreferences.ProfilePreferences preferences;

    public UploadInformationView(ConnectionPreferences.ProfilePreferences preferences) {
        this.preferences = preferences;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final MaterialAlertDialogBuilder builder1 = new MaterialAlertDialogBuilder(new android.view.ContextThemeWrapper(getContext(), R.style.Theme_App_EditPages));
        builder1.setTitle(R.string.alert_information);
        builder1.setPositiveButton(R.string.button_ok, (v,b)-> dismiss());
        builder1.setView(R.layout.layout_files_for_upload_info);
        return builder1.create();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshText(preferences);
    }

    public void refreshText(ConnectionPreferences.ProfilePreferences preferences) {
        PiwigoSessionDetails piwigoSessionDetails = PiwigoSessionDetails.getInstance(preferences);

        EditText serverAcceptableFilesView = getDialog().findViewById(R.id.files_uploadable_field);
        if (piwigoSessionDetails != null) {
            String list = CollectionUtils.toCsvList(PiwigoSessionDetails.getInstance(preferences).getAllowedFileTypes(), ", ");
            String fileTypesStr = String.format("%1$s", list == null ? " * " : list);
            serverAcceptableFilesView.setText(fileTypesStr);
        } else {
            serverAcceptableFilesView.setText("");
        }
    }

//    @Override
//    public View onCreateView(LayoutInflater inflater, ViewGroup container,
//                             Bundle savedInstanceState) {
//        // Inflate the layout to use as dialog or embedded fragment
//        View v = inflater.inflate(R.layout.layout_files_for_upload_info, container, false);
//        serverAcceptableFilesView = v.findViewById(R.id.files_uploadable_field);
//        refreshText(preferences);
//        return v;
//    }

}
