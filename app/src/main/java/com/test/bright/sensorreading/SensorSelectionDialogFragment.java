package com.test.bright.sensorreading;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Created by Bright on 2017/11/7.
 */

public class SensorSelectionDialogFragment extends DialogFragment {

    public static final String KEY_SELECTED_SENSORS = "selected_sensors";
    public static final String KEY_AVAILABLE_SENSORS_ID = "selected_sensors_id";
    public static final String KEY_AVAILABLE_SENSORS_DESC = "selected_sensors_desc";

    private int cnt=0;

    private ArrayList<Integer> mSelected = new ArrayList<>();

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        ArrayList<String> desc = getArguments().getStringArrayList(KEY_AVAILABLE_SENSORS_DESC);         //not in alphabetic order
        final ArrayList<Integer> id = getArguments().getIntegerArrayList(KEY_AVAILABLE_SENSORS_ID);

        String[] sDesc = new String[desc.size()];
        desc.toArray(sDesc);

        boolean[] checked = new boolean[desc.size()];
        if(cnt==0) {
            cnt++;
            //In default, magnetometer is checked.
            mSelected.add(Sensor.TYPE_MAGNETIC_FIELD);
        }

        for(int i=0;i<desc.size();++i){
            checked[i]=false;
            for(int j=0;j<mSelected.size();++j){
                if(id.get(i)==mSelected.get(j)){
                    checked[i]=true;
                    break;
                }
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setTitle("Select sensors")
                .setMultiChoiceItems(
                        sDesc,
                        checked,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int which, boolean isChecked) {
                                if(isChecked)
                                    mSelected.add(id.get(which));
                                else
                                    mSelected.remove(new Integer(id.get(which)));
                            }
                        }
                )
                .setPositiveButton(R.string.button_confirm, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // FIRE ZE MISSILES!
                        Bundle arg = new Bundle();
                        mListener.onDialogPositiveClicked(SensorSelectionDialogFragment.this,mSelected);
                    }
                })
                .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                        mListener.onDialogNegativeClicked(SensorSelectionDialogFragment.this);
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public void onSaveInstanceState(Bundle outState){
        outState.putIntegerArrayList(KEY_SELECTED_SENSORS,mSelected);
    }

    public interface SensorSelectionDialogListener{
        void onDialogPositiveClicked(DialogFragment dialog, ArrayList<Integer> selected);
        void onDialogNegativeClicked(DialogFragment dialog);
    }

    private SensorSelectionDialogListener mListener;

    @Override
    public void onAttach(Context ctx){
        super.onAttach(ctx);
        try{
            mListener = (SensorSelectionDialogListener) ctx;
        }
        catch (ClassCastException e){
            e.printStackTrace();
        }
    }

//    @Override
//    public void onStart(){
//        super.onStart();
//        System.out.println("Dia started");
//    }
}
