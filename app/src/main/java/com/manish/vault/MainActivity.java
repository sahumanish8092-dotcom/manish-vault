package com.manish.vault;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.crypto.SecretKeyFactory;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import javax.crypto.spec.PBEKeySpec;

/**
 * Re-created native Android shell for the archived Manish Vault 2.0 Sketchware UI.
 * Original login and home HTML are kept as assets. Imported files live only in
 * this app's private files directory; this is private app storage, not encryption.
 */
public class MainActivity extends Activity {
    private static final int REQUEST_IMPORT = 4101;
    private static final int REQUEST_DEVICE_CREDENTIAL = 4102;
    private static final int PBKDF2_ITERATIONS = 160000;
    private static final int HASH_BITS = 256;
    private static final String PREFS = "vault_auth_v2";
    private static final String KEY_USER = "user";
    private static final String KEY_SALT = "salt";
    private static final String KEY_HASH = "hash";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_FAILED_LOGINS = "failed_logins";
    private static final String KEY_LOCK_UNTIL = "lock_until";
    private static final String KEY_SECURITY_LOG = "security_log";
    private static final int MAX_FAILED_LOGINS = 5;
    private static final long LOGIN_LOCK_MS = 30_000L;
    private static final String KEYSTORE_ALIAS = "ManishVaultDataKeyV2";
    private static final byte[] ENCRYPTED_MAGIC = new byte[]{77,86,76,84,50,69,78,67};
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private static final int BG = Color.rgb(3, 8, 18);
    private static final int PANEL = Color.rgb(8, 20, 35);
    private static final int CYAN = Color.rgb(0, 225, 255);
    private static final int PURPLE = Color.rgb(174, 92, 255);
    private static final int TEXT = Color.rgb(230, 247, 255);
    private static final int MUTED = Color.rgb(139, 170, 190);
    private static final int RED = Color.rgb(255, 80, 105);

