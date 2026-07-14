# Payment Observer

## Ringkasan Project

Payment Observer adalah aplikasi Android internal FGTeam untuk menangkap notifikasi pembayaran
masuk dari aplikasi merchant, menyimpannya terlebih dahulu ke Room, lalu menyinkronkannya ke API
FGTeam. Aplikasi menggunakan Kotlin, Jetpack Compose, Room, WorkManager, OkHttp, dan Android
Keystore.

Pembayaran lokal tidak bergantung pada status login. Notification listener tetap menyimpan data
ketika perangkat offline, admin belum login, atau session telah habis. Pengiriman dilanjutkan setelah
session admin tersedia kembali.

## Sumber Notifikasi

Whitelist aplikasi disimpan pada tabel `observed_apps`. Semua sumber nonaktif secara default dan
harus diaktifkan secara independen dari UI.

| Package | Nama aplikasi | Sort order | Pola pembayaran masuk |
| --- | --- | ---: | --- |
| `com.shopeepay.id` | ShopeePay | 1 | Menyebut ShopeePay dan `diterima` |
| `com.shopee.id` | Shopee | 2 | Menyebut ShopeePay dan `diterima` |
| `com.shopeepay.merchant.id` | Shopee Partner | 3 | `Pembayaran sebesar Rp... diterima` |
| `id.co.bni.merchant` | BNI Merchant | 4 | `Transaksi Sebesar Rp ... telah berhasil` |

Contoh Shopee Partner:

```text
Title: Pembayaran sebesar Rp1.000 diterima
Body : Pembayaran sebesar Rp1.000 telah diterima pada transaksi 20820696399434593.
```

Parser menyimpan `Transaksi 20820696399434593` sebagai `sender` agar referensi tetap terlihat pada
audit.

Contoh BNI Merchant:

```text
Title: BNI Merchant
Body : Transaksi Sebesar Rp 1.000 dari BLU BCA telah berhasil
```

Parser menyimpan `BLU BCA` sebagai `sender`.

Nominal diambil dari teks `Rp`, seluruh separator dibuang, lalu disimpan sebagai `Long` dalam rupiah.
Notifikasi promo, transaksi gagal, package yang tidak terdaftar, dan aplikasi whitelist yang nonaktif
tidak disimpan.

## Autentikasi Admin

Aplikasi pertama kali dibuka pada layar login. Login menggunakan akun FGTeam melalui:

```http
POST /v1/users/login
Authorization: Basic <FGTeam basic auth>
Content-Type: application/json
```

Hanya response dengan `role === "admin"` yang diterima. Role `admin_user`, `user`, dan role lain
ditolak dengan pesan `Akun tidak memiliki akses Payment Observer`.

Session berisi:

- access token dan refresh token;
- expiry string dan expiry Unix kedua token;
- nama lengkap;
- role.

Session disimpan oleh `SessionManager` menggunakan `EncryptedSharedPreferences` dan `MasterKey`
AES-256 yang didukung Android Keystore. Password hanya hidup pada state login sementara dan tidak
pernah disimpan. Backup aplikasi juga dinonaktifkan.

Saat startup, session terenkripsi dipulihkan lalu divalidasi melalui:

```http
GET /v1/users/profile
Authorization: Bearer <access token>
```

Profile harus tetap aktif dan memiliki role tepat `admin`. Role berubah atau response strict API
`403` akan menghapus session, tetapi tidak menghapus pembayaran lokal.

Refresh menggunakan `POST /v1/users/token`. `PaymentObserverApi` melakukan pemeriksaan expiry
sebelum request, memakai `Mutex` agar refresh concurrent menjadi single-flight, menyimpan pasangan
token baru secara atomik, dan melakukan replay request maksimal satu kali setelah `401`. Refresh
token invalid atau kedaluwarsa menghapus session dan menghentikan retry hingga login berikutnya.

Konfigurasi base URL dan Basic auth berada pada `BuildConfig` di `app/build.gradle.kts`. Jangan
menulis token admin ke log, Room, Compose UI state, atau plain `SharedPreferences`.

## Alur Notifikasi dan Sinkronisasi

