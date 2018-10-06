package delit.piwigoclient.ui.events;

public class ToolbarEvent {
    private String title;
    private boolean expandToolbarView;
    private boolean contractToolbarView;

    public void setExpandToolbarView(boolean expandToolbarView) {
        this.expandToolbarView = expandToolbarView;
        this.contractToolbarView = false;
    }

    public void setContractToolbarView(boolean contractToolbarView) {
        this.contractToolbarView = contractToolbarView;
        this.expandToolbarView = false;
    }

    public boolean isExpandToolbarView() {
        return expandToolbarView;
    }

    public boolean isContractToolbarView() {
        return contractToolbarView;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

}
