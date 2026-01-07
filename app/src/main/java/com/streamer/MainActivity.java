package com.streamer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import java.io.*;

public class MainActivity extends Activity {
    
    private EditText streamKeyInput;
    private Button pickBtn, streamBtn;
    private TextView logView;
    private Uri selectedVideoUri;
    private String tempVideoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Keep screen on so stream doesn't die
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // --- UI ---
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);
        layout.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("YouTube Loop Streamer");
        title.setTextSize(24);
        layout.addView(title);

        pickBtn = new Button(this);
        pickBtn.setText("1. Select Video (16:9 or 9:16)");
        layout.addView(pickBtn);

        streamKeyInput = new EditText(this);
        streamKeyInput.setHint("Paste YouTube Stream Key Here");
        layout.addView(streamKeyInput);

        streamBtn = new Button(this);
        streamBtn.setText("2. START LOOP STREAM");
        streamBtn.setEnabled(false);
        layout.addView(streamBtn);

        logView = new TextView(this);
        logView.setText("Status: Ready");
        logView.setPadding(0, 50, 0, 0);
        layout.addView(logView);

        setContentView(layout);

        // --- Actions ---
        tempVideoPath = getCacheDir().getAbsolutePath() + "/stream_temp.mp4";

        pickBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            startActivityForResult(intent, 101);
        });

        streamBtn.setOnClickListener(v -> startStream());
    }

    private void startStream() {
        String key = streamKeyInput.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(this, "Enter Stream Key!", Toast.LENGTH_SHORT).show();
            return;
        }

        logView.setText("Status: Starting Engine...");
        streamBtn.setEnabled(false);

        // YouTube RTMP URL + Your Key
        String rtmpUrl = "rtmp://a.rtmp.youtube.com/live2/" + key;

        // FFmpeg Command:
        // -re (Read input at native speed)
        // -stream_loop -1 (Loop forever)
        // -i [file] (Input)
        // -c:v copy -c:a copy (Copy format directly, don't re-encode = FAST)
        // -f flv (Format for streaming)
        
        String cmd = "-re -stream_loop -1 -i " + tempVideoPath + " -c:v libx264 -preset ultrafast -b:v 3000k -maxrate 3000k -bufsize 6000k -pix_fmt yuv420p -g 50 -c:a aac -b:a 128k -ar 44100 -f flv " + rtmpUrl;

        FFmpegKit.executeAsync(cmd, session -> {
            ReturnCode returnCode = session.getReturnCode();
            runOnUiThread(() -> {
                if (returnCode.isSuccess()) {
                    logView.setText("Stream Finished (Stopped).");
                } else {
                    logView.setText("Stream Failed/Stopped. Check Key.");
                }
                streamBtn.setEnabled(true);
            });
        }, log -> {
            runOnUiThread(() -> logView.setText("Streaming... \n" + log.getMessage()));
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            selectedVideoUri = data.getData();
            logView.setText("Copying file... wait...");
            
            // Background copy to avoid freezing UI
            new Thread(() -> {
                copyFileToCache(selectedVideoUri);
                runOnUiThread(() -> {
                    logView.setText("Video Ready! Enter Key & Start.");
                    streamBtn.setEnabled(true);
                });
            }).start();
        }
    }

    // Helper: Android cannot stream from Gallery URI directly with FFmpeg
    // We must copy it to a private app file first.
    private void copyFileToCache(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(tempVideoPath)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
