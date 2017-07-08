package com.galarzaa.androidthings.samples;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.galarzaa.androidthings.Rc522;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.SpiDevice;

import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private Rc522 mRrc522;
    RfidTask mRfidTask;
    private TextView mTag;
    private TextView mResultsDescription;
    private Button button;

    private SpiDevice spiDevice;
    private Gpio gpioReset;

    private static final String SPI_PORT = "SPI0.0";
    private static final String PIN_RESET = "BCM25";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mResultsDescription = (TextView)findViewById(R.id.results_description);
        mTag = (TextView)findViewById(R.id.tag);
        button = (Button)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRfidTask = new RfidTask(mRrc522);
                mRfidTask.execute();
                ((Button)v).setText(R.string.reading);
            }
        });
        PeripheralManagerService pioService = new PeripheralManagerService();
        try {
            spiDevice = pioService.openSpiDevice(SPI_PORT);
            gpioReset = pioService.openGpio(PIN_RESET);
            mRrc522 = new Rc522(spiDevice, gpioReset);
        } catch (IOException e) {
            Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try{
            if(spiDevice != null){
                spiDevice.close();
            }
            if(gpioReset != null){
                gpioReset.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class RfidTask extends AsyncTask<Object, Object, byte[]> {
        private static final String TAG = "RfidTask";
        private Rc522 rc522;

        RfidTask(Rc522 rc522){
            this.rc522 = rc522;
        }

        @Override
        protected void onPreExecute() {
            button.setEnabled(false);
        }

        @Override
        protected byte[] doInBackground(Object... params) {
            while(true){
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
                //Check if a RFID tag has been found
                if(!rc522.request()){
                    continue;
                }
                //Check for collision errors
                if(!rc522.antiCollisionDetect()){
                    continue;
                }
                byte[] uuid = rc522.getUid();
                rc522.selectTag(uuid);
                //We're trying to read block 1 in sector 2
                byte block = Rc522.getBlockAddress(2,1);
                //Mifare cards default key, this won't work if the key for the tag has been previously changed
                byte[] key = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
                //Data that will be written to block 1, sector 2
                byte[] newData = {0x0F,0x0E,0x0D,0x0C,0x0B,0x0A,0x09,0x08,0x07,0x06,0x05,0x04,0x03,0x02,0x01};
                boolean authResult = rc522.authenticateCard(Rc522.AUTH_A, block, key);
                if(authResult){
                    if(rc522.writeBlock(block,newData)) {
                        Log.i(TAG, "Data written successfully.");
                    }else{
                        Log.w(TAG, "Could not write to tag");
                    }
                    //Byte buffer to hold data
                    byte[] buff = new byte[16];
                    if(rc522.readBlock(block,buff)) {
                        Log.i(TAG, "Data read: " + Arrays.toString(buff));
                    }else{
                        Log.w(TAG, "Could not read data from tag");
                    }
                    rc522.stopCrypto();
                }else{
                    Log.e(TAG,"Could not authenticate tag.");
                }
                return uuid;
            }
        }

        @Override
        protected void onPostExecute(byte[] result) {
            StringBuilder sb = new StringBuilder();
            String prefix = "";
            Log.i("RfidTask",Arrays.toString(result));
            for(byte b : result){
                int ubyte = b&0xff;
                if(ubyte == 0){
                    break;
                }
                sb.append(prefix);
                prefix = "-";
                sb.append(ubyte);

            }
            button.setEnabled(true);
            button.setText(R.string.start);
            mTag.setText(sb.toString());
            mResultsDescription.setVisibility(View.VISIBLE);
            mTag.setVisibility(View.VISIBLE);
        }
    }
}
