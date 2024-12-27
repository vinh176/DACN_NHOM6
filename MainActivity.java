package vn.edu.app2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    static final String TAG = "WebSocket";
    WebSocket webSocket;
    EditText ipAddressEditText;
    TextView fireTextView, gasTextView, fireStatusTextView, ledStatusTextView, fanStatusTextView, sirenStatusTextView;
    Button connectButton, toggleLedButton, toggleFanButton, toggleSirenButton;
    Switch switchMode;
    boolean ledStatus = false;
    boolean fanStatus = false;
    boolean sirenStatus = false;
    boolean isAutomaticMode = true;
    private long lastUpdate = 0;
    private static final long DEBOUNCE_INTERVAL = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind UI elements
        fireStatusTextView = findViewById(R.id.fireStatusTextView);
        ipAddressEditText = findViewById(R.id.ipAddressEditText);
        fireTextView = findViewById(R.id.fireTextView);
        gasTextView = findViewById(R.id.gasTextView);
        connectButton = findViewById(R.id.connectButton);
        switchMode = findViewById(R.id.switchMode);
        ledStatusTextView = findViewById(R.id.ledStatusTextView);
        fanStatusTextView = findViewById(R.id.fanStatusTextView);
        sirenStatusTextView = findViewById(R.id.sirenStatusTextView);
        toggleLedButton = findViewById(R.id.toggleLedButton);
        toggleFanButton = findViewById(R.id.togglefanButton);
        toggleSirenButton = findViewById(R.id.toggleSirenButton);

        connectButton.setOnClickListener(view -> {
            String ipAddress = ipAddressEditText.getText().toString().trim();
            if (!ipAddress.isEmpty()) {
                connectWebSocket(ipAddress);
            } else {
                Toast.makeText(MainActivity.this, "ĐỊA CHỈ IP KHÔNG CHÍNH XÁC", Toast.LENGTH_SHORT).show();
            }
        });

        switchMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isAutomaticMode = isChecked;
            String mode = isChecked ? "automatic" : "manual";
            sendCommandToESP32("mode", mode);
            Toast.makeText(MainActivity.this, "Mode set to: " + mode, Toast.LENGTH_SHORT).show();
        });

        toggleLedButton.setOnClickListener(view -> {
            if (!isAutomaticMode) {
                ledStatus = !ledStatus;
                sendCommandToESP32("led", ledStatus ? "on" : "off");
            } else {
                Toast.makeText(MainActivity.this, "ĐANG Ở CHẾ ĐỘ TỰ ĐỘNG", Toast.LENGTH_SHORT).show();
            }
        });

        toggleFanButton.setOnClickListener(view -> {
            if (!isAutomaticMode) {
                fanStatus = !fanStatus;
                sendCommandToESP32("fan", fanStatus ? "on" : "off");
            } else {
                Toast.makeText(MainActivity.this, "ĐANG Ở CHẾ ĐỘ TỰ ĐỘNG", Toast.LENGTH_SHORT).show();
            }
        });

        toggleSirenButton.setOnClickListener(view -> {
            if (!isAutomaticMode) {
                sirenStatus = !sirenStatus;
                sendCommandToESP32("siren", sirenStatus ? "on" : "off");
            } else {
                Toast.makeText(MainActivity.this, "ĐANG Ở CHẾ ĐỘ TỰ ĐỘNG", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void connectWebSocket(String ipAddress) {
        if (webSocket != null) {
            webSocket.close(1000, "Closing previous connection");
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("ws://" + ipAddress + ":81")
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "CONNECTED TO WEBSOCKET", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdate > DEBOUNCE_INTERVAL) {
                    lastUpdate = currentTime;

                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonObject = new JSONObject(text);
                            int fireValue = jsonObject.optInt("fire", 1);
                            int gasValue = jsonObject.optInt("gas", 0);
                            boolean receivedLedStatus = jsonObject.optInt("led", 0) == 1;
                            boolean receivedFanStatus = jsonObject.optInt("fan", 0) == 1;
                            boolean receivedSirenStatus = jsonObject.optInt("siren", 0) == 1;

                            fireTextView.setText("Fire Status: " + (fireValue == 0 ? "Detected" : "Safe"));
                            gasTextView.setText("Gas Value: " + gasValue);
                            ledStatusTextView.setText("LED Status: " + (receivedLedStatus ? "ON" : "OFF"));
                            fanStatusTextView.setText("Fan Status: " + (receivedFanStatus ? "ON" : "OFF"));
                            sirenStatusTextView.setText("Siren Status: " + (receivedSirenStatus ? "ON" : "OFF"));

                            if (isAutomaticMode) {
                                handleAutomaticMode(fireValue, gasValue);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this, "LỖI NHẬN DỮ LIỆU ", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "KẾT NỐI THẤT BẠI: " + t.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "WEBSOCKET CLOSING: " + reason, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "WEBSOCKET CLOSED", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void handleAutomaticMode(int fireValue, int gasValue) {
        if (fireValue == 0 && gasValue > 1000) {
            fireStatusTextView.setText("Cảnh báo: CÓ CHÁY VÀ RÒ RỈ GAS");
            updateDeviceStatus(true, true, true);
            sendDeviceCommands("on");
        }
        else if(fireValue == 0)
        {
            fireStatusTextView.setText("Cảnh báo: CÓ CHÁY");
            updateDeviceStatus(true, true, true);
            sendDeviceCommands("on");
        }
        else if(gasValue > 1000)
        {
            fireStatusTextView.setText("Cảnh báo: RÒ RỈ GAS");
            updateDeviceStatus(true, true, true);
            sendDeviceCommands("on");
        }
        else
        {
            fireStatusTextView.setText("Cảnh báo: NO");
            updateDeviceStatus(false, false, false);
            sendDeviceCommands("off");
        }
    }

    private void updateDeviceStatus(boolean led, boolean fan, boolean siren) {
        ledStatus = led;
        fanStatus = fan;
        sirenStatus = siren;

        ledStatusTextView.setText("LED Status: " + (led ? "ON" : "OFF"));
        fanStatusTextView.setText("Fan Status: " + (fan ? "ON" : "OFF"));
        sirenStatusTextView.setText("Siren Status: " + (siren ? "ON" : "OFF"));
    }

    private void sendDeviceCommands(String status) {
        sendCommandToESP32("led", status);
        sendCommandToESP32("fan", status);
        sendCommandToESP32("siren", status);
    }

    private void sendCommandToESP32(String device, String status) {
        try {
            JSONObject command = new JSONObject();
            command.put("device", device);
            command.put("status", status);
            if (webSocket != null) {
                webSocket.send(command.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "LỖI TRUYỀN NHẬN DỮ LIỆU", Toast.LENGTH_SHORT).show();
        }
    }
}
