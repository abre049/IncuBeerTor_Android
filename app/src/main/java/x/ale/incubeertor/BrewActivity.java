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
                    if (mService != null){
                        updateGUI();
                    } else {
                        bindBluetoothService();
                    }
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
        updateTextInfo();
        updatePlot();
    }

    private void updateTextInfo() {
        TextView timeText = (TextView) findViewById(R.id.time);
        timeText.setText("Run Time: " + mService.runTime.toString() + " ms");

        TextView targetTempText = (TextView) findViewById(R.id.target_temp_text);
        targetTempText.setText("Target Temperature: " + mService.currentTargetTemp.toString());

        if (mService.temp1.size() >0) {
            TextView temp1Text = (TextView) findViewById(R.id.temp1_text);
            temp1Text.setText("Temperature 1: " + mService.temp1.get(mService.temp1.size() - 1).toString());
        }

        if (mService.temp2.size() >0) {
            TextView temp2Text = (TextView) findViewById(R.id.temp2_text);
            temp2Text.setText("Temperature 2: " + mService.temp2.get(mService.temp2.size()-1).toString());
        }

        if (mService.arduinoState.size() >0) {
            TextView stateText = (TextView) findViewById(R.id.state_text);
            stateText.setText("State: " + mService.arduinoState.get(mService.arduinoState.size() - 1).toString());
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
        for (int idx = 0; idx<mService.time.size() ;idx++){
            x.add(mService.time.get(idx).floatValue()/(1000*60*60*24)); // need to be 1000*60*60*24, but 1000*60*60*24 is for testing pruposes to keep time consistant
        }
        temp1Series = new SimpleXYSeries(x, mService.temp1, "");
        temp2Series= new SimpleXYSeries(x, mService.temp2, "");


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
