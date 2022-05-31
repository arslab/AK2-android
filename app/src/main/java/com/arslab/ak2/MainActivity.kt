package com.arslab.ak2

//import android.R
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
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

import kotlinx.serialization.*
import kotlinx.serialization.json.*

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

        val tagTextView: TextView = findViewById(R.id.text0)
        tagTextView.text = "カードをタッチしてください 0417"

        val btn_in: Button = findViewById(R.id.btn_in)
        btn_in.setOnClickListener {
            tagTextView.text = "recording!"
            callAK2CreateAttendance("01:10:4D:00:82:0A:1E:04")
        }

        val btn_out: Button = findViewById(R.id.btn_out)
        btn_out.setOnClickListener {
            tagTextView.text = "recording!"
            callAK2CreateAttendance("01:10:4D:00:82:0A:1E:04")
        }

        val button: Button = findViewById(R.id.btn_query)
        button.setOnClickListener {
            tagTextView.text = "query!"
            callAK2QueryAttendance()
        }

        val btn_setting: Button = findViewById(R.id.btn_setting)
        btn_setting.setOnClickListener {
            tagTextView.text = "setting!"
            settings()
        }

        // タッチ音の設定
        val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

        soundPool = SoundPool.Builder()
                .setAudioAttributes(audioAttributes)
                .setMaxStreams(2)
                .build()

        // 音声ファイルをロードしておく
        soundOne = soundPool.load(this, R.raw.touchsound, 1)
    }

    override fun onResume() {
        super.onResume()
        // NFCタグの検出を有効化
        mNfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techLists)
    }

    /* ICカードのタッチ時（NFCタグの検出時) に呼ばれる */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // カードのIDを取得
        val tagId: ByteArray = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID) ?: return
        val list = ArrayList<String>()
        for (byte in tagId) {
            list.add(String.format("%02X", byte.toInt() and 0xFF))
        }
        val cardid       = list.joinToString(":")   // 文字列に変換

        // 画面にカードIDを表示
        val tagTextView: TextView = findViewById(R.id.text0)
        tagTextView.text = cardid

        callAK2CreateAttendance(cardid) // AK2CreateAttendance API を呼んで勤怠記録を送信
        soundPool.play(soundOne, 1.0f, 1.0f, 0, 0, 1.0f)

    }

    // メイン(UI)スレッドでメッセージを表示する
    // AK2QueryKintai API の結果を表示する
    fun displayMessage(response: String) {
        runOnUiThread {

            // response (json) を受け取る data class
            @Serializable
            data class Attendance(
                val Date: String,
                val UserID: String,
                val Time: String,
                val Remarks: String,
                val TrxCode: String,
            )
            @Serializable
            data class AK2Response(
                val body: List<Attendance>,
            )

            val jsonString   = """{"body": """ + response +"""}"""
            val ak2_response = Json.decodeFromString<AK2Response>(jsonString)
            val ak2_userid   = ak2_response.body[0].UserID
            val ak2_date     = ak2_response.body[0].Date
            val ak2_time     = ak2_response.body[0].Time

            val tagTextView:   TextView = findViewById(R.id.text0)
            val textViewText1: TextView = findViewById(R.id.text1)
            val textViewText2: TextView = findViewById(R.id.text2)
            tagTextView.text   = ak2_userid
            textViewText1.text = ak2_date
            textViewText2.text = ak2_time
        }
    }

    // AK2CreateAttendance API の結果を表示する
    fun displayMessage2(response: String) {
        runOnUiThread {

            // response (json) を受け取る data class
            @Serializable
            data class Attendance2(
                    val EmpNo:    String,
                    val Date:     String,
                    val Time:     String,
                    val TrxCode:  String,
                    val LastName: String,
                    val Remarks:  String,
            )
            @Serializable
            data class AK2Response2(
                    val statusCode: Int,
                    val body: Attendance2
            )

            // json を decode して data classにセットする
            val ak2_response = Json.decodeFromString<AK2Response2>(response)
            val ak2_empno    = ak2_response.body.EmpNo
            val ak2_date     = ak2_response.body.Date
            val ak2_time     = ak2_response.body.Time
            val ak2_trxcode  = ak2_response.body.TrxCode
            val ak2_lastname = ak2_response.body.LastName
            val ak2_remarks  = ak2_response.body.Remarks

            val tagTextView:   TextView = findViewById(R.id.text0)
            val textViewText1: TextView = findViewById(R.id.text1)
            val textViewText2: TextView = findViewById(R.id.text2)

            tagTextView.text   = ak2_empno   + "     " + ak2_lastname
            textViewText1.text = ak2_date    + "     " + ak2_time
            textViewText2.text = ak2_trxcode + "     " + ak2_remarks
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

    // AK2CreateAttendance API を呼ぶ
    private fun callAK2CreateAttendance(cardid: String) {
        val urlStr       = "https://iv822lhlh5.execute-api.ap-northeast-1.amazonaws.com/dev"  // Kintai Record の登録

        val dataStore: SharedPreferences = getSharedPreferences("DataStore", Context.MODE_PRIVATE)
        var deviceid = dataStore.getString("DeviceID", "NoData") // DataStoreからdeviceidを読み込む
        //val deviceid = "android001"

        val sendDataJson = "{\"CardID\": \""+ cardid + "\", \"Remarks\": \""+ deviceid + "\" }"
        val request = Request.Builder()
                .url(urlStr)
                .post(sendDataJson.toRequestBody(JSON_MEDIA))
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string().orEmpty() // Responseの読み出し
                Log.i("http response", responseBody)
                displayMessage2(responseBody)                         // responseを画面に表示
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("Error", e.toString())
            }
        })
    }

    // AK2QueryAttendance API を呼ぶ
    private fun callAK2QueryAttendance() {

        val urlStr       = "https://9628fzjhee.execute-api.ap-northeast-1.amazonaws.com/dev/"
        val sendDataJson = "{\"UserID\":\"99117\"}"      // todo: deviceidをkeyにする
        val request = Request.Builder()
                .url(urlStr)
                .post(sendDataJson.toRequestBody(JSON_MEDIA))
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string().orEmpty()
                Log.i("http response", responseBody)
                displayMessage(responseBody)
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("Error", e.toString())
            }
        })
    }

    private fun settings() {
        val intent = Intent(applicationContext, SettingsActivity::class.java)
        startActivity(intent)
    }

}


