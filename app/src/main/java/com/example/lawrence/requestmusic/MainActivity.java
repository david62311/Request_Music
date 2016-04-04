/************************************************************
 * MainActivity for Request Music (tentative title)         *
 * Main view for the Request Music app                      *
 * Connects to the specified server                         *
 * Populates the screen with controls                       *
 * 															*
 * by Lawrence Bouzane (inexpensive on github)				*
 ************************************************************/

/**
 * Provides the classes necessary to create an Android client to communicate with the DJ Music Manager.
 */
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
    private String[] results = new String[]{"Search for a song!"};
    private ArrayAdapter<String> mSearchResultsAdapter, mPlaylistAdapter;
    private TextView currentlyPlaying;
    private ProgressBar songProgressBar;
    private TextView elapsed, duration;
    private boolean attemptedSearch = false;

    /**
     * Populates the view and sets up action listeners when the Activity is created.
     * @param savedInstanceState The saved instance state bundle.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //set the view to the activity_main xml file.
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //add the add to playlist button and set up its action listener
        Button addButton = (Button) findViewById(R.id.addButton);
        if (addButton != null) {
            addButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //only does something if a search has been attempted.
                    if (attemptedSearch) {
                        try {
                            outToServer.writeObject("add");
                            outToServer.writeObject(searchResults.getSelectedItemPosition());
                            //update the playlist once the song is added to show the result of the button press!
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

        //add the skip request button and set up its action listener
        Button skipButton = (Button) findViewById(R.id.skipButton);
        if (skipButton != null) {
            skipButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        outToServer.writeObject("skip");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Snackbar.make(view, "skip request sent", Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show();
                }
            });
        }

        //add the record message button and set up its action listener
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
                        //set text back to the initial state
                        recordButton.setText(R.string.record_start);
                        //stop and send the recording
                        stopRecording();
                        sendRecording();
                        recording = false;
                    }
                    else {
                        //set the button to show how to stop the recording and start recording
                        recordButton.setText(R.string.record_stop);
                        startRecording();
                        recording = true;

                    }
                }
            });
        }


        //set up the currently playing TextView
        currentlyPlaying = (TextView) findViewById(R.id.textView);

        //set up the search results Spinner and its adapter
        searchResults = (Spinner) findViewById(R.id.spinner);
        mSearchResultsAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, new ArrayList<>(Arrays.asList(results)));
        searchResults.setAdapter(mSearchResultsAdapter);


        //set up the playlist adapter and the playlist ListView
        mPlaylistAdapter = new ArrayAdapter<>(this, R.layout.list_item_playlist, R.id.list_item_playlist_textview, new ArrayList<String>());

        ListView playlist = (ListView) findViewById(R.id.listView);
        if (playlist != null) {
            playlist.setAdapter(mPlaylistAdapter);
        }

        //set up the progress bar and TextViews
        songProgressBar = (ProgressBar) findViewById(R.id.mainProgressBar);
        elapsed = (TextView) findViewById(R.id.mainElapsed);
        duration = (TextView) findViewById(R.id.mainDuration);


    }

    /**
     * Create the options menu.
     * @param menu The menu to create.
     * @return The options menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        //add the search button and its action listener
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

    /**
     * Handles what to do when an options
     * @param item The selected options item.
     * @return Whether something has happened.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();


        //the admin login is pressed -- start the login fragment
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_admin) {
           DialogFragment fragment = new AdminSigninFragment();
            fragment.show(getFragmentManager(), "test");
            return true;
        }

        //refresh is pressed -- refresh the playlist
        else if(id == R.id.refresh){
            refresh();
        }

        //settings is pressed -- open the settings view
        else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Connect to the server socket on startup.
     */
    @Override
    public void onStart() {
        super.onStart();
        //get server IP address from the stored preferences.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String address = prefs.getString(getString(R.string.server_address_key), "1.1.1.1");
        if (!connected) connectToDJServer(address);

        UpdateTask updateTask = new UpdateTask();
        updateTask.execute();
        ProgressTask progressTask = new ProgressTask();
        progressTask.execute();


    }

    /**
     * Disconnect from the server when the Activity is stopped.
     */
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

    /**
     * Connect to the given IP address via a ConnectTask.
     * @param ip The server IP.
     */
    private void connectToDJServer(String ip) {
        ConnectTask task = new ConnectTask();
        task.execute(ip);
    }

    /**
     * Checks if the system is connected. If it isn't open the settings menu.
     */
    private void checkIfConnected() {
        if(outToServer == null) startActivity(new Intent(this, SettingsActivity.class));
    }

    /**
     * Get the search results and put it on the Spinner.
     */
    private void readSearchResults(){
        SearchResultsTask task = new SearchResultsTask();
        task.execute();
    }

    /**
     * Refresh the playlist.
     */
    private void refresh() {
        UpdateTask task = new UpdateTask();
        task.execute();
    }

    /**
     * Handles the connection to the server.
     */
    public class ConnectTask extends AsyncTask<String, Void, Void> {

        /**
         * Connect to the given IP.
         * @param params The server IP.
         * @return Nothing.
         */
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

    /**
     * Disconnects from the server.
     */
    public class DisconnectTask extends AsyncTask<Void, Void, Void> {
        /**
         * Disconnect from the server.
         * @param params Nothing.
         * @return Nothing.
         */
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

    /**
     * Updates the playlist view.
     */
    public class UpdateTask extends AsyncTask<Void, Void, String[]> {
        /**
         * Send a playlist command to the server and gives back the result.
         * @param params Nothing.
         * @return A String array containing the playlist details.
         */
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
        /**
         * Update the playlist adapter.
         * @param result The result from the doInBackground task.
         */
        @Override
        protected void onPostExecute(String[] result) {
            //update the playlist adapter
            mPlaylistAdapter.clear();
            if (result != null) {
                currentlyPlaying.setText(result[0]);

                for (int i = 1; i < result.length; i++) {
                    mPlaylistAdapter.add(result[i]);
                }
            }
            else {
                currentlyPlaying.setText("No song currently playing");
                mPlaylistAdapter.add("Search for and add a song!");
            }
        }
    }

    /**
     * Gets the search results and updates the spinner
     */
    public class SearchResultsTask extends AsyncTask<Void, Void, String[]> {
        /**
         * Gets the search results from the server and returns them.
         * @param params Nothing.
         * @return A String array containing the search results.
         */
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

        /**
         * Updates the search results spinner
         * @param result The results from the server.
         */
        @Override
        protected void onPostExecute(String[] result) {
            //update the search results Spinner
            mSearchResultsAdapter.clear();
            if (result != null) {
                for(String searchStr : result) {
                    mSearchResultsAdapter.add(searchStr);
                }
                //set selection to the first result and update the attemptedSearch status.
                searchResults.setSelection(0);
                attemptedSearch = true;
            }
        }
    }

    /**
     * Start recording a message from the microphone as a .m4a file.
     */
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

    /**
     * Stop recording the message and turn off the microphone.
     */
    private void stopRecording() {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
    }

    /**
     * Send the recorded .m4a to the file as an array of bytes.
     */
    private void sendRecording() {
        try {
            checkIfConnected();
            outToServer.writeObject("message");
            File output = new File(mFilename);
            //set up the byte array
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

    /**
     * Updates the progress bar and TextViews.
     */
    public class ProgressTask extends AsyncTask<Void, Void, int[]> {

        /**
         * Get the progress details from the server and return them.
         * @param params Nothing.
         * @return An int array containing the relevant details.
         */
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
        /**
         * Update the progress bar and TextViews with results from the server.
         * @param result The progress information.
         */
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
            //if elapsedTime is a multiple of 10000 update the playlist info!
            //this corresponds to 10 second intervals
            if (elapsedTime % 10000 == 0) {
                UpdateTask updateTask = new UpdateTask();
                updateTask.execute();
            }
            //rerun this task (if the system is not closed)
            if (connected) {
                ProgressTask progressTask = new ProgressTask();
                progressTask.execute();
            }
        }

        /**
         * Converts an integer time into a String readable in the form mm:ss.
         * @param time The time to be converted.
         * @return The String formatted time.
         */
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
