package com.example.llama;

import android.content.Context;
import java.io.FileOutputStream;

public class Logger {

    public static void log(Context ctx, String msg) {
        try {
            FileOutputStream fos = ctx.openFileOutput("log.txt", Context.MODE_APPEND);
            fos.write((msg + "\n").getBytes());
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
