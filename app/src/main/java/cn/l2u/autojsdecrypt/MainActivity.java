package cn.l2u.autojsdecrypt;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MainActivity extends AppCompatActivity {

    public static final int PICKFILE_REQUEST_CODE = 1001;


    private LinkedList<String> logs = new LinkedList<>();

    private String apkPath = null;

    private BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return logs.size();
        }

        @Override
        public Object getItem(int position) {
            return logs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = new TextView(MainActivity.this);
            }
            TextView textView = (TextView) convertView;
            textView.setText(logs.get(position));
            return convertView;
        }
    };

    private void printl(String... log) {
        String l = String.join(" ", log);

        runOnUiThread(() -> {
            if (logs.size() > 1000) logs.removeFirst();
            logs.add(l);
            adapter.notifyDataSetChanged();
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions(new String[]{
                "android.permission.WRITE_EXTERNAL_STORAGE"
        }, 0);

        ListView logView = findViewById(R.id.logView);
        logView.setAdapter(adapter);
    }

    public void selectApk(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("file/*");
        startActivityForResult(intent, PICKFILE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == PICKFILE_REQUEST_CODE) {
            if (data != null) {
                logs.clear();
                apkPath = getRealPathFromURI(data.getData());
                printl("目标文件:\n" + apkPath);
            }
        } else super.onActivityResult(requestCode, resultCode, data);
    }

    public String getRealPathFromURI(Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor == null) return null;
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    public void runDecrypt(View view) {
        if (apkPath == null) {
            printl("请先选择apk");
            return;
        }

        if (!new File(apkPath).exists()) {
            printl("apk文件不存在");
            return;
        }

        logs.clear();

        printl("开始解密,目标文件:\n" + apkPath);

        new Thread(() -> {
            try {
                File apkFile = new File(apkPath);

                File outDir = new File(Environment.getExternalStorageDirectory(), "AutoJsDecrypt/" + apkFile.getName());

                FileUtils.deleteQuietly(outDir);

                if (!outDir.exists() && !outDir.mkdirs()) {
                    printl("无法创建文件夹:" + outDir);
                    return;
                }

                Tools tools = new Tools();

                try (ZipFile zipFile = new ZipFile(apkFile);) {
                    ZipEntry entry = zipFile.getEntry("classes.dex");
                    try (InputStream in = zipFile.getInputStream(entry);
                         FileOutputStream out = new FileOutputStream(new File(getCacheDir(), "classes.dex"));) {
                        IOUtils.copy(in, out);
                    }

                    entry = zipFile.getEntry("assets/project/project.json");

                    String config;
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        config = IOUtils.toString(in, StandardCharsets.UTF_8);
                    }

                    tools.init(new File(getCacheDir(), "classes.dex").getAbsolutePath(), config);

                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        entry = entries.nextElement();

                        if (!entry.getName().startsWith("assets/project")) continue;
                        if (!entry.getName().endsWith(".js")) continue;

                        File outFile = new File(outDir, entry.getName());
                        outFile.getParentFile().mkdirs();

                        try (InputStream in = zipFile.getInputStream(entry); FileOutputStream out = new FileOutputStream(outFile)) {
                            byte[] data = tools.decrypt(IOUtils.toByteArray(in));
                            out.write(data);
                            printl(entry.getName().substring("assets/project".length() + 1) + " " + "成功");
                        } catch (Exception e) {
                            printl(entry.getName().substring("assets/project".length() + 1) + " " + "失败");
                        }
                    }
                }

                printl("解密完成");
            } catch (Exception e) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                e.printStackTrace(new PrintStream(bos));
                printl("操作失败:\n" + new String(bos.toByteArray()));
            }
        }).start();
    }
}