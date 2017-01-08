package x.ale.incubeertor;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
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
import java.text.DecimalFormat;
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
    public Float currentTargetTemp;
    List<Float> targetTemp;
    List<Float> targetTime;
    List<Long> time;
    public List<Float> pulledTargetTemps;
    public List<Float> pulledRunTimes;
    public List<Float> temp1s;
    public List<Float> temp2s;
    public List<String> arduinoStates;
    Calendar calendar;
    SimpleDateFormat dateFormat;
    private byte[] lastMessage;

    Handler mHandler;
    final int RECIEVE_MESSAGE = 1;
    private StringBuilder sb;
    ConnectedThread mConnectedThread;
//    BluetoothSocket btSocket;
//    OutputStream outStream;

    static final Integer FREQUENCY = 24; //number of updates per day
    static final Integer SEND_TARGETS = 0;
    static final Character DATA_POINT = '1';
    static final Character FINISHED = '2';
    static final Character RESEND = '3';
    static final Character PROFILE_NAME = '6';
    static final Character START_TIME = '7';
    static final Character TARGET_TIMES = '8';
    static final Character TARGET_TEMPS = '9';
    static final Character TARGET_TEMP = 't';
    static final Character TEMP1 = 'i';
    static final Character TEMP2 = 'j';
    static final Character PELTIER_STATE = 'p';
    static final Character RUN_TIME = 'r';

    final public static File savePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath() + File.separator + "Brew Data");



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Function call", "onStartCommand");
        extras = intent.getExtras();
        initiateDatabaseElement();
        loadProfile();
//        interpolateTargetTemp();
        setStartTime();
        makeCsvFile();

        mHandler = new Handler() {
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
                            Log.d("Message from Arduino: ", sbprint);            // log input;
                            manageMsg(sbprint);
                        }
                        break;
                }
            };
        };

//        connectToArduino((BluetoothDevice) extras.get("btDevice"));

        ConnectThread arduinoConnection = new ConnectThread((BluetoothDevice) extras.get("btDevice"));
        arduinoConnection.start();
        try { arduinoConnection.join(); } catch (InterruptedException e) { e.printStackTrace(); }
        sendTargetsToArduino();

//        setupBtThread(); //this is the thread in which the input is read. The output is sent in the original thread with a timer

//        try {
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }


