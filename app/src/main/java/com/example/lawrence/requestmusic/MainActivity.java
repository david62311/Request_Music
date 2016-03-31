package com.example.lawrence.requestmusic;

import android.app.DialogFragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static String mFilename = Environment.getExternalStorageDirectory().getAbsolutePath().concat("/test.m4a");
    private MediaRecorder mRecorder = null;


    private Socket serverSocket, currentlyPlayingSocket;
    private ObjectInputStream inFromServer, inFromCurrentlyPlaying;
    private ObjectOutputStream outToServer;
    private boolean connected = false;
    private boolean recording = false;
    private boolean playing = false;
    private Spinner searchResults;
    private String[] results = new String[]{"search first by"};
    private ArrayAdapter<String> mSearchResultsAdapter, mPlaylistAdapter;
    private TextView currentlyPlaying;
    private ProgressBar songProgressBar;
    private TextView elapsed, duration;
    private boolean attemptedSearch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Button addButton = (Button) findViewById(R.id.addButton);
        if (addButton != null) {
            addButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (attemptedSearch) {
                        try {
                            outToServer.writeObject("add");
                            outToServer.writeObject(searchResults.getSelectedItemPosition());
                            UpdateTask task = new UpdateTask();
                            task.execute();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        Snackbar.make(view, "added to playlist", Snackbar.LENGTH_SHORT)
                                .setAction("Action", null).show();
                    }
                }
            });
        }

        Button skipButton = (Button) findViewById(R.id.skipButton);
        if (skipButton != null) {
            skipButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Snackbar.make(view, "skip request sent", Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show();
                }
            });
        }

        final Button recordButton = (Button) findViewById(R.id.recordButton);

        if (recordButton != null) {
            recordButton.setOnClickListener(new View.OnClickListener() {
                @Override
            public void onClick(View view){
                    if (recording){
                        //change the button text to make it seem like the system is doing something other than sleeping
                        recordButton.setText("Sending...");
                        //sleep the thread in order to not cut off the last part of the recorded message!
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        recordButton.setText(R.string.record_start);
                        stopRecording();
                        sendRecording();
                        recording = false;
                    }
                    else {
                        recordButton.setText(R.string.record_stop);
                        startRecording();
                        recording = true;

                    }
                }
            });
        }


        currentlyPlaying = (TextView) findViewById(R.id.textView);

        searchResults = (Spinner) findViewById(R.id.spinner);
        mSearchResultsAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, new ArrayList<>(Arrays.asList(results)));
        searchResults.setAdapter(mSearchResultsAdapter);



        mPlaylistAdapter = new ArrayAdapter<>(this, R.layout.list_item_playlist, R.id.list_item_playlist_textview, new ArrayList<String>());

        ListView playlist = (ListView) findViewById(R.id.listView);
        if (playlist != null) {
            playlist.setAdapter(mPlaylistAdapter);
        }

        songProgressBar = (ProgressBar) findViewById(R.id.mainProgressBar);
        elapsed = (TextView) findViewById(R.id.mainElapsed);
        duration = (TextView) findViewById(R.id.mainDuration);


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        final SearchView searchBox = (SearchView) menu.findItem(R.id.search).getActionView();
        searchBox.setQueryHint("Search for a song");
        searchBox.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                try {
                    outToServer.writeObject("search");
                    outToServer.writeObject(searchBox.getQuery().toString());
                    readSearchResults();

                } catch (IOException e) {
                    e.printStackTrace();
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();



        //noinspection SimplifiableIfStatement
        if (id == R.id.action_admin) {
           DialogFragment fragment = new AdminSigninFragment();
            fragment.show(getFragmentManager(), "test");
            return true;
        }

        else if(id == R.id.refresh){
            refresh();
        }

        else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String address = prefs.getString(getString(R.string.server_address_key), "1.1.1.1");
        if (!connected) connectToDJServer(address);

        UpdateTask updateTask = new UpdateTask();
        updateTask.execute();
        ProgressTask progressTask = new ProgressTask();
        progressTask.execute();


    }

    @Override
    public void onStop() {
        super.onStop();
        //tells the server to close the socket.
        try {
            outToServer.writeObject("close");
            inFromServer.close();
            inFromCurrentlyPlaying.close();
            outToServer.close();
            connected = false;
            attemptedSearch = false;
            playing = false;
        } catch (IOException e) {
            e.printStackTrace();
        }

        DisconnectTask task = new DisconnectTask();
        task.execute();

    }

    private void connectToDJServer(String ip) {
        ConnectTask task = new ConnectTask();
        task.execute(ip);
    }

    private void checkIfConnected() {
        if(outToServer == null) startActivity(new Intent(this, SettingsActivity.class));
    }

    private void readSearchResults(){
        SearchResultsTask task = new SearchResultsTask();
        task.execute();
    }

    private void refresh() {
        UpdateTask task = new UpdateTask();
        task.execute();
    }

    //handles the connection to the server socket
    public class ConnectTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params){
            if (params.length == 0) return null;

            try {
                serverSocket = new Socket(params[0], 1729);
                if (serverSocket.isConnected()) {
                    currentlyPlayingSocket = new Socket(params[0], 1729);
                    //currentlyPlayingSocket.setSoTimeout(115); //set a 115ms timeout in order to make the other stuff work on startup :)
                    inFromServer = new ObjectInputStream(serverSocket.getInputStream());
                    outToServer = new ObjectOutputStream(serverSocket.getOutputStream());
                    inFromCurrentlyPlaying = new ObjectInputStream(currentlyPlayingSocket.getInputStream());
                    connected = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;

        }
    }

    public class DisconnectTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                serverSocket.close();
                currentlyPlayingSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public class UpdateTask extends AsyncTask<Void, Void, String[]> {
        @Override
        protected String[] doInBackground(Void... params) {
            checkIfConnected();
            try {
                outToServer.writeObject("playlist");
                return (String[]) inFromServer.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(String[] result) {
            mPlaylistAdapter.clear();
            if (result != null) {
                currentlyPlaying.setText(result[0]);

                for (int i = 1; i < result.length; i++) {
                    mPlaylistAdapter.add(result[i]);
                }

                // New data is back from the server.  Hooray!
            }
            else {
                currentlyPlaying.setText("No song currently playing");
                mPlaylistAdapter.add("Search for and add a song!");
            }
        }
    }

    //handles the connection to the server socket
    public class SearchResultsTask extends AsyncTask<Void, Void, String[]> {

        @Override
        protected String[] doInBackground(Void... params){
            checkIfConnected();
            String[] out = new String[1];
            try {
                out = (String[]) inFromServer.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
            return out;

        }
        @Override
        protected void onPostExecute(String[] result) {
            if (result != null) {
                mSearchResultsAdapter.clear();
                for(String searchStr : result) {
                    mSearchResultsAdapter.add(searchStr);
                }
                // New data is back from the server.  Hooray!
                searchResults.setSelection(0);
                attemptedSearch = true;
            }
        }
    }

    private void startRecording() {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setOutputFile(mFilename);

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e(LOG_TAG, mFilename);
            Log.e(LOG_TAG, "prepare() failed");
        }

        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
    }

    //TODO: make sure it does not go over 10 seconds in length! (check size of file before writing it)
    private void sendRecording() {
        try {
            checkIfConnected();
            outToServer.writeObject("message");
            File output = new File(mFilename);
            byte[] myByteArray = new byte[(int) output.length()];
            FileInputStream fis = new FileInputStream(output);
            BufferedInputStream bis = new BufferedInputStream(fis);
            //noinspection ResultOfMethodCallIgnored
            bis.read(myByteArray, 0, myByteArray.length);
            outToServer.writeObject(myByteArray);
        } catch (IOException e) {
            e.printStackTrace();
        }
        UpdateTask task = new UpdateTask();
        task.execute();
    }

    public class ProgressTask extends AsyncTask<Void, Void, int[]> {
        @Override
        protected int[] doInBackground(Void... params) {
            checkIfConnected();
            try {
                if(!playing) {
                    outToServer.writeObject("playing");
                    playing = (boolean) inFromServer.readObject();
                }
                if (playing) {
                    return (int[]) inFromCurrentlyPlaying.readObject();
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(int[] result) {
            int elapsedTime = -1;
            int durationTime;
            if (result != null) {
                durationTime = result[0];
                elapsedTime = result[1];
                songProgressBar.setMax(durationTime);
                songProgressBar.setProgress(elapsedTime);
                duration.setText(convertTimeToString(durationTime));
                elapsed.setText(convertTimeToString(elapsedTime));

            }
            //if elapsedtime is a multiple of 10000 update the playlist info!
            //this corresponds to 10 second intervals
            if (elapsedTime % 10000 == 0) {
                UpdateTask updateTask = new UpdateTask();
                updateTask.execute();
            }
            //rerun this task (if the system is not closed
            if (connected) {
                ProgressTask progressTask = new ProgressTask();
                progressTask.execute();
            }
        }

        private String convertTimeToString(int time){
            int seconds = time / 1000 % 60;
            int minutes = time / 60000;
            if (seconds < 10) {
                return minutes + ":0" + seconds;
            }
            return minutes + ":" + seconds;
        }
    }

}
