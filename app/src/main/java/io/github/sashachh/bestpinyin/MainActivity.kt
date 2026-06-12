package io.github.sashachh.bestpinyin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Best Pinyin Keyboard\n\n台灣拼音鍵盤 — 中英雙語輸入\n\n1. Enable the keyboard\n2. Switch to it\n3. Type!"
            textSize = 18f
        })
        root.addView(Button(this).apply {
            text = "1 · Enable keyboard"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        })
        root.addView(Button(this).apply {
            text = "2 · Switch keyboard"
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        })
        setContentView(root)
    }
}
