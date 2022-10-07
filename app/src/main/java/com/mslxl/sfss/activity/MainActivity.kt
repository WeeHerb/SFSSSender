package com.mslxl.sfss.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.isDigitsOnly
import com.mslxl.sfss.R

class MainActivity : AppCompatActivity() {

    private val pattern = "\\d{1,3}(\\.\\d{1,3}){3}".toPattern().toRegex()
    private lateinit var textInputIp: EditText
    private lateinit var textInputPort: EditText
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textInputIp = findViewById(R.id.text_input_ip)
        textInputPort = findViewById(R.id.text_input_port)

        findViewById<Button>(R.id.btn_connect).setOnClickListener {
            if (textInputPort.text.isDigitsOnly() && textInputIp.text.toString().matches(pattern)){
                val intent = Intent(this, ScannerActivity::class.java)
                intent.putExtra(ScannerActivity.BUNDLE_IP_KEY, textInputIp.text.toString())
                intent.putExtra(ScannerActivity.BUNDLE_PORT_KEY, textInputPort.text.toString().toInt())
                startActivity(intent)
            }else{
                Toast.makeText(this, R.string.tip_format_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
}