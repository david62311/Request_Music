/************************************************************
 * AdminSigninFragment for Request Music (tentative title)  *
 * handles sending the admin password to the server         *
 * starts the AdminActivity if the password is correct      *
 * 															*
 * by Lawrence Bouzane (inexpensive on github)				*
 ************************************************************/

/**
 * Provides the classes necessary to create an Android client to communicate with the DJ Music Manager.
 */
package com.example.lawrence.requestmusic;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;


public class AdminSigninFragment extends DialogFragment {

    /**
     * Creates the dialog asking for the server password.
     * @param savedInstanceState The saved instance state bundle.
     * @return A dialog with
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.fragment_admin_signin, null);
        //add a PasswordBox to the dialog with a login option and a cancel option.
        final EditText passwordBox = (EditText) view.findViewById(R.id.password);
        builder.setView(view)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    LoginTask task = new LoginTask((MainActivity) getActivity());
                    task.execute(passwordBox.getText().toString()); //need to pull password from the thingy
                    try {
                        Thread.sleep(250); //this prevents the fragment from being removed from the activity while the password is being checked.
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    AdminSigninFragment.this.getDialog().cancel();
                }
        });
        return builder.create();
    }


    /**
     * Checks the login info and creates the AdminActivity if it is correct.
     */
    public class LoginTask extends AsyncTask<String, Void, Boolean> {
        MainActivity mActivity;

        /**
         * Start the LoginTask associated with the calling mActivity.
         * @param mActivity The activity calling the LoginTask.
         */
        public LoginTask(MainActivity mActivity){
            this.mActivity = mActivity;
        }

        /**
         * Send the adminLogin command and the entered password to the server.
         * @param params The admin password.
         * @return The status of the login.
         */
        @Override
        protected Boolean doInBackground(String... params){
            try {
                //connect to the server to verify password
                //get the stored server IP address
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
                String address = prefs.getString(getString(R.string.server_address_key), "1.1.1.1");
                //connect to the sockets and setup IO streams
                Socket serverSocket = new Socket(address, 1729);
                Socket currentlyPlayingSocket = new Socket(address, 1729);
                ObjectInputStream inFromServer = new ObjectInputStream(serverSocket.getInputStream());
                ObjectOutputStream outToServer = new ObjectOutputStream(serverSocket.getOutputStream());
                ObjectInputStream inFromCurrentlyPlaying = new ObjectInputStream(currentlyPlayingSocket.getInputStream());
                //send the login command
                outToServer.writeObject("adminLogin");
                outToServer.writeObject(params[0]);
                //get the result
                boolean out = (boolean) inFromServer.readObject();
                //close the sockets after sending password
                outToServer.writeObject("close");
                inFromServer.close();
                inFromCurrentlyPlaying.close();
                outToServer.close();
                serverSocket.close();
                currentlyPlayingSocket.close();
                return out;
            }
            catch(IOException | ClassNotFoundException e){
                e.printStackTrace();
            }
            return null;

        }

        /**
         * Check the boolean if the password was correct. If it is, start the AdminActivity.
         * Do nothing if false.
         * @param result The status of the login request.
         */
        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                if(isAdded() && mActivity != null) {
                    startActivity(new Intent(mActivity, AdminActivity.class));
                }
            }

        }
    }
}

