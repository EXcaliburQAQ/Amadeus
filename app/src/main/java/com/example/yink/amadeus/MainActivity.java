package com.example.yink.amadeus;

/*
 * Big thanks to https://github.com/RIP95 aka Emojikage
 */

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements RecognitionListener {

    private final String TAG = "MainActivity";

    private VoiceLine[] mVoiceLines = VoiceLine.Line.getLines();
    private Random mRandomgen = new Random();
    private String mRecogLang;
    private String[] mContextLang;
    private SpeechRecognizer mSR;
    private ImageView mIvKurisu;
    private SharedPreferences sSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mIvKurisu = (ImageView) findViewById(R.id.imageView_kurisu);
        ImageView ivSubtitlesBackground = (ImageView) findViewById(R.id.imageView_subtitles);
        sSettings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        initSpeech();

        final int REQUEST_PERMISSION_RECORD_AUDIO = 11302;

        if (!sSettings.getBoolean("show_subtitles", false)) {
            ivSubtitlesBackground.setVisibility(View.INVISIBLE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION_RECORD_AUDIO);
        }

        Amadeus.speak(mVoiceLines[VoiceLine.Line.HELLO], MainActivity.this);

        initClickListener();
    }

    private void initSpeech() {
        if (mSR == null){
            mRecogLang = sSettings.getString("recognition_lang", "ja-JP");
            mContextLang = mRecogLang.split("-");
            mSR = SpeechRecognizer.createSpeechRecognizer(this);
            if (SpeechRecognizer.isRecognitionAvailable(this)){
                mSR.setRecognitionListener(this);
            } else {
                Toast.makeText(this, "Speech Recognition is not available",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initClickListener(){

        final Handler handler = new Handler();
        final Runnable loop = new Runnable() {
            @Override
            public void run() {
                if (Amadeus.isLoop) {
                    Amadeus.speak(mVoiceLines[mRandomgen.nextInt(mVoiceLines.length)], MainActivity.this);
                    handler.postDelayed(this, 5000 + mRandomgen.nextInt(5) * 1000);
                }
            }
        };

        mIvKurisu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!Amadeus.isLoop && !Amadeus.isSpeaking) {
                    // Input during loop produces bugs and mixes with output
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        MainActivity host = (MainActivity) view.getContext();

                        int permissionCheck = ContextCompat.checkSelfPermission(host, Manifest.permission.RECORD_AUDIO);
                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                            promptSpeechInput();
                        } else {
                            Amadeus.speak(mVoiceLines[VoiceLine.Line.DAGA_KOTOWARU], MainActivity.this);
                        }
                        return;
                    }
                    promptSpeechInput();
                }
            }});


        mIvKurisu.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (!Amadeus.isLoop && !Amadeus.isSpeaking) {
                    handler.post(loop);
                    Amadeus.isLoop = true;
                } else {
                    handler.removeCallbacks(loop);
                    Amadeus.isLoop = false;
                }
                return true;
            }
        });
    }

    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, mRecogLang);
        if (mSR != null){
            mSR.stopListening();
            mSR.startListening(intent);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LangContext.wrap(newBase));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSR != null)
            mSR.destroy();
        if (Amadeus.m != null)
            Amadeus.m.release();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Amadeus.isLoop = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Amadeus.isLoop = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case 1: {
                if (resultCode == RESULT_OK && null != data) {

                    /* Switch language within current context for voice recognition */
                    Context context = LangContext.load(getApplicationContext(), mContextLang[0]);

                    ArrayList<String> input = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    Amadeus.responseToInput(input.get(0), context, MainActivity.this);
                }
                break;
            }

        }
    }

    @Override
    public void onReadyForSpeech(Bundle params) {

    }

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onRmsChanged(float rmsdB) {

    }

    @Override
    public void onBufferReceived(byte[] buffer) {

    }

    @Override
    public void onEndOfSpeech() {

    }

    @Override
    public void onError(int error) {
        mSR.cancel();
        Amadeus.speak(mVoiceLines[VoiceLine.Line.SORRY], MainActivity.this);
    }

    @Override
    public void onResults(Bundle results) {
        String input = "";
        String debug = "";
        Log.d(TAG, "Received results");
        ArrayList data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        if (data != null){
//            for (Object word: data) {
//                debug += word + "\n";
//            }
//            Log.d(TAG, debug);

            input += data.get(0);
            /* TODO: Japanese doesn't split the words. Sigh. */
            String[] splitInput = input.split(" ");

            /* Really, google? */
            if (splitInput[0].equalsIgnoreCase("Асистент")) {
                splitInput[0] = "Ассистент";
            }

            /* Switch language within current context for voice recognition */
            Context context = LangContext.load(getApplicationContext(), mContextLang[0]);

            if (splitInput.length > 2 && splitInput[0].equalsIgnoreCase(context.getString(R.string.assistant))) {
                String cmd = splitInput[1].toLowerCase();
                String[] args = new String[splitInput.length - 2];
                System.arraycopy(splitInput, 2, args, 0, splitInput.length - 2);

                if (cmd.contains(context.getString(R.string.open))) {
                    Amadeus.openApp(args, MainActivity.this);
                }

            } else {
                Amadeus.responseToInput(input, context, MainActivity.this);
            }
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {

    }

    @Override
    public void onEvent(int eventType, Bundle params) {

    }
}
