package com.test.bright.sensorreading;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.w3c.dom.Text;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Date;
import java.util.LinkedList;

public class SensorActivity extends AppCompatActivity implements SensorEventListener{
    private SensorManager mSensorManager;
    private Sensor mMag;
    private long uTime1,uTime2,uTimeStart;
    private long nInterval;
    private int bufSize=200;
    private boolean running=false;
    private boolean plotting=false;
    private LinkedList<DataPoint> pointBuf = new LinkedList<>();

    LineGraphSeries<DataPoint> series;

    private Button fab,plot_btn;
    private GraphView graph;
    private TextView sensor_data_text;

    private PrintWriter fout;
    private File dataDir;
    private String filename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        fab = (Button) findViewById(R.id.fab);
        fab.setText("Start");
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(running==false){
                    running=true;
                    mSensorManager.registerListener(SensorActivity.this, mMag, 1000*1000/*SensorManager.SENSOR_DELAY_NORMAL*/);
                    fab.setText("Pause");
                    uTimeStart=System.nanoTime();
                    if(fout!=null)  fout.close();
                    try {
                        filename=new Date().toString()+".txt";
                        fout = new PrintWriter(new File(dataDir,filename));
                        System.out.println(Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+filename);
                    }
                    catch(Exception e){
                        e.printStackTrace();
                        System.out.println("Failed to open file");
                    }
                    graph.removeAllSeries();
                    series=new LineGraphSeries<>();
                    graph.addSeries(series);
                }
                else{
                    running=false;
                    try {
                        mSensorManager.unregisterListener(SensorActivity.this, mMag);
                    }
                    catch(Exception e){
                        Log.d("Warning","Sensor not registered");
                    }
                    fout.close();
                    Toast toast = Toast.makeText(SensorActivity.this,"Data saved to file \""+filename+"\"",Toast.LENGTH_SHORT);
                    toast.show();
                    while(!pointBuf.isEmpty())
                        pointBuf.remove();
                    fab.setText("Start");
                }
            }
        });

        plot_btn = findViewById(R.id.plot_btn);
        plot_btn.setText("Start plotting");
        plot_btn.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(plotting==false) {
                            plot_btn.setText("Pause Plotting");
                            series = new LineGraphSeries<>(pointBuf.toArray(new DataPoint[0]));
                            graph.removeAllSeries();
                            graph.addSeries(series);
                            plotting=true;
                        }
                        else{
                            plot_btn.setText("Start Plotting");
                            plotting=false;
                        }

                    }
                }
        );

        sensor_data_text = (TextView) findViewById(R.id.sensor_data_text);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mMag = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        long mInterval = 100;
        nInterval=mInterval*1000*1000;
        graph = (GraphView) findViewById(R.id.graph);
        graph.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                    }
                }
        );
        series = new LineGraphSeries<>();
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
            dataDir = new File(Environment.getExternalStorageDirectory(),"SensorReading");
            dataDir.mkdirs();
        }
        catch(Exception e){
            e.printStackTrace();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        if(getRequestedOrientation()!= ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        super.onResume();
        if(running) {
            mSensorManager.registerListener(this, mMag, 1000 * 1000/*SensorManager.SENSOR_DELAY_NORMAL*/);
            uTime1 = System.nanoTime();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(running)
            mSensorManager.unregisterListener(this);
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.
        uTime2=System.nanoTime();
        if(uTime2-uTime1>=nInterval) {
            uTime1=uTime2;
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            double m=Math.sqrt(x*x+y*y+z*z);
            pointBuf.addLast(new DataPoint((uTime2 - uTimeStart) / 1e9, m));
            if(pointBuf.size()>bufSize){
                pointBuf.removeFirst();
            }
            fout.format("%f\t%f\n",(uTime2-uTimeStart)/1e9,m);
            sensor_data_text.setText(Double.toString(Math.sqrt(x * x + y * y + z * z)));
            if(plotting) {
                series.appendData(new DataPoint((uTime2 - uTimeStart) / 1e9, m), true, bufSize, false);
            }
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
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if(fout!=null)
            fout.close();
    }
}