1. `ShopeePayNotificationListenerService` menerima `StatusBarNotification`. Nama class dipertahankan
   untuk kompatibilitas component Android, tetapi listener mendukung seluruh aplikasi whitelist.
2. Listener membaca title/body dan mengambil `LocalDateTime.now()` satu kali.
3. Package dicari pada Room dan hanya diproses jika `is_enabled = 1`.
4. `PaymentNotificationParser` memvalidasi pola aplikasi, nominal, dan status pembayaran masuk.
5. ID 32 karakter dibuat secara deterministik dari package, notification key, dan `postTime`.
6. Payment di-insert ke Room dengan `OnConflictStrategy.IGNORE`.
7. Setelah insert baru, WorkManager dijadwalkan dengan constraint jaringan aktif.
8. Worker mengirim seluruh record `is_sync_to_db = 0` memakai Bearer token admin.
9. Response sukses menandai payment sudah sync dan menyimpan remote match status.
10. Error jaringan menghasilkan exponential backoff. Session tidak tersedia menghasilkan worker
    sukses tanpa menghapus record; login berikutnya menjadwalkan ulang seluruh pending payment.

WorkManager menggunakan unique work `payment-observer-pending-sync`. Logout hanya menghapus session
terenkripsi. Tidak ada tombol, DAO, atau repository operation untuk menghapus transaksi.

## Integrasi API

### Ingest payment

```http
POST /v1/payment-observer/payments
Authorization: Bearer <admin access token>
```

Payload berasal langsung dari entity lokal:

```json
{
  "id": "32-character-deterministic-id",
  "sourceNotificationKey": "notification-key",
  "amount": 1000,
  "rawAmount": "Rp1.000",
  "sender": "BLU BCA",
  "title": "BNI Merchant",
  "body": "Transaksi Sebesar Rp 1.000 dari BLU BCA telah berhasil",
  "appName": "BNI Merchant",
  "packageName": "id.co.bni.merchant",
  "createdAt": "2026-07-14 10:15:00.123",
  "updatedAt": "2026-07-14 10:15:00.123"
}
```

API bersifat idempotent. ID dan data immutable yang sama menghasilkan `existing`; ID sama dengan
data berbeda menghasilkan `409` dan payment lokal tetap belum sync dengan error terakhir tersimpan.

### Rekonsiliasi manual

Tombol `Sinkronkan / Cek Status`:

1. mengirim seluruh payment yang belum sync;
2. membagi ID yang sudah sync menjadi batch maksimal 500;
3. memanggil `POST /v1/payment-observer/payments/status`;
4. memperbarui `remote_match_status`, `remote_order_id`, dan `remote_matched_at`.

Mapping status API ke Room:

| Status API | Status lokal |
| --- | --- |
| `not_synced` | `is_sync_to_db = 0` |
| `synced_unmatched` | `unmatched` |
| `reserved` | `reserved` |
| `matched` | `matched` dan menyimpan order `FG-...` |

Pengecekan remote order tidak berjalan periodik; hanya dijalankan oleh tombol manual.

## Database Room

Database: `payment_observer.db`
Room: 2.7.2
Schema version: 3

Migration yang wajib dipertahankan:

- `1 -> 2`: menambahkan metadata sinkronisasi pada `incoming_payments`;
- `2 -> 3`: menambahkan Shopee Partner dan BNI Merchant ke whitelist tanpa menghapus data lama.

Schema export tersedia di:

```text
app/schemas/com.fgteam.paymentobserver.data.PaymentObserverDatabase/
```

### Tabel `observed_apps`

```sql
CREATE TABLE observed_apps (
    package_name TEXT NOT NULL PRIMARY KEY,
    app_name TEXT NOT NULL,
    is_enabled INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
```

Toggle hanya mengubah `is_enabled` dan `updated_at`; `created_at` tidak berubah.

### Tabel `incoming_payments`

