# SkinShot (tahap 1: Screenshot Manager)

Mod Fabric client-only buat **Minecraft 26.3**. Gak ada hubungannya sama login/akun,
jadi jalan di akun original maupun offline/cracked.

## Fitur tahap 1
- Tekan **K** (bisa diganti di Options > Controls) untuk buka Screenshot Manager
- Daftar screenshot terbaru dulu, per halaman
- Klik nama file = buka di viewer bawaan OS
- Hapus (klik 2x: "Hapus" -> "Yakin?")
- Tombol Buka Folder

## Build
Butuh: **JDK 25** dan **Gradle 9.4+** (atau buka foldernya di IntelliJ IDEA 2025.3+).

```
gradle wrapper --gradle-version latest     # sekali aja, bikin ./gradlew
./gradlew build                            # hasil: build/libs/skinshot-0.1.0.jar
./gradlew runClient                        # tes langsung di dev environment
```

## Build tanpa install apa-apa (GitHub Actions)
1. Bikin repo baru di GitHub, upload semua isi folder ini (termasuk folder `.github`).
2. Buka tab **Actions** > workflow **Build** (jalan otomatis tiap upload).
3. Kalau sukses, download artifact **skinshot-jar** di bawah halaman run-nya.
   Pake jar `skinshot-0.1.0.jar` (bukan yang `-sources`).
4. Kalau gagal, buka log langkah `gradle build`, copy pesan error-nya ke chat.

## Pasang
Taruh `skinshot-0.1.0.jar` + **Fabric API 0.165.0+26.3** di folder `mods`
(Fabric Loader 0.20.1+).

## Kalau build error
Versi di `gradle.properties` dicek ke https://fabricmc.net/develop (pilih 26.3) dan samain
kalau beda. API Minecraft 26.x sering berubah antar versi minor; kalau ada error compile,
copy-paste pesannya apa adanya.
