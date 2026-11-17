# Regulile pentru Hilt, Compose, OkHttp și kotlinx.serialization vin din bibliotecile respective.

# Decodorul audio FFmpeg (Media3) e încărcat prin reflecție de DefaultRenderersFactory.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { <init>(...); }
-keep class androidx.media3.decoder.ffmpeg.FfmpegLibrary { *; }
