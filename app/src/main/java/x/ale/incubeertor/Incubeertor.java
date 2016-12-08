/*
No current copyright ?apply for crative commons licence
 */

package x.ale.incubeertor;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.content.Intent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Incubeertor extends AppCompatActivity {

    private Profile profile;
    private XYPlot stepsPlot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incubeertor);
        stepsPlot = (XYPlot) findViewById(R.id.steps_plot);
    }

    public void newProfile(View view) {
        /**
         * Creates a pop-up UI to enter the name of a new profile
         */
        LayoutInflater layoutInflater = (LayoutInflater)getBaseContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        final View popupView = layoutInflater.inflate(R.layout.new_profile_popup, null);
        final PopupWindow popup = new PopupWindow(popupView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.showAtLocation(popupView, Gravity.CENTER, 0,0);

        Button addNewProfileButton = (Button) popupView.findViewById(R.id.add_new_profile);

        addNewProfileButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                /**
                 * creates a new profile class with the specified name and saves it
                 */
                EditText editText = (EditText) popupView.findViewById(R.id.new_profile_name);
                String name = editText.getText().toString();
                profile = new Profile(name);
                try {
                    profile.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                popup.dismiss();
                try {
                    loadProfile(name);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void loadSelectProfile(View view) {
        /**
         * Creates a pop-up UI to select a profile from the save directory.
         */
        LayoutInflater layoutInflater = (LayoutInflater)getBaseContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        final View popupView = layoutInflater.inflate(R.layout.select_profile_popup, null);
        final PopupWindow popup = new PopupWindow(popupView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true);
        final LinearLayout layout = (LinearLayout) popupView.findViewById(R.id.layout);

        //make list of profiles
        final File file[] = Profile.savePath.listFiles();
        for (int i=0; i < file.length; i++) {
            Button button = new Button(this);
            LayoutParams buttonLayoutParam = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            button.setLayoutParams(buttonLayoutParam);
            final File workingFile = file[i];
            final String name = workingFile.getName().substring(0,workingFile.getName().length()-4); // remove .csv
            button.setText(name);

            button.setOnClickListener(new View.OnClickListener(){
            @Override
                public void onClick(View view){
                try {
                    loadProfile(name);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }});
            layout.addView(button, buttonLayoutParam);
        }
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.showAtLocation(popupView, Gravity.CENTER, 0,0);
    }

    public void loadProfile(String fileName) throws IOException {
        profile = Profile.loadProfile(fileName);
        updateGUI();
    }

    public void updateGUI(){
        Button selectButton = (Button) findViewById(R.id.select_profile);
        selectButton.setText(profile.getName());
        refreshSteps();
        refreshPlot();
    }
    public void refreshSteps(){
        removeStepsFromGUI();
        addStepsToGUI();
    }
    public void removeStepsFromGUI(){
        LinearLayout ll = (LinearLayout)findViewById(R.id.steps_vertical_layout);
        int childCount = ll.getChildCount();
        for (int i = 1; i < childCount-1; i++) {
            ll.removeViewAt(1);
        }
    }

    public void addStepsToGUI(){
        /**
         * Sorts and loads all the current steps into the steps grid view
         */
        LinearLayout steps_layout = (LinearLayout) findViewById(R.id.steps_vertical_layout);
        profile.sortSteps();
        for (int i=0; i<profile.steps.size(); i++){ //final List<Float> step : profile.steps;
            final List<Float> step = profile.steps.get(i);
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            LinearLayout ll = (LinearLayout)inflater.inflate(R.layout.step_template, null);

            Button timeButton = (Button) ll.findViewById(R.id.time_button);
            timeButton.setText(step.get(0).toString());
            timeButton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View view){
                    changeStepValuePopup(step, 0);
                }
            });

            Button tempButton = (Button) ll.findViewById(R.id.temp_button);
            tempButton.setText(step.get(1).toString());

            steps_layout.addView(ll,i+1);
        }
    }

    public void refreshPlot(){
        List<Float> x = new ArrayList<Float>();
        List<Float> y = new ArrayList<Float>();
        for (List<Float> step : profile.steps){
            x.add(step.get(0));
            y.add(step.get(1));
        }
        XYSeries s1 = new SimpleXYSeries(x, y, "");
        stepsPlot.clear();
        stepsPlot.addSeries(s1, new LineAndPointFormatter(Color.GREEN, Color.GREEN, null, null));
        stepsPlot.redraw();
    }

    public void changeStepValuePopup(final List<Float> step, final int idx){
        /**
         * Creates pop-up UI to enter new values to each step
         */
        LayoutInflater layoutInflater = (LayoutInflater)getBaseContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        final View popupView = layoutInflater.inflate(R.layout.change_step_value_popup, null);
        final PopupWindow popup = new PopupWindow(popupView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.showAtLocation(popupView, Gravity.CENTER, 0,0);

        final EditText valueEditText = (EditText) popupView.findViewById(R.id.value);
        valueEditText.setText(step.get(idx).toString());

        Button enterButton = (Button) popupView.findViewById(R.id.enter_button);

        enterButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                String newValue = valueEditText.getText().toString();
                step.set(0, Float.valueOf(newValue));
                updateGUI();
                popup.dismiss();
            }
        });
    }

    public void deleteProfile(View view){

    }

    public void addNewStep(View view){
        /**
         * Adds values in last row of steps grid view to steps data, then saves and updates GUI
         */
        List<Float> step = new ArrayList<Float>();

        EditText editText = (EditText) findViewById(R.id.new_step_time);
        float t = Float.valueOf(editText.getText().toString());
        step.add(t);

        editText = (EditText) findViewById(R.id.new_step_temp);
        t = Float.valueOf(editText.getText().toString());
        step.add(t);

        profile.addStep(step);
        profile.print();
        try {
            profile.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
        updateGUI();
    }

    public void brew(View view){
        /**
         * starts activity to select which BT device to connect to
         */
        Intent intent = new Intent(this, BluetoothActivity.class);
        intent.putExtra("profile", profile.getName());
        startActivity(intent);
    }

    public void save(View view){
        try {
            profile.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
