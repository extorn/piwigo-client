package delit.piwigoclient.model.piwigo;

import delit.libs.util.ObjectUtils;

public class ExifDataItem {
    private String title;
    private String value;

    public ExifDataItem(String title, String value) {
        this.title = title;
        this.value = value;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof ExifDataItem)) {
            return false;
        }
        ExifDataItem other = (ExifDataItem) obj;
        return ObjectUtils.areEqual(title, other.title) && ObjectUtils.areEqual(value, other.value);
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : title.hashCode();
    }
}
