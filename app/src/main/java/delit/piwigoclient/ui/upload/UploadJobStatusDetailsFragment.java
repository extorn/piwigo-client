package delit.piwigoclient.ui.upload;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import delit.libs.core.util.Logging;
import delit.libs.ui.view.button.MaterialCheckboxTriState;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.BasePiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;

public class UploadJobStatusDetailsFragment<F extends UploadJobStatusDetailsFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> {
    private static final String STATE_UPLOAD_JOB_ID = "uploadJobId";
    private static final String TAG = "UpJobStatFrag";
    private UploadJob uploadJob;

    public static UploadJobStatusDetailsFragment newInstance(UploadJob job) {
        UploadJobStatusDetailsFragment fragment = new UploadJobStatusDetailsFragment();
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
        View v = inflater.inflate(R.layout.fragment_upload_job_status,container, false);

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if(getArguments() != null) {
            long uploadJobId = getArguments().getLong(STATE_UPLOAD_JOB_ID);
            uploadJob = BasePiwigoUploadService.getActiveForegroundJob(getContext(), uploadJobId);
        }
        if(savedInstanceState != null) {
            long uploadJobId = savedInstanceState.getLong(STATE_UPLOAD_JOB_ID);
            uploadJob = BasePiwigoUploadService.getActiveForegroundJob(getContext(), uploadJobId);
        }

        if(uploadJob == null) {
            Logging.log(Log.INFO, TAG, "removing from activity as no upload job to show status for");
            getParentFragmentManager().popBackStack();
            return;
        }

        ArrayList<Uri> filesAwaitingUpload = uploadJob.getFilesAwaitingUpload();
        HashSet<Uri> filesMidTransfer = uploadJob.getFilesWithStatus(UploadJob.UPLOADING);
        HashSet<Uri> filesAwaitingVerification = uploadJob.getFilesWithStatus(UploadJob.UPLOADED);
        HashSet<Uri> filesAwaitingConfiguration = uploadJob.getFilesWithStatus(UploadJob.VERIFIED);
        HashSet<Uri> filesFinishedWith = uploadJob.getFilesWithStatus(UploadJob.CONFIGURED);
        HashSet<Uri> filesNeedDeletingFromServer = uploadJob.getFilesWithStatus(UploadJob.REQUIRES_DELETE);
        boolean tempAlbumNeedsDelete = uploadJob.getTemporaryUploadAlbum() > 0;

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
        ListView errorsEncounteredList = view.findViewById(R.id.errors_encountered_list);

        if(uploadJob.hasErrors()) {
            textView.setVisibility(View.VISIBLE);
            errorsEncounteredList.setVisibility(View.VISIBLE);
            errorsEncounteredList.setAdapter(new UploadJobErrorsListAdapter(uploadJob.getErrors()));
        } else {
            textView.setVisibility(View.GONE);
            errorsEncounteredList.setVisibility(View.GONE);
        }

    }

    private static class UploadJobErrorsListAdapter extends BaseAdapter {

        private final SimpleDateFormat piwigoDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        private final ArrayList<Map.Entry<Date, String>> dataIndex;

        public UploadJobErrorsListAdapter(LinkedHashMap<Date,String> errors) {
            this.dataIndex = new ArrayList<>();
            dataIndex.addAll(errors.entrySet());
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getCount() {
            return dataIndex.size();
        }

        @Override
        public Object getItem(int position) {
            return dataIndex.get(position);
        }

        @Override
        public long getItemId(int position) {
            return dataIndex.get(position).getKey().getTime();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            Map.Entry<Date, String> thisDataItem = dataIndex.get(position);

            View v = convertView;
            if(v == null) {
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_list_item_simple, parent, false);
            }
            TextView view = v.findViewById(R.id.list_item_name);
            view.setText(piwigoDateFormat.format(thisDataItem.getKey()));

            view = v.findViewById(R.id.list_item_details);
            view.setText(thisDataItem.getValue());

            return v;
        }
    }
}
