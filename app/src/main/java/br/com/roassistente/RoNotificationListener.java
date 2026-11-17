package br.com.roassistente;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Reads only WhatsApp notification metadata/previews for exact names the user
 * explicitly allowlisted in the Ro app. No message is sent to a remote service.
 */
public class RoNotificationListener extends NotificationListenerService {
    public static final String CHANNEL_ID = "ro_authorized_messages";
    private static final String PREFS = "ro_local_settings";
    private static final String KEY_CONTACTS = "allowed_contacts";
    private static final String KEY_VOICE_ALERTS = "voice_alerts";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";

    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                int result = textToSpeech.setLanguage(new Locale("pt", "BR"));
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        if (getPackageName().equals(sbn.getPackageName())) return;
        if (!WHATSAPP_PACKAGE.equals(sbn.getPackageName())) return;

        Notification notification = sbn.getNotification();
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle extras = notification.extras;
        if (extras == null) return;
        if (Build.VERSION.SDK_INT >= 24 && extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) {
            // Group messages can contain senders who were not individually authorized.
            return;
        }

        CharSequence titleValue = extras.getCharSequence(Notification.EXTRA_TITLE);
        String sender = titleValue == null ? "" : titleValue.toString().trim();
        if (sender.isEmpty() || !isAllowedContact(sender)) return;

        CharSequence messageValue = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (messageValue == null || messageValue.length() == 0) {
            messageValue = extras.getCharSequence(Notification.EXTRA_TEXT);
        }
        String message = messageValue == null ? "" : messageValue.toString().trim();
        if (message.length() > 1200) message = message.substring(0, 1200) + "…";

        // Do not process a notification if the user cannot receive the Ro action
        // notification that asks for explicit approval to read the preview.
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }

        int id = createNotificationId(sender, sbn.getKey());
        postRoPrompt(sender, message, id);
        announceArrivalIfEnabled(sender, id);
    }

    private boolean isAllowedContact(String sender) {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> allowed = preferences.getStringSet(KEY_CONTACTS, new HashSet<>());
        if (allowed == null || allowed.isEmpty()) return false;
        String normalizedSender = normalize(sender);
        for (String candidate : allowed) {
            if (candidate != null && normalize(candidate).equals(normalizedSender)) return true;
        }
        return false;
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private int createNotificationId(String sender, String key) {
        int hash = (sender + ":" + key).hashCode() & 0x7fffffff;
        return hash == 0 ? 1 : hash;
    }

    private void postRoPrompt(String sender, String message, int id) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setAction(MainActivity.ACTION_OPEN_MESSAGE);
        openIntent.putExtra(MainActivity.EXTRA_SENDER, sender);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, id, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this).setPriority(Notification.PRIORITY_DEFAULT);

        builder.setSmallIcon(R.drawable.ic_ro)
                .setContentTitle("Mensagem de " + sender)
                .setContentText(message.isEmpty()
                        ? "A notificação não trouxe uma prévia de texto."
                        : "Toque em “Ler em voz alta” para ouvir a prévia.")
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPendingIntent);
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setTimeoutAfter(2 * 60 * 1000L);
        }

        if (!message.isEmpty()) {
            Intent readIntent = new Intent(this, MainActivity.class);
            readIntent.setAction(MainActivity.ACTION_READ_MESSAGE);
            readIntent.putExtra(MainActivity.EXTRA_SENDER, sender);
            // The preview is attached only to this short-lived, explicit user action.
            // It is not written to a database, file, chat history, or network service.
            readIntent.putExtra(MainActivity.EXTRA_MESSAGE, message);
            readIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent readPendingIntent = PendingIntent.getActivity(
                    this, id + 1, readIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (Build.VERSION.SDK_INT >= 23) {
                builder.addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_ro),
                        "Ler em voz alta",
                        readPendingIntent).build());
            } else {
                builder.addAction(R.drawable.ic_ro, "Ler em voz alta", readPendingIntent);
            }
        }

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(id, builder.build());
    }

    private void announceArrivalIfEnabled(String sender, int id) {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean enabled = preferences.getBoolean(KEY_VOICE_ALERTS, false);
        if (!enabled || !ttsReady || textToSpeech == null) return;
        String announcement = "Ro aqui. Chegou uma mensagem de " + sender
                + ". Toque no aviso da Ro e escolha Ler em voz alta se quiser ouvir.";
        textToSpeech.speak(announcement, TextToSpeech.QUEUE_ADD, null, "ro-arrival-" + id);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.notification_channel_description));
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.onDestroy();
    }
}
