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
package red5pro.org.testandroidproject.tests.TwoWayTest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.red5pro.streaming.R5Connection;
import com.red5pro.streaming.R5Stream;
import com.red5pro.streaming.R5StreamProtocol;
import com.red5pro.streaming.config.R5Configuration;
import com.red5pro.streaming.event.R5ConnectionEvent;
import com.red5pro.streaming.event.R5ConnectionListener;
import com.red5pro.streaming.event.R5RemoteCallContainer;
import com.red5pro.streaming.source.R5Camera;
import com.red5pro.streaming.source.R5Microphone;
import com.red5pro.streaming.view.R5VideoView;

import org.json.JSONArray;

import red5pro.org.testandroidproject.R;
import red5pro.org.testandroidproject.tests.PublishTest.PublishTest;
import red5pro.org.testandroidproject.tests.TestContent;

/**
 * Created by davidHeimann on 3/4/16.
 */
public class TwoWayTest extends PublishTest {
    protected R5VideoView display;
    protected R5Stream subscribe;
    protected Thread listThread;
    protected boolean isPublishing = false;
    protected boolean isSubscribing = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.twoway_test, container, false);

        preview = (R5VideoView)rootView.findViewById(R.id.videoPreview);
        // needed to keep the preview above the subscribe view on some devices, especially for 8.0+
        preview.setZOrderMediaOverlay(true);

        publish();

        publish.client = this;
        preview.setZOrderMediaOverlay(true);

        final R5ConnectionListener additionalListener = this;
        publish.setListener(new R5ConnectionListener() {
            @Override
            public void onConnectionEvent(R5ConnectionEvent r5ConnectionEvent) {

                additionalListener.onConnectionEvent(r5ConnectionEvent);

                if(r5ConnectionEvent == R5ConnectionEvent.START_STREAMING){

                    isPublishing = true;
                    sendRemoteCall();

                }

                if(r5ConnectionEvent == R5ConnectionEvent.DISCONNECTED){

                    if(isSubscribing){
                        isSubscribing = false;
                        subscribe.stop();
                        subscribe = null;
                    }

                    isPublishing = false;
                }
            }
        });

        display = (R5VideoView)rootView.findViewById(R.id.videoView);

        return rootView;
    }


    private void sendRemoteCall(){
        listThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    Thread.sleep(2500);

                    if(!Thread.interrupted()) {
                        publish.connection.call(new R5RemoteCallContainer("streams.getLiveStreams", "R5GetLiveStreams", null));
                    }
                } catch (Exception e) {
                    if(e.toString().contains("InterruptedException")) {
                        e.printStackTrace();
                    }
                    System.out.println("failed to get new streams");
                }
            }
        });
        listThread.start();

    }

    public void R5GetLiveStreams(String streams){

        if(subscribe!=null)
            return;

        System.out.println("Got the streams: "+streams);

        //parse string as JSON
        JSONArray names;
        try {
            names = new JSONArray(streams);
        } catch (Exception e) {
            System.out.println("Failed to parse streams to JSONArray");
            return;
        }

        //Look for the other stream, subscribe when available
        for(int i  = 0; i < names.length(); i++){
            try {
                if(TestContent.GetPropertyString("stream2").equals(names.getString(i))){
                    listThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(2500);

                                if(!Thread.interrupted())
                                    onSubscribeReady();

                            }catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    listThread.start();
                    return;
                }
            } catch (Exception e){
                e.printStackTrace();
                System.out.println("Item at index " + i + " cannot be retrieved as a String");
            }
        }

        listThread = new Thread(new Runnable() {
            @Override
            public void run() {

                try{
                    //the target stream hasn't been found, try again
                    Thread.sleep(1500);
                    sendRemoteCall();

                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        listThread.start();

    }

    private void onSubscribeReady( ){

        if( subscribe != null )
            return;

        System.out.println("bitrate - subscribe start");

        Handler r = new Handler(Looper.getMainLooper());
        final R5ConnectionListener additionalListener = this;

        r.post(new Runnable() {
            @Override
            public void run() {
                System.out.println("Subscribing");

                R5Configuration config = new R5Configuration(R5StreamProtocol.RTSP,
                        TestContent.GetPropertyString("host"),
                        TestContent.GetPropertyInt("port"),
                        TestContent.GetPropertyString("context"),
                        TestContent.GetPropertyFloat("subscribe_buffer_time"));
                config.setLicenseKey(TestContent.GetPropertyString("license_key"));
                config.setBundleID(getActivity().getPackageName());

                R5Connection connection = new R5Connection(config);

                //setup a new stream using the connection
                subscribe = new R5Stream(connection);

                //show all logging
                subscribe.setLogLevel(R5Stream.LOG_LEVEL_DEBUG);

                //find the view and attach the stream
                display.attachStream(subscribe);

                display.showDebugView(TestContent.GetPropertyBool("debug_view"));

                R5ConnectionListener listener = new R5ConnectionListener() {
                    @Override
                    public void onConnectionEvent(R5ConnectionEvent r5ConnectionEvent) {

//                        additionalListener.onConnectionEvent(r5ConnectionEvent);
                        Log.d("Subscriber", ":onConnectionEvent " + r5ConnectionEvent.name());

                        if(r5ConnectionEvent == R5ConnectionEvent.START_STREAMING){

                            isSubscribing = true;
                        }

                        if(r5ConnectionEvent == R5ConnectionEvent.ERROR){

                            subscribe.stop();
                            subscribe = null;
                            isSubscribing = false;
                            sendRemoteCall();
                        }

                        if(r5ConnectionEvent == R5ConnectionEvent.DISCONNECTED){

                            if(isSubscribing) {
                                isSubscribing = false;
                                subscribe.stop();
                                subscribe = null;
                            }

                        }
                    }
                };
                subscribe.setListener(listener);

                //Unlike basic subscription, two-way needs echo cancellation, which needs the subscriber and publisher
                //to use the same Audio Controller - instead of recreating it for stability, we delay the subscriber
                subscribe.play(TestContent.GetPropertyString("stream2"));

                killListThread();
            }
        });

    }

    protected void killListThread() {
        if(listThread != null){
            listThread.interrupt();
            listThread = null;
        }
    }

    @Override
    public void onStop() {
        killListThread();
        if(subscribe != null && isSubscribing) {
            isSubscribing = false;
            subscribe.stop();
            subscribe = null;
        }
        this.stopPublish(publishTestListener);

        super.onStop();
    }

    @Override
    public Boolean isPublisherTest () {
        return false;
    }
}
