# Sidepeek R8 rules.
# Manifest components (services, activities, receivers) and views used from XML are kept
# automatically by AGP. Keep the custom view's constructors used by layout inflation.
-keep class app.sidepeek.GuideVideoView { public <init>(android.content.Context, android.util.AttributeSet); }
# Readable crash traces in the diagnostics log.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
