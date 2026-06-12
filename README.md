# Best Pinyin Keyboard

A from-scratch Android pinyin keyboard optimized for Mandarin as used in Taiwan.
Type pinyin, get 臺灣正體 — full sentences at a time — with English words mixed
into the candidates. Inspired by the discontinued Google Pinyin Input.

## Features

- Full-sentence conversion (Viterbi over a frequency dictionary): `woxiangchifan` → 我想吃飯
- Taiwan-first vocabulary: `yidali` → 義大利, `lese` → 垃圾, `ruanti` → 軟體, `jieyun` → 捷運
- Bilingual: English words appear as candidates while typing; 中/En key for one-tap switching
- Learns your word choices (on-device only, no network)
- Google Pinyin-style light theme, 26-key layout
- ~75k word Chinese dictionary (Taiwan Traditional) + 20k word English dictionary

## Build

Built automatically by GitHub Actions on every push — grab the APK from the
Actions tab → latest run → Artifacts. Or locally: `gradle :app:assembleDebug`.

## Dictionary pipeline

Generated from open data: word frequencies via wordfreq, Traditional/Taiwan
conversion via OpenCC (s2twp), pinyin readings via pypinyin, plus a hand-curated
Taiwan vocabulary boost list. No proprietary data.

## License

MIT. Dictionary data derives from wordfreq (MIT), OpenCC (Apache-2.0), and
pypinyin (MIT).
