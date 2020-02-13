package delit.piwigoclient.model.piwigo;

public class ServerConfig {
    private boolean commentsAllowed;
    private boolean anonymousCommentsAllowed;
    private boolean commentsAuthorMandatory;
    private boolean commentsEmailMandatory;
    private boolean galleryLocked;
    private String galleryTitle;
    private boolean ratingAllowed;
    private boolean anonymousRatingAllowed;
    private boolean commentsUserDeletable;
    private boolean commentsUserEditable;

    public boolean isCommentsAllowed() {
        return commentsAllowed;
    }

    public void setCommentsAllowed(boolean commentsAllowed) {
        this.commentsAllowed = commentsAllowed;
    }

    public boolean isAnonymousCommentsAllowed() {
        return anonymousCommentsAllowed;
    }

    public void setAnonymousCommentsAllowed(boolean anonymousCommentsAllowed) {
        this.anonymousCommentsAllowed = anonymousCommentsAllowed;
    }

    public boolean isCommentsAuthorMandatory() {
        return commentsAuthorMandatory;
    }

    public void setCommentsAuthorMandatory(boolean commentsAuthorMandatory) {
        this.commentsAuthorMandatory = commentsAuthorMandatory;
    }

    public boolean isCommentsEmailMandatory() {
        return commentsEmailMandatory;
    }

    public void setCommentsEmailMandatory(boolean commentsEmailMandatory) {
        this.commentsEmailMandatory = commentsEmailMandatory;
    }

    public boolean isGalleryLocked() {
        return galleryLocked;
    }

    public void setGalleryLocked(boolean galleryLocked) {
        this.galleryLocked = galleryLocked;
    }

    public String getGalleryTitle() {
        return galleryTitle;
    }

    public void setGalleryTitle(String galleryTitle) {
        this.galleryTitle = galleryTitle;
    }

    public boolean isRatingAllowed() {
        return ratingAllowed;
    }

    public void setRatingAllowed(boolean ratingAllowed) {
        this.ratingAllowed = ratingAllowed;
    }

    public boolean isAnonymousRatingAllowed() {
        return anonymousRatingAllowed;
    }

    public void setAnonymousRatingAllowed(boolean anonymousRatingAllowed) {
        this.anonymousRatingAllowed = anonymousRatingAllowed;
    }

    public boolean isCommentsUserDeletable() {
        return commentsUserDeletable;
    }

    public void setCommentsUserDeletable(boolean commentsUserDeletable) {
        this.commentsUserDeletable = commentsUserDeletable;
    }

    public boolean isCommentsUserEditable() {
        return commentsUserEditable;
    }

    public void setCommentsUserEditable(boolean commentsUserEditable) {
        this.commentsUserEditable = commentsUserEditable;
    }
}
