package io.github.sashachh.bestpinyin

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class BestPinyinService : InputMethodService(), KeyListener {

    private lateinit var engine: PinyinEngine
    private var keyboard: KeyboardView? = null
    private var candidateBar: LinearLayout? = null
    private var candidateScroll: HorizontalScrollView? = null

    private val composing = StringBuilder()
    private var chineseMode = true
    private var shifted = false

    override fun onCreate() {
        super.onCreate()
        engine = PinyinEngine(this)
        engine.loadAsync {
            keyboard?.post { updateCandidates() }
        }
    }

    override fun onCreateInputView(): View {
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F1F3F4"))
        }
        candidateBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        candidateScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(candidateBar)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (44 * density).toInt(),
            )
        }
        root.addView(
            candidateScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * density).toInt()),
        )
        keyboard = KeyboardView(this).apply {
            listener = this@BestPinyinService
            chineseMode = this@BestPinyinService.chineseMode
        }
        root.addView(
            keyboard,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        return root
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        clearComposing()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        composing.setLength(0)
    }

    // ---- key handling ----

    override fun onKey(key: Key) {
        val kb = keyboard ?: return
        when (key.type) {
            KeyType.CHAR -> handleChar(key.label)
            KeyType.SHIFT -> {
                if (chineseMode) {
                    // Google Pinyin behavior: Shift jumps to English
                    commitRawComposing()
                    chineseMode = false
                    shifted = true
                } else {
                    shifted = !shifted
                }
                syncKeyboard()
            }
            KeyType.BACKSPACE -> handleBackspace()
            KeyType.SYMBOLS -> {
                kb.symbolsPage = true
                kb.refresh()
            }
            KeyType.ABC -> {
                kb.symbolsPage = false
                kb.refresh()
            }
            KeyType.MODE -> {
                commitRawComposing()
                chineseMode = !chineseMode
                shifted = false
                syncKeyboard()
            }
            KeyType.SPACE -> handleSpace()
            KeyType.ENTER -> handleEnter()
            KeyType.COMMA -> commitPunct(if (chineseMode) "，" else ",")
            KeyType.PERIOD -> commitPunct(if (chineseMode) "。" else ".")
        }
    }

    private fun handleChar(label: String) {
        if (label.isEmpty()) return
        val c = label[0]
        if (chineseMode && c.isLetter()) {
            composing.append(c.lowercaseChar())
            currentInputConnection?.setComposingText(composing, 1)
            updateCandidates()
        } else {
            val text = if (!chineseMode && shifted && c.isLetter()) label.uppercase() else label
            currentInputConnection?.commitText(text, 1)
            if (shifted) {
                shifted = false
                syncKeyboard()
            }
        }
    }

    private fun handleBackspace() {
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - 1)
            if (composing.isEmpty()) {
                currentInputConnection?.commitText("", 1)
                clearComposing()
            } else {
                currentInputConnection?.setComposingText(composing, 1)
                updateCandidates()
            }
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    private fun handleSpace() {
        if (composing.isNotEmpty()) {
            val cands = engine.candidates(composing.toString())
            if (cands.isNotEmpty()) {
                pickCandidate(cands[0])
            } else {
                commitRawComposing()
            }
        } else {
            currentInputConnection?.commitText(" ", 1)
        }
    }

    private fun handleEnter() {
        if (composing.isNotEmpty()) {
            commitRawComposing()
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun commitPunct(p: String) {
        if (composing.isNotEmpty()) {
            val cands = engine.candidates(composing.toString())
            if (cands.isNotEmpty()) pickCandidate(cands[0]) else commitRawComposing()
        }
        currentInputConnection?.commitText(p, 1)
    }

    private fun commitRawComposing() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.commitText(composing.toString(), 1)
            clearComposing()
        }
    }

    private fun clearComposing() {
        composing.setLength(0)
        currentInputConnection?.finishComposingText()
        candidateBar?.removeAllViews()
        candidateScroll?.scrollTo(0, 0)
    }

    // ---- candidates ----

    private fun pickCandidate(c: Candidate) {
        val code = composing.substring(0, c.consumed.coerceAtMost(composing.length))
        currentInputConnection?.commitText(c.text, 1)
        if (!c.isSentence) engine.recordSelection(code, c.text)
        if (c.consumed >= composing.length) {
            clearComposing()
        } else {
            composing.delete(0, c.consumed)
            currentInputConnection?.setComposingText(composing, 1)
            updateCandidates()
        }
    }

    private fun updateCandidates() {
        val bar = candidateBar ?: return
        bar.removeAllViews()
        candidateScroll?.scrollTo(0, 0)
        if (composing.isEmpty()) return
        val density = resources.displayMetrics.density
        val cands = engine.candidates(composing.toString())
        cands.forEachIndexed { i, c ->
            val tv = TextView(this).apply {
                text = c.text
                textSize = 18f
                setTextColor(if (i == 0) Color.parseColor("#1A73E8") else Color.parseColor("#202124"))
                setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                gravity = Gravity.CENTER
                setOnClickListener { pickCandidate(c) }
            }
            bar.addView(
                tv,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
    }

    private fun syncKeyboard() {
        keyboard?.let {
            it.chineseMode = chineseMode
            it.shifted = shifted
            it.refresh()
        }
    }
}
