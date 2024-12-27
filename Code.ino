#include <WiFi.h>
#include <WebSocketsServer.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <ArduinoJson.h>

const char* ssid = "DACN";
const char* password = "11110000";

LiquidCrystal_I2C lcd(0x27, 16, 2);

const int fireSensorPin = 26;
const int gasSensorPin = 32;
const int ledPin = 16;
const int fanPin = 17;
const int sirenPin = 18;

WebSocketsServer webSocket = WebSocketsServer(81);

bool isAutomaticMode = true;

unsigned long previousMillis = 0;
const long interval = 1000;  // Kiểm tra cảm biến mỗi giây

void webSocketEvent(uint8_t num, WStype_t type, uint8_t* payload, size_t length) {
    if (type == WStype_CONNECTED) {
        IPAddress ip = webSocket.remoteIP(num);
        Serial.printf("Client %u connected from %s\n", num, ip.toString().c_str());
    } else if (type == WStype_DISCONNECTED) {
        Serial.printf("Client %u disconnected\n", num);
    } else if (type == WStype_TEXT) {
        String message = String((char*)payload);
        Serial.println("Received message: " + message);

        StaticJsonDocument<200> doc;
        DeserializationError error = deserializeJson(doc, message);

        if (error) {
            Serial.println("Failed to parse JSON");
            return;
        }

        const char* device = doc["device"];
        const char* status = doc["status"];

        if (strcmp(device, "mode") == 0) {
            isAutomaticMode = strcmp(status, "automatic") == 0;
            Serial.println(isAutomaticMode ? "Mode set to automatic" : "Mode set to manual");
        }

        if (!isAutomaticMode) {
            if (strcmp(device, "led") == 0) {
                digitalWrite(ledPin, strcmp(status, "on") == 0);
                Serial.println("LED control");
            } else if (strcmp(device, "fan") == 0) {
                digitalWrite(fanPin, strcmp(status, "on") == 0);
            } else if (strcmp(device, "siren") == 0) {
                digitalWrite(sirenPin, strcmp(status, "on") == 0);
            }
        }
    }
}

void setup() {
    Serial.begin(9600);
    lcd.init();
    lcd.backlight();

    WiFi.begin(ssid, password);
    Serial.println("Connecting to WiFi...");
    lcd.print("WiFi Connecting...");
    while (WiFi.status() != WL_CONNECTED) {
        delay(1000);
        Serial.print(".");
        lcd.setCursor(0, 1);
        lcd.print(".");
    }

    Serial.println(WiFi.localIP());
    lcd.setCursor(0, 0);
    lcd.print("Dia Chi IP: ");
    lcd.setCursor(0, 1);
    lcd.print(WiFi.localIP());
    delay(7000);
    lcd.clear();
    webSocket.begin();
    webSocket.onEvent(webSocketEvent);

    pinMode(fireSensorPin, INPUT);
    pinMode(gasSensorPin, INPUT);
    pinMode(ledPin, OUTPUT);
    pinMode(fanPin, OUTPUT);
    pinMode(sirenPin, OUTPUT);

    digitalWrite(ledPin, LOW);
    digitalWrite(fanPin, LOW);
    digitalWrite(sirenPin, LOW);
}

void loop() {
    webSocket.loop();

    unsigned long currentMillis = millis();

    if (currentMillis - previousMillis >= interval) {
        previousMillis = currentMillis;

        int fireValue = digitalRead(fireSensorPin);
        int gasValue = analogRead(gasSensorPin);

        sendWebSocketData(fireValue, gasValue);

        Serial.print("Fire Sensor: ");
        Serial.println(fireValue == 0 ? "Detected" : "Safe");

        Serial.print("Gas Sensor Value: ");
        Serial.println(gasValue);

        if (isAutomaticMode) {
            if (fireValue == 0 || gasValue > 1000) {
                digitalWrite(ledPin, HIGH);
                digitalWrite(fanPin, HIGH);
                digitalWrite(sirenPin, HIGH);
            } else {
                digitalWrite(ledPin, LOW);
                digitalWrite(fanPin, LOW);
                digitalWrite(sirenPin, LOW);
            }
        }
        displayToLCD(fireValue, gasValue);
    }
}

void sendWebSocketData(int fireValue, int gasValue) {
    int ledStatus = digitalRead(ledPin);
    int fanStatus = digitalRead(fanPin);
    int sirenStatus = digitalRead(sirenPin);

    String jsonResponse = "{";
    jsonResponse += "\"fire\": " + String(fireValue) + ",";
    jsonResponse += "\"gas\": " + String(gasValue) + ",";
    jsonResponse += "\"led\": " + String(ledStatus) + ",";
    jsonResponse += "\"fan\": " + String(fanStatus) + ",";
    jsonResponse += "\"siren\": " + String(sirenStatus);
    jsonResponse += "}";

    webSocket.broadcastTXT(jsonResponse);
}

void displayToLCD(int fireValue, int gasValue) {
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("Fire: ");
    lcd.print(fireValue == 0 ? "YES" : "NO");

    lcd.setCursor(0, 1);
    lcd.print("Gas:");
    lcd.print(gasValue);
}