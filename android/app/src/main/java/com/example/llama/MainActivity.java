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

    // public native String stringFromJNI();
    public native String infer(String input);  // 声明新的 JNI 函数

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);

        try {
            Logger.log(this, "App start");

            // String result = stringFromJNI();
            // Log.e("TEST", this.getClass().getName());

            // 复制文件从 assets 到内部存储
            FileUtils.copyAssetToInternalStorage(this, "gte-small-q8_0.gguf");

            // 调用 infer 方法并获取推理结果
            String input = "Hello, AI!";
            String result = infer(input);  // 调用 JNI 推理

            Logger.log(this, "JNI result: " + result);
            tv.setText(result);  // 显示推理结果
        } catch (Throwable e) {
            Logger.log(this, "ERROR: " + e.toString());
            tv.setText("ERROR:\n" + e.toString());
        }

        setContentView(tv);
    }
}
