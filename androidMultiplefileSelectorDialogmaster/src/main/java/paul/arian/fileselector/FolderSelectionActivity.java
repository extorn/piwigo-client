package paul.arian.fileselector;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.commonsware.cwac.merge.MergeAdapter;

public class FolderSelectionActivity extends Activity {

    private static final String TAG = "FileSelection";
    private static final String FILES_TO_UPLOAD = "upload";
    File mainPath = new File(Environment.getExternalStorageDirectory()+"");
    private ArrayList<File> resultFileList;

    private ListView directoryView;
    private ArrayList<File> directoryList = new ArrayList<>();
    private ArrayList<String> directoryNames = new ArrayList<>();
    private ArrayList<String> fileNames = new ArrayList<>();
    Button ok, all,cancel,storage,New;
    TextView path;




    Boolean switcher = false;
    String primary_sd;
    String secondary_sd;

    int index = 0;
    int top = 0;
    private AlertDialog alertDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_selection);
        //getActionBar().setDisplayHomeAsUpEnabled(true);

        directoryView = findViewById(R.id.directorySelectionList);
        ok = findViewById(R.id.ok);
        all = findViewById(R.id.all);
        cancel = findViewById(R.id.cancel);
        storage = findViewById(R.id.storage);
        New = findViewById(R.id.New);
        path = findViewById(R.id.folderpath);

        all.setEnabled(false);


        loadLists();

        ExtStorageSearch();
        if(secondary_sd==null){
            storage.setEnabled(false);
        }

        directoryView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                index = directoryView.getFirstVisiblePosition();
                View v = directoryView.getChildAt(0);
                top = (v == null) ? 0 : v.getTop();

                File lastPath = mainPath;
                try {
                    if (position < directoryList.size()) {
                        mainPath = directoryList.get(position);
                        loadLists();
                    }
                }catch(Throwable e){
                    mainPath = lastPath;
                    loadLists();
                }
            }
        });

        ok.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                ok();
            }
        });

        cancel.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                finish();
            }
        });

        storage.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                try {
                    if (!switcher) {
                        mainPath = new File(secondary_sd);
                        loadLists();
                        switcher = true;
                        storage.setText(getString(R.string.Int));
                    } else {
                        mainPath = new File(primary_sd);
                        loadLists();
                        switcher = false;
                        storage.setText(getString(R.string.ext));
                    }
                }catch (Throwable e){
                    if(BuildConfig.DEBUG) {
                        Log.e(TAG, "Setting onClickListener", e);
                    }
                }
            }
        });

        New.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                AlertDialog.Builder alert = new AlertDialog.Builder(v.getContext());
                alert.setTitle( getString(R.string.New) );
                alert.setMessage( getString(R.string.CNew) );

                final EditText input = new EditText(v.getContext());
                alert.setView(input);

                alert.setPositiveButton(getString(R.string.create), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String fileName = input.getText().toString();
                        // Verify if a value has been entered.
                        if(fileName != null && fileName.length() > 0) {
                            // Notify the listeners.
                            File newFolder = new File(mainPath.getPath()+"/"+fileName+"/");
                            newFolder.mkdirs();
                            loadLists();
                        }
                    }
                });
                alert.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Do nothing, automatically the dialog is going to be closed.
                    }
                });

                // Show the dialog.
                alert.show();

            }
        });
    }

    @Override
    public void onBackPressed() {
        try {
            if(mainPath.equals(Environment.getExternalStorageDirectory().getParentFile().getParentFile())){
                finish();
            }else{
                File parent = mainPath.getParentFile();
                mainPath = parent;
                loadLists();
                directoryView.setSelectionFromTop(index, top);
            }

        }catch (Throwable e){
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "backPressed", e);
            }
        }
    }

    public void ok(){
        Intent result = this.getIntent();
        result.putExtra(FILES_TO_UPLOAD, mainPath);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    private void loadLists(){
        FileFilter fileFilter = new FileFilter() {
            public boolean accept(File file) {
                return file.isFile();
            }
        };
        FileFilter directoryFilter = new FileFilter(){
            public boolean accept(File file){
                return file.isDirectory();
            }
        };

        //if(mainPath.exists() && mainPath.length()>0){
            //Lista de directorios
            File[] tempDirectoryList = mainPath.listFiles(directoryFilter);

        if (tempDirectoryList != null && tempDirectoryList.length > 1) {
            Arrays.sort(tempDirectoryList, new Comparator<File>() {
                @Override
                public int compare(File object1, File object2) {
                    return object1.getName().compareTo(object2.getName());
                }
            });
        }


        directoryList = new ArrayList<>();
            directoryNames = new ArrayList<>();
            for(File file: tempDirectoryList){
                directoryList.add(file);
                directoryNames.add(file.getName());
            }
            ArrayAdapter<String> directoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, directoryNames);
            directoryView.setAdapter(directoryAdapter);

            //Lista de ficheros
            File[] tempFileList = mainPath.listFiles(fileFilter);

        if (tempFileList != null && tempFileList.length > 1) {
            Arrays.sort(tempFileList, new Comparator<File>() {
                @Override
                public int compare(File object1, File object2) {
                    return object1.getName().compareTo(object2.getName());
                }
            });
        }

        ArrayList<File> fileList = new ArrayList<>();
            fileNames = new ArrayList<>();
            for(File file : tempFileList){
                fileList.add(file);
                fileNames.add(file.getName());
            }



            path.setText(mainPath.toString());
            iconload();
       // }
    }

    public void iconload(){
        String[] foldernames = new String[directoryNames.size()];
        foldernames = directoryNames.toArray(foldernames);

        String[] filenames = new String[fileNames.size()];
        filenames = fileNames.toArray(filenames);

        CustomListSingleOnly adapter1 = new CustomListSingleOnly(FolderSelectionActivity.this, directoryNames.toArray(foldernames), mainPath.getPath());
        CustomListSingleOnly adapter2 = new CustomListSingleOnly(FolderSelectionActivity.this, fileNames.toArray(filenames), mainPath.getPath());


        MergeAdapter adap = new MergeAdapter();

        adap.addAdapter(adapter1);
        adap.addAdapter(adapter2);


        directoryView.setAdapter(adap);
    }

    public void ExtStorageSearch() {


        //First Attempt
        primary_sd = System.getenv("EXTERNAL_STORAGE");
        secondary_sd = System.getenv("SECONDARY_STORAGE");


        if (primary_sd == null) {
            primary_sd = Environment.getExternalStorageDirectory() + "";
        }
        if (secondary_sd == null) {//if fail, search among known list of extStorage Locations
            for (String string : getStorageDirectories(this)) {
                if ((new File(string)).exists() && (new File(string)).isDirectory() && !(string.equals(primary_sd))) {
                    secondary_sd = string;
                    break;
                }
            }
        }

    }

    private static final Pattern DIR_SEPORATOR = Pattern.compile("/");

    /**
     * Returns all available SD-Cards in the system (include emulated)
     * <p/>
     * Warning: Hack! Based on Android source code of version 4.3 (API 18)
     * Because there is no standard way to get it.
     * Edited by hendrawd
     *
     * @return paths to all available SD-Cards in the system (include emulated)
     */
    public static String[] getStorageDirectories(Context context) {
        // Final set of paths
        final Set<String> rv = new HashSet<>();
        // Primary physical SD-CARD (not emulated)
        final String rawExternalStorage = System.getenv("EXTERNAL_STORAGE");
        // All Secondary SD-CARDs (all exclude primary) separated by ":"
        final String rawSecondaryStoragesStr = System.getenv("SECONDARY_STORAGE");
        // Primary emulated SD-CARD
        final String rawEmulatedStorageTarget = System.getenv("EMULATED_STORAGE_TARGET");
        if (TextUtils.isEmpty(rawEmulatedStorageTarget)) {
            //fix of empty raw emulated storage on marshmallow
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                File[] files = context.getExternalFilesDirs(null);
                for (File file : files) {
                    String applicationSpecificAbsolutePath = file.getAbsolutePath();
                    String emulatedRootPath = applicationSpecificAbsolutePath.substring(0, applicationSpecificAbsolutePath.indexOf("Android/data"));
                    rv.add(emulatedRootPath);
                }
            } else {
                // Device has physical external storage; use plain paths.
                if (TextUtils.isEmpty(rawExternalStorage)) {
                    // EXTERNAL_STORAGE undefined; falling back to default.
                    rv.addAll(Arrays.asList(getPhysicalPaths()));
                } else {
                    rv.add(rawExternalStorage);
                }
            }
        } else {
            // Device has emulated storage; external storage paths should have
            // userId burned into them.
            final String rawUserId;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                rawUserId = "";
            } else {
                final String path = Environment.getExternalStorageDirectory().getAbsolutePath();
                final String[] folders = DIR_SEPORATOR.split(path);
                final String lastFolder = folders[folders.length - 1];
                boolean isDigit = false;
                try {
                    Integer.valueOf(lastFolder);
                    isDigit = true;
                } catch (NumberFormatException ignored) {
                }
                rawUserId = isDigit ? lastFolder : "";
            }
            // /storage/emulated/0[1,2,...]
            if (TextUtils.isEmpty(rawUserId)) {
                rv.add(rawEmulatedStorageTarget);
            } else {
                rv.add(rawEmulatedStorageTarget + File.separator + rawUserId);
            }
        }
        // Add all secondary storages
        if (!TextUtils.isEmpty(rawSecondaryStoragesStr)) {
            // All Secondary SD-CARDs splited into array
            final String[] rawSecondaryStorages = rawSecondaryStoragesStr.split(File.pathSeparator);
            Collections.addAll(rv, rawSecondaryStorages);
        }
        return rv.toArray(new String[rv.size()]);
    }

    /**
     * @return physicalPaths based on phone model
     */
    private static String[] getPhysicalPaths() {
        return new String[]{
                "/storage/sdcard0",
                "/storage/sdcard1",                 //Motorola Xoom
                "/storage/extsdcard",               //Samsung SGS3
                "/storage/sdcard0/external_sdcard", //User request
                "/mnt/extsdcard",
                "/mnt/sdcard/external_sd",          //Samsung galaxy family
                "/mnt/external_sd",
                "/mnt/media_rw/sdcard1",            //4.4.2 on CyanogenMod S3
                "/removable/microsd",               //Asus transformer prime
                "/mnt/emmc",
                "/storage/external_SD",             //LG
                "/storage/ext_sd",                  //HTC One Max
                "/storage/removable/sdcard1",       //Sony Xperia Z1
                "/data/sdext",
                "/data/sdext2",
                "/data/sdext3",
                "/data/sdext4",
                "/sdcard1",                         //Sony Xperia Z
                "/sdcard2",                         //HTC One M8s
                "/storage/microsd"                  //ASUS ZenFone 2
        };
    }




}
