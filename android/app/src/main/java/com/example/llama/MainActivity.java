package com.example.llama;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    static {
        try {
            System.loadLibrary("wrapper");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public native String stringFromJNI();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);

        try {
            Logger.log(this, "App start");

            String result = stringFromJNI();
            // Log.e("TEST", this.getClass().getName());

            Logger.log(this, "JNI result: " + result);
            tv.setText(result);

        } catch (Throwable e) {
            Logger.log(this, "ERROR: " + e.toString());
            tv.setText("ERROR:\n" + e.toString());
        }

        setContentView(tv);
    }
}
