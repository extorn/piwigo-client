package delit.piwigoclient.ui.file;

import androidx.documentfile.provider.DocumentFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexpDocumentFileFilter implements DocumentFileFilter {

    private Matcher matcher;

    public RegexpDocumentFileFilter withFilenamePattern(String filenameRegexp) {
        Pattern filenamePattern = Pattern.compile(filenameRegexp);
        matcher = filenamePattern.matcher("");
        return this;
    }

    @Override
    public boolean accept(DocumentFile f) {
        String name = f.getName();
        if(name == null) {
            return false;
        }
        return matcher.reset(name).matches();
    }
}