```sql
CREATE TABLE incoming_payments (
    id TEXT NOT NULL PRIMARY KEY,
    source_notification_key TEXT NOT NULL,
    amount INTEGER NOT NULL,
    raw_amount TEXT NOT NULL,
    sender TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    app_name TEXT NOT NULL,
    package_name TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    is_sync_to_db INTEGER NOT NULL DEFAULT 0,
    synced_at TEXT NULL,
    sync_attempt_count INTEGER NOT NULL DEFAULT 0,
    last_sync_attempt_at TEXT NULL,
    last_sync_error TEXT NULL,
    remote_match_status TEXT NULL,
    remote_order_id TEXT NULL,
    remote_matched_at TEXT NULL
);

CREATE INDEX index_payments_created_at
ON incoming_payments(created_at);

CREATE INDEX index_payments_package_created
ON incoming_payments(package_name, created_at);
```

Metadata sinkronisasi tidak boleh mengubah `created_at` atau `updated_at` asli pembayaran.

## Aturan Waktu

Seluruh waktu menggunakan `LocalDateTime` perangkat dan disimpan sebagai SQLite `TEXT`:

```text
yyyy-MM-dd HH:mm:ss.SSS
```

Contoh: `2026-07-14 10:15:00.123`.

Aturan:

- gunakan waktu lokal Asia/Jakarta/perangkat tanpa konversi UTC;
- pertahankan presisi millisecond tiga digit;
- gunakan waktu callback yang sama untuk `created_at` dan `updated_at` saat insert;
- `StatusBarNotification.postTime` hanya menjadi bagian ID deterministik;
- jangan menambah atau mengurangi tujuh jam;
- pertahankan core-library desugaring karena `minSdk = 21`.

## UI

UI berbasis Jetpack Compose dan memiliki dua state utama:

- layar login jika session admin tidak tersedia;
- halaman observer setelah session admin valid.

Halaman observer menampilkan:

- identitas dan status session admin tanpa mengekspos token;
- logout;
- total nominal pembayaran hari ini;
- status notification-listener permission;
- toggle independen untuk empat aplikasi whitelist;
- filter daftar per aplikasi;
- jumlah payment belum sync;
- tombol sinkronisasi/rekonsiliasi manual;
- nominal, pengirim/referensi, waktu, dan status remote setiap payment.

Filter package hanya memengaruhi daftar, bukan total harian.

## Struktur Implementasi

```text
auth/
  AdminSession.kt                 Model session internal
  SessionManager.kt               Encrypted token storage
data/
  IncomingPayment.kt              Entity payment Room
  ObservedApp.kt                  Entity whitelist dan package constants
  ObservedAppDao.kt               Query/toggle whitelist
  PaymentDao.kt                   Query dan metadata sinkronisasi
  PaymentNotificationParser.kt    Parser semua aplikasi merchant
  PaymentNotificationRepository.kt Repository Room
  PaymentObserverDatabase.kt      Room singleton, migration, dan seed
network/
  PaymentObserverApi.kt           Login, refresh, profile, ingest, status
service/
  ShopeePayNotificationListenerService.kt Notification listener component
sync/
  PaymentSyncRepository.kt        Orkestrasi upload dan rekonsiliasi
  PaymentSyncWorker.kt            WorkManager dan backoff
ui/payments/
  PaymentsUiState.kt              State UI tanpa token
  PaymentsViewModel.kt            Auth, Room flow, sync, dan UI actions
  PaymentsScreen.kt               Login dan observer Compose UI
```

## Build dan Pengujian

Versi utama:

| Komponen | Versi |
| --- | --- |
| Android Gradle Plugin | 8.8.0 |
| Gradle Wrapper | 8.10.2 |
| Kotlin | 2.0.21 |
| Room | 2.7.2 |
| WorkManager | 2.9.1 |
| OkHttp | 4.12.0 |
| `compileSdk` / `targetSdk` | 35 |
| `minSdk` | 21 |
| Java target | 11 |

Verifikasi lokal:

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Instrumentation test pada perangkat/emulator:

```shell
./gradlew connectedDebugAndroidTest
```

Test mencakup parser ShopeePay, Shopee Partner, dan BNI Merchant; notifikasi tidak valid; ID
deterministik; converter datetime; operasi Room; whitelist; Compose UI; serta migration Room 2 ke 3.

Schema JSON harus selalu ikut diperbarui ketika version Room berubah. Jangan memakai destructive
migration karena pembayaran yang belum sync tidak boleh hilang.
