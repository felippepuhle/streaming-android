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
package red5pro.org.testandroidproject.tests.PublishCustomSourceTest;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.view.SurfaceHolder;

import com.red5pro.streaming.media.R5AudioController;
import com.red5pro.streaming.source.R5VideoSource;

/**
 * How to use this class. Replace the Red5Camera instance with an instance of this.
 * Aquire a YV12 image from the camera, screen shot, or other resource.
 * Call 'setImage(buffer)' to drive the video stream.
 * The image will be cropped and centered in a gradient field. within the encodeYUV420 method
 *
 * Created by Andy Shaules for iOS on 4/8/2015.
 * Adapted for Android by David Heimann on 5/11/2016
 */
public class CustomVideoSource extends R5VideoSource {
    int width = 320;
    int height = 240;
    int bpp = 12;
    byte bufferIn[];
    byte bufferOut[];
    Thread manip;
    Thread engine;
    private volatile boolean doEncode=true;
    private long streamTime = 0;
    int[] pixels = new int[320 * 240];
    boolean change = false;

    private boolean isEncoding = false;


    @Override
    protected void initSource() {
        //set the raw image input type, only YV12 is currently supported
        setFrameType(ImageFormat.YV12);

        // 10 fps is roughly how fast we can change the image data with this method
        // With something else creating the buffer of RGB data, it could be much higher
        setFramerate(10);
    }

    /**
     * This draws the 'lastImage' member into the incoming buffer while customizing pixels
     * @param yuv420sp
     * @param argb
     * @param width
     * @param height
     */
    void encodeYUV420(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;
        int vuIndex = frameSize + frameSize/4;
        int a, R, G, B, Y, U, V;
        int index = 0;
        for ( int y = 0; y < height; y++) {
            for ( int x = 0; x < width; x++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                yuv420sp[yIndex] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));

                if (y % 2 == 0 && x % 2 == 0) {
                    yuv420sp[uvIndex] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[vuIndex] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }

                yIndex++;

                if (y % 2 == 0 && x % 2 == 0) {

                    uvIndex++;
                    vuIndex++;
                }

                index ++;
            }
        }
    }

    @Override
    public void startEncoding() {

        //Ensure this is only called once
        if(isEncoding)
            return;

        isEncoding = true;

        manip = new Thread(new Runnable() {
            @Override
            public void run() {

                while(doEncode) {
                    long start = System.currentTimeMillis();
                    while (change) {
                        try {

                            Thread.sleep(10);
                        } catch (Exception e) { return; }
                    }
                    if(Thread.interrupted()){
                        return;
                    }


                    double scaledTime = start * 0.01;
                    int cursor = 0;
                    float scale = 0.04f;

                    for (int x = 0; x < 320; x++) {
                        for (int y = 0; y < 240; y++) {


                            float cx = x * scale;
                            float cy = y * scale;

                            double v = Math.sin(cx + scaledTime);

                            // Having too many trig functions slowed down the example
//                            v += Math.sin(cy + scaledTime);
//                            v += Math.sin(cx + cy + scaledTime);
//
//                            cx += scale * Math.sin(scaledTime * 0.33f);
//                            cy += scale * Math.sin(scaledTime * 0.2f);

                            v += Math.sin(Math.sqrt(cx * cx + cy * cy + 1.0) + scaledTime);

                            pixels[(y * width) + x] = ((int) (Math.sin(v * Math.PI) * 255.0)) << 16 |  //r
                                    ((int) (Math.cos(v * Math.PI) * 255.0)) << 8;  //g
                            //( 0 );  //b

                        }
                    }

                    change = true;
                    long elapsed = System.currentTimeMillis() - start;
                    if (elapsed < 100) {
                        try {

                            Thread.sleep(100 - elapsed);
                        } catch (Exception e) { return; }
                    }
                }
            }
        });
        manip.start();

        engine = new Thread( new Runnable() {
            @Override
            public void run() {
                int sizeInBits = (int) ((width * height) * bpp);
                bufferIn=new byte[sizeInBits/8];
                bufferOut=new byte[sizeInBits/8];
                int  cursor = 0;
                float scale = 0.4f;

                //a nice base gradient
                for(int o=0;o<240;o++){
                    for(int h=0;h<320;h++){

                        pixels[cursor++]= o<<16 | (240-o)<<8 |  ( (int)( h /320.0 * 255.0) & 0xFF);
                    }
                }

                // Call to encoding function : convert pixels to Yuv Binary data
                encodeYUV420(bufferIn, pixels, 320, 240);
                long startTime = System.currentTimeMillis();

                while(doEncode){

                    while (!change && doEncode){
                        try {
                            Thread.sleep(10);
                        }catch (Exception e){return;}
                    }
                    if(!doEncode || Thread.interrupted()) {
                        return;
                    }

                    encodeYUV420(bufferIn, pixels, 320, 240);
                    change = false;

                    prepareFrame(bufferIn,bufferOut);

                    double timeStamp = System.currentTimeMillis()-startTime;
                    timeStamp*=1000;

                    if(R5AudioController.getInstance().getAudioSampleTime()*1000>streamTime)
                        streamTime=R5AudioController.getInstance().getAudioSampleTime()*1000;
                    else
                        streamTime=(long)timeStamp;

                    encode(bufferOut,streamTime,false);
                }
            }
        });
        engine.start();
    }

    @Override
    public void setPreviewDisplay(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void stopEncoding() {

        doEncode=false;
        if(manip != null)
            manip.interrupt();
        if(engine != null)
            engine.interrupt();
    }
}
