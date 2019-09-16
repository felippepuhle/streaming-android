//
// Copyright © 2015 Infrared5, Inc. All rights reserved.
//
// The accompanying code comprising examples for use solely in conjunction with Red5 Pro (the "Example Code")
// is  licensed  to  you  by  Infrared5  Inc.  in  consideration  of  your  agreement  to  the  following
// license terms  and  conditions.  Access,  use,  modification,  or  redistribution  of  the  accompanying
// code  constitutes your acceptance of the following license terms and conditions.
//
// Permission is hereby granted, free of charge, to you to use the Example Code and associated documentation
// files (collectively, the "Software") without restriction, including without limitation the rights to use,
// copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
// persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The Software shall be used solely in conjunction with Red5 Pro. Red5 Pro is licensed under a separate end
// user  license  agreement  (the  "EULA"),  which  must  be  executed  with  Infrared5,  Inc.
// An  example  of  the EULA can be found on our website at: https://account.red5pro.com/assets/LICENSE.txt.
//
// The above copyright notice and this license shall be included in all copies or portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,  INCLUDING  BUT
// NOT  LIMITED  TO  THE  WARRANTIES  OF  MERCHANTABILITY, FITNESS  FOR  A  PARTICULAR  PURPOSE  AND
// NONINFRINGEMENT.   IN  NO  EVENT  SHALL INFRARED5, INC. BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
// WHETHER IN  AN  ACTION  OF  CONTRACT,  TORT  OR  OTHERWISE,  ARISING  FROM,  OUT  OF  OR  IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
package red5pro.org.testandroidproject.tests.SubscribeReconnectTest;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.red5pro.streaming.R5Stream;
import com.red5pro.streaming.event.R5ConnectionEvent;
import com.red5pro.streaming.event.R5ConnectionListener;
import com.red5pro.streaming.view.R5VideoView;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import red5pro.org.testandroidproject.R;
import red5pro.org.testandroidproject.tests.SubscribeTest.SubscribeTest;
import red5pro.org.testandroidproject.tests.TestContent;

public class SubscribeReconnectTest extends SubscribeTest  {

    public Thread callThread;
    public boolean stopped = false;
    public int reconnectDelay = 2000;


    private void findStreams() {

        final String port = TestContent.getFormattedPortSetting(TestContent.GetPropertyString("server_port"));
        final String urlStr = "http://" + TestContent.GetPropertyString("host") + port + "/" + TestContent.GetPropertyString("context") + "/streams.jsp";

        if(callThread != null) {
            callThread.interrupt();
            callThread = null;
        }

        Log.d("SubReconnectTest", "Requesting stream list...");

        callThread = new Thread(new Runnable() {
            @Override
            public void run() {

                try {

                    URL url = new URL(urlStr);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    String responseString = "error: somehow string not assigned to?";
                    try {
                        if (urlConnection.getResponseCode() == 200 && !Thread.interrupted()) {
                            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                            StringBuilder stringBuilder = new StringBuilder();
                            String line;
                            while ((line = bufferedReader.readLine()) != null) {
                                stringBuilder.append(line);
                            }
                            responseString = stringBuilder.toString().replaceAll("\\s+", "");
                            bufferedReader.close();
                        }
                        else {
                            responseString = "error: http issue, response code - " + urlConnection.getResponseCode();
                        }
                    }
                    catch (Exception e) {
                    }
                    finally {
                        urlConnection.disconnect();
                    }

                    if(!Thread.interrupted()) {

                        if (!responseString.startsWith("error")) {

                            Log.d("SubReconnectTest", "Stream list receieved...");

                            boolean exists = false;
                            JSONArray list = new JSONArray(responseString);

                            for (int i = 0; i < list.length(); i++) {
                                if (list.getJSONObject(i).getString("name").equals( TestContent.GetPropertyString("stream1") )) {
                                    exists = true;
                                    break;
                                }
                            }

                            final boolean willConnect = exists;

                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {

                                    if(willConnect) {
                                        Log.d("SubReconnectTest", "Attempting a reconnect...");
                                        reconnect();
                                    }
                                    else {
                                        Log.d("SubReconnectTest", "Publisher does not exist.");
                                        delayReconnect(reconnectDelay);
                                    }

                                }
                            });

                        }
                        else {

                            Log.d("SubReconnectTest", "Error: " + responseString);
                            delayReconnect(reconnectDelay);

                        }
                    }

                }
                catch (Exception e) {
                    Log.d("SubReconnectTest", "Error");
                    e.printStackTrace();
                }

            }
        });
        callThread.start();

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.subscribe_test, container, false);

        //find the view and attach the stream
        display = (R5VideoView) view.findViewById(R.id.videoView);

        findStreams();

        return view;

    }

    protected void reconnect () {
        if (subscribe != null) {
            subscribe.removeListener();
        }
        Log.d("SubReconnectTest", "Let's connect!");
        Subscribe();
        SetupListener();
    }

    protected void delayReconnect (int delay) {

        Log.d("SubReconnectTest", "We'll try again in a bit...");

        final SubscribeReconnectTest subscribeTest = this;
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                subscribeTest.findStreams();
            }
        }, delay);

    }


    public void SetupListener(){

        final R5ConnectionListener additionalListener = this;
        final SubscribeReconnectTest subscribeTest = this;
        final R5Stream subscriber = this.subscribe;
        final R5VideoView view = this.display;

        subscribe.setListener(new R5ConnectionListener() {
            @Override
            public void onConnectionEvent(R5ConnectionEvent r5ConnectionEvent) {

                final R5ConnectionListener me = this;
                additionalListener.onConnectionEvent(r5ConnectionEvent);

                if (r5ConnectionEvent == R5ConnectionEvent.CLOSE && !SubscribeReconnectTest.this.stopped) {


                    Handler h = new Handler(Looper.getMainLooper());
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {

                            if(!stopped) {
                                subscribeTest.findStreams();
                            }

                        }
                    }, reconnectDelay);

                }
                else if (r5ConnectionEvent == R5ConnectionEvent.NET_STATUS && r5ConnectionEvent.message.equals("NetStream.Play.UnpublishNotify")) {

                    Handler h = new Handler(Looper.getMainLooper());

                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {

                            subscriber.setListener(null);
                            subscriber.stop();
                            view.attachStream(null);
                            subscribeTest.delayReconnect(reconnectDelay);

                        }
                    }, reconnectDelay);


                }
            }
        });
    }

    @Override
    public void onStop() {

        super.onStop();
        if (subscribe != null) {
            subscribe.removeListener();
            subscribe = null;
        }
        if(callThread != null) {
            callThread.interrupt();
            callThread = null;
        }
        stopped = true;

    }

}
