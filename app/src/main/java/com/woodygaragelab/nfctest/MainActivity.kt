package com.woodygaragelab.nfctest

//import android.R
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
//import android.media.AudioManager
//import android.media.SoundPool
import android.media.AudioAttributes
import android.media.SoundPool

import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcF
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    private val READ_TIMEOUT_MILLISECONDS: Int       = 2000
    private val CONNECTION_TIMEOUT_MILLISECONDS: Int = 2000
    private var mNfcAdapter: NfcAdapter?             = null
    private var pendingIntent: PendingIntent?        = null
    private var intentFilters: Array<IntentFilter>?  = null
    private var techLists: Array<Array<String>>?     = null
    private lateinit var soundPool: SoundPool
    private var soundOne                              = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        // 受け取るIntentを指定
        intentFilters = arrayOf(IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED))

        // 反応するタグの種類を指定
        techLists = arrayOf(
                arrayOf(Ndef::class.java.name),
                arrayOf(NdefFormatable::class.java.name),
                arrayOf(NfcF::class.java.name)
        )

        mNfcAdapter = NfcAdapter.getDefaultAdapter(applicationContext)

        var tagTextView: TextView = findViewById(R.id.tagText)
        tagTextView.text = "カードをタッチしてください 0308"

        val btn_in: Button = findViewById(R.id.btn_in)
        btn_in.setOnClickListener {
            tagTextView.text = "recording!"
            startRecordRequest("IN")
        }

        val btn_out: Button = findViewById(R.id.btn_out)
        btn_out.setOnClickListener {
            tagTextView.text = "recording!"
            startRecordRequest("OUT")
        }

        val button: Button = findViewById(R.id.btn_query)
        button.setOnClickListener {
            tagTextView.text = "query!"
            startPostRequest()
        }

        val audioAttributes = AudioAttributes.Builder()
                // USAGE_MEDIA
                // USAGE_GAME
                .setUsage(AudioAttributes.USAGE_GAME)
                // CONTENT_TYPE_MUSIC
                // CONTENT_TYPE_SPEECH, etc.
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

        soundPool = SoundPool.Builder()
                .setAudioAttributes(audioAttributes)
                // ストリーム数に応じて
                .setMaxStreams(2)
                .build()

        // one.wav をロードしておく
        soundOne = soundPool.load(this, R.raw.touchsound, 1)

    }

    override fun onResume() {
        super.onResume()
        // NFCタグの検出を有効化
        mNfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techLists)
    }

    /* NFCタグの検出時に呼ばれる */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // タグのIDを取得
        val tagId: ByteArray = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID) ?: return

        var list = ArrayList<String>()
        for (byte in tagId) {
            list.add(String.format("%02X", byte.toInt() and 0xFF))
        }

        // 画面にタグIDを表示
        var tagTextView: TextView = findViewById(R.id.tagText)
        tagTextView.text = list.joinToString(":")

        startRecordRequest("IN") // 勤怠記録を送信
        soundPool.play(soundOne, 1.0f, 1.0f, 0, 0, 1.0f)

    }

    // メイン(UI)スレッドでメッセージを表示する
    fun displayMessage(s: String) {
        runOnUiThread {
            var tagTextView: TextView = findViewById(R.id.tagText)
            tagTextView.text = s
        }
    }

    override fun onPause() {
        super.onPause()
        mNfcAdapter?.disableForegroundDispatch(this)
    }

    // OkHttpClientを作成
    private val client = OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT_MILLISECONDS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MILLISECONDS.toLong(), TimeUnit.MILLISECONDS)
            .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    fun startRecordRequest(txn: String) {
        val urlStr = "https://yhwmivrrp7.execute-api.ap-northeast-1.amazonaws.com/dev"  // Kintai Record の登録
        val sendDataJson =
                "{\"UserID\": \"99117\",\"TrxCode\":\""+ txn + "\",\"Date\": \"2022/02/22\",\"Time\": \"12:01:00\"," +
                        "\"EmpNo\": \"99117\",\"FirstName\": \"ARS\",\"LastName\": \"lab\",\"Remarks\": \"test\" }	"  // Query data
        // Requestを作成
        val request = Request.Builder()
                .url(urlStr)
                .post(sendDataJson.toRequestBody(JSON_MEDIA))
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string().orEmpty() // Responseの読み出し
                Log.i("http response", responseBody)
                displayMessage(responseBody)                         // responseを画面に表示
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("Error", e.toString())
            }
        })
    }

    //override fun startPostRequest() {
    fun startPostRequest() {

        val urlStr = "https://el2gjqf1n3.execute-api.ap-northeast-1.amazonaws.com/dev"
        // Bodyのデータ
        val sendDataJson = "{\"UserID\":\"99117\"}"

        // Requestを作成
        val request = Request.Builder()
                .url(urlStr)
                .post(sendDataJson.toRequestBody(JSON_MEDIA))
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                // Responseの読み出し
                val responseBody = response.body?.string().orEmpty()
                // 必要に応じてCallback
                Log.i("http response", responseBody)
                displayMessage(responseBody)
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("Error", e.toString())
                // 必要に応じてCallback
            }
        })
    }


}