// old code
//    fun startRecordRequest_old(txn: String) {
//        val urlStr = "https://yhwmivrrp7.execute-api.ap-northeast-1.amazonaws.com/dev"  // Kintai Record の登録
//        val sendDataJson =
//                "{\"UserID\": \"99117\",\"TrxCode\":\""+ txn + "\",\"Date\": \"2022/02/22\",\"Time\": \"12:01:00\"," +
//                        "\"EmpNo\": \"99117\",\"FirstName\": \"ARS\",\"LastName\": \"lab\",\"Remarks\": \"test\" }	"  // Query data
//        // Requestを作成
//        val request = Request.Builder()
//                .url(urlStr)
//                .post(sendDataJson.toRequestBody(JSON_MEDIA))
//                .build()
//
//        client.newCall(request).enqueue(object : Callback {
//            override fun onResponse(call: Call, response: Response) {
//                val responseBody = response.body?.string().orEmpty() // Responseの読み出し
//                Log.i("http response", responseBody)
//                displayMessage(responseBody)                         // responseを画面に表示
//            }
//
//            override fun onFailure(call: Call, e: IOException) {
//                Log.e("Error", e.toString())
//            }
//        })
//    }

//val jsonString = """{ "name" : "taro", "age" : 10 }"""
// デシリアライズ
//val person = Json.decodeFromString<Person>(jsonString)
//println(person)
//            @Serializable
//            data class Person(
//                val name : String,
//                val age : Int
//            )

//            @Serializable
//            data class Fuga(
//                //val text: String,
//                val Date: String,
//                val UserID: String,
//                val Time: String,
//                val Remarks: String,
//                val TrxCode: String,
//            )
//            @Serializable
//            data class Hoge(
//                    //val text: String,
//                    //@SerialName("fuga")
//                    val body: List<Attendance>,
//            )
//val aK2Response = Json.decodeFromString<AK2Response>(response)
//println(aK2Response)

//println(response) // Hoge(text=hoge, num=3, flag=true)
