-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
# JLayer (decoder MP2 delle registrazioni) legge sfd.ser accanto alle sue classi: nomi e pacchetto non vanno cambiati
-keep class javazoom.jl.decoder.** { *; }
-dontwarn javazoom.jl.player.**
-dontwarn javax.sound.**
