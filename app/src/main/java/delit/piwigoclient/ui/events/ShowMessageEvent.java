package delit.piwigoclient.ui.events;

import androidx.annotation.StringRes;

public class ShowMessageEvent extends SingleUseEvent {

    public static final int TYPE_INFO = 0;
    public static final int TYPE_WARN = 1;
    public static final int TYPE_ERROR = 2;

    private String message;
    private @StringRes int titleResId;
    private int type;

    /**
     *
     * @param type
     * @param titleResId String resource ID
     * @param message
     */
    public ShowMessageEvent(int type, @StringRes int titleResId, String message) {
        this.type = type;
        if(type != TYPE_INFO && type != TYPE_ERROR && type != TYPE_WARN) {
            throw new IllegalArgumentException("Message type must be one of declared constants in ShowMessageEvent class");
        }
        this.titleResId = titleResId;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public @StringRes int getTitleResId() {
        return titleResId;
    }

    public int getType() {
        return type;
    }
}
