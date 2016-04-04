/************************************************************
 * AdminActivity for Request Music (tentative title)  		*
 * view with admin controls for the Request Music app       *
 * 															*
 * by Lawrence Bouzane (inexpensive on github)				*
 ************************************************************/

/**
 * Provides the classes necessary to create an Android client to communicate with the DJ Music Manager.
 */
package com.example.lawrence.requestmusic;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

public class AdminActivity extends AppCompatActivity {

    private static final String LOG_TAG = AdminActivity.class.getSimpleName();
    private Socket serverSocket, currentlyPlayingSocket;
    private ObjectInputStream inFromServer, inFromCurrentlyPlaying;
    private ObjectOutputStream outToServer;
    private boolean connected = false;
    private ArrayAdapter<String> mPlaylistAdapter;
    private ProgressBar songProgressBar;
    private TextView elapsed, duration;


    /**
     * Populates the view and sets up action listeners when the Activity is created.
     * @param savedInstanceState The saved instance state bundle.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //set the view to the activity_admin xml file.
        setContentView(R.layout.activity_admin);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //add the play button and set its action listener.
        ImageButton playButton = (ImageButton) findViewById(R.id.admin_play_button);
        if (playButton != null) {
            playButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //send a play command to the server
                    try {
                        if (connected) outToServer.writeObject("play");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Snackbar.make(view, "Playing the song :)", Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show();
                }
            });
        }

        //add the pause button and set its action listener
        ImageButton pauseButton = (ImageButton) findViewById(R.id.admin_pause_button);
        if (pauseButton != null) {
            pauseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (connected) try {
                        outToServer.writeObject("pause");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Snackbar.make(view, "Pausing song...", Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show();
                }
            });
        }

        //add the skip button and set its action listener
        ImageButton skipButton = (ImageButton) findViewById(R.id.admin_skip_button);
        if (skipButton != null) {
            skipButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (connected) try {
                        outToServer.writeObject("adminSkip");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Snackbar.make(view, "Skipping song...", Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show();
                }
            });
        }

        //set up the adapter to populate the playlist Listview.
        mPlaylistAdapter = new ArrayAdapter<>(this, R.layout.list_item_playlist, R.id.list_item_playlist_textview, new ArrayList<String>());

        //set up the playlist Listview and set its adapter.
        ListView playlist = (ListView) findViewById(R.id.playlist_view);
        if (playlist != null) {
            playlist.setAdapter(mPlaylistAdapter);
        }

        //set up the progress bar and the text elapsed and durations.
        songProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        elapsed = (TextView) findViewById(R.id.elapsed);
        duration = (TextView) findViewById(R.id.duration);
    }

    /**
     * Connect to the server once the app's view is created, and update the playlist.
     */
    @Override
    public void onStart() {
        super.onStart();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String address = prefs.getString(getString(R.string.server_address_key), "1.1.1.1");
        if (!connected) connectToDJServer(address);
        UpdateTask task = new UpdateTask();
        task.execute();
        ProgressTask progressTask = new ProgressTask();
        progressTask.execute();

    }

    /**
     * close the connection to the server when the app is stopped.
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
        } catch (IOException e) {
            e.printStackTrace();
        }
        DisconnectTask task = new DisconnectTask();
        task.execute();
        connected = false;

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
                currentlyPlayingSocket = new Socket(params[0], 1729);
                inFromServer = new ObjectInputStream(serverSocket.getInputStream());
                outToServer = new ObjectOutputStream(serverSocket.getOutputStream());
                inFromCurrentlyPlaying = new ObjectInputStream(currentlyPlayingSocket.getInputStream());
                connected = true;
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
                connected = false;
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
            //if not null, fill the adapter with the results entries.
            if (result != null) {
                mPlaylistAdapter.clear();
                for (String playlistEntry : result) {
                    mPlaylistAdapter.add(playlistEntry);
                }
            }
        }
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
            try {
                return (int[]) inFromCurrentlyPlaying.readObject();
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
