package delit.piwigoclient.model.piwigo;

import android.text.Spanned;

import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Created by gareth on 07/04/18.
 */

public class PiwigoUtils {

    private PiwigoUtils() {
    }

    public static <T extends Identifiable> HashSet<Long> toSetOfIds(Collection<T> identifiables) {
        if (identifiables == null) {
            return null;
        }
        HashSet<Long> ids = new HashSet<>(identifiables.size());
        for (T identifiable : identifiables) {
            ids.add(identifiable.getId());
        }

        return ids;
    }

    public static <T extends Identifiable> boolean containsItemWithId(ArrayList<T> items, long id) {
        for (T item : items) {
            if (item.getId() == id ) {
                return true;
            }
        }
        return false;
    }

    public static <T extends Identifiable> void removeAll(HashSet<T> items, HashSet<Long> itemIds) {
        for (Iterator<T> it = items.iterator(); it.hasNext(); ) {
            T r = it.next();
            if (itemIds.contains(r.getId())) {
                it.remove();
            }
        }
    }

    public static @Nullable Spanned getSpannedHtmlText(@Nullable String rawText) {
        if(rawText == null) {
            return null;
        }
        Spanned text =  HtmlCompat.fromHtml(rawText.replaceAll("\n\n|\r\n\r\n", "<div/>"), HtmlCompat.FROM_HTML_OPTION_USE_CSS_COLORS);
        return text;
    }

    public static @Nullable String getResourceDescriptionOutsideAlbum(@Nullable String description) {
        return description; // the below is handled inside the server
//        String desc = description;
//        if(desc == null) {
//            return null;
//        }
//        // support for the extended description plugin.
//        int idx = Math.max(desc.indexOf("<!--more-->"), desc.indexOf("<!--complete-->"));
//        idx = Math.max(idx, desc.indexOf("<!--up-down-->"));
//        if(idx > 0) {
//            desc = desc.substring(0, idx);
//        }
//        return desc;
    }

    public static @Nullable String getResourceDescriptionInsideAlbum(@Nullable String description) {
        return description; // the below is handled inside the server
//        String desc = description;
//        if(desc == null) {
//            return null;
//        }
//        // support for the extended description plugin.
//        String[] parts = desc.split("<!--more-->|<!--complete-->|<!--up-down-->");
//        if(parts.length == 2) {
//            if(desc.indexOf("<!--more") == parts[0].length()) {
//                desc = parts[0] + parts[1];
//            } else if(desc.indexOf("<!--complete") == parts[0].length()) {
//                desc = parts[1];
//            } else if(desc.indexOf("<!--up-down") == parts[0].length()) {
//                desc = parts[0] + parts[1];
//            }
//        }
//        return desc;
    }
}
