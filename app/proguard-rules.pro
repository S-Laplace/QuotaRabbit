# ---- kotlinx.serialization (scoped to the app package, minimal keep) ----
# Generated serializers are referenced when a class is serialized; without these,
# R8 can strip the Companion/$$serializer and serialization fails at runtime with
# "Serializer for class 'X' is not found".
-keepclassmembers @kotlinx.serialization.Serializable class com.alice.quotarabbit.** {
    *** Companion;
    *** INSTANCE;
}
-if @kotlinx.serialization.Serializable class com.alice.quotarabbit.**
-keepclassmembers class com.alice.quotarabbit.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.alice.quotarabbit.**$$serializer { *; }

# Runtime annotation metadata used by serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
