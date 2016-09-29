package x.ale.incubeertor;

import android.os.Environment;
import android.util.Log;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * Created by Alex on 9/09/2016.
 */
public class Profile{
    public String name;
    public List<List<Float>> steps = new ArrayList<List<Float>>();
    final public static File savePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath()+ File.separator + "Brew Profiles");

    public Profile(String name) {
        this.name = name;
//        Log.d("NAME", this.name);
    }

    public String getName(){
        return name;
    }

    public void addStep(List<Float> step){
        steps.add(step);
    }

    public void save() throws IOException {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            if (!savePath.isDirectory()) {
                savePath.mkdir();
            }

            File file = new File(savePath, File.separator + name + ".csv");

            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            CSVWriter writer = new CSVWriter(new FileWriter(file));;

            List<String[]> data = new ArrayList<String[]>();
            data.add(name.split(","));
            data.add("time,temperature".split(","));
            for (List<Float> step : steps){
                data.add((step.get(0).toString()+","+step.get(1).toString()).split(","));
            }

            try {
                for(String[] row : data){
                    writer.writeNext(row);
                }
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }


    }
    public static Profile loadProfile(String profileName) throws IOException {
        File file = new File(savePath, File.separator + profileName+".csv");
        CSVReader reader = new CSVReader (new FileReader(file), ',', '"', 2);
        final Profile p = new Profile(profileName);
        String[] nextLine;
        while ((nextLine = reader.readNext()) != null) {
            if (nextLine != null) {
                //Verifying the read data here
                String time = nextLine[0];
                String temp = nextLine[1];
                List<Float> step = new ArrayList<Float>();
                step.add(Float.valueOf(time));
                step.add(Float.valueOf(temp));
                p.addStep(step);
            }
        }

        return p;
    }

    public void print(){
//        Log.d("Profile", "Name: "+name);
//        Log.d("Profile", "Steps: Time, Temp");
        for (List<Float> step :steps){
//            Log.d("Profile", step.get(0).toString()+ ", "+step.get(1).toString());
            }
    }
    public void sortSteps(){
        Log.d("function_call", "sort");
        Comparator<List<Float>> comparator_rows = new Comparator<List<Float>>(){
            @Override
            public int compare(List<Float> o1, List<Float> o2){
                Float i = o2.get(0);
                Float j = o1.get(0);
                if (i < j) {
                    return 1;
                } else if (i > j) {
                    return -1;
                } else {
                    return 0;
                }
            }
        };

        Collections.sort(steps,  comparator_rows);
    }

}
