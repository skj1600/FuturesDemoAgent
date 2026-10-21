package com.example.futuresdemoagent;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    TextView status, balance;
    Button startButton, stopButton;
    boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 40, 32, 32);
        layout.setBackgroundColor(Color.rgb(18, 22, 32));

        TextView title = new TextView(this);
        title.setText("Futures Demo Agent");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        balance = new TextView(this);
        balance.setText("\nDemo Balance: 1500\n");
        balance.setTextSize(20);
        balance.setTextColor(Color.WHITE);
        layout.addView(balance);

        status = new TextView(this);
        status.setText("Status: STOPPED\n");
        status.setTextSize(18);
        status.setTextColor(Color.YELLOW);
        layout.addView(status);

        startButton = new Button(this);
        startButton.setText("START DEMO");
        layout.addView(startButton);

        stopButton = new Button(this);
        stopButton.setText("STOP");
        layout.addView(stopButton);

        TextView note = new TextView(this);
        note.setText("\nDemo mode only.\nNo real trades or guaranteed profit.");
        note.setTextColor(Color.LTGRAY);
        layout.addView(note);

        startButton.setOnClickListener(v -> {
            running = true;
            status.setText("Status: DEMO RUNNING\n");
            status.setTextColor(Color.GREEN);
        });

        stopButton.setOnClickListener(v -> {
            running = false;
            status.setText("Status: STOPPED\n");
            status.setTextColor(Color.YELLOW);
        });

        setContentView(layout);
    }
}
