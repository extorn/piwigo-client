package delit.piwigoclient.ui.upgrade;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.database.PiwigoUploadsDatabase;
import delit.piwigoclient.database.PriorUploadRepository;
import delit.piwigoclient.database.entity.PriorUpload;
import delit.piwigoclient.database.entity.UploadDestination;
import delit.piwigoclient.database.pojo.UploadDestinationWithPriorUploads;
import delit.piwigoclient.ui.preferences.AutoUploadJobConfig;
import delit.piwigoclient.ui.preferences.AutoUploadJobsConfig;

public class PreferenceMigrator392Paid extends PreferenceMigrator {

    public PreferenceMigrator392Paid() {
        super(392);
    }

    @Override
    protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
        // No prefs as such. Just migrate the prior uploads
        migratePriorUploadsToDatabase(context, prefs);
    }

    private void migratePriorUploadsToDatabase(Context context, SharedPreferences prefs) {
        AutoUploadJobsConfig autoUploadJobsConfig = new AutoUploadJobsConfig(context);
        if(autoUploadJobsConfig.hasUploadJobs(context)) {
            PriorUploadRepository priorUploadRepository = PriorUploadRepository.getInstance(PiwigoUploadsDatabase.getInstance(context));

            List<UploadDestinationWithPriorUploads> destinations = new ArrayList<>();
            for(AutoUploadJobConfig config : autoUploadJobsConfig.getAutoUploadJobs(context)) {
                AutoUploadJobConfig.PriorUploads priorUploads = AutoUploadJobConfig.PriorUploads.loadFromFile(context, config.getJobId());
                if(priorUploads != null) {
                    destinations.add(buildPriorUploadsForDatabase(context, prefs, config, priorUploads.getFileUrisAndHashcodes()));
                }
            }
            priorUploadRepository.insertAll(destinations);

        }
    }

    private UploadDestinationWithPriorUploads buildPriorUploadsForDatabase(Context context, SharedPreferences prefs, AutoUploadJobConfig config, Map<Uri, String> fileUrisAndHashcodes) {
        ConnectionPreferences.ProfilePreferences connectionPrefs = config.getConnectionPrefs(context, prefs);
        String profileKey = connectionPrefs.getAbsoluteProfileKey(prefs, context);
        Uri serverUri = Uri.parse(Objects.requireNonNull(connectionPrefs.getPiwigoServerAddress(prefs,context)));
        UploadDestinationWithPriorUploads destination = new UploadDestinationWithPriorUploads(new UploadDestination(profileKey, serverUri));
        List<PriorUpload> uploads = new ArrayList<>(fileUrisAndHashcodes.size());
        Date now = new Date();
        for (Map.Entry<Uri, String> uriStringEntry : fileUrisAndHashcodes.entrySet()) {
            uploads.add(new PriorUpload(null, uriStringEntry.getKey(), uriStringEntry.getValue(), now));
        }
        destination.setPriorUploads(uploads);
        return destination;
    }
}
