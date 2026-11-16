# R8 rules for the release build. Room, Compose and kotlinx.serialization ship their own consumer
# rules; keep the @Serializable classes' generated serializers reachable for reflection-free lookup.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