    private WebView webView;
    private LinearLayout nativeRoot;
    private SharedPreferences prefs;
    private File vaultRoot, notesRoot, trashRoot;
    private String currentSection = "home";
    private String folderPath = "";
    private String pendingImportType = "all";
    private boolean unlocked = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        vaultRoot = new File(getFilesDir(), "VaultFiles");
        notesRoot = new File(getFilesDir(), "VaultNotes");
        trashRoot = new File(getFilesDir(), "VaultTrash");
        vaultRoot.mkdirs(); notesRoot.mkdirs(); trashRoot.mkdirs();
        ensureEncryptionKey();
        migratePlaintextFilesAsync();
        if (!prefs.contains(KEY_HASH)) showCredentialDialog(true);
        else showLogin();
    }

    private void initWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new VaultBridge(), "AndroidVault");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleWebUrl(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleWebUrl(url);
            }
        });
    }

    private boolean handleWebUrl(String url) {
        if (url != null && url.startsWith("manishvault://")) {
            String section = url.substring("manishvault://".length()).toLowerCase(Locale.ROOT);
            runOnUiThread(() -> openSection(section));
            return true;
        }
        return url != null && (url.startsWith("http:") || url.startsWith("https:") || url.startsWith("intent:"));
    }

    private void showLogin() {
        unlocked = false;
        currentSection = "login";
        initWebView();
        setContentView(webView);
        webView.loadUrl("file:///android_asset/login.html");
    }

    private void showHome() {
        unlocked = true;
        currentSection = "home";
        setContentView(webView);
        webView.loadUrl("file:///android_asset/home.html");
    }

    private class VaultBridge {
        @JavascriptInterface public void login(String user, String password) {
            final String enteredUser = user == null ? "" : user.trim();
            final String enteredPassword = password == null ? "" : password;
            new Thread(() -> {
                long now = System.currentTimeMillis();
                long lockUntil = prefs.getLong(KEY_LOCK_UNTIL, 0L);
                if (now < lockUntil) {
                    long seconds = Math.max(1L, (lockUntil - now + 999L) / 1000L);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "Vault temporarily locked. Try again in " + seconds + " seconds.",
                            Toast.LENGTH_LONG).show());
                    return;
                }
                String savedUser = prefs.getString(KEY_USER, "");
                String salt64 = prefs.getString(KEY_SALT, "");
                String expected = prefs.getString(KEY_HASH, "");
                boolean valid = !enteredPassword.isEmpty() && enteredUser.equals(savedUser)
                        && verifyPassword(enteredPassword, salt64, expected);
                if (valid) {
                    prefs.edit().putInt(KEY_FAILED_LOGINS, 0).putLong(KEY_LOCK_UNTIL, 0L).apply();
                    runOnUiThread(() -> {
                        logSecurityEvent("Successful password unlock");
                        showHome();
                    });
                } else {
                    int attempts = prefs.getInt(KEY_FAILED_LOGINS, 0) + 1;
                    SharedPreferences.Editor edit = prefs.edit().putInt(KEY_FAILED_LOGINS, attempts);
                    boolean locked = attempts >= MAX_FAILED_LOGINS;
                    if (locked) {
                        edit.putInt(KEY_FAILED_LOGINS, 0).putLong(KEY_LOCK_UNTIL, now + LOGIN_LOCK_MS);
                    }
                    edit.apply();
                    runOnUiThread(() -> {
                        logSecurityEvent(locked ? "5 failed login attempts — 30 second lockout"
                                : "Failed login attempt " + attempts + " of " + MAX_FAILED_LOGINS);
                        Toast.makeText(MainActivity.this,
                                locked ? "Too many attempts. Vault locked for 30 seconds."
                                        : "Wrong username or password",
                                Toast.LENGTH_SHORT).show();
                        if (webView != null) webView.evaluateJavascript(
                                "(function(){var t=document.getElementById('toast');if(t){t.textContent='INVALID LOGIN';t.classList.add('show');}})();",
                                null);
                    });
                }
            }, "vault-login-check").start();
        }
        @JavascriptInterface public void fingerprint() { runOnUiThread(MainActivity.this::startBiometric); }
        @JavascriptInterface public void forgotPassword() { runOnUiThread(MainActivity.this::forgotPassword); }
    }

    private void showCredentialDialog(boolean firstRun) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(8), dp(22), dp(4));
        EditText user = new EditText(this); user.setSingleLine(true); user.setHint("Create username");
        EditText pass = new EditText(this); pass.setSingleLine(true); pass.setHint("Create master password (6+ characters)");
        pass.setInputType(129);
        EditText confirm = new EditText(this); confirm.setSingleLine(true); confirm.setHint("Confirm master password"); confirm.setInputType(129);
        form.addView(user); form.addView(pass); form.addView(confirm);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(firstRun ? "Create your Manish Vault" : "Set a new master password")
                .setMessage("Remember this password. It is stored as a one-way hash and cannot be displayed later.")
                .setView(form)
                .setCancelable(!firstRun)
                .setPositiveButton(firstRun ? "CREATE VAULT" : "RESET PASSWORD", null)
                .setNegativeButton(firstRun ? "EXIT" : "CANCEL", (d, w) -> { if (firstRun) finish(); })
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String u = user.getText().toString().trim();
            String p = pass.getText().toString();
            String c = confirm.getText().toString();
            if (u.length() < 3) { user.setError("Use at least 3 characters"); return; }
            if (p.length() < 6) { pass.setError("Use at least 6 characters"); return; }
            if (!p.equals(c)) { confirm.setError("Passwords do not match"); return; }
            if (firstRun) prefs.edit().putString(KEY_USER, u).apply();
            savePassword(p);
            dialog.dismiss();
            if (firstRun) showLogin(); else Toast.makeText(this, "Master password updated", Toast.LENGTH_LONG).show();
        }));
        dialog.show();
    }

    private void savePassword(String password) {
        try {
            byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
            String salt64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP);
            String hash64 = android.util.Base64.encodeToString(derive(password, salt), android.util.Base64.NO_WRAP);
            prefs.edit().putString(KEY_SALT, salt64).putString(KEY_HASH, hash64).apply();
        } catch (Exception e) { Toast.makeText(this, "Could not save password", Toast.LENGTH_LONG).show(); }
    }

    private boolean verifyPassword(String password, String salt64, String expected64) {
        try {
            byte[] salt = android.util.Base64.decode(salt64, android.util.Base64.DEFAULT);
            byte[] expected = android.util.Base64.decode(expected64, android.util.Base64.DEFAULT);
            byte[] actual = derive(password, salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) { return false; }
    }

    private byte[] derive(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BITS);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }

    private void startBiometric() {
        if (Build.VERSION.SDK_INT < 28) {
            Toast.makeText(this, "Fingerprint sign-in needs Android 9 or newer", Toast.LENGTH_LONG).show(); return;
        }
        if (checkSelfPermission(android.Manifest.permission.USE_BIOMETRIC) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.USE_BIOMETRIC}, 51); return;
        }
        try {
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                    .setTitle("Unlock Manish Vault")
                    .setSubtitle("Authenticate with your enrolled biometric")
                    .setNegativeButton("CANCEL", getMainExecutor(), (dialog, which) -> {})
                    .build();
            prompt.authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    runOnUiThread(() -> { logSecurityEvent("Successful biometric unlock"); showHome(); });
                }
                @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, errString, Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception e) { Toast.makeText(this, "Biometric unlock is not available on this device", Toast.LENGTH_LONG).show(); }
    }

    private void forgotPassword() {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            new AlertDialog.Builder(this).setTitle("Password recovery")
                    .setMessage("Set a secure screen lock in Android Settings first. Vault password is never shown in plain text.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        Intent intent = km.createConfirmDeviceCredentialIntent("Verify device owner", "Confirm your screen lock to reset the vault password");
        if (intent != null) startActivityForResult(intent, REQUEST_DEVICE_CREDENTIAL);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DEVICE_CREDENTIAL && resultCode == RESULT_OK) {
            showCredentialDialog(false); return;
        }
        if (requestCode == REQUEST_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importUri(data.getData(), pendingImportType);
        }
    }

    private void openSection(String section) {
        if (!unlocked) return;
        currentSection = section;
        folderPath = "";
        switch (section) {
            case "files": case "photos": case "videos": case "folders": case "recent": case "favorites": case "trash":
                renderListSection(section); break;
            case "notes": renderNotes(); break;
            case "search": searchFiles(); break;
            case "settings": renderSettings(); break;
            case "about": renderAbout(); break;
            default: showHome(); break;
        }
    }

    private void renderListSection(String section) {
        String title = section.equals("favorites") ? "FAVORITES" : section.equals("recent") ? "RECENT FILES" :
                section.equals("trash") ? "RECYCLE BIN" : section.equals("photos") ? "PHOTOS" :
                section.equals("videos") ? "VIDEOS" : section.equals("folders") ? "FOLDERS" : "SECURE FILES";
        LinearLayout page = createPage(title, section.equals("trash") ? RED : CYAN);
        if (section.equals("folders")) {
            addAction(page, "+ CREATE FOLDER", PURPLE, () -> createFolder(vaultRoot));
            addFolderRows(page, vaultRoot, "");
            return;
        }
        if (!section.equals("trash")) {
            addAction(page, section.equals("photos") ? "+ ADD PHOTO" : section.equals("videos") ? "+ ADD VIDEO" : "+ ADD FILE",
                    CYAN, () -> launchImport(section.equals("photos") ? "image/*" : section.equals("videos") ? "video/*" : "*/*"));
        }
        File base = section.equals("trash") ? trashRoot : section.equals("notes") ? notesRoot : vaultRoot;
        List<File> files = collectFiles(base);
        if (section.equals("photos")) files.removeIf(f -> !isImage(f));
        if (section.equals("videos")) files.removeIf(f -> !isVideo(f));
        if (section.equals("favorites")) {
            Set<String> fav = prefs.getStringSet(KEY_FAVORITES, new HashSet<>());
            files.removeIf(f -> !fav.contains(f.getAbsolutePath()));
        }
        if (section.equals("recent")) files.sort((a,b) -> Long.compare(b.lastModified(), a.lastModified()));
        else files.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        addFilesToPage(page, files, section.equals("trash"));
    }

    private void addFolderRows(LinearLayout page, File directory, String relative) {
        File[] children = directory.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        int count = 0;
        for (File child : children) {
            if (child.isDirectory()) {
                count++;
                addRow(page, "📁  " + child.getName(), "Open folder", CYAN, () -> {
                    folderPath = relative.isEmpty() ? child.getName() : relative + "/" + child.getName();
                    renderFolderContents(child);
                }, () -> folderOptions(child));
            }
        }
        if (count == 0) addEmpty(page, "No folders yet. Tap CREATE FOLDER to add one.");
    }

    private void renderFolderContents(File folder) {
        currentSection = "folder";
        LinearLayout page = createPage(folder.getName().toUpperCase(Locale.ROOT), CYAN);
        addAction(page, "‹ BACK TO FOLDERS", PURPLE, () -> openSection("folders"));
        addAction(page, "+ ADD FILE HERE", CYAN, () -> launchImport("*/*"));
        addFilesToPage(page, collectFiles(folder), false);
    }

    private void folderOptions(File folder) {
        String[] options = {"Open", "Rename", "Move to Trash"};
        new AlertDialog.Builder(this).setTitle(folder.getName()).setItems(options, (d, which) -> {
            if (which == 0) renderFolderContents(folder);
            if (which == 1) renameFile(folder);
            if (which == 2) moveToTrash(folder);
        }).show();
    }

    private void addFilesToPage(LinearLayout page, List<File> files, boolean inTrash) {
        if (files.isEmpty()) { addEmpty(page, "Nothing here yet"); return; }
        for (File f : files) {
            String details = humanSize(f.length()) + "  •  " + (isImage(f) ? "Image" : isVideo(f) ? "Video" : "Private file");
            addRow(page, iconFor(f) + "  " + f.getName(), details, inTrash ? RED : TEXT,
                    () -> { if (inTrash) restoreFromTrash(f); else openStoredFile(f); }, () -> fileOptions(f, inTrash));
        }
    }

    private void renderNotes() {
        LinearLayout page = createPage("SECRET NOTES", PURPLE);
        addAction(page, "+ NEW NOTE", PURPLE, this::createNote);
        List<File> notes = collectFiles(notesRoot);
        notes.sort((a,b) -> Long.compare(b.lastModified(), a.lastModified()));
        if (notes.isEmpty()) addEmpty(page, "Your private notes will appear here.");
        for (File note : notes) {
            addRow(page, "📝  " + note.getName(), humanSize(note.length()), PURPLE,
                    () -> editNote(note), () -> new AlertDialog.Builder(this).setTitle(note.getName())
                            .setItems(new String[]{"Open / Edit", "Move to Trash"}, (d,w) -> { if (w == 0) editNote(note); else moveToTrash(note); }).show());
        }
    }

    private void logSecurityEvent(String event) {
        String old = prefs.getString(KEY_SECURITY_LOG, "");
        String entry = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new java.util.Date()) + "  •  " + event;
        String updated = entry + (old.isEmpty() ? "" : "\n" + old);
        String[] lines = updated.split("\n");
        StringBuilder bounded = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 50); i++) {
            if (i > 0) bounded.append('\n');
            bounded.append(lines[i]);
        }
        prefs.edit().putString(KEY_SECURITY_LOG, bounded.toString()).apply();
    }

    private void showSecurityLog() {
        String log = prefs.getString(KEY_SECURITY_LOG, "");
        new AlertDialog.Builder(this).setTitle("Security activity")
                .setMessage(log.isEmpty() ? "No security events recorded yet." : log)
                .setPositiveButton("CLOSE", null)
                .setNeutralButton("CLEAR LOG", (d, w) -> {
                    prefs.edit().remove(KEY_SECURITY_LOG).apply();
                    Toast.makeText(this, "Security log cleared", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void ensureEncryptionKey() { try { getVaultKey(); } catch (Exception e) { logSecurityEvent("Encryption key initialization failed"); } }

    private SecretKey getVaultKey() throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        java.security.Key key = ks.getKey(KEYSTORE_ALIAS, null); if (key instanceof SecretKey) return (SecretKey) key;
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return g.generateKey();
    }
    private boolean isEncryptedFile(File f) throws Exception {
        if (f.length() < ENCRYPTED_MAGIC.length) return false; byte[] h = new byte[ENCRYPTED_MAGIC.length];
        try (FileInputStream in = new FileInputStream(f)) { return in.read(h) == h.length && Arrays.equals(h, ENCRYPTED_MAGIC); }
    }
    private void encryptStream(InputStream in, File dest) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        // AndroidKeyStore requires a provider-generated IV when randomized
        // encryption is enforced. Never supply our own IV to ENCRYPT_MODE.
        c.init(Cipher.ENCRYPT_MODE, getVaultKey());
        byte[] iv = c.getIV();
        if (iv == null || iv.length != GCM_IV_BYTES) {
            throw new java.security.GeneralSecurityException("Could not generate a valid AES-GCM IV");
        }
        try (FileOutputStream raw = new FileOutputStream(dest)) {
            raw.write(ENCRYPTED_MAGIC);
            raw.write(iv);
            try (CipherOutputStream out = new CipherOutputStream(raw, c)) {
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
            }
        } catch (Exception e) {
            // Do not leave a partial/corrupt vault entry after a failed import.
            if (dest.exists()) dest.delete();
            throw e;
        }
    }
    private InputStream decryptingInput(File f) throws Exception {
        FileInputStream raw = new FileInputStream(f); byte[] h = new byte[ENCRYPTED_MAGIC.length];
        if (raw.read(h) != h.length || !Arrays.equals(h, ENCRYPTED_MAGIC)) { raw.close(); return new FileInputStream(f); }
        byte[] iv = new byte[GCM_IV_BYTES]; int off=0; while(off<iv.length){int n=raw.read(iv,off,iv.length-off);if(n<0){raw.close();throw new java.io.IOException("Bad encrypted header");}off+=n;}
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,getVaultKey(),new GCMParameterSpec(GCM_TAG_BITS,iv)); return new CipherInputStream(raw,c);
    }
    private byte[] readDecrypted(File f) throws Exception { try(InputStream in=decryptingInput(f);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();} }
    private void writeEncrypted(File f, byte[] data) throws Exception { try(InputStream in=new java.io.ByteArrayInputStream(data)){encryptStream(in,f);} }
    private void migratePlaintextFilesAsync() { new Thread(() -> { int n=migrateDirectory(vaultRoot)+migrateDirectory(notesRoot)+migrateDirectory(trashRoot); if(n>0)runOnUiThread(()->logSecurityEvent("Encrypted "+n+" existing items")); },"vault-migration").start(); }
    private int migrateDirectory(File dir) { int count=0; File[] files=dir.listFiles(); if(files==null)return 0; for(File f:files){if(f.isDirectory()){count+=migrateDirectory(f);continue;} try{if(!isEncryptedFile(f)){File tmp=new File(f.getParentFile(),f.getName()+".encrypting");try(InputStream in=new FileInputStream(f)){encryptStream(in,tmp);}if(tmp.renameTo(f))count++;else tmp.delete();}}catch(Exception e){runOnUiThread(()->logSecurityEvent("An item could not be encrypted"));}}return count; }

    private void renderSettings() {
        LinearLayout page = createPage("SECURITY SETTINGS", PURPLE);
        addRow(page, "🔑  Change master password", "Update your vault password", CYAN,
                () -> showCredentialDialog(false), null);
        addRow(page, "🛡️  Security activity log", "Review recent unlock and failed-login events", CYAN,
                this::showSecurityLog, null);
        addRow(page, "🧹  Empty recycle bin", "Permanently remove items in Trash", RED,
                () -> new AlertDialog.Builder(this).setTitle("Empty recycle bin?").setMessage("This permanently deletes everything in your vault trash.")
                        .setNegativeButton("CANCEL", null).setPositiveButton("EMPTY", (d,w) -> emptyTrash()).show(), null);
        addRow(page, "🔒  Lock vault now", "Return to the secure login screen", PURPLE, this::showLogin, null);
        addRow(page, "📦  Storage location", "App-private storage: " + getFilesDir().getAbsolutePath(), MUTED, () -> {}, null);
    }

    private void renderAbout() {
        LinearLayout page = createPage("ABOUT PHONE", CYAN);
        addInfo(page, "DEVICE", Build.MANUFACTURER + " " + Build.MODEL);
        addInfo(page, "ANDROID", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        addInfo(page, "APP", "Manish Vault 2.0 · Recreated Android build");
        addInfo(page, "STORAGE", "Files are stored in this app's private area. They are not encrypted at rest.");
        addInfo(page, "PRIVACY", "No account, cloud sync or network upload is configured in this build.");
    }

    private LinearLayout createPage(String title, int accent) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(14), dp(16), dp(30));
        page.setBackgroundColor(BG);
        scroll.addView(page);
        setContentView(scroll);
        addText(page, "✦  MANISH VAULT 2.0", 12, accent, true, 3, 0, 0);
        addText(page, title, 23, TEXT, true, 0, 2, 14);
        addAction(page, "‹ BACK TO VAULT HOME", CYAN, this::showHome);
        return page;
    }

    private void addInfo(LinearLayout page, String label, String value) {
        LinearLayout card = card();
        addText(card, label, 10, CYAN, true, 0, 0, 5);
        addText(card, value, 15, TEXT, false, 0, 0, 0);
        page.addView(card, matchWrap());
    }

    private void addEmpty(LinearLayout page, String message) {
        LinearLayout card = card(); addText(card, "◇  " + message, 14, MUTED, false, 0, 0, 0);
        page.addView(card, matchWrap());
    }

    private void addAction(LinearLayout page, String label, int accent, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setTextColor(accent); b.setAllCaps(false); b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(outline(accent, PANEL, 16));
        b.setPadding(dp(12), dp(8), dp(12), dp(8)); b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = matchWrap(); lp.bottomMargin = dp(9); page.addView(b, lp);
    }

    private void addRow(LinearLayout page, String title, String subtitle, int accent, Runnable click, Runnable longClick) {
        LinearLayout row = card(); row.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 15, accent, true); row.addView(t);
        TextView s = text(subtitle, 11, MUTED, false); LinearLayout.LayoutParams sp = matchWrap(); sp.topMargin=dp(4); row.addView(s,sp);
        row.setOnClickListener(v -> click.run());
        if (longClick != null) row.setOnLongClickListener(v -> { longClick.run(); return true; });
        LinearLayout.LayoutParams lp = matchWrap(); lp.bottomMargin=dp(9); page.addView(row,lp);
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14),dp(13),dp(14),dp(13)); l.setBackground(outline(0x553B617A, PANEL, 15)); return l;
    }

    private void addText(LinearLayout parent, String value, int size, int color, boolean bold, int top, int bottom, int start) {
        TextView t = text(value,size,color,bold); LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin=dp(top); lp.bottomMargin=dp(bottom); lp.leftMargin=dp(start); parent.addView(t,lp);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }

    private GradientDrawable outline(int stroke, int fill, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius));
        d.setStroke(dp(1),stroke); return d;
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1,-2); }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private void launchImport(String mimeType) {
        pendingImportType = mimeType;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType(mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_IMPORT); }
        catch (ActivityNotFoundException e) { Toast.makeText(this,"No file picker is available",Toast.LENGTH_LONG).show(); }
    }

    private void importUri(Uri uri, String type) {
        try {
            String name = queryDisplayName(uri);
            File destinationDir = vaultRoot;
            if (currentSection.equals("folder") && !folderPath.isEmpty()) destinationDir = new File(vaultRoot, folderPath);
            destinationDir.mkdirs();
            File destination = uniqueFile(destinationDir, name);
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new IllegalStateException("Unable to read selected file");
            try (InputStream source = in) { encryptStream(source, destination); }
            Toast.makeText(this,"Saved privately in Manish Vault",Toast.LENGTH_LONG).show();
            if (currentSection.equals("folder")) renderFolderContents(destinationDir); else openSection(currentSection);
        } catch (Exception e) { Toast.makeText(this,"Import failed: " + e.getMessage(),Toast.LENGTH_LONG).show(); }
    }

    private String queryDisplayName(Uri uri) {
        String result = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) result = c.getString(i);
            }
        } catch (Exception ignored) {}
        if (result == null || result.trim().isEmpty()) result = "VaultFile_" + System.currentTimeMillis();
        return result.replace("/", "_").replace("\\", "_");
    }

    private File uniqueFile(File dir, String name) {
        File target = new File(dir,name); if (!target.exists()) return target;
        String base=name, ext=""; int dot=name.lastIndexOf('.');
        if (dot>0) { base=name.substring(0,dot); ext=name.substring(dot); }
        int i=1; do { target=new File(dir,base+"_"+i+ext); i++; } while(target.exists()); return target;
    }

    private List<File> collectFiles(File root) {
        List<File> result = new ArrayList<>(); collectFilesRecursive(root,result); return result;
    }
    private void collectFilesRecursive(File dir, List<File> result) {
        File[] list = dir.listFiles(); if (list == null) return;
        for (File f : list) { if (f.isDirectory()) collectFilesRecursive(f,result); else if (f.isFile()) result.add(f); }
    }

    private boolean isImage(File f) { String n=f.getName().toLowerCase(Locale.ROOT); return n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png")||n.endsWith(".webp")||n.endsWith(".gif")||n.endsWith(".heic"); }
    private boolean isVideo(File f) { String n=f.getName().toLowerCase(Locale.ROOT); return n.endsWith(".mp4")||n.endsWith(".mkv")||n.endsWith(".webm")||n.endsWith(".mov")||n.endsWith(".3gp")||n.endsWith(".avi"); }
    private String iconFor(File f) { if (f.isDirectory()) return "📁"; if(isImage(f)) return "🖼"; if(isVideo(f)) return "🎬"; if(f.getName().toLowerCase().endsWith(".pdf")) return "📕"; return "📄"; }
    private String humanSize(long size) {
        if(size<1024)return size+" B"; if(size<1024*1024)return String.format(Locale.US,"%.1f KB",size/1024.0);
        if(size<1024L*1024*1024)return String.format(Locale.US,"%.1f MB",size/(1024.0*1024));
        return String.format(Locale.US,"%.2f GB",size/(1024.0*1024*1024));
    }

    private void fileOptions(File file, boolean inTrash) {
        List<String> options = new ArrayList<>();
        if (inTrash) { options.add("Restore"); options.add("Delete permanently"); }
        else { options.add(isFavorite(file) ? "Remove from Favorites" : "Add to Favorites"); options.add("Rename"); options.add("Move to Trash"); }
        new AlertDialog.Builder(this).setTitle(file.getName()).setItems(options.toArray(new String[0]), (d,w) -> {
            if(inTrash) { if(w==0) restoreFromTrash(file); else permanentDelete(file); }
            else { if(w==0) toggleFavorite(file); else if(w==1) renameFile(file); else moveToTrash(file); }
        }).show();
    }

    private boolean isFavorite(File file) { return prefs.getStringSet(KEY_FAVORITES,new HashSet<>()).contains(file.getAbsolutePath()); }
    private void toggleFavorite(File file) {
        Set<String> fav = new HashSet<>(prefs.getStringSet(KEY_FAVORITES,new HashSet<>()));
        if(!fav.add(file.getAbsolutePath())) fav.remove(file.getAbsolutePath());
        prefs.edit().putStringSet(KEY_FAVORITES,fav).apply();
        Toast.makeText(this,"Favorites updated",Toast.LENGTH_SHORT).show();
        openSection(currentSection.equals("folder")?"files":currentSection);
    }

    private void moveToTrash(File file) {
        if(file==null || !file.exists()) return;
        File target=uniqueFile(trashRoot,file.getName());
        boolean ok=file.renameTo(target);
        if(ok) {
            Set<String> fav=new HashSet<>(prefs.getStringSet(KEY_FAVORITES,new HashSet<>())); fav.remove(file.getAbsolutePath()); prefs.edit().putStringSet(KEY_FAVORITES,fav).apply();
            Toast.makeText(this,"Moved to Recycle Bin",Toast.LENGTH_SHORT).show();
            openSection(currentSection.equals("folder")?"files":currentSection);
        } else Toast.makeText(this,"Could not move item",Toast.LENGTH_SHORT).show();
    }

    private void restoreFromTrash(File file) {
        File dest=uniqueFile(vaultRoot,file.getName());
        if(file.renameTo(dest)) { Toast.makeText(this,"Restored to Secure Files",Toast.LENGTH_SHORT).show(); renderListSection("trash"); }
        else Toast.makeText(this,"Restore failed",Toast.LENGTH_SHORT).show();
    }
    private void permanentDelete(File file) { deleteRecursively(file); renderListSection("trash"); }
    private void deleteRecursively(File file) { if(file.isDirectory()){File[] c=file.listFiles();if(c!=null)for(File x:c)deleteRecursively(x);} file.delete(); }
    private void emptyTrash() { File[] c=trashRoot.listFiles(); if(c!=null)for(File f:c)deleteRecursively(f); openSection("settings"); Toast.makeText(this,"Recycle Bin emptied",Toast.LENGTH_SHORT).show(); }

    private void renameFile(File file) {
        EditText input=new EditText(this); input.setSingleLine(true); input.setText(file.getName());
        new AlertDialog.Builder(this).setTitle("Rename item").setView(input).setNegativeButton("CANCEL",null).setPositiveButton("SAVE",(d,w)->{
            String name=input.getText().toString().trim(); if(name.isEmpty()||name.contains("/")||name.contains("\\")){Toast.makeText(this,"Invalid name",Toast.LENGTH_SHORT).show();return;}
            File dest=uniqueFile(file.getParentFile(),name); if(file.renameTo(dest)) openSection(currentSection); else Toast.makeText(this,"Rename failed",Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void createFolder(File parent) {
        EditText input=new EditText(this); input.setSingleLine(true); input.setHint("Folder name");
        new AlertDialog.Builder(this).setTitle("Create secure folder").setView(input).setNegativeButton("CANCEL",null).setPositiveButton("CREATE",(d,w)->{
            String name=input.getText().toString().trim(); if(name.isEmpty()||name.contains("/")||name.contains("\\")){Toast.makeText(this,"Invalid folder name",Toast.LENGTH_SHORT).show();return;}
            File folder=new File(parent,name); if(folder.mkdir()) { Toast.makeText(this,"Folder created",Toast.LENGTH_SHORT).show(); openSection("folders"); }
            else Toast.makeText(this,"Folder already exists or could not be created",Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void createNote() { editNote(null); }
    private void editNote(File existing) {
        EditText input=new EditText(this); input.setGravity(Gravity.TOP); input.setMinLines(8); input.setTextSize(15);
        if(existing!=null) { try { input.setText(new String(readDecrypted(existing), java.nio.charset.StandardCharsets.UTF_8)); } catch(Exception ignored){} }
        new AlertDialog.Builder(this).setTitle(existing==null?"New secret note":existing.getName()).setView(input).setNegativeButton("CANCEL",null).setPositiveButton("SAVE",(d,w)->{
            String body=input.getText().toString(); if(body.trim().isEmpty())return;
            try {
                File target=existing;
                if(target==null) target=uniqueFile(notesRoot,"Note_"+System.currentTimeMillis()+".txt");
                writeEncrypted(target, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Toast.makeText(this,"Note saved privately",Toast.LENGTH_SHORT).show(); renderNotes();
            }catch(Exception e){Toast.makeText(this,"Could not save note",Toast.LENGTH_SHORT).show();}
        }).show();
    }

    private void searchFiles() {
        EditText input=new EditText(this); input.setSingleLine(true); input.setHint("Search files, folders and notes");
        new AlertDialog.Builder(this).setTitle("Search Vault").setView(input).setNegativeButton("CANCEL",null).setPositiveButton("SEARCH",(d,w)->{
            String q=input.getText().toString().trim().toLowerCase(Locale.ROOT);
            if(q.isEmpty()){showHome();return;}
            LinearLayout page=createPage("SEARCH RESULTS",CYAN);
            List<File> all=new ArrayList<>(); all.addAll(collectFiles(vaultRoot)); all.addAll(collectFiles(notesRoot));
            for(File f:all) if(f.getName().toLowerCase(Locale.ROOT).contains(q)) addRow(page,iconFor(f)+"  "+f.getName(),humanSize(f.length()),CYAN,()->openStoredFile(f),()->fileOptions(f,false));
            if(page.getChildCount()<=2)addEmpty(page,"No matches for: "+q);
        }).show();
    }

    private void openStoredFile(File file) {
        try {
            String mime=getMimeType(file.getName());
            File preview = new File(getCacheDir(), "preview_" + System.currentTimeMillis() + "_" + file.getName());
            try (InputStream in = decryptingInput(file); FileOutputStream out = new FileOutputStream(preview)) { byte[] buffer=new byte[16384]; int n; while((n=in.read(buffer))!=-1)out.write(buffer,0,n); }
            Uri uri=FileProvider.getUriForFile(this,"com.manish.vault.fileprovider",preview);
            Intent intent=new Intent(Intent.ACTION_VIEW); intent.setDataAndType(uri,mime); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch(Exception e) { new AlertDialog.Builder(this).setTitle(file.getName()).setMessage("Saved in private vault storage.\n"+humanSize(file.length())+"\n\nNo compatible viewer was found.") .setPositiveButton("OK",null).show(); }
    }
    private String getMimeType(String name) {
        String n=name.toLowerCase(Locale.ROOT);
        if(n.endsWith(".jpg")||n.endsWith(".jpeg"))return "image/jpeg"; if(n.endsWith(".png"))return "image/png"; if(n.endsWith(".webp"))return "image/webp";
        if(n.endsWith(".mp4"))return "video/mp4"; if(n.endsWith(".pdf"))return "application/pdf"; if(n.endsWith(".txt"))return "text/plain"; return "*/*";
    }

    @Override public void onBackPressed() {
        if(!unlocked){super.onBackPressed();return;}
        if(currentSection.equals("home")){ logSecurityEvent("Vault manually locked"); showLogin(); return;}
        if(currentSection.equals("folder")){openSection("folders");return;}
        showHome();
    }
}