//        new Timer().scheduleAtFixedRate(new TimerTask() {
//            @Override
//            public void run() {
//                sendData();
//            }
//        }, 0, 60000);//put here time 1000 milliseconds=1 second
        // TODO: set up timer which reconnects if arduino if needed, then communicates with it, updating the database and saving it
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "Service Destroyed", Toast.LENGTH_LONG).show();
    }

    private void initiateDatabaseElement() {
        Log.d("Function call", "initiateDatabaseElements");
        calendar = Calendar.getInstance();
        targetTemp = new ArrayList<Float>();
        time = new ArrayList<Long>();
        pulledRunTimes = new ArrayList<Float>();
        pulledTargetTemps = new ArrayList<Float>();
        temp1s = new ArrayList<Float>();
        temp2s = new ArrayList<Float>();
        arduinoStates = new ArrayList<String>();
        dateFormat = new SimpleDateFormat("dd_MM_yyyy");
        sb = new StringBuilder();
        targetTemp = new ArrayList<Float>();
        targetTime = new ArrayList<Float>();
    }
    private void loadProfile(){
        Log.d("Function call", "loadProfile");
        try {
            profile = Profile.loadProfile(extras.getString("profile"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void interpolateTargetTemp() {
//        TODO: interpolate to nearest tenth of a degree
        Log.d("Function call", "interpolateTargetTemp");
        targetTime.add(profile.steps.get(0).get(0));
        targetTemp.add(profile.steps.get(0).get(1));
        for (int idx = 0; idx < profile.steps.size()-1 ;idx++) {
            float temp1 = profile.steps.get(idx).get(1);
            float temp2 = profile.steps.get(idx+1).get(1);
            float time1 = profile.steps.get(idx).get(0);
            float time2 = profile.steps.get(idx+1).get(0);
            float timeDifference = time2-time1;
            int substeps = (int)((temp2-temp1)*10)+1;
            for (int counter = 0; counter < substeps; counter++){
                if (temp2 > temp1) {
                    targetTemp.add(temp1 + (float) (0.1 * counter)); // each temp will be 0.1 degree higher
                } else if (temp2 < temp1) {
                    targetTemp.add(temp1 - (float) (0.1 * counter)); // 0.1 degree lower
                } else {
                    targetTemp.add(temp2); // or unchanged
                }
                targetTime.add(time1+(timeDifference*(counter+1)/substeps));
            }
        }


//        for (int idx = 0;idx<profile.steps.size()-1 ;idx++) {
//            float timeDifference = profile.steps.get(idx+1).get(0) - profile.steps.get(idx).get(0);
//
//            Float from = profile.steps.get(idx).get(1);
//            Float to = profile.steps.get(idx + 1).get(1);
//            Float tempDifference = to - from;
//            for (int counter = 0; counter < (timeDifference * FREQUENCY); counter++) { //hourly updates
//                Float tempTemp = (float)(from + (tempDifference * counter / (timeDifference * FREQUENCY)));
//                targetTemp.add(tempTemp);
//            }
//        }
    }

    private void sendTargetsToArduino(){
        Log.d("Function call", "sendTargetsToArduino");
        String msgOutString = "";

        msgOutString += String.valueOf(profile.getName());
        msgOutString+= ";" + String.valueOf(PROFILE_NAME);

        msgOutString += String.valueOf(startTime);
        msgOutString+= ";" + String.valueOf(START_TIME);

//        DecimalFormat df = new DecimalFormat("#");
//        df.setMaximumFractionDigits(7);
        for (int idx = 0; idx < profile.steps.size() ;idx++){
            long time_in_s = (long) (profile.steps.get(idx).get(0) * 24*60*60); // arduino handles time in seconds
            msgOutString+= String.valueOf(time_in_s);
            msgOutString+= ",";
        }
        msgOutString+= ";" + String.valueOf(TARGET_TIMES);

        for (int idx = 0; idx < profile.steps.size() ;idx++){
            msgOutString+= String.valueOf(profile.steps.get(idx).get(1));
            msgOutString+= ",";
        }
        msgOutString+= ";" + String.valueOf(TARGET_TEMPS);

        Log.d("msgOutString", msgOutString);
        byte[] msgBuffer = msgOutString.getBytes();
        mConnectedThread.write(msgBuffer);
        lastMessage = msgBuffer;
    }

    private void resendLastMessage(){
        Log.d("Function call", "sendLastMessage");
        mConnectedThread.write(lastMessage);
    }

    private void makeCsvFile(){
        Log.d("Function call", "makeCvsFile");
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

    private class ConnectThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            Log.d("Function call", "ConnectThread.run");

            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                    connectException.printStackTrace();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.d("Function call", "ConnectedThread.run");

            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    mHandler.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    private void manageMsg(String msg){
        Log.d("Function call", "manageMsg");
        if (msg.charAt(0) == DATA_POINT){
            updateData(msg);
        } else if (msg.charAt(0) == FINISHED){
            //TODO: make function which closes down the software gracefully
        } else if (msg.charAt(0) == RESEND){
            resendLastMessage();
        }

    }

//    private void connectToArduino(BluetoothDevice arduino){
//        btSocket = null;
//        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
//        try {
//            btSocket = arduino.createRfcommSocketToServiceRecord(uuid);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        try {
//            btSocket.connect();
//        } catch (IOException connectException){
//            try {
//                btSocket.close();
//            } catch (IOException closeException) { }
//            // Unable to connect; close the socket and get out
////            return;
//        }
//
//        try {
//            outStream = btSocket.getOutputStream();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//    }

//    private void setupBtThread() {
//        mConnectedThread = new ConnectedThread(btSocket);
//        mConnectedThread.start();
//    }
    private void setStartTime() {
        Log.d("Function call", "setStartTime");
        startTime = System.currentTimeMillis();
    }

//    private void sendData() {
//        setTargetTemp();
//        sendTempToArduino();
//    }
//    private void setTargetTemp() {
//        Long currentTime = System.currentTimeMillis();
//        runTime = currentTime-startTime;
//        Integer targetTempIdx = (runTime.intValue()/((1000*60*60*24)/(FREQUENCY))); //(FREQ)*60*60for debugging purposes only
//        currentTargetTemp = targetTemp.get(targetTempIdx);
//    }
//    private void sendTempToArduino() {
//        try {
//            String msgOutString = currentTargetTemp.toString() + currentTargetTemp.toString();
//            byte[] msgBuffer = msgOutString.getBytes();
//            outStream.write(msgBuffer);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    private String formatString(String inString) {
        return inString.substring(1, inString.length()-1);
    }

    public class MyBluetoothBinder extends Binder {
        BluetoothService getService() {
            // Return this instance of BluetoothService so clients can call public methods
            return BluetoothService.this;
        }
    }



//    private class ConnectedThreadOld extends Thread {
//        private final InputStream mmInStream;
//
//        public ConnectedThreadOld(BluetoothSocket socket) {
//            InputStream tmpIn = null;
////            OutputStream tmpOut = null;
//
//            // Get the input and output streams, using temp objects because
//            // member streams are final
//            try {
//                tmpIn = socket.getInputStream();
////                tmpOut = socket.getOutputStream();
//            } catch (IOException e) {
//            }
//
//            mmInStream = tmpIn;
////            mmOutStream = tmpOut;
//        }
//
//        public void run() {
//            byte[] buffer = new byte[256];  // buffer store for the stream
//            int bytes; // bytes returned from read()
//
//            // Keep listening to the InputStream until an exception occurs
//            while (true) {
//                try {
//                    // Read from the InputStream
//                    bytes = mmInStream.read(buffer);        // Get number of bytes and message in "buffer"
//                    arduinoReader.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();     // Send to message queue Handler
//                } catch (IOException e) {
//                    break;
//                }
//            }
//        }
//    }

    private void updateData(String string) {
        Log.d("Function call", "updateData");
//        if (string.substring(0, string.length()/2).equals(string.substring(string.length()/2))) {
        String buf = "";
        for (int i = 1; i < string.length(); i++) { //0 is ignored as it is the progressState/request from arduino
            if (string.charAt(i) == ';') {
                i++;
                if (string.charAt(i) == PROFILE_NAME) {
                    //do nothing. This is used for re-connecting to arduino
                } else if (string.charAt(i) == START_TIME) {
                    //do nothing. This is used for re-connecting to arduino
                } else if (string.charAt(i) == RUN_TIME) {
                    pulledRunTimes.add(Float.valueOf(buf));
                } else if (string.charAt(i) == TARGET_TEMP) {
                    pulledTargetTemps.add(Float.valueOf(buf));
                } else if (string.charAt(i) == TEMP1) {
                    temp1s.add(Float.valueOf(buf));
                } else if (string.charAt(i) == TEMP2) {
                    temp2s.add(Float.valueOf(buf));
                } else if (string.charAt(i) == PELTIER_STATE) {
                    arduinoStates.add(buf);
                }
                buf = "";
            } else {
                buf += string.charAt(i);
            }

        }
        saveData();
    }
    private void saveData() {
        Log.d("Function call", "saveData");
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {

            Date tempDate = new Date(startTime);
            String dateText = dateFormat.format(tempDate);
            File file = new File(savePath, File.separator + profile.getName() +"_"+ dateText + ".csv");

            try {
                CSVWriter writer = new CSVWriter(new FileWriter(file, true));
                writer.writeNext((pulledRunTimes.get(pulledRunTimes.size() - 1).toString()+","+
                        pulledTargetTemps.get(pulledTargetTemps.size() - 1).toString()+","+
                        temp1s.get(temp1s.size() - 1).toString()+","+
                        temp2s.get(temp2s.size() - 1).toString()+","+
                        arduinoStates.get(arduinoStates.size() - 1).toString()).split(","));
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
