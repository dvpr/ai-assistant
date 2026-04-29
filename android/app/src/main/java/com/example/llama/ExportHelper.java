package com.example.llama;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 数据库导出辅助类：提供导出 memory.db 到 Download 目录，并可选分享。
 */
public class ExportHelper {
    private final Activity activity;
    private final DebugLogger logger;

    public ExportHelper(Activity activity, DebugLogger logger) {
        this.activity = activity;
        this.logger = logger;
    }

    /** 执行数据库导出（需先确保已获得存储权限，且指纹验证通过） */
    public void exportDatabase(ExportCallback callback) {
        try {
            File dbFile = activity.getDatabasePath("memory.db");
            if (dbFile == null || !dbFile.exists()) {
                callback.onError("数据库文件不存在");
                return;
            }

            File destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!destDir.exists()) destDir.mkdirs();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File destFile = new File(destDir, "AI_Memory_Backup_" + timestamp + ".db");

            FileInputStream fis = new FileInputStream(dbFile);
            FileOutputStream fos = new FileOutputStream(destFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fis.close();
            fos.close();

            logger.log("数据库导出成功: " + destFile.getAbsolutePath());
            callback.onSuccess(destFile);
        } catch (Exception e) {
            logger.log("导出失败: " + e.getMessage());
            callback.onError(e.getMessage());
        }
    }

    /** 通过 Intent 分享文件（需要 FileProvider 配置） */
    public void shareFile(File file) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/octet-stream");
        Uri fileUri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", file);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(shareIntent, "分享备份文件"));
    }

    public interface ExportCallback {
        void onSuccess(File exportedFile);
        void onError(String error);
    }
}