package com.example.llama;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * UI 管理器：负责动态创建 Activity 的所有视图，并提供更新界面组件的便捷方法。
 */
public class UiManager {
    private final Activity activity;
    private TextView resultTextView;
    private TextView debugTextView;
    private EditText inputEditText;
    private DebugLogger debugLogger;

    // 视图组件的引用，供外部直接访问（也可以通过 getter 获取）
    public Button loadButton;
    public Button exportDbButton;
    public Button generateButton;
    public FrameLayout fragmentContainer;

    public UiManager(Activity activity) {
        this.activity = activity;
    }

    /**
     * 构建完整的布局并返回根视图。
     * @param debugLogger 调试日志工具，用于初始化 debugTextView
     * @return 根视图 LinearLayout
     */
    public LinearLayout buildLayout(DebugLogger debugLogger) {
        this.debugLogger = debugLogger;

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        // 模型管理 Fragment 容器
        fragmentContainer = new FrameLayout(activity);
        fragmentContainer.setId(View.generateViewId());
        fragmentContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(fragmentContainer);

        // 重新加载模型按钮
        loadButton = new Button(activity);
        loadButton.setText("重新加载模型");
        layout.addView(loadButton);

        // 导出记忆数据库按钮
        exportDbButton = new Button(activity);
        exportDbButton.setText("导出记忆数据库");
        layout.addView(exportDbButton);

        // 输入框
        inputEditText = new EditText(activity);
        inputEditText.setHint("输入提示词，例如：你好，请介绍一下你自己");
        layout.addView(inputEditText);

        // 生成按钮
        generateButton = new Button(activity);
        generateButton.setText("生成文本");
        layout.addView(generateButton);

        // 结果显示区
        resultTextView = new TextView(activity);
        resultTextView.setText("生成结果将显示在这里");
        resultTextView.setTextIsSelectable(true);
        resultTextView.setTextSize(14);
        ScrollView resultScrollView = new ScrollView(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        resultScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (150 * density)));
        resultScrollView.setPadding(16, 16, 16, 16);
        resultScrollView.setBackgroundColor(0xFFF0F0F0);
        resultScrollView.addView(resultTextView);
        layout.addView(resultScrollView);

        // 调试日志区
        debugTextView = new TextView(activity);
        debugTextView.setTextSize(12);
        debugTextView.setPadding(16, 16, 16, 16);
        debugTextView.setTextIsSelectable(true);
        ScrollView debugScrollView = new ScrollView(activity);
        debugScrollView.addView(debugTextView);
        layout.addView(debugScrollView);

        // 绑定调试日志
        debugLogger.init(debugTextView);
        return layout;
    }

    /** 获取输入文本 */
    public String getInputText() {
        return inputEditText.getText().toString().trim();
    }

    /** 设置结果显示文本 */
    public void setResultText(String text) {
        resultTextView.setText(text);
    }

    /** 追加结果显示文本（用于流式输出） */
    public void appendResultText(String text) {
        String current = resultTextView.getText().toString();
        if (current.equals("📱 本地生成中...") || current.equals("☁️ 云端思考中...")) {
            resultTextView.setText(text);
        } else {
            resultTextView.setText(current + text);
        }
    }

    /** 获取调试日志 TextView（用于初始化 DebugLogger） */
    public TextView getDebugTextView() {
        return debugTextView;
    }
}