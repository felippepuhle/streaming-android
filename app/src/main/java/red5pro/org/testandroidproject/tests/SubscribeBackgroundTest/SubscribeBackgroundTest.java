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
package red5pro.org.testandroidproject.tests.SubscribeBackgroundTest;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.red5pro.streaming.view.R5VideoView;

import red5pro.org.testandroidproject.R;
import red5pro.org.testandroidproject.TestDetailFragment;

/**
 * Created by davidHeimann on 12/6/17.
 */

public class SubscribeBackgroundTest extends TestDetailFragment {

    private boolean shouldClean = false;
    private R5VideoView display;
    private Intent subIntent;
    private SubscribeService subService;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bg_subscribe_test, container, false);

        //find the view and attach the stream
        display = (R5VideoView) view.findViewById(R.id.videoView);

        //Bind to service - will create if doesn't exist
        subIntent = new Intent(getActivity(), SubscribeService.class);
        detectToStartService();

        Button endButton = (Button) view.findViewById(R.id.endButton);
        endButton.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if( event.getAction() == MotionEvent.ACTION_UP ){
                    shouldClean = true;
                    getActivity().onBackPressed();
                    return true;
                }
                return false;
            }
        });

        return view;
    }

    private void detectToStartService(){
        boolean found = false;
        ActivityManager actManager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        try {
            for (ActivityManager.RunningServiceInfo serviceInfo : actManager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceInfo.service.getClassName().equals(SubscribeService.class.getName())) {
                    found = true;
                }
            }
        }catch (NullPointerException e){}

        if(!found){
            getActivity().startService(subIntent);
        }

        getActivity().bindService(subIntent, subServiceConnection, Context.BIND_IMPORTANT);
    }

    private ServiceConnection subServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            subService = ((SubscribeService.SubscribeServiceBinder)service).getService();

            subService.setDisplay(display);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            subService = null;
        }
    };

    @Override
    public void onResume() {
        if(subService != null)
        {
            subService.setDisplayOn(true);
        }
        super.onResume();
    }

    @Override
    public void onStop() {

        if(subService != null) {
            subService.setDisplayOn(false);
        }

        if(shouldClean()) {
            getActivity().unbindService(subServiceConnection);
            getActivity().stopService(subIntent);
            subService = null;
        }

        super.onStop();
    }

    @Override
    public void onDestroy() {
        if(subService != null){
            getActivity().unbindService(subServiceConnection);
            getActivity().stopService(subIntent);
            subService = null;
        }

        super.onDestroy();
    }

    @Override
    public Boolean shouldClean() {
        return shouldClean;
    }
}
