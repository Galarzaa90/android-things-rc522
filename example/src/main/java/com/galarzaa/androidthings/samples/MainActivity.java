package com.galarzaa.androidthings.samples;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
            SpiDevice spiDevice = pioService.openSpiDevice("SPI0.0");
            Gpio resetPin = pioService.openGpio("BCM25");
            mRrc522 = new Rc522(spiDevice, resetPin);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private class RfidTask extends AsyncTask<Object, Object, byte[]> {
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
                boolean success = rc522.request();
                if(success){
                    success = rc522.antiCollisionDetect();
                    if(success){
                        return rc522.getUuid();
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
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
