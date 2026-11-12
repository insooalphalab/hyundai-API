# Vehicle SDK는 리플렉션·바인더로 불릴 수 있어 통째로 남긴다
-keep class ai.pleos.** { *; }
-dontwarn ai.pleos.**
-dontwarn android.car.**

# 화면(HTML)이 window.Android.* 로 부르는 함수
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.seatroom.app.MainActivity$Bridge { *; }
