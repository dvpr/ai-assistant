package com.example.llama;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {

    // 复制 assets 中的文件到应用内部存储
    public static void copyAssetToInternalStorage(Context context, String assetFileName) {
        try {
            // 从 assets 文件夹读取文件
            InputStream in = context.getAssets().open(assetFileName);
            File outFile = new File(context.getFilesDir(), assetFileName);  // 获取应用内部存储的路径
            OutputStream out = new FileOutputStream(outFile);  // 将文件写入内部存储

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}