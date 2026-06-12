package io.github.sashachh.bestpinyin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

enum class KeyType { CHAR, SHIFT, BACKSPACE, SYMBOLS, ABC, MODE, SPACE, ENTER, COMMA, PERIOD }

class Key(val type: KeyType, val label: String = "", val weight: Float = 1f) {
    var rect = RectF()
}

interface KeyListener {
    fun onKey(key: Key)
}

class KeyboardView(context: Context) : View(context) {

    var listener: KeyListener? = null
    var chineseMode = true
    var shifted = false
    var symbolsPage = false

    private val rowHeightDp = 56f
    private val density = context.resources.displayMetrics.density

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#202124")
    }
    private val bgColor = Color.parseColor("#E8EAED")
    private val keyColor = Color.WHITE
    private val specialColor = Color.parseColor("#DADCE0")
    private val pressedColor = Color.parseColor("#C6CACD")
    private val accentColor = Color.parseColor("#1A73E8")

    private var pressed: Key? = null
    private val handler = Handler(Looper.getMainLooper())
    private var repeating = false
    private val repeater = object : Runnable {
        override fun run() {
            val k = pressed
            if (k != null && k.type == KeyType.BACKSPACE) {
                listener?.onKey(k)
                handler.postDelayed(this, 50L)
            }
        }
    }

    private val letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val symbolRows = listOf("1234567890", "@#$%&-+()/", "*\"':;!?~")

    private fun buildRows(): List<List<Key>> {
        val rows = mutableListOf<List<Key>>()
        if (!symbolsPage) {
            rows.add(letterRows[0].map { Key(KeyType.CHAR, it.toString()) })
            // home row has 9 keys; layoutKeys() centers it automatically
            rows.add(letterRows[1].map { Key(KeyType.CHAR, it.toString()) })
            val r3 = mutableListOf<Key>()
            r3.add(Key(KeyType.SHIFT, "⇧", 1.5f))
            letterRows[2].forEach { r3.add(Key(KeyType.CHAR, it.toString())) }
            r3.add(Key(KeyType.BACKSPACE, "⌫", 1.5f))
            rows.add(r3)
        } else {
            rows.add(symbolRows[0].map { Key(KeyType.CHAR, it.toString()) })
            rows.add(symbolRows[1].map { Key(KeyType.CHAR, it.toString()) })
            val r3 = mutableListOf<Key>()
            symbolRows[2].forEach { r3.add(Key(KeyType.CHAR, it.toString())) }
            r3.add(Key(KeyType.BACKSPACE, "⌫", 1.5f))
            rows.add(r3)
        }
        val bottom = mutableListOf<Key>()
        if (!symbolsPage) {
            bottom.add(Key(KeyType.SYMBOLS, "?123", 1.5f))
        } else {
            bottom.add(Key(KeyType.ABC, "ABC", 1.5f))
        }
        bottom.add(Key(KeyType.COMMA, if (chineseMode) "，" else ",", 1f))
        bottom.add(Key(KeyType.MODE, if (chineseMode) "中" else "En", 1f))
        bottom.add(Key(KeyType.SPACE, "", 3.5f))
        bottom.add(Key(KeyType.PERIOD, if (chineseMode) "。" else ".", 1f))
        bottom.add(Key(KeyType.ENTER, "↵", 1.5f))
        rows.add(bottom)
        return rows
    }

    private var rows: List<List<Key>> = buildRows()

    fun refresh() {
        rows = buildRows()
        layoutKeys()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (rowHeightDp * density * 4).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys()
    }

    private fun layoutKeys() {
        if (width == 0) return
        val rowH = height / 4f
        val gap = 3f * density
        rows.forEachIndexed { ri, row ->
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
            // center rows whose total weight < 10 (e.g. the 9-key home row)
            val unit = width / maxOf(totalWeight, 10f)
            var x = (width - unit * totalWeight) / 2f
            val top = ri * rowH + gap
            val bottom = (ri + 1) * rowH - gap
            for (key in row) {
                val kw = unit * key.weight
                key.rect = RectF(x + gap, top, x + kw - gap, bottom)
                x += kw
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bgColor)
        val r = 8f * density
        for (row in rows) {
            for (key in row) {
                if (key.rect.width() <= 0) continue
                keyPaint.color = when {
                    key === pressed -> pressedColor
                    key.type == KeyType.SHIFT && shifted -> accentColor
                    key.type == KeyType.CHAR || key.type == KeyType.SPACE ||
                        key.type == KeyType.COMMA || key.type == KeyType.PERIOD -> keyColor
                    else -> specialColor
                }
                canvas.drawRoundRect(key.rect, r, r, keyPaint)
                val label = displayLabel(key)
                if (label.isNotEmpty()) {
                    textPaint.textSize = if (label.length > 2) 14f * density else 20f * density
                    textPaint.color = if (key.type == KeyType.SHIFT && shifted) Color.WHITE
                    else Color.parseColor("#202124")
                    val y = key.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(label, key.rect.centerX(), y, textPaint)
                }
            }
        }
    }

    private fun displayLabel(key: Key): String {
        if (key.type == KeyType.CHAR && !symbolsPage && key.label.length == 1 && key.label[0].isLetter()) {
            return if (shifted && !chineseMode) key.label.uppercase() else key.label
        }
        return key.label
    }

    private fun findKey(x: Float, y: Float): Key? {
        for (row in rows) {
            for (key in row) {
                if (key.rect.contains(x, y)) return key
            }
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = findKey(event.x, event.y)
                if (pressed?.type == KeyType.BACKSPACE) {
                    repeating = true
                    handler.postDelayed(repeater, 400L)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(repeater)
                val key = pressed
                pressed = null
                invalidate()
                if (key != null) listener?.onKey(key)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(repeater)
                pressed = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
