package delit.piwigoclient.ui.upload;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.BasePiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.ui.common.button.AppCompatCheckboxTriState;
import delit.piwigoclient.ui.common.fragment.MyFragment;

public class UploadJobStatusDetailsFragment extends MyFragment {
    private static final String STATE_UPLOAD_JOB_ID = "uploadJobId";
    private UploadJob uploadJob;

    public static UploadJobStatusDetailsFragment newInstance(UploadJob job) {
        UploadJobStatusDetailsFragment fragment = new UploadJobStatusDetailsFragment();
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
        View v = inflater.inflate(R.layout.fragment_upload_job_status,null);

        if(getArguments() != null) {
            long uploadJobId = getArguments().getLong(STATE_UPLOAD_JOB_ID);
            uploadJob = BasePiwigoUploadService.getActiveForegroundJob(getContext(), uploadJobId);
        }
        if(savedInstanceState != null) {
            long uploadJobId = savedInstanceState.getLong(STATE_UPLOAD_JOB_ID);
            uploadJob = BasePiwigoUploadService.getActiveForegroundJob(getContext(), uploadJobId);
        }

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if(uploadJob == null) {
            getFragmentManager().popBackStack();
        }

        ArrayList<File> filesAwaitingUpload = uploadJob.getFilesAwaitingUpload();
        HashSet<File> filesMidTransfer = uploadJob.getFilesWithStatus(UploadJob.UPLOADING);
        HashSet<File> filesAwaitingVerification = uploadJob.getFilesWithStatus(UploadJob.UPLOADED);
        HashSet<File> filesAwaitingConfiguration = uploadJob.getFilesWithStatus(UploadJob.VERIFIED);
        HashSet<File> filesFinishedWith = uploadJob.getFilesWithStatus(UploadJob.CONFIGURED);
        HashSet<File> filesNeedDeletingFromServer = uploadJob.getFilesWithStatus(UploadJob.REQUIRES_DELETE);
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

        AppCompatCheckboxTriState checkBox = view.findViewById(R.id.temporary_album_needs_deleting);
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
            this.dataIndex = new ArrayList<Map.Entry<Date, String>>();
            for(Map.Entry<Date,String> d : errors.entrySet()) {
                dataIndex.add(d);
            }
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

            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_simple_list_item, null);
            TextView view = v.findViewById(R.id.name);
            view.setText(piwigoDateFormat.format(thisDataItem.getKey()));

            view = v.findViewById(R.id.details);
            view.setText(thisDataItem.getValue() != null ? thisDataItem.getValue() : "???");

            return v;
        }
    }
}
