package delit.piwigoclient.model.piwigo;

import com.google.gson.JsonElement;

/**
 * Created by gareth on 30/12/17.
 */

public class PiwigoJsonResponse {
    private String stat;
    private int err;
    private String message;
    private JsonElement result;

    public String getStat() {
        return stat;
    }

    public void setStat(String stat) {
        this.stat = stat;
    }

    public int getErr() {
        return err;
    }

    public void setErr(int err) {
        this.err = err;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public JsonElement getResult() {
        return result;
    }

    public void setResult(JsonElement result) {
        this.result = result;
    }
}
