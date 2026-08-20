# Règles ProGuard du projet.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.aireautoroute.app.data.** {
    *** Companion;
}
