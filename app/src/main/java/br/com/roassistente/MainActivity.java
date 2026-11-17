package br.com.roassistente;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    public static final String ACTION_READ_MESSAGE = "br.com.roassistente.action.READ_MESSAGE";
    public static final String ACTION_OPEN_MESSAGE = "br.com.roassistente.action.OPEN_MESSAGE";
    public static final String EXTRA_SENDER = "ro_sender";
    public static final String EXTRA_MESSAGE = "ro_message";

    private static final int REQUEST_SPEECH = 4101;
    private static final int REQUEST_AUDIO_PERMISSION = 4102;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 4103;
    private static final String PREFS = "ro_local_settings";
    private static final String KEY_CONTACTS = "allowed_contacts";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_VOICE_ALERTS = "voice_alerts";
    private static final int COLOR_BG = Color.rgb(13, 16, 38);
    private static final int COLOR_CARD = Color.rgb(24, 29, 58);
    private static final int COLOR_TEXT = Color.rgb(244, 245, 255);
    private static final int COLOR_MUTED = Color.rgb(173, 180, 210);
    private static final int COLOR_ACCENT = Color.rgb(143, 155, 255);

    private SharedPreferences preferences;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private String pendingSpeech;
    private TextView permissionStatus;
    private TextView conversationText;
    private LinearLayout contactList;
    private EditText contactInput;
    private EditText nameInput;
    private Switch voiceSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        textToSpeech = new TextToSpeech(getApplicationContext(), this);
        createNotificationChannel();
        buildScreen();
        if (savedInstanceState == null) {
            handleIncomingIntent(getIntent());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(22), dp(20), dp(32));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView brand = text("RO", 32, COLOR_TEXT, true);
        page.addView(brand);
        TextView subtitle = text("ASSISTENTE PESSOAL  •  VERSÃO 0.1", 11, COLOR_ACCENT, true);
        subtitle.setLetterSpacing(0.11f);
        addGap(page, 5);
        page.addView(subtitle);
        addGap(page, 16);
        TextView welcome = text("Oi! Eu sou a Ro.", 22, COLOR_TEXT, true);
        page.addView(welcome);
        TextView intro = text("Vamos começar com voz e avisos de mensagens que você autorizar.", 14, COLOR_MUTED, false);
        intro.setLineSpacing(dp(3), 1f);
        page.addView(intro);

        addGap(page, 20);
        permissionStatus = text("Verificando permissões…", 13, COLOR_MUTED, false);
        addCard(page, "STATUS DO ANDROID", permissionStatus);

        addGap(page, 14);
        addSectionTitle(page, "1. Permissões");
        Button notificationAccessButton = makeButton("Autorizar acesso às notificações", true);
        notificationAccessButton.setOnClickListener(v -> openNotificationListenerSettings());
        page.addView(notificationAccessButton);
        addGap(page, 8);
        Button appNotificationsButton = makeButton("Ativar avisos da Ro", false);
        appNotificationsButton.setOnClickListener(v -> requestOwnNotificationPermission());
        page.addView(appNotificationsButton);
        TextView permissionHint = text("O Android abrirá as próprias configurações. Ative a Ro manualmente; você pode revogar o acesso quando quiser.", 12, COLOR_MUTED, false);
        permissionHint.setLineSpacing(dp(2), 1f);
        page.addView(permissionHint);

        addGap(page, 18);
        addSectionTitle(page, "2. Como devo chamar você?");
        nameInput = makeInput("Seu nome", 1);
        nameInput.setText(preferences.getString(KEY_NAME, ""));
        page.addView(nameInput);
        addGap(page, 7);
        Button saveName = makeButton("Salvar nome", false);
        saveName.setOnClickListener(v -> saveUserName());
        page.addView(saveName);

        addGap(page, 18);
        addSectionTitle(page, "3. Contatos autorizados");
        TextView allowHint = text("Adicione o nome exatamente como aparece no título da notificação do WhatsApp. A lista começa vazia: ninguém é autorizado automaticamente.", 12, COLOR_MUTED, false);
        allowHint.setLineSpacing(dp(2), 1f);
        page.addView(allowHint);
        addGap(page, 8);
        contactInput = makeInput("Nome do contato", 1);
        page.addView(contactInput);
        addGap(page, 7);
        Button addContactButton = makeButton("Adicionar contato autorizado", true);
        addContactButton.setOnClickListener(v -> addAllowedContact());
        page.addView(addContactButton);
        addGap(page, 8);
        contactList = new LinearLayout(this);
        contactList.setOrientation(LinearLayout.VERTICAL);
        page.addView(contactList);
        renderContacts();

        addGap(page, 18);
        addSectionTitle(page, "4. Aviso falado");
        voiceSwitch = new Switch(this);
        voiceSwitch.setText("A Ro pode anunciar o nome do contato");
        voiceSwitch.setTextColor(COLOR_TEXT);
        voiceSwitch.setTextSize(14);
        voiceSwitch.setPadding(dp(2), dp(7), dp(2), dp(7));
        voiceSwitch.setChecked(preferences.getBoolean(KEY_VOICE_ALERTS, false));
        voiceSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(KEY_VOICE_ALERTS, isChecked).apply());
        page.addView(voiceSwitch);
        TextView voiceHint = text("Desligado por padrão. Quando ativado, a Ro só anuncia que chegou uma mensagem; o conteúdo só é falado depois que você tocar em “Ler em voz alta”.", 12, COLOR_MUTED, false);
        voiceHint.setLineSpacing(dp(2), 1f);
        page.addView(voiceHint);
        addGap(page, 8);
        Button testVoice = makeButton("Testar voz da Ro", false);
        testVoice.setOnClickListener(v -> speak("Oi! Eu sou a Ro. Minha voz está funcionando."));
        page.addView(testVoice);

        addGap(page, 18);
        addSectionTitle(page, "5. Conversar com a Ro");
        conversationText = text("Ainda não começamos uma conversa.", 13, COLOR_MUTED, false);
        conversationText.setLineSpacing(dp(3), 1f);
        addCard(page, "CONVERSA", conversationText);
        addGap(page, 8);
        Button speakButton = makeButton("Falar com a Ro", true);
        speakButton.setOnClickListener(v -> startVoiceInput());
        page.addView(speakButton);
        addGap(page, 8);
        EditText typedQuestion = makeInput("Ou escreva uma pergunta", 1);
        page.addView(typedQuestion);
        addGap(page, 7);
        Button sendTyped = makeButton("Enviar pergunta", false);
        sendTyped.setOnClickListener(v -> {
            String question = typedQuestion.getText().toString().trim();
            if (question.isEmpty()) {
                Toast.makeText(this, "Escreva uma pergunta primeiro.", Toast.LENGTH_SHORT).show();
                return;
            }
            typedQuestion.setText("");
            respondLocally(question);
        });
        page.addView(sendTyped);

        addGap(page, 18);
        TextView privacy = text("PRIVACIDADE: nesta versão, os dados de contatos ficam no aparelho. A Ro não envia notificações para uma IA, não acessa conversas diretamente e não guarda o texto das mensagens. A prévia fica temporariamente ligada ao botão de leitura da notificação; não entra em histórico nem é enviada a um servidor. O reconhecimento de voz usa o serviço de fala configurado no Android.", 11, COLOR_MUTED, false);
        privacy.setLineSpacing(dp(3), 1f);
        page.addView(privacy);

        setContentView(scroll);
    }

    private void addSectionTitle(LinearLayout parent, String value) {
        TextView title = text(value, 16, COLOR_TEXT, true);
        parent.addView(title);
        addGap(parent, 8);
    }

    private void addCard(LinearLayout parent, String heading, View content) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.setBackground(rounded(COLOR_CARD, 17));
        TextView title = text(heading, 10, COLOR_ACCENT, true);
        title.setLetterSpacing(0.12f);
        card.addView(title);
        addGap(card, 8);
        card.addView(content);
        parent.addView(card, matchWrap());
    }

    private void addGap(LinearLayout parent, int heightDp) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private EditText makeInput(String hint, int lines) {
        EditText input = new EditText(this);
        input.setSingleLine(lines == 1);
        input.setHint(hint);
        input.setHintTextColor(COLOR_MUTED);
        input.setTextColor(COLOR_TEXT);
        input.setTextSize(14);
        input.setPadding(dp(13), dp(10), dp(13), dp(10));
        input.setBackground(rounded(Color.rgb(33, 39, 73), 13));
        return input;
    }

    private Button makeButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? COLOR_BG : COLOR_TEXT);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setBackground(rounded(primary ? COLOR_ACCENT : Color.rgb(42, 49, 86), 13));
        return button;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void saveUserName() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) name = "amigo";
        preferences.edit().putString(KEY_NAME, name).apply();
        Toast.makeText(this, "Vou chamar você de " + name + ".", Toast.LENGTH_SHORT).show();
    }

    private void addAllowedContact() {
        String contact = contactInput.getText().toString().trim();
        if (contact.isEmpty()) {
            Toast.makeText(this, "Digite o nome do contato.", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> updated = new HashSet<>(preferences.getStringSet(KEY_CONTACTS, new HashSet<>()));
        for (String current : updated) {
            if (current.equalsIgnoreCase(contact)) {
                Toast.makeText(this, "Esse contato já está autorizado.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        updated.add(contact);
        preferences.edit().putStringSet(KEY_CONTACTS, updated).apply();
        contactInput.setText("");
        renderContacts();
    }

    private void removeAllowedContact(String contact) {
        Set<String> updated = new HashSet<>(preferences.getStringSet(KEY_CONTACTS, new HashSet<>()));
        updated.remove(contact);
        preferences.edit().putStringSet(KEY_CONTACTS, updated).apply();
        renderContacts();
    }

    private void renderContacts() {
        if (contactList == null) return;
        contactList.removeAllViews();
        ArrayList<String> contacts = new ArrayList<>(preferences.getStringSet(KEY_CONTACTS, new HashSet<>()));
        Collections.sort(contacts, String.CASE_INSENSITIVE_ORDER);
        if (contacts.isEmpty()) {
            TextView empty = text("Nenhum contato autorizado ainda.", 12, COLOR_MUTED, false);
            contactList.addView(empty);
            return;
        }
        for (String contact : contacts) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(4), dp(4), dp(4));
            row.setBackground(rounded(Color.rgb(33, 39, 73), 11));
            TextView name = text(contact, 13, COLOR_TEXT, true);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button remove = new Button(this);
            remove.setText("Remover");
            remove.setAllCaps(false);
            remove.setTextSize(11);
            remove.setTextColor(Color.rgb(255, 166, 180));
            remove.setMinHeight(dp(36));
            remove.setMinimumHeight(dp(36));
            remove.setPadding(dp(5), 0, dp(5), 0);
            remove.setBackgroundColor(Color.TRANSPARENT);
            remove.setOnClickListener(v -> removeAllowedContact(contact));
            row.addView(remove);
            LinearLayout.LayoutParams params = matchWrap();
            params.bottomMargin = dp(6);
            contactList.addView(row, params);
        }
    }

    private void openNotificationListenerSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Não consegui abrir as permissões. Procure por Acesso às notificações nas configurações do Android.", Toast.LENGTH_LONG).show();
        }
    }

    private void requestOwnNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
        } else {
            Toast.makeText(this, "Os avisos da Ro estão permitidos.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasOwnNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotificationListenerAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName component = new ComponentName(this, RoNotificationListener.class);
        return enabled.contains(component.flattenToString());
    }

    private void refreshPermissionStatus() {
        if (permissionStatus == null) return;
        boolean listener = hasNotificationListenerAccess();
        boolean appNotifications = hasOwnNotificationPermission();
        String status = "Acesso às notificações: " + (listener ? "ATIVADO" : "DESATIVADO")
                + "\nAvisos da Ro: " + (appNotifications ? "PERMITIDOS" : "BLOQUEADOS");
        permissionStatus.setText(status);
        permissionStatus.setTextColor(listener && appNotifications ? Color.rgb(121, 225, 182) : COLOR_MUTED);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    RoNotificationListener.CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.notification_channel_description));
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Pode falar. Estou ouvindo.");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            startActivityForResult(intent, REQUEST_SPEECH);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Este aparelho não tem um serviço de reconhecimento de voz disponível.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceInput();
            } else {
                Toast.makeText(this, "A permissão do microfone foi negada.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            refreshPermissionStatus();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) respondLocally(matches.get(0));
        }
    }

    private void respondLocally(String question) {
        String clean = question == null ? "" : question.trim();
        String normalized = clean.toLowerCase(new Locale("pt", "BR"));
        String name = preferences.getString(KEY_NAME, "amigo").trim();
        if (name.isEmpty()) name = "amigo";
        String answer;
        if (normalized.contains("que horas") || normalized.contains("hora é")) {
            answer = "Agora são " + new SimpleDateFormat("HH:mm", new Locale("pt", "BR")).format(new Date()) + ", " + name + ".";
        } else if (normalized.contains("seu nome") || normalized.contains("quem é você")) {
            answer = "Eu sou a Ro, sua assistente pessoal.";
        } else if (normalized.contains("como você está") || normalized.contains("tudo bem")) {
            answer = "Estou pronta para ajudar. Ainda estou na minha primeira versão, mas já estou começando a funcionar.";
        } else if (normalized.contains("parar") || normalized.contains("silêncio")) {
            answer = "Tudo bem. Vou ficar em silêncio até você me chamar de novo.";
        } else {
            answer = "Entendi, " + name + ". Nesta primeira versão eu consigo conversar de forma básica e ler a prévia de notificações autorizadas. A inteligência artificial completa virá numa próxima etapa.";
        }
        if (conversationText != null) conversationText.setText("Você: " + clean + "\n\nRo: " + answer);
        speak(answer);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        String sender = intent.getStringExtra(EXTRA_SENDER);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        if (ACTION_READ_MESSAGE.equals(action)) {
            if (message != null && !message.trim().isEmpty()) {
                pendingSpeech = "Mensagem de " + safeSender(sender) + ". " + message;
                speak(pendingSpeech);
            } else {
                pendingSpeech = "A notificação de " + safeSender(sender) + " não trouxe uma prévia do texto. Abra o WhatsApp para ler a mensagem.";
                speak(pendingSpeech);
            }
        } else if (ACTION_OPEN_MESSAGE.equals(action)) {
            pendingSpeech = "Chegou uma notificação de " + safeSender(sender) + ". Toque na opção Ler em voz alta se quiser ouvir a prévia.";
            speak(pendingSpeech);
        }
    }

    private String safeSender(String sender) {
        return sender == null || sender.trim().isEmpty() ? "um contato autorizado" : sender.trim();
    }

    private void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (!ttsReady || textToSpeech == null) {
            pendingSpeech = text;
            return;
        }
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ro-" + System.currentTimeMillis());
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
            int result = textToSpeech.setLanguage(new Locale("pt", "BR"));
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            if (ttsReady && pendingSpeech != null) {
                String text = pendingSpeech;
                pendingSpeech = null;
                speak(text);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.onDestroy();
    }
}
