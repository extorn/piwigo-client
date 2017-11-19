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

public class CustomListSingleOnly extends ArrayAdapter<String>{
    private final Activity context;
    private final String[] web;
    String ParentFolder;
    public CustomListSingleOnly(Activity context, String[] web ,String path) {
        super(context, R.layout.list_single_only, web);
        this.context = context;
        this.web = web;
            ParentFolder = path;
    }

    @NonNull
    @Override
    public View getView(int position, View view, @NonNull ViewGroup parent) {
        LayoutInflater inflater = context.getLayoutInflater();
        View rowView= inflater.inflate(R.layout.list_single_only, null, true);
        TextView txtTitle = rowView.findViewById(R.id.txt);
        ImageView imageView = rowView.findViewById(R.id.img);
        txtTitle.setText(web[position]);
        if((new File(ParentFolder+"/"+web[position])).isDirectory()){
            imageView.setImageResource(R.drawable.folder);//sets to folder
        }else if((new File(ParentFolder+"/"+web[position])).isFile()) {//sets to file
            PicassoFactory.getInstance(context).getPicassoSingleton().load(
                    new File(
                            ParentFolder + "/" + web[position]
                    )).placeholder(R.drawable.document_gray).resize(50, 50).into(imageView);
        }
        return rowView;
    }

}

