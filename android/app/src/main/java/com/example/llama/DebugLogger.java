package com.example.llama;

import android.widget.TextView;
import android.widget.ScrollView;
import android.view.View;
import android.app.Activity;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DebugLogger {
    private static DebugLogger instance;
    private TextView debugTextView;
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

    private DebugLogger() {}

    public static DebugLogger getInstance() {
        if (instance == null) {
            instance = new DebugLogger();
        }
        return instance;
    }

    public void init(TextView textView) {
        this.debugTextView = textView;
        log("=== 调试日志系统已启动 ===");
    }

    public void log(final String message) {
        if (debugTextView == null) {
            Log.e("DebugLogger", "TextView未初始化: " + message);
            return;
        }

        if (debugTextView.getContext() instanceof Activity) {
            ((Activity) debugTextView.getContext()).runOnUiThread(() -> {
                String timestamp = sdf.format(new Date());
                String logLine = timestamp + " - " + message + "\n";
                debugTextView.append(logLine);
                // 自动滚动到底部
                View parent = (View) debugTextView.getParent();
                if (parent instanceof ScrollView) {
                    ((ScrollView) parent).fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }
}