package com.nicholasquirk.comicviewer;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

/**
 *
 * @author Nicholas Quirk
 *
 */
public class FileSystem extends Activity implements OnItemClickListener {

    private String currDirectory;
    private String sdRootDirectory;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.filesystem);

        boolean canViewStorage = isExternalStorageAvailable();

        if (canViewStorage) {
            this.sdRootDirectory = Environment.getExternalStorageDirectory().toString();
            populateCurrentDirectory(this.sdRootDirectory, false);
        }
    }

    private boolean isExternalStorageAvailable() {
        boolean mExternalStoargeAvailable = false;
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            mExternalStoargeAvailable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            mExternalStoargeAvailable = true;
        } else {
            mExternalStoargeAvailable = false;
        }
        return mExternalStoargeAvailable;
    }

    private void populateCurrentDirectory(String directory, boolean useCurrDirectory) {
        File file = null;
        File[] files = null;
        String[] items = null;

        ListView file_browser = (ListView) findViewById(R.id.filesystem_nav_view);

        if (useCurrDirectory == true) {
            file = new File(currDirectory, directory);
        } else {
            file = new File(directory);
        }

        if (file != null && file.isDirectory()) {
            this.currDirectory = file.getAbsolutePath();
            files = file.listFiles();
        }

        if (files != null) {
            items = new String[(files.length) + 1];
            items[0] = "..";
            for (int i = 1; i < files.length + 1; i++) {
                items[i] = files[i - 1].getName();
            }
        } else {
            items = new String[]{".."};
        }

        Arrays.sort(items);

        file_browser.setAdapter(new ArrayAdapter<String>(this, R.layout.list_item, items));
        file_browser.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {

        TextView tv = (TextView) arg1;
        String s = tv.getText().toString();
        File f = new File(this.currDirectory, s);

        if (isComicBookFile(f)) {
            try {
                File inflatedDirectory = null;
                if (f.getName().endsWith(".cbz") || f.getName().endsWith(".zip")) {
                    inflatedDirectory = inflateZipComicBookArchive(f);
                    launchImageViewer(inflatedDirectory.getAbsolutePath());
                } else if (f.getName().endsWith(".cbr") || f.getName().endsWith(".rar")) {
                    inflatedDirectory = inflateRarComicBookArchive(f);
                    launchImageViewer(inflatedDirectory.getAbsolutePath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            if (s.equals("..")) {
                String prevDirectory = f.getParentFile().getParentFile().getAbsolutePath();
                if (prevDirectory.contains(this.sdRootDirectory)) {
                    populateCurrentDirectory(prevDirectory, false);
                } else {
                    populateCurrentDirectory(this.sdRootDirectory, false);
                }
            } else {
                if (f.isDirectory()) {
                    populateCurrentDirectory(f.getName(), true);
                } else {
                    // random file
                }
            }
        }

    }

    private boolean isComicBookFile(File f) {
        boolean isValidFile = false;
        int extensionIndex = f.getName().lastIndexOf(".");

        if (extensionIndex != -1) {
            String ext = f.getName().substring(extensionIndex);
            if (ext.equalsIgnoreCase(".cbz") || ext.equalsIgnoreCase(".zip") || ext.equalsIgnoreCase(".rar") || ext.equalsIgnoreCase(".cbr")) {
                isValidFile = true;
            }
        }
        return isValidFile;
    }

    private File inflateRarComicBookArchive(File archive) {

        File tempDir = new File(this.sdRootDirectory, ".qComicViewer");
        tempDir.mkdirs();
        deleteDirContents(getFilesDir());
        int prefixIncrement = 1;

        Archive arch = null;
        try {
            arch = new Archive(archive);
        } catch (RarException e) {
        } catch (IOException e1) {
        }

        if (arch != null) {

            if (arch.isEncrypted()) {
                Toast.makeText(getApplicationContext(), "Archive is encryped.", Toast.LENGTH_SHORT).show();
            }

            FileHeader fh = null;

            try {
                while (true) {

                    fh = arch.nextFileHeader();

                    if (fh == null) {
                        break;
                    }
                    if (fh.isEncrypted()) {
                        continue;
                    }

                    if (fh.isDirectory()) {
                        Toast.makeText(getApplicationContext(), "Archive contains directory.", Toast.LENGTH_SHORT).show();
                    } else {
                        FileOutputStream stream = openFileOutput(String.format("%05d", prefixIncrement) + "-" + fh.getFileNameString(), Context.MODE_PRIVATE);
                        arch.extractFile(fh, stream);
                        stream.close();
                        prefixIncrement++;
                    }
                }
            } catch (IOException e) {
            } catch (RarException e) {
            }
        }

        return tempDir;
    }

    private File inflateZipComicBookArchive(File archive) throws IOException {

        InputStream is = new FileInputStream(archive);
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));
        FileOutputStream fos = null;
        File tempDir = new File(this.sdRootDirectory, ".qComicViewer");
        tempDir.mkdirs();
        deleteDirContents(getFilesDir());

        int prefixIncrement = 1;

        try {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[2048];
                int count;

                while ((count = zis.read(buffer)) != -1) {
                    baos.write(buffer, 0, count);
                }

                String filename = null;
                if (ze.getName().lastIndexOf("/") != -1) {
                    filename = ze.getName().substring(ze.getName().lastIndexOf("/") + 1);
                } else {
                    filename = ze.getName();
                }

                byte[] bytes = baos.toByteArray();

                if (isImageFile(filename)) {
                    fos = openFileOutput(String.format("%05d", prefixIncrement) + "-" + filename, Context.MODE_PRIVATE);
                    fos.write(bytes);
                    fos.flush();
                    prefixIncrement++;
                }
            }
        } finally {
            if (zis != null) {
                zis.close();
            }
            if (fos != null) {
                fos.close();
            }
        }

        return tempDir;
    }

    private void launchImageViewer(String directoryPath) {
        Intent i = new Intent(this, ImageViewer.class);
        i.putExtra("imagesPath", getFilesDir().getAbsolutePath());
        startActivity(i);
    }

    private boolean isImageFile(String filename) {
        boolean isValidFile = false;
        int extensionIndex = filename.lastIndexOf(".");

        if (extensionIndex != -1) {
            String ext = filename.substring(extensionIndex);
            if (ext.equalsIgnoreCase(".jpg") || ext.equalsIgnoreCase(".png") || ext.equalsIgnoreCase(".gif")
                    || ext.equalsIgnoreCase(".jpeg") || ext.equalsIgnoreCase(".bmp")) {
                isValidFile = true;
            }
        }
        return isValidFile;
    }

    public static void deleteDirContents(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            File f;
            for (int i = 0; i < children.length; i++) {
                f = new File(dir, children[i]);
                if (!f.getName().equals("last_page.txt")) {
                    f.delete();
                }
            }
        }
    }
}