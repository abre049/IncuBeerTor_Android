package x.ale.incubeertor;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
//import android.icu.util.Calendar;
//import android.icu.util.TimeZone;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.FloatRange;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.os.Handler;
import android.util.Log;

import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
//import java.util.logging.Handler;
import java.util.logging.LogRecord;


public class BrewActivity extends AppCompatActivity {

    static final Integer FREQUENCY = 24; //number of updates per day
    final public static File savePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath()+ File.separator + "Brew Data");

    Bundle extras;
    BluetoothSocket btSocket;
    OutputStream outStream;
    InputStream inStream;
    Profile profile;
    XYPlot plot;
    Calendar calendar;
    Long startTime;
    Long runTime;
    List<Float> targetTemp;
    Float currentTargetTemp;
    List<Long> time;
    List<Float> pulledTargetTemps;
    List<Float> temp1;
    List<Float> temp2;
    List<String> arduinoState;
    SimpleDateFormat dateFormat;
    Handler arduinoReader;
    final int RECIEVE_MESSAGE = 1;
    private StringBuilder sb;
    ConnectedThread mConnectedThread;


    @TargetApi(Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        Log.d("Initiate Activity", "BrewActivity");
        setContentView(R.layout.activity_brew);

        extras = getIntent().getExtras();
        calendar = Calendar.getInstance();
        targetTemp = new ArrayList<Float>();
        time = new ArrayList<Long>();
        pulledTargetTemps = new ArrayList<Float>();
        temp1 = new ArrayList<Float>();
        temp2 = new ArrayList<Float>();
        arduinoState = new ArrayList<String>();
        dateFormat = new SimpleDateFormat("dd_MM_yyyy");
        sb = new StringBuilder();


        connectToArduino((BluetoothDevice) extras.get("btDevice"));
        loadProfile();
        loadPlot();
        interpolateTargetTemp();
        startTime = System.currentTimeMillis();
//        Log.d("startTime", startTime.toString());


        arduinoReader = new Handler() {

            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case RECIEVE_MESSAGE:                                                   // if receive massage
                        byte[] readBuf = (byte[]) msg.obj;
                        String strIncom = new String(readBuf, 0, msg.arg1);                 // create string from bytes array
                        sb.append(strIncom);                                                // append string
                        int endOfLineIndex = sb.indexOf("\r\n");                            // determine the end-of-line
                        if (endOfLineIndex > 0) {                                            // if end-of-line,
                            String sbprint = sb.substring(0, endOfLineIndex);               // extract string
                            sb.delete(0, sb.length());                                      // and clear
//                            Log.d("Data from Arduino: ", sbprint);            // log input;


                            updateData(sbprint);
                            updateTextInfo();
                            updatePlot();
                        }
                        //Log.d(TAG, "...String:"+ sb.toString() +  "Byte:" + msg.arg1 + "...");
                        break;
                }
            };
        };

        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateAll();
                }
            });}
        }, 0, 60000);//put here time 1000 milliseconds=1 second

    }

    private void updateData(String string) {
//        Log.d("function_call", "updateData");
//        Log.d("input string", string);
        for (int i = 0; i < string.length(); i++){
            if (string.charAt(i) == ','){
                i++;
                int j;
                for (j = i; j < string.length(); j++){
                    if (string.charAt(j) == ';'){
                        break;
                    }
                }
                if (string.charAt(i) == 't' && string.charAt(i+1) == 't'){
                    pulledTargetTemps.add(Float.valueOf(string.substring(i+2,(j))));
                    time.add(runTime); // only add time if there is data available.
                } else if (string.charAt(i) == 't' && string.charAt(i+1) == '1'){
//                    Log.d("temp1", string.substring(i+2,(j)));
                    temp1.add(Float.valueOf(string.substring(i+2,(j))));
                } else if (string.charAt(i) == 't' && string.charAt(i+1) == '2'){
                    temp2.add(Float.valueOf(string.substring(i+2,(j))));
                } else if (string.charAt(i) == 's' && string.charAt(i+1) == 't'){
                    arduinoState.add((string.substring(i+2,(j))));
                }


//                Log.d("i", String.valueOf(i));
                i = j;


            }
        }

    }

    private void interpolateTargetTemp() {
        for (int idx = 0;idx<profile.steps.size()-1 ;idx++) {
            float timeDifference = profile.steps.get(idx+1).get(0) - profile.steps.get(idx).get(0);

            Float from = profile.steps.get(idx).get(1);
            Float to = profile.steps.get(idx + 1).get(1);
            Float tempDifference = to - from;
            for (int counter = 0; counter < (timeDifference * FREQUENCY); counter++) { //hourly updates
                Float tempTemp = (float)(from + (tempDifference * counter / (timeDifference * FREQUENCY)));
                targetTemp.add(tempTemp);
            }
        }
//        Log.d("TT size", Integer.toString(targetTemp.size()));
//        for (int i = 0; i<targetTemp.size();i++){
//            Log.d("TT", targetTemp.get(i).toString());
//        }
    }

    private void connectToArduino(BluetoothDevice arduino){
        btSocket = null;
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        try {
            btSocket = arduino.createRfcommSocketToServiceRecord(uuid);
            btSocket.connect();
            outStream = btSocket.getOutputStream();
//            inStream = btSocket.getInputStream();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
//        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
//            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
//                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
//            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);        // Get number of bytes and message in "buffer"
                    arduinoReader.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();     // Send to message queue Handler

                } catch (IOException e) {
                    break;
                }
            }
        }
    }

    private void loadProfile(){
        try {
//            Log.d("extras profile", extras.getString("profile"));
            profile = Profile.loadProfile(extras.getString("profile"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        profile.print();
    }

    private void loadPlot(){
//        Log.d("function_call", "loadPlot");
        plot = (XYPlot) findViewById(R.id.plot);
        List<Float> x = new ArrayList<Float>();
        List<Float> y = new ArrayList<Float>();
        for (List<Float> step : profile.steps){
            x.add(step.get(0));
            y.add(step.get(1));
        }
        XYSeries s1 = new SimpleXYSeries(x, y, "");
        plot.clear();
        plot.addSeries(s1, new LineAndPointFormatter(Color.GREEN, Color.GREEN, null, null));
        plot.redraw();
    }

    private void updateAll() {

//        Log.d("function_call", "updateAll");
        setTargetTemp();
        sendTempToArduino();
        readArduino();
        saveData();
//        updateTextInfo();
//        updatePlot();
//        Log.d("currentTT", currentTargetTemp.toString());

    }

    private void setTargetTemp() {
        Long currentTime = System.currentTimeMillis();
        runTime = currentTime-startTime;
        Integer targetTempIdx = (runTime.intValue()/((1000*60*60*24)/(FREQUENCY))); //(FREQ)*60*60for debugging purposes only
        currentTargetTemp = targetTemp.get(targetTempIdx);
    }

    private void readArduino() {
//        Integer available = null;
//        try {
//            available = inStream.available();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        while (available>0) {
//            try {
//                // Read from the InputStream
//                byte buffer[];
//                buffer = new byte[1024];
//                //Read is synchronous call which keeps on waiting until data is available
//                int bytes = inStream.read(buffer);
//                if(bytes > 0){
//              /*If data is non zero which means a data has been sent from Arduino to
//               Android */
//                    String data = new String(String.valueOf(bytes));
//                    Log.d("input string", data);
//                }
//            } catch (IOException e) {
//
//            }
//        }
//
//
//        time.add(runTime);
//        temp1.add(currentTargetTemp);
//        temp2.add((float) (currentTargetTemp+0.5));
//        arduinoState.add("Heat");

    }

    private void saveData() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            if (!savePath.isDirectory()) {
                savePath.mkdir();
            }

            Date tempDate = new Date(startTime);
            String dateText = dateFormat.format(tempDate);
            File file = new File(savePath, File.separator + profile.getName() +"_"+ dateText + ".csv");

            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            CSVWriter writer = null;
            try {
                writer = new CSVWriter(new FileWriter(file));
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                writer.writeNext((profile.getName()+","+dateText).split(","));
                writer.writeNext(("time,"+ formatString(time.toString())).split(","));
                writer.writeNext(("target temp,"+ formatString(pulledTargetTemps.toString())).split(","));
                writer.writeNext(("temp1,"+ formatString(temp1.toString())).split(","));
                writer.writeNext(("temp2,"+ formatString(temp2.toString())).split(","));
                writer.writeNext(("state,"+ formatString(arduinoState.toString())).split(","));

                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private String formatString(String inString) {
        return inString.substring(1, inString.length()-1);
    }

    private void sendTempToArduino() {
        try {
            byte[] msgBuffer = currentTargetTemp.toString().getBytes();
            outStream.write(msgBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateTextInfo() {
        TextView timeText = (TextView) findViewById(R.id.time);
        timeText.setText("Run Time: " + runTime.toString() + " ms");

        TextView targetTempText = (TextView) findViewById(R.id.target_temp_text);
        targetTempText.setText("Target Temperature: " + currentTargetTemp.toString());

        if (temp1.size() >0) {
            TextView temp1Text = (TextView) findViewById(R.id.temp1_text);
            temp1Text.setText("Temperature 1: " + temp1.get(temp1.size() - 1).toString());
        }

        if (temp2.size() >0) {
            TextView temp2Text = (TextView) findViewById(R.id.temp2_text);
            temp2Text.setText("Temperature 2: " + temp2.get(temp2.size()-1).toString());
        }

        if (arduinoState.size() >0) {
            TextView stateText = (TextView) findViewById(R.id.state_text);
            stateText.setText("State: " + arduinoState.get(arduinoState.size() - 1).toString());
        }
    }

    private void updatePlot() {
        XYSeries targetTempSeries;
        XYSeries temp1Series;
        XYSeries temp2Series;
        XYSeries stateSeries;

        plot = (XYPlot) findViewById(R.id.plot);

        List<Float> x = new ArrayList<Float>();
        List<Float> y = new ArrayList<Float>();
        for (List<Float> step : profile.steps){
            x.add(step.get(0));
            y.add(step.get(1));
        }
        targetTempSeries = new SimpleXYSeries(x, y, "");

        x = new ArrayList<Float>();
        for (int idx = 0; idx<time.size() ;idx++){
            x.add(time.get(idx).floatValue()/(1000*60*60*24)); // need to be 1000*60*60*24, but 1000*60*60*24 is for testing pruposes to keep time consistant
        }
        temp1Series = new SimpleXYSeries(x, temp1, "");
        temp2Series= new SimpleXYSeries(x, temp2, "");


        plot.clear();
        plot.addSeries(targetTempSeries, new LineAndPointFormatter(Color.GREEN, Color.GREEN, null, null));
        plot.addSeries(temp1Series, new LineAndPointFormatter(Color.BLUE, Color.BLUE, null, null));
        plot.addSeries(temp2Series, new LineAndPointFormatter(Color.GRAY, Color.GRAY, null, null));

        plot.redraw();
    }


}
