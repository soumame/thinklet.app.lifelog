# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LifeLog is a specialized Android application for the THINKLET device that enables up to 8 hours of continuous camera capture and optional 5-channel audio recording. The app captures periodic photos and saves them as JPEG files with EXIF metadata, with optional microphone recording capability.

## Development Setup

### Prerequisites
- Android Studio Ladybug Feature Drop 2024.2.2+
- GitHub personal access token with `read:packages` permission

### Required Configuration
Create `local.properties` with GitHub credentials:
```properties
TOKEN=<github_personal_access_token>
USERNAME=<github_username>
```

This is required to access the private `ai.fd.thinklet:sdk-audio` package from GitHub Packages.

## Common Commands

### Build & Install
```bash
# Install debug build to device
./gradlew installDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean

# Run tests (no tests currently exist)
./gradlew test
```

### Hardware Key Configuration
Deploy pre-configured key setups:

```bash
# 1440x1080 without microphone
adb push keyConfigs/1440_1080.json /sdcard/Android/data/ai.fd.thinklet.app.launcher/files/key_config.json && adb reboot

# 2592x1944 with microphone
adb push keyConfigs/2592_1944_withMic.json /sdcard/Android/data/ai.fd.thinklet.app.launcher/files/key_config.json && adb reboot

# 1440x1080 with S3 upload (edit S3 credentials first)
adb push keyConfigs/1440_1080_withS3.json /sdcard/Android/data/ai.fd.thinklet.app.launcher/files/key_config.json && adb reboot
```

### Device Interaction
```bash
# Switch to MTP mode for file access
adb shell svc usb setFunction mtp true

# Use scrcpy for remote device control
scrcpy
```

## Architecture

The project follows Clean Architecture with modular design:

- **app/** - Main application module (UI layer with Jetpack Compose)
- **data/** - Data layer module (repositories and implementations)

### Key Technologies
- **Language**: Kotlin with Coroutines
- **UI**: Jetpack Compose with Material 3
- **DI**: Dagger Hilt
- **Camera**: CameraX 1.4.1
- **Audio**: Custom THINKLET SDK (`ai.fd.thinklet:sdk-audio:0.1.6`)
- **GIF Encoding**: androidndkgif 1.0.1

### Core Repositories (in data module)
- `SnapShotRepository` - Camera capture functionality
- `JpegSaverRepository` - JPEG file saving with EXIF metadata
- `MicRepository` - 5-channel microphone recording
- `AudioCaptureRepository` - Audio processing and file management
- `FileSelectorRepository` - File management and MTP deployment
- `TimerRepository` - Interval timing for periodic capture
- `VibrateRepository` - Device vibration feedback

## Application Parameters

The app accepts launch parameters via Android Intent extras:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `longSide` | String/Int | 640 | Image long side resolution |
| `shortSide` | String/Int | 480 | Image short side resolution |
| `intervalSeconds` | String/Int | 300 | Capture interval (minimum 10 seconds) |
| `enabledMic` | String/Boolean | false | Enable microphone recording |
| `s3Enabled` | String/Boolean | false | Enable S3 upload |
| `s3BucketName` | String | null | S3 bucket name |
| `s3Region` | String | null | AWS region (e.g., "ap-northeast-1") |
| `s3AccessKey` | String | null | AWS access key |
| `s3SecretKey` | String | null | AWS secret key |
| `s3Endpoint` | String | null | Custom S3 endpoint URL (for S3-compatible services) |

## File Output Structure

### Local Storage
Files are saved to: `/DCIM/lifelog/YYYYMMDD/`
- JPEG files: Saved with filename format `YYYY-MM-DD-HHMMSS.jpg` including EXIF metadata
- Audio files: Max 1GB per file before rotating to new files

### S3 Storage (Optional)
When S3 is enabled, JPEG files are automatically uploaded to:
- S3 path: `lifelog/YYYY/MM/DD/YYYY-MM-DD-HHMMSS.jpg`
- **S3-compatible services**: Supports AWS S3, MinIO, Cloudflare R2, and other S3-compatible storage
- **Custom endpoints**: Use `s3Endpoint` parameter for non-AWS services (leave empty for AWS S3)
- **WiFi-only uploads**: Files are only uploaded when connected to WiFi (no mobile data usage)
- **Upload queue**: Files captured without WiFi are queued and uploaded when WiFi becomes available
- **Offline resilience**: Local files are preserved even if S3 upload fails
- **Queue persistence**: Upload queue survives app restarts

### JPEG EXIF Metadata
Each JPEG file includes the following EXIF metadata:
- DateTime, DateTimeOriginal, DateTimeDigitized: Current timestamp
- Make: "THINKLET"
- Model: "LifeLog Camera" 
- Software: "LifeLog v1.0"
- Orientation: Normal
- ColorSpace: sRGB

## Development Notes

### Testing
- No unit tests currently exist in the codebase
- Manual testing on THINKLET hardware required
- All permissions must be granted manually via device settings before use

### File Upload Events
To extend functionality when files are saved, modify the saved event handlers in:
- `SnapshotUseCase.kt` (line ~24-27): `jpegSaverRepository.savedEvent`
- `MicRecordUseCase.kt` (line ~51-54): `audioCaptureRepository.savedEvent`

### Namespace
- Main app: `ai.fd.thinklet.app.lifelog`
- Data module: `ai.fd.thinklet.library.lifelog.data`