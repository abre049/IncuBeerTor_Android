package x.ale.incubeertor;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.os.Handler;
import android.widget.Toast;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class BluetoothService extends Service {

    IBinder mBinder = new MyBluetoothBinder();
    Bundle extras;
    public Profile profile;
    Long startTime;
    public Long runTime;
    List<Float> targetTemp;
    public Float currentTargetTemp;
    List<Long> time;
    List<Float> pulledTargetTemps;
    public List<Float> temp1;
    public List<Float> temp2;
    public List<String> arduinoState;
    Calendar calendar;
    SimpleDateFormat dateFormat;

    Handler arduinoReader;
    final int RECIEVE_MESSAGE = 1;
    private StringBuilder sb;
    ConnectedThread mConnectedThread;
    BluetoothSocket btSocket;
    OutputStream outStream;

    static final Integer FREQUENCY = 24; //number of updates per day
    final public static File savePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath() + File.separator + "Brew Data");

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        extras = intent.getExtras();
        initiateDatabaseElement();
        loadProfile();
        interpolateTargetTemp();
        setStartTime();
        makeCsvFile();

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
                            Log.d("Data from Arduino: ", sbprint);            // log input;
                            updateData(sbprint);
                        }
                        break;
                }
            };
        };

        connectToArduino((BluetoothDevice) extras.get("btDevice"));
        setupBtThread(); //this is the thread in which the input is read. The output is sent in the original thread with a timer
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendData();
            }
        }, 0, 60000);//put here time 1000 milliseconds=1 second
        // TODO: set up timer which reconnects if arduino if needed, then communicates with it, updating the database and saving it
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "Service Destroyed", Toast.LENGTH_LONG).show();
    }

    private void initiateDatabaseElement() {
        calendar = Calendar.getInstance();
        targetTemp = new ArrayList<Float>();
        time = new ArrayList<Long>();
        pulledTargetTemps = new ArrayList<Float>();
        temp1 = new ArrayList<Float>();
        temp2 = new ArrayList<Float>();
        arduinoState = new ArrayList<String>();
        dateFormat = new SimpleDateFormat("dd_MM_yyyy");
        sb = new StringBuilder();
    }
    private void loadProfile(){
        try {
            profile = Profile.loadProfile(extras.getString("profile"));
        } catch (IOException e) {
            e.printStackTrace();
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
    }
    private void makeCsvFile(){
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            if (!savePath.isDirectory()) {
                savePath.mkdir();
            }

            Date tempDate = new Date(startTime);
            String dateText = dateFormat.format(tempDate);
            File file = new File(savePath, File.separator + profile.getName() + "_" + dateText + ".csv");

            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                CSVWriter writer = new CSVWriter(new FileWriter(file));
                writer.writeNext("time,target temp,temp1,temp2,state".split(","));
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private void connectToArduino(BluetoothDevice arduino){
        btSocket = null;
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        try {
            btSocket = arduino.createRfcommSocketToServiceRecord(uuid);
            btSocket.connect();
            outStream = btSocket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void setupBtThread() {
        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();
    }
    private void setStartTime() {
        startTime = System.currentTimeMillis();
    }

    private void sendData() {
        setTargetTemp();
        sendTempToArduino();
    }
    private void setTargetTemp() {
        Long currentTime = System.currentTimeMillis();
        runTime = currentTime-startTime;
        Integer targetTempIdx = (runTime.intValue()/((1000*60*60*24)/(FREQUENCY))); //(FREQ)*60*60for debugging purposes only
        currentTargetTemp = targetTemp.get(targetTempIdx);
    }
    private void sendTempToArduino() {
        try {
            String msgOutString = currentTargetTemp.toString() + currentTargetTemp.toString();
            byte[] msgBuffer = msgOutString.getBytes();
            outStream.write(msgBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String formatString(String inString) {
        return inString.substring(1, inString.length()-1);
    }

    public class MyBluetoothBinder extends Binder {
        BluetoothService getService() {
            // Return this instance of BluetoothService so clients can call public methods
            return BluetoothService.this;
        }
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;

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

    private void updateData(String string) {
        if (string.substring(0, string.length()/2).equals(string.substring(string.length()/2))) {
            for (int i = 0; i < string.length()/2; i++) {
                if (string.charAt(i) == ',') {
                    i++;
                    int j;
                    for (j = i; j < string.length(); j++) {
                        if (string.charAt(j) == ';') {
                            break;
                        }
                    }
                    if (string.charAt(i) == 't' && string.charAt(i + 1) == 't') {
                        pulledTargetTemps.add(Float.valueOf(string.substring(i + 2, (j))));
                        time.add(runTime); // only add time if there is data available.
                    } else if (string.charAt(i) == 't' && string.charAt(i + 1) == '1') {
                        temp1.add(Float.valueOf(string.substring(i + 2, (j))));
                    } else if (string.charAt(i) == 't' && string.charAt(i + 1) == '2') {
                        temp2.add(Float.valueOf(string.substring(i + 2, (j))));
                    } else if (string.charAt(i) == 's' && string.charAt(i + 1) == 't') {
                        arduinoState.add((string.substring(i + 2, (j))));
                    }
                    i = j;
                }
            }
            saveData();
        }
    }
    private void saveData() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {

            Date tempDate = new Date(startTime);
            String dateText = dateFormat.format(tempDate);
            File file = new File(savePath, File.separator + profile.getName() +"_"+ dateText + ".csv");

            try {
                CSVWriter writer = new CSVWriter(new FileWriter(file, true));
                writer.writeNext((time.get(-1).toString()+","+
                        pulledTargetTemps.get(-2).toString()+","+
                        temp1.get(-1).toString()+","+
                        temp2.get(-1).toString()+","+
                        arduinoState.get(-1).toString()).split(","));
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }



        }
    }
}
