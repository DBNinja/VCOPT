# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class org.openprinttag.model.**$$serializer { *; }
-keepclassmembers class org.openprinttag.model.** { *** Companion; }

# Jackson
-keep class com.fasterxml.jackson.databind.ObjectMapper { public <methods>; }
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

# SnakeYAML
-keep class org.yaml.snakeyaml.** { *; }

# Model classes
-keep class org.openprinttag.model.** { *; }
