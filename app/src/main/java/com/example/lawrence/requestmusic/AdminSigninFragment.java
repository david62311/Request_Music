package com.example.lawrence.requestmusic;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;


public class AdminSigninFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.fragment_admin_signin, null);
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


    public class LoginTask extends AsyncTask<String, Void, Boolean> {
        MainActivity mActivity;

        public LoginTask(MainActivity mActivity){
            this.mActivity = mActivity;
        }

        @Override
        protected Boolean doInBackground(String... params){
            try {
                //connect to the server to verify password
                Socket serverSocket = new Socket("192.168.0.61", 1729);
                Socket currentlyPlayingSocket = new Socket("192.168.0.61", 1729);
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
        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                if(isAdded() && mActivity != null) {
                    startActivity(new Intent(mActivity, AdminActivity.class));
                }
            }
            //else TODO: notify user that login failed

        }
    }
}

