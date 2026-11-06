# PhotoShelf

A read-only Android photo organizer for a Synology NAS. Sign in with your QuickConnect ID and PhotoShelf indexes every photo and video in your Photo folder, then lets you browse them several ways and save or share copies to your phone. It never changes anything on the NAS.

## What it does

**Ways to organize**
- **Timeline**: everything by date taken, grouped by day, month or year, with a year bar for jumping back in time and an "On this day" strip of past years.
- **Albums**: every top-level folder becomes an album (Dan Cell, Kayla Phone, FamPics, House Construction…), with year folders listed separately.
- **Folders**: browse the NAS folder tree exactly as it is, with breadcrumbs and photo counts.
- **Favorites**: heart any photo. Favorites are stored on the phone only.
- **Filters on every view**: All / Photos / Videos / Screenshots, plus search by file or folder name, sort (newest, oldest, name) and 2–6 photos per row.

**Getting photos off the NAS (copies only)**
- **Save** the original to the phone (Pictures/PhotoShelf, videos in Movies/PhotoShelf).
- **Copy to…** any folder you pick (SD card, Downloads, a cloud drive folder).
- **Share** to any app.
- **Copy** a photo to the clipboard to paste into another app.
- Long-press to select many at once. Tap "Select" on a date heading to select that whole day, month or year.

**Viewer**: swipe between photos, pinch or double-tap to zoom, full resolution on Wi-Fi or at home (tap **HD** on mobile data), details sheet, "Show in folder", and videos open in your phone's video player.

## Dates

Photos are placed on the timeline by, in order:
1. the camera date inside the photo (EXIF), read once for files that need it,
2. a date in the file name (`20190704_123456.jpg`, `Screenshot_20190704-…`, `PXL_…`),
3. the file's modified date.

iPhone files like `IMG_1234.HEIC` have no date in the name, so after the first scan the app reads a small part of each one (on Wi-Fi by default) to get the real date. You'll see "Reading photo dates" while it works. This happens once per photo.

## Read-only guarantee

The app only uses these Synology APIs: `SYNO.API.Info` (query), `SYNO.API.Auth` (login), `SYNO.FileStation.List` (list_share, list), `SYNO.FileStation.Thumb` (get) and `SYNO.FileStation.Download` (download). There is no code to upload, rename, move, edit or delete on the NAS.

For extra safety, create a DSM user for the app with **Read only** permission on the Photo shared folder and File Station access (DSM > Control Panel > User & Group, then Application Privileges).

## How it connects

You enter your QuickConnect ID (or an address). Like DS file, the app asks Synology's QuickConnect service where the NAS is, then tries, in order: the home-network address, any direct internet address, and finally the QuickConnect relay. It picks the fastest route that answers and re-checks automatically when you change networks (for example leaving home Wi-Fi). On the home network it accepts the NAS's built-in self-signed certificate; every other route requires a valid certificate.

2-step verification is supported: enter the code once and the phone is remembered.

Your password is encrypted with the Android Keystore and never leaves the phone except to sign in to your own NAS.

## Building the APK

### GitHub Actions (no Android Studio needed)
1. Push this folder to a GitHub repository (private is recommended).
2. The **Build PhotoShelf APK** workflow runs automatically and publishes `PhotoShelf.apk` to a release named **app**.
3. On the phone, download `PhotoShelf.apk` from that release and open it. Allow "Install unknown apps" for your browser when asked.

Every push builds a new version that installs over the old one (the app is signed with the fixed key in `app/photoshelf.keystore`; keep the repository private).

### Android Studio
Open the folder, let Gradle sync, then Run, or Build > Build APK(s).

Requirements: Android 10 or newer.
