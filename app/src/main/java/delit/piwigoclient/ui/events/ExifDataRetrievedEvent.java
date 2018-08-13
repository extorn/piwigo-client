package delit.piwigoclient.ui.events;

import com.drew.metadata.Metadata;

public class ExifDataRetrievedEvent {
    String uri;
    Metadata metadata;

    public ExifDataRetrievedEvent(String uri, Metadata metadata) {
        this.uri = uri;
        this.metadata = metadata;
    }

    public String getUri() {
        return uri;
    }

    public Metadata getMetadata() {
        return metadata;
    }
}
