# kotlinx.serialization: @Serializable sınıfların serializer'ları korunur.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class xyz.adilemree.dersdefteri.** {
    *** Companion;
}
-keepclasseswithmembers class xyz.adilemree.dersdefteri.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Widget düğmesinin sınıfı Glance tarafından adıyla oluşturulur.
-keep class xyz.adilemree.dersdefteri.widget.MarkLessonDoneAction { <init>(); }
