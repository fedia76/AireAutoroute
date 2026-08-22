# Règles ProGuard/R8 du projet.
#
# La release est minifiée (R8 en « full mode » depuis AGP 8). Les règles ci-dessous couvrent le
# seul mécanisme réflexif de l'application : kotlinx.serialization, qui lit les catalogues JSON
# des assets et écrit les contributions de l'utilisateur.

-keepattributes RuntimeVisibleAnnotations, AnnotationDefault, InnerClasses
-dontnote kotlinx.serialization.**

# Les sérialiseurs sont générés par le compilateur ; R8 doit conserver le point d'entrée
# `Companion.serializer()` de chaque type annoté @Serializable.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Cas des objets et des enums annotés @Serializable, dont le sérialiseur passe par INSTANCE.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
