package x.ale.incubeertor;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class BrewActivity extends AppCompatActivity {

    Bundle extras;
    XYPlot plot;
    BluetoothService mService;
    boolean mBound = false;

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("BrewActivity", "onCreate");
        setContentView(R.layout.activity_brew);
        extras = getIntent().getExtras();

        // Bind to LocalService
        bindBluetoothService();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mService != null && mService.pulledRunTimes != null){ //if there is a connection and there is available data
                        updateGUI();
                    }
//                    else {
//                        bindBluetoothService();
//                    }
                }
            });
            }
        }, 0, 2000); //put here time 1000 milliseconds=1 second
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void bindBluetoothService() {
        Intent intent = new Intent(this, BluetoothService.class);
        intent.putExtra("profile", (String) extras.get("profile"));
        intent.putExtra("btDevice", (BluetoothDevice) extras.get("btDevice"));
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private void updateGUI() {
        Log.d("function call", "updateGUI");
        updateTextInfo();
        updatePlot();
    }

    private void updateTextInfo() {
        if (mService.pulledRunTimes.size() >0) {
            TextView timeText = (TextView) findViewById(R.id.time);
            timeText.setText("Run Time: " + mService.pulledRunTimes.get(mService.pulledRunTimes.size() - 1).toString() + " ms");
        }

        if (mService.pulledTargetTemps.size() >0) {
            TextView targetTempText = (TextView) findViewById(R.id.target_temp_text);
            targetTempText.setText("Target Temperature: " + mService.pulledTargetTemps.get(mService.pulledTargetTemps.size() - 1).toString());
        }

        if (mService.temp1s.size() >0) {
            TextView temp1Text = (TextView) findViewById(R.id.temp1_text);
            temp1Text.setText("Temperature 1: " + mService.temp1s.get(mService.temp1s.size() - 1).toString());
        }

        if (mService.temp2s.size() >0) {
            TextView temp2Text = (TextView) findViewById(R.id.temp2_text);
            temp2Text.setText("Temperature 2: " + mService.temp2s.get(mService.temp2s.size() - 1).toString());
        }

        if (mService.arduinoStates.size() >0) {
            TextView stateText = (TextView) findViewById(R.id.state_text);
            stateText.setText("State: " + mService.arduinoStates.get(mService.arduinoStates.size() - 1).toString());
        }
    }

    private void updatePlot() {
        XYSeries targetTempSeries;
        XYSeries temp1Series;
        XYSeries temp2Series;
        // TODO: add arduino state to graph if possible

        plot = (XYPlot) findViewById(R.id.plot);

        List<Float> x = new ArrayList<Float>();
        List<Float> y = new ArrayList<Float>();
        for (List<Float> step : mService.profile.steps){
            x.add(step.get(0));
            y.add(step.get(1));
        }
        targetTempSeries = new SimpleXYSeries(x, y, "");

        x = new ArrayList<Float>();
        for (int idx = 0; idx<mService.pulledRunTimes.size(); idx++){
            x.add(mService.pulledRunTimes.get(idx).floatValue()/(1000*60*60*24)); // convert ms to days
        }
        temp1Series = new SimpleXYSeries(x, mService.temp1s, "");
        temp2Series= new SimpleXYSeries(x, mService.temp2s, "");


        plot.clear();
        plot.addSeries(targetTempSeries, new LineAndPointFormatter(Color.GREEN, Color.GREEN, null, null));
        plot.addSeries(temp1Series, new LineAndPointFormatter(Color.BLUE, Color.BLUE, null, null));
        plot.addSeries(temp2Series, new LineAndPointFormatter(Color.GRAY, Color.GRAY, null, null));

        plot.redraw();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothService.MyBluetoothBinder binder = (BluetoothService.MyBluetoothBinder) service;
            mService = binder.getService();

            if(mService != null) {
                Log.d("service-bind", "Service is bonded successfully!");
            }
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {mBound = false;
        }
    };
}
