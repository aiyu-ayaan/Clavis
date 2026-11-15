# Release rules go here. Strip logging once Log calls exist.

# ML Kit / Firebase components are created by reflection from manifest metadata; R8 would strip their constructors
# and text recognition would fail to initialise.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class * implements com.google.firebase.components.ComponentRegistrar
