package com.manish.vault;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.Manifest;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
    private static final int REQ_MEDIA = 4101;
    private static final int REQ_DOCUMENT = 4102;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int PIN_ITERATIONS = 120000;
    private static final int WRAP_ITERATIONS = 180000;

    private static final int BG = Color.rgb(3, 7, 17);
    private static final int PANEL = Color.rgb(8, 18, 35);
    private static final int PANEL_2 = Color.rgb(10, 27, 50);
    private static final int BLUE = Color.rgb(0, 154, 255);
    private static final int CYAN = Color.rgb(38, 219, 255);
    private static final int WHITE = Color.rgb(229, 242, 255);
    private static final int MUTED = Color.rgb(135, 178, 211);
    private static final int RED = Color.rgb(255, 91, 112);

    private SharedPreferences prefs;
    private LinearLayout root;
    private LinearLayout content;
    private byte[] masterKey;
    private boolean pickerActive = false;
    private long backgroundAt = 0L;
    private String currentPage = "home";
    private boolean decoyMode = false;
    private static final int REQ_LOCATION = 4103;
    private static final int RECOVERY_ITERATIONS = 240000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("vault_settings", MODE_PRIVATE);
        // Remove any decrypted preview left behind if Android previously killed the process.
        deleteTree(new File(getCacheDir(), "vault_previews"));
        getWindow().setStatusBarColor(Color.rgb(2, 8, 20));
        getWindow().setNavigationBarColor(Color.rgb(2, 8, 20));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        showLockScreen();
    }

    @Override
    protected void onPause() {
        super.onPause();
        backgroundAt = System.currentTimeMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (backgroundAt > 0L && masterKey != null && !pickerActive) {
            int timeout = prefs.getInt("auto_lock_seconds", 300);
            if (System.currentTimeMillis() - backgroundAt >= timeout * 1000L) {
                clearMasterKey();
                showLockScreen();
                toast("Vault auto-locked");
            }
        }
        backgroundAt = 0L;
    }

    @Override
    public void onBackPressed() {
        if (masterKey == null) {
            super.onBackPressed();
        } else if (!"home".equals(currentPage)) {
            showHomeScreen();
        } else {
            lockVault();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MEDIA || requestCode == REQ_DOCUMENT) {
            pickerActive = false;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                String category = requestCode == REQ_MEDIA ? "media" : "documents";
                if (copyUriIntoVault(data.getData(), category)) {
                    if ("media".equals(category)) showMediaScreen(); else showDocumentsScreen();
                }
            }
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void clearMasterKey() {
        if (masterKey != null) {
            for (int i = 0; i < masterKey.length; i++) masterKey[i] = 0;
            masterKey = null;
        }
        decoyMode = false;
        File previews = new File(getCacheDir(), "vault_previews");
        deleteTree(previews);
    }

    private void lockVault() {
        if (masterKey != null) logEvent(decoyMode ? "Decoy vault locked" : "Vault locked");
        clearMasterKey();
        decoyMode = false;
        showLockScreen();
    }

    private void showLockScreen() {
        currentPage = "lock";
        LinearLayout page = newPageRoot();
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        center.setPadding(dp(25), dp(30), dp(25), dp(30));
        page.addView(center, new LinearLayout.LayoutParams(-1, -1));

        TextView emblem = text("♛  ◈  ♛", 31, CYAN, true);
        emblem.setGravity(Gravity.CENTER);
        emblem.setShadowLayer(22f, 0, 0, BLUE);
        center.addView(emblem, matchWrap());

        TextView title = text("MANISH VAULT", 30, WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setShadowLayer(18f, 0, 0, BLUE);
        title.setPadding(0, dp(8), 0, dp(4));
        center.addView(title, matchWrap());

        TextView subtitle = text(prefs.contains("pin_hash") ? "ENTER YOUR SECURITY PIN" : "CREATE YOUR SECURITY PIN", 13, CYAN, true);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(9), 0, dp(26));
        center.addView(subtitle, matchWrap());

        EditText pin = edit("6-digit PIN", false, true);
        pin.setInputType( android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD );
        pin.setGravity(Gravity.CENTER);
        pin.setTextSize(22);
        center.addView(pin, matchWrap());

        Button access = primaryButton(prefs.contains("pin_hash") ? "UNLOCK VAULT" : "CREATE VAULT PIN");
        LinearLayout.LayoutParams accessParams = matchWrap();
        accessParams.setMargins(0, dp(22), 0, 0);
        center.addView(access, accessParams);
        access.setOnClickListener(v -> handlePin(pin.getText().toString()));

        TextView hint = text("PRIVATE • AES-256-GCM • LOCAL ENCRYPTION", 11, MUTED, true);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(25), 0, 0);
        center.addView(hint, matchWrap());
        Button forgot = smallButton("FORGOT PIN?  •  RECOVERY KEY");
        LinearLayout.LayoutParams forgotParams = matchWrap();
        forgotParams.setMargins(0, dp(17), 0, 0);
        center.addView(forgot, forgotParams);
        forgot.setOnClickListener(v -> showForgotPinDialog());
        TextView reminder = text(prefs.contains("wrapped_master_recovery")
                ? "Use your saved Recovery Key if you forget this PIN."
                : "Set up a Recovery Key inside Security Center before forgetting your PIN.", 10, MUTED, false);
        reminder.setGravity(Gravity.CENTER);
        reminder.setPadding(dp(8), dp(10), dp(8), 0);
        center.addView(reminder, matchWrap());
        setContentView(page);
    }

    private void handlePin(String pin) {
        if (pin == null || !pin.matches("[0-9]{6}")) {
            toast("Enter exactly 6 digits");
            return;
        }
        long remaining = prefs.getLong("lockout_until", 0L) - System.currentTimeMillis();
        if (remaining > 0L) {
            toast("Too many attempts. Try again in " + ((remaining + 999L) / 1000L) + " seconds.");
            return;
        }
        try {
            if (!prefs.contains("pin_hash")) {
                byte[] pinSalt = randomBytes(16);
                byte[] pinHash = derive(pin, pinSalt, PIN_ITERATIONS);
                prefs.edit().putString("pin_salt", b64(pinSalt)).putString("pin_hash", b64(pinHash)).apply();
                createAndWrapMasterKey(pin);
                resetFailedAttempts();
                logEvent("Vault created");
                toast("Vault PIN created. Set up Recovery Key in Security Center.");
            } else {
                byte[] salt = Base64Compat.decode(prefs.getString("pin_salt", ""));
                byte[] expected = Base64Compat.decode(prefs.getString("pin_hash", ""));
                byte[] actual = derive(pin, salt, PIN_ITERATIONS);
                if (!MessageDigest.isEqual(expected, actual)) {
                    if (tryUnlockDecoy(pin)) {
                        resetFailedAttempts();
                        logEvent("Decoy vault unlocked");
                        showHomeScreen();
                        return;
                    }
                    recordFailedAttempt();
                    toast("Incorrect PIN");
                    return;
                }
                decoyMode = false;
                if (!prefs.contains("wrapped_master")) {
                    // Upgrade a very early install without changing its existing PIN hash.
                    createAndWrapMasterKey(pin);
                    toast("Vault encryption enabled for this install");
                } else {
                    unlockMasterKey(pin);
                }
                resetFailedAttempts();
                logEvent("Vault unlocked");
            }
            showHomeScreen();
        } catch (Exception e) {
            clearMasterKey();
            decoyMode = false;
            toast("Could not unlock vault. Encrypted data was not changed.");
        }
    }

    private void recordFailedAttempt() {
        int count = prefs.getInt("failed_pin_count", 0) + 1;
        long delay = 0L;
        if (count >= 3) {
            int exponent = Math.min(count - 3, 5);
            delay = Math.min(300000L, 15000L * (1L << exponent));
        }
        long until = delay == 0L ? 0L : System.currentTimeMillis() + delay;
        prefs.edit().putInt("failed_pin_count", count).putLong("lockout_until", until).apply();
        logEvent("Incorrect PIN attempt #" + count);
    }

    private void resetFailedAttempts() {
        prefs.edit().putInt("failed_pin_count", 0).putLong("lockout_until", 0L).apply();
    }

    private boolean tryUnlockDecoy(String pin) throws Exception {
        if (!prefs.contains("decoy_pin_hash")) return false;
        byte[] salt = Base64Compat.decode(prefs.getString("decoy_pin_salt", ""));
        byte[] actual = derive(pin, salt, PIN_ITERATIONS);
        byte[] expected = Base64Compat.decode(prefs.getString("decoy_pin_hash", ""));
        if (!MessageDigest.isEqual(expected, actual)) return false;
        byte[] keySalt = Base64Compat.decode(prefs.getString("decoy_key_salt", ""));
        byte[] wrappingKey = derive(pin, keySalt, WRAP_ITERATIONS);
        byte[] wrapped = Base64Compat.decode(prefs.getString("decoy_wrapped_master", ""));
        byte[] decoyKey = decryptBytes(wrapped, wrappingKey);
        wipe(wrappingKey);
        if (decoyKey.length != 32) throw new Exception("Invalid decoy key");
        masterKey = decoyKey;
        decoyMode = true;
        return true;
    }

    private void createAndWrapMasterKey(String pin) throws Exception {
        masterKey = randomBytes(32);
        byte[] keySalt = randomBytes(16);
        byte[] wrappingKey = derive(pin, keySalt, WRAP_ITERATIONS);
        byte[] wrapped = encryptBytes(masterKey, wrappingKey);
        prefs.edit().putString("key_salt", b64(keySalt)).putString("wrapped_master", b64(wrapped)).apply();
        wipe(wrappingKey);
    }

    private void unlockMasterKey(String pin) throws Exception {
        byte[] keySalt = Base64Compat.decode(prefs.getString("key_salt", ""));
        byte[] wrappingKey = derive(pin, keySalt, WRAP_ITERATIONS);
        byte[] wrapped = Base64Compat.decode(prefs.getString("wrapped_master", ""));
        masterKey = decryptBytes(wrapped, wrappingKey);
        wipe(wrappingKey);
        if (masterKey.length != 32) throw new Exception("Invalid vault key");
    }

    private void changePin(String newPin) throws Exception {
        if (decoyMode) throw new Exception("Main PIN changes are unavailable in this session");
        byte[] pinSalt = randomBytes(16);
        byte[] keySalt = randomBytes(16);
        byte[] hash = derive(newPin, pinSalt, PIN_ITERATIONS);
        byte[] wrappingKey = derive(newPin, keySalt, WRAP_ITERATIONS);
        byte[] wrapped = encryptBytes(masterKey, wrappingKey);
        prefs.edit().putString("pin_salt", b64(pinSalt)).putString("pin_hash", b64(hash))
                .putString("key_salt", b64(keySalt)).putString("wrapped_master", b64(wrapped)).apply();
        wipe(wrappingKey);
        logEvent("Main vault PIN changed");
    }

    private byte[] derive(String pin, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, 256);
        try {
            // PBKDF2-HMAC-SHA256 is available from Android API 26. Keep API 23-25 usable
            // with HMAC-SHA1; existing installations on API 26+ keep the original SHA-256 path.
            String algorithm = Build.VERSION.SDK_INT >= 26 ? "PBKDF2WithHmacSHA256" : "PBKDF2WithHmacSHA1";
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private byte[] randomBytes(int count) {
        byte[] out = new byte[count];
        new SecureRandom().nextBytes(out);
        return out;
    }

    private String b64(byte[] data) {
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
    }

    private void wipe(byte[] value) {
        if (value != null) for (int i = 0; i < value.length; i++) value[i] = 0;
    }

    private byte[] encryptBytes(byte[] plain, byte[] key) throws Exception {
        byte[] iv = randomBytes(IV_LENGTH);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plain);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(iv);
        out.write(encrypted);
        return out.toByteArray();
    }

    private byte[] decryptBytes(byte[] encrypted, byte[] key) throws Exception {
        if (encrypted == null || encrypted.length <= IV_LENGTH) throw new Exception("Encrypted data is incomplete");
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(encrypted, 0, iv, 0, IV_LENGTH);
        byte[] cipherText = new byte[encrypted.length - IV_LENGTH];
        System.arraycopy(encrypted, IV_LENGTH, cipherText, 0, cipherText.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(cipherText);
    }

    private LinearLayout newPageRoot() {
        NeonRootLayout page = new NeonRootLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(15), dp(12), dp(15), dp(15));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(2, 7, 18), Color.rgb(5, 15, 32), Color.rgb(1, 5, 14)});
        page.setBackground(bg);
        return page;
    }

    private class NeonRootLayout extends LinearLayout {
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        NeonRootLayout(Context context) {
            super(context);
            setWillNotDraw(false);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            edgePaint.setStyle(Paint.Style.STROKE);
            edgePaint.setStrokeWidth(dp(2));
            edgePaint.setShader(new LinearGradient(0, 0, 0, getHeight(),
                    new int[]{Color.TRANSPARENT, BLUE, CYAN, BLUE, Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            edgePaint.setShadowLayer(dp(11), 0, 0, BLUE);
            float left = dp(3);
            float right = getWidth() - dp(3);
            canvas.drawLine(left, dp(18), left, getHeight() - dp(18), edgePaint);
            canvas.drawLine(right, dp(18), right, getHeight() - dp(18), edgePaint);
            edgePaint.clearShadowLayer();
            edgePaint.setShader(null);
        }
    }

    private void showHomeScreen() {
        currentPage = "home";
        root = newPageRoot();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(3), dp(18), dp(3), dp(24));
        scroll.addView(column);

        TextView crest = text("♛  🛡  ♛", 38, CYAN, true);
        crest.setGravity(Gravity.CENTER);
        crest.setShadowLayer(24f, 0, 0, BLUE);
        column.addView(crest, matchWrap());
        TextView title = text("MANISH VAULT", 29, WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setShadowLayer(20f, 0, 0, BLUE);
        column.addView(title, matchWrap());
        TextView subtitle = text("Your private space is ready", 15, CYAN, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(5), 0, dp(22));
        column.addView(subtitle, matchWrap());

        addHomeCard(column, "📷", "Photos & Videos", "Keep personal media encrypted", this::showMediaScreen);
        addHomeCard(column, "▰", "Secure Documents", "Protect PDFs and other files", this::showDocumentsScreen);
        addHomeCard(column, "✎", "Secret Notes", "Private notes stored encrypted", this::showNotesScreen);
        addHomeCard(column, "♙", "Password Manager", "Encrypted account credentials", this::showPasswordsScreen);
        addHomeCard(column, "⬡", "Security Settings", "Change PIN and auto-lock", this::showSettingsScreen);
        addHomeCard(column, "🛡", "Security Center", "Recovery, anti-theft, device health and activity", this::showSecurityCenter);

        TextView status = text("●  AES-GCM ENCRYPTION  •  DEVICE STORAGE", 10, MUTED, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(16), 0, dp(13));
        column.addView(status, matchWrap());
        Button lock = primaryButton("🔒   LOCK VAULT");
        column.addView(lock, matchWrap());
        lock.setOnClickListener(v -> lockVault());
        TextView footer = text("Your Privacy  •  Our Priority", 13, CYAN, true);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(15), 0, dp(5));
        column.addView(footer, matchWrap());
        setContentView(root);
    }

    private void addHomeCard(LinearLayout parent, String icon, String title, String description, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(15), dp(14), dp(15));
        card.setBackground(cardBackground());
        card.setElevation(dp(8));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, dp(7), 0, dp(7));
        parent.addView(card, cp);

        TextView iconView = text(icon, 29, CYAN, true);
        iconView.setGravity(Gravity.CENTER);
        iconView.setShadowLayer(12f, 0, 0, BLUE);
        card.addView(iconView, new LinearLayout.LayoutParams(dp(55), dp(52)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(9), 0, dp(5), 0);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        labels.addView(text(title, 17, WHITE, true), matchWrap());
        TextView desc = text(description, 12, MUTED, false);
        desc.setPadding(0, dp(4), 0, 0);
        labels.addView(desc, matchWrap());
        TextView arrow = text("›", 31, CYAN, true);
        arrow.setShadowLayer(12f, 0, 0, BLUE);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(28), -2));
        card.setOnClickListener(v -> action.run());
    }

    private LinearLayout startSubPage(String page, String title, String subtitle) {
        currentPage = page;
        root = newPageRoot();
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(58)));
        Button back = smallButton("‹ BACK");
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(90), dp(44)));
        back.setOnClickListener(v -> showHomeScreen());
        TextView heading = text(title, 19, WHITE, true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(10), 0, 0, 0);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView sub = text(subtitle, 12, CYAN, false);
        sub.setPadding(dp(4), dp(3), dp(4), dp(13));
        root.addView(sub, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(3), dp(4), dp(3), dp(20));
        scroll.addView(content);
        setContentView(root);
        return content;
    }

    private void showMediaScreen() {
        LinearLayout body = startSubPage("media", "PHOTOS & VIDEOS", "Your media is encrypted in private app storage");
        Button add = primaryButton("＋  ADD PHOTO OR VIDEO");
        body.addView(add, matchWrap());
        add.setOnClickListener(v -> launchPicker(true));
        renderFiles(body, "media");
    }

    private void showDocumentsScreen() {
        LinearLayout body = startSubPage("documents", "SECURE DOCUMENTS", "PDFs and other files are encrypted on import");
        Button add = primaryButton("＋  ADD DOCUMENT");
        body.addView(add, matchWrap());
        add.setOnClickListener(v -> launchPicker(false));
        renderFiles(body, "documents");
    }

    private void launchPicker(boolean media) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(media ? "*/*" : "*/*");
            if (media) intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pickerActive = true;
            startActivityForResult(intent, media ? REQ_MEDIA : REQ_DOCUMENT);
        } catch (ActivityNotFoundException e) {
            pickerActive = false;
            toast("No file picker is available on this device");
        }
    }

    private boolean copyUriIntoVault(Uri uri, String category) {
        if (masterKey == null) { toast("Unlock the vault first"); return false; }
        String name = queryDisplayName(uri);
        String mime = getContentResolver().getType(uri);
        if (mime == null) mime = "application/octet-stream";
        if ("media".equals(category) && !(mime.startsWith("image/") || mime.startsWith("video/"))) {
            toast("Choose an image or video file");
            return false;
        }
        String id = UUID.randomUUID().toString();
        File dir = currentVaultDataDir();
        if (!dir.exists() && !dir.mkdirs()) { toast("Could not create secure storage"); return false; }
        File encryptedFile = new File(dir, id + ".mvault");
        byte[] iv = randomBytes(IV_LENGTH);
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream raw = new FileOutputStream(encryptedFile)) {
            if (in == null) throw new Exception("Cannot read selected file");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            raw.write(iv);
            try (CipherOutputStream encryptedOut = new CipherOutputStream(raw, cipher)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) encryptedOut.write(buffer, 0, read);
            }
            JSONObject store = loadStore();
            JSONArray files = store.optJSONArray("files");
            if (files == null) files = new JSONArray();
            JSONObject record = new JSONObject();
            record.put("id", id);
            record.put("name", name);
            record.put("mime", mime);
            record.put("category", category);
            record.put("added", System.currentTimeMillis());
            files.put(record);
            store.put("files", files);
            saveStore(store);
            toast("Encrypted and saved: " + name);
            logEvent("Encrypted file imported");
            return true;
        } catch (Exception e) {
            encryptedFile.delete();
            toast("Could not encrypt this file");
            return false;
        }
    }

    private String queryDisplayName(Uri uri) {
        String name = "vault-file";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return name == null ? "vault-file" : name.replace("/", "_").replace("\\", "_");
    }

    private void renderFiles(LinearLayout parent, String category) {
        try {
            JSONArray files = loadStore().optJSONArray("files");
            int count = 0;
            if (files != null) {
                for (int i = files.length() - 1; i >= 0; i--) {
                    JSONObject item = files.getJSONObject(i);
                    if (!category.equals(item.optString("category"))) continue;
                    count++;
                    addFileCard(parent, item);
                }
            }
            if (count == 0) {
                TextView empty = text("Nothing here yet. Use the blue button above to add a file.", 14, MUTED, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(12), dp(32), dp(12), dp(32));
                parent.addView(empty, matchWrap());
            }
        } catch (Exception e) {
            toast("Could not load vault files");
        }
    }

    private void addFileCard(LinearLayout parent, JSONObject item) {
        String id = item.optString("id");
        String name = item.optString("name", "Encrypted file");
        String mime = item.optString("mime", "application/octet-stream");
        LinearLayout card = cardColumn(parent);
        TextView label = text("▣  " + name, 15, WHITE, true);
        card.addView(label, matchWrap());
        TextView sub = text(mime, 11, MUTED, false);
        sub.setPadding(0, dp(5), 0, dp(10));
        card.addView(sub, matchWrap());
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(actions, matchWrap());
        Button open = smallButton("OPEN");
        Button share = smallButton("SHARE");
        Button delete = smallButton("DELETE");
        actions.addView(open, new LinearLayout.LayoutParams(0, dp(42), 1f));
        actions.addView(share, new LinearLayout.LayoutParams(0, dp(42), 1f));
        actions.addView(delete, new LinearLayout.LayoutParams(0, dp(42), 1f));
        open.setOnClickListener(v -> openEncryptedFile(id, name, mime, false));
        share.setOnClickListener(v -> openEncryptedFile(id, name, mime, true));
        delete.setOnClickListener(v -> confirmDeleteFile(id, name));
    }

    private void openEncryptedFile(String id, String name, String mime, boolean share) {
        try {
            File encrypted = new File(currentVaultDataDir(), id + ".mvault");
            if (!encrypted.exists()) { toast("Encrypted file is missing"); return; }
            File tempDir = new File(getCacheDir(), "vault_previews");
            if (!tempDir.exists() && !tempDir.mkdirs()) throw new Exception("cache");
            String safeName = name.replaceAll("[^A-Za-z0-9._-]", "_");
            File temp = new File(tempDir, UUID.randomUUID().toString() + "_" + safeName);
            decryptFileTo(encrypted, temp);
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", temp);
            Intent intent;
            if (share) {
                intent = new Intent(Intent.ACTION_SEND);
                intent.setType(mime);
                intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            } else {
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(contentUri, mime);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, share ? "Share decrypted copy" : "Open file"));
        } catch (ActivityNotFoundException e) {
            toast("No app can open this file type");
        } catch (Exception e) {
            toast("Could not decrypt file. It may be damaged.");
        }
    }

    private void decryptFileTo(File encrypted, File output) throws Exception {
        try (FileInputStream raw = new FileInputStream(encrypted)) {
            byte[] iv = new byte[IV_LENGTH];
            int offset = 0;
            while (offset < iv.length) {
                int n = raw.read(iv, offset, iv.length - offset);
                if (n < 0) throw new Exception("Invalid encrypted file");
                offset += n;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            try (CipherInputStream in = new CipherInputStream(raw, cipher); FileOutputStream out = new FileOutputStream(output)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            output.delete();
            throw e;
        }
    }

    private void confirmDeleteFile(String id, String name) {
        new AlertDialog.Builder(this).setTitle("Delete encrypted file?")
                .setMessage("Remove " + name + " from this vault?")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE", (d, w) -> {
                    try {
                        new File(currentVaultDataDir(), id + ".mvault").delete();
                        JSONObject store = loadStore();
                        JSONArray old = store.optJSONArray("files");
                        JSONArray keep = new JSONArray();
                        if (old != null) for (int i = 0; i < old.length(); i++) {
                            JSONObject item = old.getJSONObject(i);
                            if (!id.equals(item.optString("id"))) keep.put(item);
                        }
                        store.put("files", keep);
                        saveStore(store);
                        if ("media".equals(currentPage)) showMediaScreen(); else showDocumentsScreen();
                        logEvent("Encrypted file deleted");
                        toast("File deleted from vault");
                    } catch (Exception e) { toast("Could not delete file record"); }
                }).show();
    }

    private JSONObject loadStore() throws Exception {
        File file = currentStoreFile();
        if (!file.exists()) {
            JSONObject empty = new JSONObject();
            empty.put("files", new JSONArray());
            empty.put("notes", new JSONArray());
            empty.put("passwords", new JSONArray());
            return empty;
        }
        byte[] encrypted = readAll(new FileInputStream(file));
        byte[] plain = decryptBytes(encrypted, masterKey);
        JSONObject store = new JSONObject(new String(plain, "UTF-8"));
        if (store.optJSONArray("files") == null) store.put("files", new JSONArray());
        if (store.optJSONArray("notes") == null) store.put("notes", new JSONArray());
        if (store.optJSONArray("passwords") == null) store.put("passwords", new JSONArray());
        return store;
    }

    private void saveStore(JSONObject store) throws Exception {
        byte[] plain = store.toString().getBytes("UTF-8");
        byte[] encrypted = encryptBytes(plain, masterKey);
        File temp = new File(getFilesDir(), decoyMode ? "decoy_store.tmp" : "vault_store.tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) { out.write(encrypted); out.flush(); out.getFD().sync(); }
        File target = currentStoreFile();
        File backup = new File(getFilesDir(), decoyMode ? "decoy_store.bak" : "vault_store.bak");
        if (backup.exists()) backup.delete();
        if (target.exists() && !target.renameTo(backup)) throw new Exception("Cannot stage old vault data");
        if (!temp.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target);
            throw new Exception("Cannot save vault data");
        }
        if (backup.exists()) backup.delete();
    }

    private byte[] readAll(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    private void showNotesScreen() {
        LinearLayout body = startSubPage("notes", "SECRET NOTES", "Notes are encrypted before being saved");
        Button add = primaryButton("＋  NEW SECRET NOTE");
        body.addView(add, matchWrap());
        add.setOnClickListener(v -> editNote(null, -1));
        try {
            JSONArray notes = loadStore().optJSONArray("notes");
            if (notes == null || notes.length() == 0) addEmpty(body, "No notes yet. Create your first private note.");
            else for (int i = notes.length() - 1; i >= 0; i--) addNoteCard(body, notes.getJSONObject(i), i);
        } catch (Exception e) { toast("Could not load notes"); }
    }

    private void addNoteCard(LinearLayout parent, JSONObject note, int index) {
        LinearLayout card = cardColumn(parent);
        card.addView(text(note.optString("title", "Untitled note"), 16, WHITE, true), matchWrap());
        TextView body = text(note.optString("body", ""), 13, MUTED, false);
        body.setMaxLines(3);
        body.setPadding(0, dp(7), 0, dp(10));
        card.addView(body, matchWrap());
        LinearLayout actions = new LinearLayout(this);
        card.addView(actions, matchWrap());
        Button edit = smallButton("EDIT");
        Button delete = smallButton("DELETE");
        actions.addView(edit, new LinearLayout.LayoutParams(0, dp(42), 1f));
        actions.addView(delete, new LinearLayout.LayoutParams(0, dp(42), 1f));
        edit.setOnClickListener(v -> editNote(note, index));
        delete.setOnClickListener(v -> deleteRecord("notes", index, "Delete this note?", this::showNotesScreen));
    }

    private void editNote(JSONObject existing, int index) {
        LinearLayout form = dialogForm();
        EditText title = edit("Note title", false, false);
        EditText body = edit("Write your private note...", true, false);
        if (existing != null) { title.setText(existing.optString("title")); body.setText(existing.optString("body")); }
        form.addView(title, matchWrap());
        form.addView(body, new LinearLayout.LayoutParams(-1, dp(150)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(existing == null ? "New secret note" : "Edit secret note")
                .setView(form).setNegativeButton("CANCEL", null).setPositiveButton("SAVE", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            String b = body.getText().toString();
            if (t.isEmpty() || b.trim().isEmpty()) { toast("Enter a title and note"); return; }
            try {
                JSONObject store = loadStore();
                JSONArray notes = store.optJSONArray("notes"); if (notes == null) notes = new JSONArray();
                JSONObject record = new JSONObject(); record.put("title", t); record.put("body", b); record.put("updated", System.currentTimeMillis());
                if (existing == null) notes.put(record); else notes.put(index, record);
                store.put("notes", notes); saveStore(store); logEvent("Secret note saved or edited"); dialog.dismiss(); showNotesScreen(); toast("Note saved encrypted");
            } catch (Exception e) { toast("Could not save note"); }
        }));
        dialog.show();
    }

    private void showPasswordsScreen() {
        LinearLayout body = startSubPage("passwords", "PASSWORD MANAGER", "Credentials are encrypted locally on this device");
        Button add = primaryButton("＋  ADD PASSWORD");
        body.addView(add, matchWrap());
        add.setOnClickListener(v -> editPassword(null, -1));
        try {
            JSONArray list = loadStore().optJSONArray("passwords");
            if (list == null || list.length() == 0) addEmpty(body, "No passwords saved yet.");
            else for (int i = list.length() - 1; i >= 0; i--) addPasswordCard(body, list.getJSONObject(i), i);
        } catch (Exception e) { toast("Could not load passwords"); }
    }

    private void addPasswordCard(LinearLayout parent, JSONObject item, int index) {
        LinearLayout card = cardColumn(parent);
        card.addView(text(item.optString("service", "Account"), 16, WHITE, true), matchWrap());
        TextView user = text("User: " + item.optString("username", ""), 12, MUTED, false);
        user.setPadding(0, dp(5), 0, dp(9)); card.addView(user, matchWrap());
        LinearLayout actions = new LinearLayout(this); card.addView(actions, matchWrap());
        Button reveal = smallButton("SHOW / COPY"); Button edit = smallButton("EDIT"); Button delete = smallButton("DELETE");
        actions.addView(reveal, new LinearLayout.LayoutParams(0, dp(42), 1f));
        actions.addView(edit, new LinearLayout.LayoutParams(0, dp(42), 1f));
        actions.addView(delete, new LinearLayout.LayoutParams(0, dp(42), 1f));
        reveal.setOnClickListener(v -> showPassword(item.optString("password", "")));
        edit.setOnClickListener(v -> editPassword(item, index));
        delete.setOnClickListener(v -> deleteRecord("passwords", index, "Delete this saved password?", this::showPasswordsScreen));
    }

    private void showPassword(String password) {
        LinearLayout box = dialogForm();
        TextView value = text(password, 18, WHITE, true);
        value.setTextIsSelectable(true);
        box.addView(value, matchWrap());
        new AlertDialog.Builder(this).setTitle("Saved password").setView(box)
                .setNegativeButton("CLOSE", null).setPositiveButton("COPY", (d, w) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Vault password", password));
                    logEvent("Password copied to clipboard");
                    toast("Copied. Clipboard will clear in 30 seconds.");
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                                CharSequence current = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                                if (current != null && password.contentEquals(current)) {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                                }
                            }
                        } catch (Exception ignored) { }
                    }, 30000L);
                }).show();
    }

    private void editPassword(JSONObject existing, int index) {
        LinearLayout form = dialogForm();
        EditText service = edit("Website / App", false, false);
        EditText username = edit("Username / Email", false, false);
        EditText password = edit("Password", false, false);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (existing != null) {
            service.setText(existing.optString("service")); username.setText(existing.optString("username")); password.setText(existing.optString("password"));
        }
        form.addView(service, matchWrap()); form.addView(username, matchWrap()); form.addView(password, matchWrap());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(existing == null ? "Add password" : "Edit password")
                .setView(form).setNegativeButton("CANCEL", null).setPositiveButton("SAVE", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String s = service.getText().toString().trim(); String u = username.getText().toString().trim(); String p = password.getText().toString();
            if (s.isEmpty() || p.isEmpty()) { toast("Website/App and password are required"); return; }
            try {
                JSONObject store = loadStore(); JSONArray list = store.optJSONArray("passwords"); if (list == null) list = new JSONArray();
                JSONObject record = new JSONObject(); record.put("service", s); record.put("username", u); record.put("password", p); record.put("updated", System.currentTimeMillis());
                if (existing == null) list.put(record); else list.put(index, record);
                store.put("passwords", list); saveStore(store); logEvent("Password entry saved or edited"); dialog.dismiss(); showPasswordsScreen(); toast("Password saved encrypted");
            } catch (Exception e) { toast("Could not save password"); }
        }));
        dialog.show();
    }

    private void deleteRecord(String key, int index, String message, Runnable refresh) {
        new AlertDialog.Builder(this).setMessage(message).setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE", (d, w) -> {
                    try {
                        JSONObject store = loadStore(); JSONArray old = store.optJSONArray(key); JSONArray keep = new JSONArray();
                        if (old != null) for (int i = 0; i < old.length(); i++) if (i != index) keep.put(old.get(i));
                        store.put(key, keep); saveStore(store); logEvent("Vault record deleted: " + key); refresh.run(); toast("Deleted");
                    } catch (Exception e) { toast("Could not delete item"); }
                }).show();
    }

    private void showSettingsScreen() {
        LinearLayout body = startSubPage("settings", "SECURITY SETTINGS", "Manage how this vault is protected");
        if (decoyMode) {
            LinearLayout card = cardColumn(body);
            card.addView(text("PRIVACY SETTINGS", 16, WHITE, true), matchWrap());
            card.addView(text("Only general session controls are available in this view.", 12, MUTED, false), matchWrap());
            Button clear = primaryButton("CLEAR TEMPORARY FILES"); card.addView(clear, matchWrap());
            clear.setOnClickListener(v -> { deleteTree(new File(getCacheDir(), "vault_previews")); toast("Temporary files cleared"); });
            Button lock = primaryButton("LOCK VAULT");
            LinearLayout.LayoutParams lp = matchWrap(); lp.setMargins(0, dp(10), 0, 0); card.addView(lock, lp);
            lock.setOnClickListener(v -> lockVault());
            return;
        }
        LinearLayout pinCard = cardColumn(body);
        pinCard.addView(text("CHANGE SECURITY PIN", 16, WHITE, true), matchWrap());
        TextView pinDesc = text("Replace your 6-digit PIN. Your vault encryption key will be re-wrapped.", 12, MUTED, false);
        pinDesc.setPadding(0, dp(6), 0, dp(12)); pinCard.addView(pinDesc, matchWrap());
        Button change = primaryButton("CHANGE PIN"); pinCard.addView(change, matchWrap());
        change.setOnClickListener(v -> showChangePinDialog());
        Button security = smallButton("OPEN ADVANCED SECURITY CENTER");
        LinearLayout.LayoutParams securityP = matchWrap(); securityP.setMargins(0, dp(10), 0, 0);
        pinCard.addView(security, securityP);
        security.setOnClickListener(v -> showSecurityCenter());

        LinearLayout lockCard = cardColumn(body);
        lockCard.addView(text("AUTO-LOCK TIMER", 16, WHITE, true), matchWrap());
        int seconds = prefs.getInt("auto_lock_seconds", 300);
        TextView current = text("Current: " + timeoutLabel(seconds), 12, MUTED, false);
        current.setPadding(0, dp(6), 0, dp(12)); lockCard.addView(current, matchWrap());
        Button timer = primaryButton("CHANGE AUTO-LOCK"); lockCard.addView(timer, matchWrap());
        timer.setOnClickListener(v -> chooseAutoLock());

        LinearLayout privacy = cardColumn(body);
        privacy.addView(text("PRIVACY STATUS", 16, WHITE, true), matchWrap());
        privacy.addView(text("• PIN hash: PBKDF2-HMAC-SHA256\n• File encryption: AES-256-GCM\n• Storage: this app's private device storage\n• Android backup: disabled", 12, MUTED, false), matchWrap());
        Button clear = smallButton("CLEAR TEMPORARY DECRYPTED FILES");
        LinearLayout.LayoutParams clearP = matchWrap(); clearP.setMargins(0, dp(12), 0, 0); privacy.addView(clear, clearP);
        clear.setOnClickListener(v -> { deleteTree(new File(getCacheDir(), "vault_previews")); toast("Temporary preview files cleared"); });
    }

    // ---------- Version 3.0 security center ----------

    private File currentVaultDataDir() {
        return new File(getFilesDir(), decoyMode ? "decoy_data" : "vault_data");
    }

    private File currentStoreFile() {
        return new File(getFilesDir(), decoyMode ? "decoy_store.enc" : "vault_store.enc");
    }

    private void logEvent(String message) {
        try {
            JSONArray log = new JSONArray(prefs.getString("security_log", "[]"));
            JSONObject item = new JSONObject();
            item.put("time", System.currentTimeMillis());
            item.put("message", message);
            log.put(item);
            JSONArray trimmed = new JSONArray();
            int start = Math.max(0, log.length() - 60);
            for (int i = start; i < log.length(); i++) trimmed.put(log.get(i));
            prefs.edit().putString("security_log", trimmed.toString()).apply();
        } catch (Exception ignored) { }
    }

    private String timeLabel(long millis) {
        if (millis <= 0L) return "Not recorded";
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(millis));
    }

    private String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.US, "%02X", b & 0xff));
        return out.toString();
    }

    private String normalizeRecoveryCode(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.US);
    }

    private void setupRecoveryKey() {
        if (masterKey == null || decoyMode) { toast("Open your main vault first"); return; }
        if (prefs.contains("wrapped_master_recovery")) {
            new AlertDialog.Builder(this).setTitle("Recovery Key is already set")
                    .setMessage("For security, the full key is shown only once when created. You can replace it now, but the previous key will stop working.")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("REPLACE KEY", (d, w) -> generateRecoveryKey()).show();
        } else {
            generateRecoveryKey();
        }
    }

    private void generateRecoveryKey() {
        try {
            byte[] random = randomBytes(16);
            String code = hex(random);
            byte[] salt = randomBytes(16);
            byte[] wrappingKey = derive(code, salt, RECOVERY_ITERATIONS);
            byte[] wrapped = encryptBytes(masterKey, wrappingKey);
            wipe(wrappingKey);
            prefs.edit().putString("recovery_salt", b64(salt))
                    .putString("wrapped_master_recovery", b64(wrapped)).apply();
            logEvent("Recovery Key created or replaced");
            TextView value = text(code, 18, CYAN, true);
            value.setTextIsSelectable(true);
            value.setGravity(Gravity.CENTER);
            value.setPadding(dp(8), dp(18), dp(8), dp(18));
            new AlertDialog.Builder(this).setTitle("SAVE YOUR RECOVERY KEY")
                    .setMessage("Write this 32-character key down and keep it somewhere safe. It is shown only now. Anyone with this key can recover the vault PIN.")
                    .setView(value).setPositiveButton("I HAVE SAVED IT", (d, w) -> {
                        new AlertDialog.Builder(this).setTitle("Confirm")
                                .setMessage("Do not uninstall the app or clear app data before saving the key. If both PIN and Recovery Key are lost, encrypted data cannot be recovered.")
                                .setPositiveButton("OK", null).show();
                        showSecurityCenter();
                    }).setNegativeButton("CLOSE", (d, w) -> showSecurityCenter()).show();
        } catch (Exception e) {
            toast("Could not create Recovery Key");
        }
    }

    private void showForgotPinDialog() {
        if (!prefs.contains("pin_hash")) {
            toast("Create your vault PIN first");
            return;
        }
        if (!prefs.contains("wrapped_master_recovery")) {
            new AlertDialog.Builder(this).setTitle("Recovery is not set up")
                    .setMessage("You must unlock the vault and create a Recovery Key in Security Center before forgetting your PIN. The existing 2.0 vault has no Recovery Key yet. Without the PIN, its encrypted data cannot be unlocked.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        LinearLayout form = dialogForm();
        EditText code = edit("32-character Recovery Key", false, false);
        EditText next = edit("New 6-digit PIN", false, true);
        EditText confirm = edit("Confirm new PIN", false, true);
        code.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        next.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        confirm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        form.addView(code, matchWrap()); form.addView(next, matchWrap()); form.addView(confirm, matchWrap());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Recover Manish Vault")
                .setMessage("Enter the Recovery Key you saved earlier, then choose a new PIN.")
                .setView(form).setNegativeButton("CANCEL", null).setPositiveButton("RECOVER", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String recovery = normalizeRecoveryCode(code.getText().toString());
            String a = next.getText().toString();
            String b = confirm.getText().toString();
            if (!recovery.matches("[0-9A-F]{32}")) { toast("Enter the complete 32-character Recovery Key"); return; }
            if (!a.matches("[0-9]{6}") || !a.equals(b)) { toast("New PINs must match and contain 6 digits"); return; }
            try {
                byte[] salt = Base64Compat.decode(prefs.getString("recovery_salt", ""));
                byte[] recoveryWrap = derive(recovery, salt, RECOVERY_ITERATIONS);
                byte[] recovered = decryptBytes(Base64Compat.decode(prefs.getString("wrapped_master_recovery", "")), recoveryWrap);
                wipe(recoveryWrap);
                if (recovered.length != 32) throw new Exception("Recovery key invalid");
                masterKey = recovered;
                decoyMode = false;
                changePin(a);
                resetFailedAttempts();
                logEvent("PIN recovered using Recovery Key");
                dialog.dismiss();
                showHomeScreen();
                toast("PIN recovered. Your encrypted vault data is unchanged.");
            } catch (Exception e) {
                clearMasterKey();
                toast("Recovery Key is incorrect or damaged");
            }
        }));
        dialog.show();
    }

    private void setupDecoyVault() {
        if (masterKey == null || decoyMode) { toast("Open your main vault first"); return; }
        boolean updating = prefs.contains("decoy_pin_hash");
        LinearLayout form = dialogForm();
        EditText oldPin = null;
        if (updating) {
            oldPin = edit("Current Decoy PIN", false, true);
            oldPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            form.addView(oldPin, matchWrap());
        }
        EditText pin = edit("New separate 6-digit Decoy PIN", false, true);
        EditText confirm = edit("Confirm Decoy PIN", false, true);
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        confirm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        form.addView(pin, matchWrap()); form.addView(confirm, matchWrap());
        final EditText oldPinField = oldPin;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(updating ? "Replace Decoy PIN" : "Create Decoy Vault")
                .setMessage(updating ? "The existing decoy encryption key and data will be retained." : "A separate encrypted storage area will be created. It will not contain your main vault files.")
                .setView(form).setNegativeButton("CANCEL", null).setPositiveButton("SAVE", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = pin.getText().toString();
            String b = confirm.getText().toString();
            if (!a.matches("[0-9]{6}") || !a.equals(b)) { toast("Decoy PINs must match and contain 6 digits"); return; }
            try {
                byte[] mainSalt = Base64Compat.decode(prefs.getString("pin_salt", ""));
                byte[] mainHash = derive(a, mainSalt, PIN_ITERATIONS);
                if (MessageDigest.isEqual(mainHash, Base64Compat.decode(prefs.getString("pin_hash", "")))) {
                    toast("Decoy PIN must be different from the main PIN"); return;
                }
                byte[] key;
                if (updating) {
                    String old = oldPinField == null ? "" : oldPinField.getText().toString();
                    byte[] oldSalt = Base64Compat.decode(prefs.getString("decoy_pin_salt", ""));
                    byte[] oldHash = derive(old, oldSalt, PIN_ITERATIONS);
                    if (!MessageDigest.isEqual(oldHash, Base64Compat.decode(prefs.getString("decoy_pin_hash", "")))) {
                        toast("Current Decoy PIN is incorrect"); return;
                    }
                    byte[] oldKeySalt = Base64Compat.decode(prefs.getString("decoy_key_salt", ""));
                    byte[] oldWrappingKey = derive(old, oldKeySalt, WRAP_ITERATIONS);
                    key = decryptBytes(Base64Compat.decode(prefs.getString("decoy_wrapped_master", "")), oldWrappingKey);
                    wipe(oldWrappingKey);
                } else {
                    key = randomBytes(32);
                }
                if (key.length != 32) throw new Exception("Invalid decoy key");
                byte[] pinSalt = randomBytes(16);
                byte[] keySalt = randomBytes(16);
                byte[] pinHash = derive(a, pinSalt, PIN_ITERATIONS);
                byte[] wrappingKey = derive(a, keySalt, WRAP_ITERATIONS);
                byte[] wrapped = encryptBytes(key, wrappingKey);
                wipe(wrappingKey);
                File dir = new File(getFilesDir(), "decoy_data");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create decoy storage");
                File decoyStore = new File(getFilesDir(), "decoy_store.enc");
                if (!decoyStore.exists()) {
                    JSONObject empty = emptyStore();
                    byte[] encrypted = encryptBytes(empty.toString().getBytes("UTF-8"), key);
                    try (FileOutputStream out = new FileOutputStream(decoyStore)) { out.write(encrypted); out.getFD().sync(); }
                }
                prefs.edit().putString("decoy_pin_salt", b64(pinSalt))
                        .putString("decoy_pin_hash", b64(pinHash))
                        .putString("decoy_key_salt", b64(keySalt))
                        .putString("decoy_wrapped_master", b64(wrapped)).apply();
                wipe(key);
                logEvent(updating ? "Decoy Vault PIN replaced" : "Decoy Vault configured");
                dialog.dismiss(); showSecurityCenter(); toast(updating ? "Decoy PIN changed; decoy data retained" : "Separate Decoy Vault is ready");
            } catch (Exception e) { toast("Could not save Decoy Vault settings"); }
        }));
        dialog.show();
    }

    private JSONObject emptyStore() throws Exception {
        JSONObject empty = new JSONObject();
        empty.put("files", new JSONArray());
        empty.put("notes", new JSONArray());
        empty.put("passwords", new JSONArray());
        return empty;
    }

    private void disableDecoyVault() {
        new AlertDialog.Builder(this).setTitle("Delete Decoy Vault?")
                .setMessage("This permanently deletes the separate decoy files, notes and passwords. Your main vault is not touched.")
                .setNegativeButton("CANCEL", null).setPositiveButton("DELETE DECOY", (d, w) -> {
                    deleteTree(new File(getFilesDir(), "decoy_data"));
                    new File(getFilesDir(), "decoy_store.enc").delete();
                    new File(getFilesDir(), "decoy_store.tmp").delete();
                    new File(getFilesDir(), "decoy_store.bak").delete();
                    prefs.edit().remove("decoy_pin_salt").remove("decoy_pin_hash")
                            .remove("decoy_key_salt").remove("decoy_wrapped_master").apply();
                    logEvent("Decoy Vault deleted"); showSecurityCenter(); toast("Decoy Vault deleted");
                }).show();
    }

    private long directorySize(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) total += directorySize(child);
        return total;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes / 1024.0;
        if (value < 1024.0) return String.format(Locale.US, "%.1f KB", value);
        value /= 1024.0;
        if (value < 1024.0) return String.format(Locale.US, "%.1f MB", value);
        return String.format(Locale.US, "%.2f GB", value / 1024.0);
    }

    private String appMemorySummary() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(info);
        return "App heap in use: " + formatBytes(used) + " / " + formatBytes(max)
                + "\nDevice available RAM: " + formatBytes(info.availMem)
                + "\nThis is a status monitor; Android manages system RAM automatically.";
    }

    private String storageSummary() {
        StatFs stat = new StatFs(getFilesDir().getAbsolutePath());
        long free = stat.getAvailableBytes();
        long total = stat.getTotalBytes();
        long app = directorySize(getFilesDir());
        return "App private data: " + formatBytes(app)
                + "\nDevice storage free: " + formatBytes(free)
                + "\nDevice storage total: " + formatBytes(total);
    }

    private void requestOrRecordLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        recordLastKnownLocation();
    }

    private void recordLastKnownLocation() {
        try {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (manager == null) { toast("Location service unavailable"); return; }
            Location best = null;
            String[] providers = new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER};
            for (String provider : providers) {
                try {
                    if (!manager.isProviderEnabled(provider)) continue;
                    Location candidate = manager.getLastKnownLocation(provider);
                    if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
                } catch (SecurityException ignored) { }
            }
            if (best == null) {
                toast("No saved location yet. Turn on device Location and try again.");
                return;
            }
            prefs.edit().putString("last_location_lat", Double.toString(best.getLatitude()))
                    .putString("last_location_lon", Double.toString(best.getLongitude()))
                    .putLong("last_location_time", best.getTime()).apply();
            logEvent("Last known location recorded manually");
            showSecurityCenter();
            toast("Last known location saved on this phone only");
        } catch (SecurityException e) { toast("Location permission was not granted"); }
        catch (Exception e) { toast("Could not read device location"); }
    }

    private void shareTheftAlert() {
        if (!prefs.contains("last_location_lat")) {
            toast("Record a last known location first"); return;
        }
        String lat = prefs.getString("last_location_lat", "");
        String lon = prefs.getString("last_location_lon", "");
        long when = prefs.getLong("last_location_time", 0L);
        String message = "Manish Vault device security alert. Last location recorded on this device at "
                + timeLabel(when) + ": https://maps.google.com/?q=" + lat + "," + lon
                + " . This location is not live and was not uploaded automatically.";
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Manish Vault security alert");
        send.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(send, "Share security alert"));
    }

    private void markDeviceAsStolen() {
        new AlertDialog.Builder(this).setTitle("Activate Theft Response?")
                .setMessage("This marks the device as reported stolen locally, records an event, clears temporary previews and locks the vault. It does not contact a server or track the device in the background.")
                .setNegativeButton("CANCEL", null).setPositiveButton("ACTIVATE", (d, w) -> {
                    prefs.edit().putBoolean("theft_mode", true).apply();
                    logEvent("Theft response activated locally");
                    deleteTree(new File(getCacheDir(), "vault_previews"));
                    lockVault();
                    toast("Local theft response activated");
                }).show();
    }

    private void clearTheftStatus() {
        new AlertDialog.Builder(this).setTitle("Clear theft status?")
                .setMessage("Confirm that you own this device and want to clear the local theft flag.")
                .setNegativeButton("CANCEL", null).setPositiveButton("CLEAR", (d, w) -> {
                    prefs.edit().putBoolean("theft_mode", false).apply();
                    logEvent("Local theft status cleared"); showSecurityCenter();
                }).show();
    }

    private void showActivityLog() {
        try {
            JSONArray log = new JSONArray(prefs.getString("security_log", "[]"));
            StringBuilder body = new StringBuilder();
            for (int i = log.length() - 1; i >= 0; i--) {
                JSONObject item = log.getJSONObject(i);
                body.append(timeLabel(item.optLong("time"))).append("\n")
                        .append("• ").append(item.optString("message")).append("\n\n");
            }
            if (body.length() == 0) body.append("No security events recorded yet.");
            TextView view = text(body.toString(), 12, MUTED, false);
            view.setTextIsSelectable(true);
            view.setPadding(dp(12), dp(12), dp(12), dp(12));
            ScrollView scroll = new ScrollView(this); scroll.addView(view);
            new AlertDialog.Builder(this).setTitle("SECURITY ACTIVITY • LAST 60 EVENTS")
                    .setView(scroll).setPositiveButton("CLOSE", null).show();
        } catch (Exception e) { toast("Could not read activity log"); }
    }

    private void showSecurityCenter() {
        LinearLayout body = startSubPage("security", "ULTIMATE SECURITY CENTER", "Local security controls • consent-based features");
        if (decoyMode) {
            LinearLayout card = cardColumn(body);
            card.addView(text("DEVICE STATUS", 16, WHITE, true), matchWrap());
            card.addView(text(appMemorySummary() + "\n\n" + storageSummary(), 12, MUTED, false), matchWrap());
            Button lock = primaryButton("EMERGENCY LOCK"); card.addView(lock, matchWrap());
            lock.setOnClickListener(v -> lockVault());
            return;
        }

        LinearLayout recovery = cardColumn(body);
        recovery.addView(text("FORGOT PIN / RECOVERY", 16, WHITE, true), matchWrap());
        recovery.addView(text(prefs.contains("wrapped_master_recovery")
                ? "Recovery Key is configured. Save your key offline; it cannot be displayed again."
                : "Not configured yet. Create and safely store a Recovery Key before you forget your PIN.", 12, MUTED, false), matchWrap());
        Button recover = primaryButton(prefs.contains("wrapped_master_recovery") ? "REPLACE RECOVERY KEY" : "SET UP RECOVERY KEY");
        recovery.addView(recover, matchWrap()); recover.setOnClickListener(v -> setupRecoveryKey());

        LinearLayout decoy = cardColumn(body);
        decoy.addView(text("SEPARATE DECOY VAULT", 16, WHITE, true), matchWrap());
        decoy.addView(text(prefs.contains("decoy_pin_hash")
                ? "Configured. Enter its separate PIN on the lock screen to open the decoy storage."
                : "Not configured. The decoy vault uses a separate key and separate encrypted files.", 12, MUTED, false), matchWrap());
        Button decoySetup = primaryButton(prefs.contains("decoy_pin_hash") ? "REPLACE DECOY PIN" : "CREATE DECOY VAULT");
        decoy.addView(decoySetup, matchWrap()); decoySetup.setOnClickListener(v -> setupDecoyVault());
        if (prefs.contains("decoy_pin_hash")) {
            Button decoyDelete = smallButton("DELETE DECOY VAULT DATA");
            LinearLayout.LayoutParams p = matchWrap(); p.setMargins(0, dp(8), 0, 0); decoy.addView(decoyDelete, p);
            decoyDelete.setOnClickListener(v -> disableDecoyVault());
        }

        LinearLayout theft = cardColumn(body);
        theft.addView(text("THEFT RESPONSE", 16, WHITE, true), matchWrap());
        theft.addView(text("Wrong-PIN attempt log and cooldown are active. Last location is recorded only when you tap the button and grant permission. Sharing is always manual.", 12, MUTED, false), matchWrap());
        Button location = primaryButton("RECORD LAST KNOWN LOCATION"); theft.addView(location, matchWrap());
        location.setOnClickListener(v -> requestOrRecordLocation());
        long locationTime = prefs.getLong("last_location_time", 0L);
        String locationText = prefs.contains("last_location_lat")
                ? "Saved locally: " + prefs.getString("last_location_lat", "") + ", " + prefs.getString("last_location_lon", "") + "\n" + timeLabel(locationTime)
                : "No location has been recorded.";
        TextView locationStatus = text(locationText, 11, MUTED, false); locationStatus.setPadding(0, dp(7), 0, dp(6)); theft.addView(locationStatus, matchWrap());
        Button share = smallButton("SHARE THEFT ALERT MANUALLY"); theft.addView(share, matchWrap()); share.setOnClickListener(v -> shareTheftAlert());
        Button mark = smallButton(prefs.getBoolean("theft_mode", false) ? "THEFT MODE ACTIVE • LOCK NOW" : "MARK DEVICE AS STOLEN / EMERGENCY LOCK");
        LinearLayout.LayoutParams markP = matchWrap(); markP.setMargins(0, dp(8), 0, 0); theft.addView(mark, markP);
        mark.setOnClickListener(v -> markDeviceAsStolen());
        if (prefs.getBoolean("theft_mode", false)) {
            Button clearTheft = smallButton("CLEAR LOCAL THEFT STATUS");
            LinearLayout.LayoutParams clearP = matchWrap(); clearP.setMargins(0, dp(8), 0, 0); theft.addView(clearTheft, clearP);
            clearTheft.setOnClickListener(v -> clearTheftStatus());
        }

        LinearLayout health = cardColumn(body);
        health.addView(text("DEVICE RAM & STORAGE", 16, WHITE, true), matchWrap());
        health.addView(text(appMemorySummary() + "\n\n" + storageSummary(), 12, MUTED, false), matchWrap());
        Button refresh = smallButton("REFRESH DEVICE HEALTH");
        LinearLayout.LayoutParams refreshP = matchWrap(); refreshP.setMargins(0, dp(9), 0, 0); health.addView(refresh, refreshP);
        refresh.setOnClickListener(v -> showSecurityCenter());

        LinearLayout logs = cardColumn(body);
        logs.addView(text("SECURITY ACTIVITY LOG", 16, WHITE, true), matchWrap());
        logs.addView(text("Last PIN failures, unlocks, recovery events, vault changes and local theft actions. Maximum 60 entries.", 12, MUTED, false), matchWrap());
        Button viewLog = primaryButton("VIEW SECURITY ACTIVITY"); logs.addView(viewLog, matchWrap()); viewLog.setOnClickListener(v -> showActivityLog());

        LinearLayout network = cardColumn(body);
        network.addView(text("REMOTE PROTECTION STATUS", 16, WHITE, true), matchWrap());
        network.addView(text("Remote lock, automatic theft notifications, cloud backup, SIM-change monitoring and camera capture are NOT connected in this build. They need a verified owner account/server or explicit platform permissions and a visible consent flow. No hidden tracking, silent camera capture or automatic data wipe is performed.", 12, MUTED, false), matchWrap());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean granted = false;
            for (int result : grantResults) if (result == PackageManager.PERMISSION_GRANTED) granted = true;
            if (granted) recordLastKnownLocation(); else toast("Location permission denied. Vault remains usable.");
        }
    }

    private void showChangePinDialog() {
        LinearLayout form = dialogForm();
        EditText next = edit("New 6-digit PIN", false, true);
        EditText confirm = edit("Confirm new PIN", false, true);
        next.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        confirm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        form.addView(next, matchWrap()); form.addView(confirm, matchWrap());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Change vault PIN").setView(form)
                .setNegativeButton("CANCEL", null).setPositiveButton("SAVE", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = next.getText().toString(); String b = confirm.getText().toString();
            if (!a.matches("[0-9]{6}")) { toast("New PIN must be exactly 6 digits"); return; }
            if (!a.equals(b)) { toast("PIN entries do not match"); return; }
            try { changePin(a); dialog.dismiss(); toast("PIN changed successfully"); showSettingsScreen(); }
            catch (Exception e) { toast("Could not change PIN"); }
        }));
        dialog.show();
    }

    private void chooseAutoLock() {
        final int[] values = new int[]{30, 60, 300, 600};
        String[] labels = new String[]{"30 seconds", "1 minute", "5 minutes (recommended)", "10 minutes"};
        int current = prefs.getInt("auto_lock_seconds", 300); int checked = 2;
        for (int i = 0; i < values.length; i++) if (values[i] == current) checked = i;
        final int initial = checked;
        new AlertDialog.Builder(this).setTitle("Auto-lock after").setSingleChoiceItems(labels, initial, (dialog, which) -> {
            prefs.edit().putInt("auto_lock_seconds", values[which]).apply(); logEvent("Auto-lock timer changed"); dialog.dismiss(); showSettingsScreen(); toast("Auto-lock updated");
        }).setNegativeButton("CANCEL", null).show();
    }

    private String timeoutLabel(int seconds) {
        if (seconds < 60) return seconds + " seconds";
        return (seconds / 60) + " minute(s)";
    }

    private void addEmpty(LinearLayout parent, String message) {
        TextView empty = text(message, 13, MUTED, false); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(15), dp(30), dp(15), dp(30)); parent.addView(empty, matchWrap());
    }

    private LinearLayout cardColumn(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14), dp(14), dp(14), dp(14)); card.setBackground(cardBackground()); card.setElevation(dp(5));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, dp(8), 0, dp(8)); parent.addView(card, params); return card;
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(8), dp(8), dp(8), dp(4)); return form;
    }

    private EditText edit(String hint, boolean multiline, boolean numeric) {
        EditText field = new EditText(this);
        field.setHint(hint); field.setHintTextColor(MUTED); field.setTextColor(WHITE); field.setTextSize(15);
        field.setSingleLine(!multiline); field.setPadding(dp(13), dp(12), dp(13), dp(12)); field.setBackground(editBackground());
        if (multiline) { field.setGravity(Gravity.TOP | Gravity.START); field.setMinLines(4); field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); }
        else if (numeric) field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, multiline ? dp(145) : dp(52)); params.setMargins(0, 0, 0, dp(10));
        field.setLayoutParams(params);
        return field;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this); button.setText(label); button.setTextSize(14); button.setTextColor(WHITE); button.setAllCaps(false); button.setTypeface(null, Typeface.BOLD);
        button.setBackground(primaryBackground()); button.setElevation(dp(8)); button.setPadding(dp(12), dp(8), dp(12), dp(8)); return button;
    }

    private Button smallButton(String label) {
        Button button = new Button(this); button.setText(label); button.setTextSize(10); button.setTextColor(CYAN); button.setAllCaps(false); button.setTypeface(null, Typeface.BOLD); button.setMinHeight(dp(40)); button.setPadding(dp(4), 0, dp(4), 0); button.setBackground(cardBackground()); return button;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(8, 23, 43), Color.rgb(5, 12, 26)});
        d.setCornerRadius(dp(15)); d.setStroke(dp(1), Color.rgb(0, 119, 220)); return d;
    }

    private GradientDrawable primaryBackground() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.rgb(4, 40, 82), Color.rgb(0, 92, 160), Color.rgb(3, 31, 66)});
        d.setCornerRadius(dp(14)); d.setStroke(dp(2), CYAN); return d;
    }

    private GradientDrawable editBackground() {
        GradientDrawable d = new GradientDrawable(); d.setColor(Color.rgb(5, 14, 28)); d.setCornerRadius(dp(10)); d.setStroke(dp(1), BLUE); return d;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child); }
        file.delete();
    }

    // Minimal Base64 wrapper keeps decoding calls easy to read.
    private static final class Base64Compat {
        static byte[] decode(String text) { return android.util.Base64.decode(text, android.util.Base64.DEFAULT); }
    }
}
