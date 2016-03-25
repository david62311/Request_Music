package com.example.lawrence.requestmusic;

import android.os.AsyncTask;
import android.os.Bundle;
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
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private final String LOG_TAG = MainActivity.class.getSimpleName();

    private GoogleApiClient client;
    private Socket serverSocket, currentlyPlayingSocket;
    private ObjectInputStream inFromServer, inFromCurrentlyPlaying;
    private ObjectOutputStream outToServer;
    private boolean connected = false;
    private Spinner searchResults;
    private String[] results = new String[]{"search first by"};
    private ArrayAdapter<String> mSearchResultsAdapter;
    private TextView currentlyPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ImageButton playButton = (ImageButton) findViewById(R.id.playButton);
        if (playButton != null) {
            playButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //send a play command to the server
                    try {
                        if (connected()) outToServer.writeObject("play");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Snackbar.make(view, "Playing the song :)", Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show();
                }
            });
        }

        ImageButton pauseButton = (ImageButton) findViewById(R.id.pauseButton);
        if (pauseButton != null) {
            pauseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        outToServer.writeObject("pause");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Snackbar.make(view, "Pausing the song :(", Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show();
                }
            });
        }

        Button addButton = (Button) findViewById(R.id.addButton);
        if (addButton != null) {
            addButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        outToServer.writeObject("add");
                        outToServer.writeObject(searchResults.getSelectedItemPosition());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Snackbar.make(view, "added to playlist", Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show();
                }
            });
        }

        ImageButton skipButton = (ImageButton) findViewById(R.id.skipButton);
        if (skipButton != null) {
            skipButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Snackbar.make(view, "skip request sent", Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show();
                }
            });
        }


        currentlyPlaying = (TextView) findViewById(R.id.textView);

        searchResults = (Spinner) findViewById(R.id.spinner);
        mSearchResultsAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, new ArrayList<>(Arrays.asList(results)));
        searchResults.setAdapter(mSearchResultsAdapter);



        connectToDJServer("192.168.0.61");

        UpdateTask updateTask = new UpdateTask();
        updateTask.execute();


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
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!connected) connectToDJServer("192.168.0.61");


    }

    @Override
    public void onStop() {
        super.onStop();
        //tells the server to close the socket.
        try {
            outToServer.writeObject("close");
        } catch (IOException e) {
            e.printStackTrace();
        }
        DisconnectTask task = new DisconnectTask();
        task.execute();
        connected = false;

    }

    private void connectToDJServer(String ip) {
        ConnectTask task = new ConnectTask();
        task.execute(ip);
    }

    private void readSearchResults(){
        SearchResultsTask task = new SearchResultsTask();
        task.execute();
    }

    private boolean connected(){
        return connected;
    }

    private void setConnected(boolean bool){
        connected = bool;
    }



    //handles the connection to the server socket
    public class ConnectTask extends AsyncTask<String, Void, Void> {

        private final String LOG_TAG = ConnectTask.class.getSimpleName();
        @Override
        protected Void doInBackground(String... params){
            if (params.length == 0) return null;

            try {
                serverSocket = new Socket(params[0], 1729);
                currentlyPlayingSocket = new Socket(params[0], 1729);
                inFromServer = new ObjectInputStream(serverSocket.getInputStream());
                outToServer = new ObjectOutputStream(serverSocket.getOutputStream());
                inFromCurrentlyPlaying = new ObjectInputStream(currentlyPlayingSocket.getInputStream());
                setConnected(true);
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

    public class UpdateTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... params) {
            try {
                outToServer.writeObject("curr");
                return (String) inFromServer.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                currentlyPlaying.setText(result);
                // New data is back from the server.  Hooray!
            }
        }
    }

    //handles the connection to the server socket
    public class SearchResultsTask extends AsyncTask<Void, Void, String[]> {

        private final String LOG_TAG = SearchResultsTask.class.getSimpleName();
        @Override
        protected String[] doInBackground(Void... params){
            String[] out = new String[1];
            try {
                Log.v(LOG_TAG, "attempting");
                out = (String[]) inFromServer.readObject();
                Log.v(LOG_TAG, Integer.toString(out.length));
            } catch (IOException e) {
                e.printStackTrace();
                Log.v(LOG_TAG, "io");
            } catch (ClassNotFoundException e){
                e.printStackTrace();
                Log.v(LOG_TAG, "cnfe");
            }
            return out;

        }
        @Override
        protected void onPostExecute(String[] result) {
            if (result != null) {
                mSearchResultsAdapter.clear();
                for(String dayForecastStr : result) {
                    mSearchResultsAdapter.add(dayForecastStr);
                }
                // New data is back from the server.  Hooray!
            }
        }
    }


}
