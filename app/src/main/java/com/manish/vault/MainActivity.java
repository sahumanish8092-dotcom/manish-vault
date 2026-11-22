package com.manish.vault;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import android.content.SharedPreferences;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import android.util.Base64;

public class MainActivity extends Activity {

    LinearLayout layout;
    EditText pinInput;
    SharedPreferences prefs;

    int gold = Color.rgb(220, 180, 75);
    int black = Color.rgb(12, 12, 14);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("vault_settings", MODE_PRIVATE);
        showLockScreen();
    }

    void showLockScreen() {
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(32, 32, 32, 32);
        layout.setBackgroundColor(black);

        TextView logo = new TextView(this);
        logo.setText("◆");
        logo.setTextSize(55);
        logo.setTextColor(gold);
        logo.setGravity(Gravity.CENTER);
        layout.addView(logo);

        TextView title = new TextView(this);
        title.setText("MANISH VAULT");
        title.setTextSize(27);
        title.setTextColor(gold);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        boolean setup = !prefs.contains("pin_hash");
        subtitle.setText(setup ? "Create Your Security PIN" : "Enter Your Security PIN");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 16, 0, 25);
        layout.addView(subtitle);

        pinInput = new EditText(this);
        pinInput.setHint("Enter 6-digit PIN");
        pinInput.setHintTextColor(Color.GRAY);
        pinInput.setTextColor(Color.WHITE);
        pinInput.setGravity(Gravity.CENTER);
        pinInput.setTextSize(20);
        pinInput.setInputType(2);
        pinInput.setSingleLine(true);
        layout.addView(pinInput);

        Button unlock = new Button(this);
        unlock.setText(setup ? "CREATE PIN" : "UNLOCK VAULT");
        unlock.setTextColor(Color.BLACK);
        unlock.setBackgroundTintList(android.content.res.ColorStateList.valueOf(gold));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            -1, -2
        );
        params.setMargins(0, 25, 0, 0);
        layout.addView(unlock, params);

        unlock.setOnClickListener(v -> handlePin());

        setContentView(layout);
    }

    String hashPin(String pin, String salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
            pin.toCharArray(),
            Base64.decode(salt, Base64.DEFAULT),
            120000,
            256
        );
        SecretKeyFactory factory =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] result = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    void handlePin() {
        String pin = pinInput.getText().toString();

        if (!pin.matches("[0-9]{6}")) {
            Toast.makeText(this, "Enter exactly 6 digits", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (!prefs.contains("pin_hash")) {
                byte[] saltBytes = new byte[16];
                new SecureRandom().nextBytes(saltBytes);
                String salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP);
                String hash = hashPin(pin, salt);

                prefs.edit()
                    .putString("pin_salt", salt)
                    .putString("pin_hash", hash)
                    .apply();

                Toast.makeText(this, "PIN created successfully", Toast.LENGTH_SHORT).show();
                showHomeScreen();
            } else {
                String salt = prefs.getString("pin_salt", "");
                String savedHash = prefs.getString("pin_hash", "");
                if (hashPin(pin, salt).equals(savedHash)) {
                    showHomeScreen();
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                    pinInput.setText("");
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Security error", Toast.LENGTH_SHORT).show();
        }
    }

    void showHomeScreen() {
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(25, 45, 25, 25);
        layout.setBackgroundColor(black);

        TextView title = new TextView(this);
        title.setText("MANISH VAULT");
        title.setTextColor(gold);
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView welcome = new TextView(this);
        welcome.setText("Your private space is ready");
        welcome.setTextColor(Color.WHITE);
        welcome.setTextSize(18);
        welcome.setGravity(Gravity.CENTER);
        welcome.setPadding(0, 25, 0, 30);
        layout.addView(welcome);

        String[] sections = {
            "📷  Photos & Videos",
            "📁  Secure Documents",
            "📝  Secret Notes",
            "🔐  Password Manager",
            "🛡️  Security Settings"
        };

        for (String section : sections) {
            TextView item = new TextView(this);
            item.setText(section);
            item.setTextColor(gold);
            item.setTextSize(17);
            item.setPadding(18, 20, 18, 20);
            item.setBackgroundColor(Color.rgb(30, 30, 32));

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.setMargins(0, 8, 0, 8);
            layout.addView(item, p);
        }

        Button lock = new Button(this);
        lock.setText("LOCK VAULT");
        lock.setOnClickListener(v -> showLockScreen());
        layout.addView(lock);

        setContentView(layout);
    }
}
