package delit.piwigoclient.ui.upload.status;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;

import delit.libs.core.util.Logging;
import delit.libs.ui.view.button.MaterialCheckboxTriState;
import delit.libs.ui.view.list.CustomExpandableListView;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.actors.ForegroundJobLoadActor;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;

public class UploadJobStatusDetailsFragment<F extends UploadJobStatusDetailsFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> {
    private static final String STATE_UPLOAD_JOB_ID = "uploadJobId";
    private static final String TAG = "UpJobStatFrag";
    private UploadJob uploadJob;

    public static UploadJobStatusDetailsFragment<?,?> newInstance(UploadJob job) {
        UploadJobStatusDetailsFragment<?,?> fragment = new UploadJobStatusDetailsFragment<>();
        fragment.setTheme(R.style.Theme_App_EditPages);
        fragment.setArguments(buildArgs(job));
        return fragment;
    }

    private static Bundle buildArgs(UploadJob job) {
        Bundle b = new Bundle();
        b.putLong(STATE_UPLOAD_JOB_ID, job.getJobId());
        return b;
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.upload_job_status_heading);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        return inflater.inflate(R.layout.fragment_upload_job_status,container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if(getArguments() != null) {
            long uploadJobId = getArguments().getLong(STATE_UPLOAD_JOB_ID);
            uploadJob = new ForegroundJobLoadActor(requireContext()).getActiveForegroundJob(uploadJobId);
        }
        if(savedInstanceState != null) {
            long uploadJobId = savedInstanceState.getLong(STATE_UPLOAD_JOB_ID);
            uploadJob = new ForegroundJobLoadActor(requireContext()).getActiveForegroundJob(uploadJobId);
        }

        if(uploadJob == null) {
            Logging.log(Log.INFO, TAG, "removing from activity as no upload job to show status for");
            getParentFragmentManager().popBackStack();
            return;
        }

        HashSet<Uri> filesAwaitingUpload = uploadJob.getFilesAwaitingUpload();
        HashSet<Uri> filesMidTransfer = uploadJob.getFilesMidTransfer();
        HashSet<Uri> filesAwaitingVerification = uploadJob.getFilesAwaitingVerification();
        HashSet<Uri> filesAwaitingConfiguration = uploadJob.getFilesAwaitingConfiguration();
        HashSet<Uri> filesFinishedWith = uploadJob.getFilesWithoutFurtherActionNeeded();
        HashSet<Uri> filesNeedDeletingFromServer = uploadJob.getFilesRequiringDeleteFromServer();
        boolean tempAlbumNeedsDelete = uploadJob.getTemporaryUploadAlbumId() > 0;

        TextView textView = view.findViewById(R.id.files_awaiting_transfer);
        textView.setText(getString(R.string.file_count_pattern, filesAwaitingUpload.size()));

        textView = view.findViewById(R.id.files_mid_data_transfer);
        textView.setText(getString(R.string.file_count_pattern,filesMidTransfer.size()));

        textView = view.findViewById(R.id.files_awaiting_verification);
        textView.setText(getString(R.string.file_count_pattern,filesAwaitingVerification.size()));

        textView = view.findViewById(R.id.files_awaiting_configuration);
        textView.setText(getString(R.string.file_count_pattern,filesAwaitingConfiguration.size()));

        textView = view.findViewById(R.id.files_requiring_server_deletion);
        textView.setText(getString(R.string.file_count_pattern,filesNeedDeletingFromServer.size()));

        textView = view.findViewById(R.id.files_fully_uploaded);
        textView.setText(getString(R.string.file_count_pattern,filesFinishedWith.size()));

        filesAwaitingUpload.removeAll(filesMidTransfer);

        MaterialCheckboxTriState checkBox = view.findViewById(R.id.temporary_album_needs_deleting);
        checkBox.setChecked(tempAlbumNeedsDelete);

        textView = view.findViewById(R.id.errors_encountered_list_label);
        CustomExpandableListView errorsEncounteredList = view.findViewById(R.id.errors_encountered_list);

        if(uploadJob.hasErrors()) {
            textView.setVisibility(View.VISIBLE);
            errorsEncounteredList.setVisibility(View.VISIBLE);
            errorsEncounteredList.setAdapter(UploadJobErrorsListAdapter.newAdapter(getContext(), uploadJob.getCopyOfErrors()));
        } else {
            textView.setVisibility(View.GONE);
            errorsEncounteredList.setVisibility(View.GONE);
        }

    }

}
