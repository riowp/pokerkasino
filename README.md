# Casino Klasik

Kumpulan game kasino klasik **offline** untuk Android, dengan chip virtual yang tersimpan lokal (SharedPreferences). Tidak butuh koneksi internet.

## Game
- **Blackjack** — hit / stand / double, blackjack bayar 3:2, bandar tarik sampai 17.
- **Roulette (Eropa, 0–36)** — merah/hitam, ganjil/genap, 1-18/19-36, dozen (2:1), angka tunggal (35:1).
- **Slot Machine** — 3 reel, tabel bayaran, simbol langka bernilai lebih tinggi.
- **Dadu (Sic Bo)** — Besar/Kecil (kalah jika triple) dan taruhan angka tunggal.

## Fitur
- Saldo awal 5.000 chip, bonus harian +1.000, tombol reset.
- Material/AppCompat dark theme, semua UI meja hijau kustom.
- 100% offline, tanpa iklan, tanpa transaksi uang nyata.

## Build APK via GitHub Actions
1. Buat repo baru di GitHub, upload seluruh isi folder ini.
2. Buka tab **Actions** → workflow **Build APK** jalan otomatis (atau klik *Run workflow*).
3. Setelah selesai, unduh artifact **casino-klasik-debug** → `app-debug.apk`.
4. Install di HP (aktifkan "Install dari sumber tidak dikenal").

Package: `com.rio.casinoklasik` · minSdk 24 · Kotlin + ViewBinding.
