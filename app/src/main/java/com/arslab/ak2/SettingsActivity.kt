package com.arslab.ak2

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
//import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
//import androidx.preference.PreferenceFragmentCompat
import android.content.SharedPreferences

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        // "dataStore"という名前でSharedPreferencesインスタンス（共有の環境設定域）を生成
        val dataStore: SharedPreferences = getSharedPreferences("DataStore", Context.MODE_PRIVATE)
        var ak2_deviceid = dataStore.getString("DeviceID", "NoData") // DataStoreからdeviceidを読み込む
        val editText1: EditText = findViewById(R.id.edit_text)
        editText1.setText(ak2_deviceid)                              // deviceidを表示する

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val btn_ok: Button = findViewById(R.id.btn_ok)
        btn_ok.setOnClickListener {
            ak2_deviceid = editText1.text.toString()
            val editor = dataStore.edit()
            editor.putString("DeviceID", ak2_deviceid)    // 入力文字列を書き込む
            editor.apply()                                // 非同期書込み。同期するときはcommit()を使う
            finish()                                      // mainActivityに戻る
        }

    }

}