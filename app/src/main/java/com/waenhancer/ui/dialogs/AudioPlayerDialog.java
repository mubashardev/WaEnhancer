package com.waenhancer.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.waenhancer.R;

import java.io.File;
import java.io.IOException;

public class AudioPlayerDialog extends Dialog {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SeekBar seekBar;
    private final ImageButton btnPlayPause;
    private final TextView tvCurrentTime;
    private MediaPlayer mediaPlayer;
    private Runnable updateRunnable = () -> {};
    private boolean isPlaying;

    public AudioPlayerDialog(@NonNull Context context, @NonNull File audioFile) {
        super(context, com.google.android.material.R.style.Theme_Material3_DayNight_Dialog);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_audio_player, null);
        setContentView(view);

        if (getWindow() != null) {
            getWindow().setLayout(
                    (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9f),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        seekBar = view.findViewById(R.id.seekBar);
        btnPlayPause = view.findViewById(R.id.btn_play_pause);
        tvCurrentTime = view.findViewById(R.id.tv_current_time);
        TextView tvTotalTime = view.findViewById(R.id.tv_total_time);
        TextView tvTitle = view.findViewById(R.id.tv_title);
        ImageButton btnClose = view.findViewById(R.id.btn_close);

        tvTitle.setText(audioFile.getName());

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.prepare();
        } catch (IOException e) {
            mediaPlayer.release();
            mediaPlayer = null;
            dismiss();
            return;
        }

        int duration = mediaPlayer.getDuration();
        seekBar.setMax(duration);
        tvCurrentTime.setText(formatTime(0));
        tvTotalTime.setText(formatTime(duration));

        mediaPlayer.setOnCompletionListener(mp -> {
            isPlaying = false;
            btnPlayPause.setImageResource(R.drawable.ic_play);
            seekBar.setProgress(0);
            tvCurrentTime.setText(formatTime(0));
            mediaPlayer.seekTo(0);
        });

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnClose.setOnClickListener(v -> dismiss());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress);
                    tvCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPlaying) {
                    int position = mediaPlayer.getCurrentPosition();
                    seekBar.setProgress(position);
                    tvCurrentTime.setText(formatTime(position));
                    handler.postDelayed(this, 100L);
                }
            }
        };

        setOnDismissListener(dialog -> releasePlayer());
        togglePlayPause();
    }

    private void togglePlayPause() {
        if (isPlaying) {
            if (mediaPlayer == null) {
                return;
            }
            mediaPlayer.pause();
            btnPlayPause.setImageResource(R.drawable.ic_play);
            handler.removeCallbacks(updateRunnable);
        } else {
            if (mediaPlayer == null) {
                return;
            }
            mediaPlayer.start();
            btnPlayPause.setImageResource(R.drawable.ic_pause);
            handler.post(updateRunnable);
        }
        isPlaying = !isPlaying;
    }

    private void releasePlayer() {
        handler.removeCallbacks(updateRunnable);
        if (mediaPlayer == null) {
            return;
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        mediaPlayer.release();
        mediaPlayer = null;
    }

    @NonNull
    private String formatTime(int millis) {
        int seconds = millis / 1000;
        int minutes = seconds / 60;
        seconds %= 60;
        if (minutes >= 60) {
            int hours = minutes / 60;
            minutes %= 60;
            return String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds);
    }
}
