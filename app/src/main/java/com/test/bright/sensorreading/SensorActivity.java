package com.test.bright.sensorreading;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.system.Os;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.w3c.dom.Text;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class SensorActivity extends AppCompatActivity implements SensorEventListener, SensorSelectionDialogFragment.SensorSelectionDialogListener, AdapterView.OnItemSelectedListener {

    private static final Map<Integer, String> INTERESTED_SENSORS;

    static {
        INTERESTED_SENSORS = new HashMap<>();
        //Basic sensors
        INTERESTED_SENSORS.put(Sensor.TYPE_ACCELEROMETER, "Accelerometer (with gravity)");
        INTERESTED_SENSORS.put(Sensor.TYPE_GYROSCOPE, "Gyroscope");
        INTERESTED_SENSORS.put(Sensor.TYPE_MAGNETIC_FIELD, "Magnetic field");
        INTERESTED_SENSORS.put(Sensor.TYPE_AMBIENT_TEMPERATURE, "Temperature");
        INTERESTED_SENSORS.put(Sensor.TYPE_LIGHT, "Light");
        INTERESTED_SENSORS.put(Sensor.TYPE_PRESSURE, "Pressure");
        INTERESTED_SENSORS.put(Sensor.TYPE_RELATIVE_HUMIDITY, "Relative humidity");

        //Composite sensors
        INTERESTED_SENSORS.put(Sensor.TYPE_ROTATION_VECTOR, "Rotation vector");
        INTERESTED_SENSORS.put(Sensor.TYPE_LINEAR_ACCELERATION, "Accelerometer (without gravity)");

        //Uncalibrated sensors
        INTERESTED_SENSORS.put(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, "Uncalibrated accelerometer");
        INTERESTED_SENSORS.put(Sensor.TYPE_GYROSCOPE_UNCALIBRATED, "Uncalibrated gyroscope");
        INTERESTED_SENSORS.put(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, "Uncalibrated magnetic field");
    }

    private SensorManager mSensorManager;
    private long uTimeStart;
    private long nInterval;
    private int bufSize = 200;
    private boolean running = false;
    private boolean plotting = false;

    private Map<Integer, SensorData> pointBufMap = new HashMap<>();
    private LineGraphSeries<DataPoint>[] series = new LineGraphSeries[4];
    private int plotting_sensor_id = -1;

    private Button fab, plot_btn;
    private GraphView graph;
    private TextView sensor_data_text;
    private DialogFragment mDialogFragment;
    private Spinner plot_selection_spinner;
    private ArrayAdapter<CharSequence> mSpinnerAdapter;

    private Map<Integer, PrintWriter> mFiles = new HashMap<>();
    private Map<Integer, Long> mTimers = new HashMap<>();

    private File baseDir, dataDir;
    private String filename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.out.println("onCreate");
        setContentView(R.layout.activity_sensor);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mDialogFragment = new SensorSelectionDialogFragment();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

//        if(savedInstanceState==null)
        checkSensors();

        fab = (Button) findViewById(R.id.fab);
        fab.setText("Start Collecting");
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (running == false) {
                    if (mSelectedSensors.size() == 0) {
                        Toast toast = Toast.makeText(SensorActivity.this, "Please select at least one sensor to collect", Toast.LENGTH_SHORT);
                        toast.show();
                        return;
                    }
                    running = true;
                    plot_btn.setEnabled(true);
                    registerSensors();
                    fab.setText("Pause Collecting");
                    uTimeStart = System.nanoTime();
                    for (int s : mSelectedSensors) {
                        mTimers.put(s, uTimeStart);
                    }
                    openFiles();
                    graph.removeAllSeries();
                    for (int s : mSelectedSensors) {
                        pointBufMap.put(s, new SensorData(bufSize));
                    }
                    for (int i = 0; i < 4; ++i) {
                        series[i] = new LineGraphSeries<>(new DataPoint[0]);
                        graph.addSeries(series[i]);
                    }
                } else {
                    running = false;
                    plot_btn.setEnabled(false);
                    mSensorManager.unregisterListener(SensorActivity.this);
                    closeFile();
                    Toast toast = Toast.makeText(SensorActivity.this, "Data saved to file \"" + filename + "\"", Toast.LENGTH_SHORT);
                    toast.show();
//                    while(!pointBuf.isEmpty())
//                        pointBuf.remove();
                    fab.setText("Start Collecting");
                }
                invalidateOptionsMenu();
            }
        });

        plot_btn = findViewById(R.id.plot_btn);
        plot_btn.setText("Start plotting");
        plot_btn.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (plotting == false) {
                            plot_btn.setText("Pause Plotting");
                            SensorData s = pointBufMap.get(plotting_sensor_id);
                            if (s == null) {
                                System.out.println("Warning: cannot get sensor data");
                                Toast toast = Toast.makeText(SensorActivity.this, "Warning: cannot get sensor data", Toast.LENGTH_SHORT);
                                toast.show();
                                return;
                            }
                            plotting = true;
                            updateSeries(s);
                        } else {
                            plot_btn.setText("Start Plotting");
                            plotting = false;
                        }

                    }
                }
        );

        plot_selection_spinner = (Spinner) findViewById(R.id.spn_plotting_sensor);
        plot_selection_spinner.setOnItemSelectedListener(this);
        setPlottingSpinner();

        sensor_data_text = (TextView) findViewById(R.id.sensor_data_text);

        long mInterval = 50;
        nInterval = mInterval * 1000 * 1000;
        graph = (GraphView) findViewById(R.id.graph);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);
        graph.getLegendRenderer().setBackgroundColor(Color.TRANSPARENT);
        graph.getLegendRenderer().setFixedPosition(10,-10);
        graph.getLegendRenderer().setVisible(true);
        Viewport v = graph.getViewport();
        v.setScrollable(true);
        v.setMaxXAxisSize(50);
        v.setScalableY(true);
        v.setScalable(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(10);

        int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        int permission = ActivityCompat.checkSelfPermission(SensorActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    SensorActivity.this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
        try {
            baseDir = new File(Environment.getExternalStorageDirectory(), "SensorReading");
            baseDir.mkdirs();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void updateSeries(SensorData s) {
        graph.removeAllSeries();
        graph.setTitle(INTERESTED_SENSORS.get(plotting_sensor_id));
        series[0] = new LineGraphSeries<>(s.x.toArray(new DataPoint[0]));
        series[0].setColor(Color.GREEN);
        series[0].setTitle("X");
        series[1] = new LineGraphSeries<>(s.y.toArray(new DataPoint[0]));
        series[1].setColor(Color.RED);
        series[1].setTitle("Y");
        series[2] = new LineGraphSeries<>(s.z.toArray(new DataPoint[0]));
        series[2].setColor(Color.BLACK);
        series[2].setTitle("Z");
        series[3] = new LineGraphSeries<>(s.a.toArray(new DataPoint[0]));
        series[3].setTitle("ALL");
        for (int i = 0; i < 4; ++i)
            graph.addSeries(series[i]);
        graph.getViewport().setMinX(s.x.getLast().getX()-10);
        graph.getViewport().setMaxX(s.x.getLast().getX());
    }

    private void closeFile() {
        for (PrintWriter f : mFiles.values())
            f.close();
    }

    private void openFiles() {
        try {
            filename = new Date().toString();
            dataDir = new File(baseDir, filename);
            dataDir.mkdirs();
            mFiles = new HashMap<>();
            for (int s : mSelectedSensors)
                mFiles.put(s, new PrintWriter(new File(dataDir, INTERESTED_SENSORS.get(s) + ".txt")));
            System.out.println(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + filename);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to open file");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("onResume");
        if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
//        if(running) {
//            registerSensors();
//            long uTimeStart = System.nanoTime();
//            for(int s:mSelectedSensors){
//                mTimers.put(s,uTimeStart);
//            }
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        System.out.println("onPause");
//        if(running)
//            mSensorManager.unregisterListener(this);
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.
        long uTime2 = System.nanoTime();
        int type = event.sensor.getType();
        long uTime1 = mTimers.get(type);
        if (uTime2 - uTime1 >= nInterval) {
            mTimers.put(type, uTime2);
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            double m = Math.sqrt(x * x + y * y + z * z);
            pointBufMap.get(type).add((uTime2 - uTimeStart) / 1e9, new float[]{x, y, z});

            mFiles.get(type).format("%f\t%f\t%f\t%f\t%f\n", (uTime2 - uTimeStart) / 1e9, x, y, z, m);
            if (plotting && type == plotting_sensor_id)
                updateSeries(pointBufMap.get(type));
        }
        // Do something with this sensor value.

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_sensor, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            showSensorSelectionDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (running)
            menu.getItem(0).setEnabled(false);
        else
            menu.getItem(0).setEnabled(true);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
        System.out.println("onDestroy");

        closeFile();
    }

    private ArrayList<String> available_sensor_desc;
    private ArrayList<Integer> available_sensor_id;

    private int checkSensors() {
        available_sensor_desc = new ArrayList<>();
        available_sensor_id = new ArrayList<>();
        int c = 0;
        ArrayList<Map.Entry<Integer, String>> entries = new ArrayList<>(INTERESTED_SENSORS.entrySet());
        Collections.sort(entries,
                new Comparator<Map.Entry<Integer, String>>() {
                    @Override
                    public int compare(Map.Entry<Integer, String> t1, Map.Entry<Integer, String> t2) {
                        return t1.getValue().compareTo(t2.getValue());
                    }
                }
        );
        for (Map.Entry<Integer, String> e : entries) {
            if ((mSensorManager.getDefaultSensor(e.getKey())) != null) {
                System.out.println(String.valueOf(++c) + ":" + e.getValue());
                available_sensor_id.add(e.getKey());
                available_sensor_desc.add(e.getValue());
            }
        }
        return c;
    }

    private void registerSensors() {
        mSensorManager.unregisterListener(this);
        for (int s : mSelectedSensors)
            mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(s), SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void showSensorSelectionDialog() {
        Bundle mAvailableSensors;
        if ((mAvailableSensors = mDialogFragment.getArguments()) == null)
            mAvailableSensors = new Bundle();
        mAvailableSensors.putIntegerArrayList(SensorSelectionDialogFragment.KEY_AVAILABLE_SENSORS_ID, available_sensor_id);
        mAvailableSensors.putStringArrayList(SensorSelectionDialogFragment.KEY_AVAILABLE_SENSORS_DESC, available_sensor_desc);
        mDialogFragment.setArguments(mAvailableSensors);
        mDialogFragment.show(getFragmentManager(), "selection");
    }

    private Map<String, Integer> spinner_txt2sensor_mapper;

    private void setPlottingSpinner() {
        mSpinnerAdapter = new ArrayAdapter<CharSequence>(this, R.layout.spinner_text_layout);
        spinner_txt2sensor_mapper = new HashMap<>();
        Set<String> ts = new TreeSet<>();
        int cnt = 0;
        for (int s : mSelectedSensors) {
            ts.add(INTERESTED_SENSORS.get(s));
            spinner_txt2sensor_mapper.put(INTERESTED_SENSORS.get(s), s);
        }
        mSpinnerAdapter.addAll(ts);
        plot_selection_spinner.setAdapter(mSpinnerAdapter);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        System.out.println("onItemSelected:" + pos);
        plotting_sensor_id = spinner_txt2sensor_mapper.get(parent.getSelectedItem().toString());
        try {
            if (running && plotting) {
                updateSeries(pointBufMap.get(plotting_sensor_id));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        System.out.println("onNothingSelected");
    }

    //In default magnetometer is selected
    private ArrayList<Integer> mSelectedSensors = new ArrayList<>(Arrays.asList(new Integer[]{Sensor.TYPE_MAGNETIC_FIELD}));

    @Override
    public void onDialogPositiveClicked(DialogFragment dialog, ArrayList<Integer> selected) {
        mSelectedSensors = new ArrayList<>(selected);
        setPlottingSpinner();
    }

    @Override
    public void onDialogNegativeClicked(DialogFragment dialog) {

    }

}

class SensorData {
    public LinkedList<DataPoint> x, y, z, a;
    int size;

    public SensorData(int size) {
        x = new LinkedList<>();
        y = new LinkedList<>();
        z = new LinkedList<>();
        a = new LinkedList<>();
        this.size = size;
    }

    public void add(double time, float[] data) {
        x.addLast(new DataPoint(time, data[0]));
        y.addLast(new DataPoint(time, data[1]));
        z.addLast(new DataPoint(time, data[2]));
        a.addLast(new DataPoint(time, Math.sqrt(data[0] * data[0] + data[1] * data[1] + data[2] * data[2])));
        if (x.size() > size) {
            x.removeFirst();
            y.removeFirst();
            z.removeFirst();
            a.removeFirst();
        }
    }
}