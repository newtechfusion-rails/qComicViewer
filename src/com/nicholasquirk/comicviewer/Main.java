package com.nicholasquirk.comicviewer;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

/**
 *
 * @author Nicholas Quirk
 *
 */
public class Main extends Activity {

    protected static final String lastPageFile = "last_page.txt";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.browse_filesystem:
                showFileSystemBrowser();
                break;
            case R.id.continue_last:
                Intent i = loadLastPage();
                if (i != null) {
                    startActivity(i);
                } else {
                    Toast.makeText(getApplicationContext(), "No comic is currently open...", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.quit_application:
                terminate();
                break;
            case R.id.about:
                showAbout();
                break;
        }
        return false;
    }

    public void terminate() {
        super.onDestroy();
        this.finish();
    }

    private void showFileSystemBrowser() {
        Intent i = new Intent(this, FileSystem.class);
        startActivity(i);
    }

    private void showAbout() {
        Intent i = new Intent(this, About.class);
        startActivity(i);
    }

    private Intent loadLastPage() {
        try {
            Intent i = new Intent(getBaseContext(), ImageViewer.class);
            FileInputStream fis = openFileInput(Main.lastPageFile);
            DataInputStream in = new DataInputStream(fis);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            String imagesPath = br.readLine();
            String currImageName = br.readLine();
            Integer currImageIndex = new Integer(br.readLine());

            if (imagesPath != null && currImageName != null && currImageIndex != null) {
                i.putExtra("loadLastPage", true);
                i.putExtra("imagesPath", imagesPath);
                i.putExtra("currImageName", currImageName);
                i.putExtra("currImageIndex", currImageIndex);

                return i;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
