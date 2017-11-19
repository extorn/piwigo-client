package paul.arian.fileselector;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.delit.PicassoFactory;

import java.io.File;

public class CustomList extends ArrayAdapter<String>{
    private final Activity context;
    private final String[] web;
    String ParentFolder;
    public CustomList(Activity context, String[] web,String path) {
        super(context, R.layout.list_single, web);
        this.context = context;
        this.web = web;
        ParentFolder = path;
    }
    @NonNull
    @Override
    public View getView(int position, View view, @NonNull ViewGroup parent) {
        LayoutInflater inflater = context.getLayoutInflater();
        View rowView= inflater.inflate(R.layout.list_single, null, true);
        TextView txtTitle = rowView.findViewById(R.id.txt);
        ImageView imageView = rowView.findViewById(R.id.img);
        txtTitle.setText(web[position]);
        PicassoFactory.getInstance(context).getPicassoSingleton().load(
                new File(
                        ParentFolder+"/"+web[position]
                )).placeholder(R.drawable.document).resize(50, 50).into(imageView);
        return rowView;
    }
}
