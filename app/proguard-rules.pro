# kotlinx.serialization ---------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep the generated serializers for our @Serializable model classes.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
}
-keepclassmembers class **$* implements kotlinx.serialization.internal.GeneratedSerializer {
    *** serializer(...);
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# AppWidgetProviders are instantiated reflectively by the framework.
-keep class * extends android.appwidget.AppWidgetProvider { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
