package app.sidepeek

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Phone-brand specific "keep running in the background" settings. Besides Android's battery
 * optimisation, Xiaomi / OPPO / vivo / Huawei… also block apps from auto-starting, which
 * stops the notification listener after a while. Menu names differ between OS versions,
 * so the texts are guides, and [open] tries the brand's screen before falling back.
 */
object OemGuide {
    enum class Brand { SAMSUNG, XIAOMI, OPPO, VIVO, HUAWEI, ASUS, OTHER }

    class Guide(
        val brand: Brand,
        /** e.g. "小米" — shown in titles. */
        val name: String,
        /** Needs a separate auto-start / background switch besides battery optimisation. */
        val needsAutostart: Boolean,
        /** Where to go and what to switch on. */
        val steps: String,
        val button: String,
        val screens: List<ComponentName>,
    )

    fun brand(): Brand {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            "samsung" in m -> Brand.SAMSUNG
            "xiaomi" in m || "redmi" in m || "poco" in m -> Brand.XIAOMI
            "oppo" in m || "realme" in m || "oneplus" in m -> Brand.OPPO
            "vivo" in m || "iqoo" in m -> Brand.VIVO
            "huawei" in m || "honor" in m -> Brand.HUAWEI
            "asus" in m -> Brand.ASUS
            else -> Brand.OTHER
        }
    }

    private fun c(pkg: String, cls: String) = ComponentName(pkg, cls)

    fun guide(): Guide = when (brand()) {
        Brand.SAMSUNG -> Guide(Brand.SAMSUNG, "三星", false,
            "設定 → 電池 → 背景使用限制 → 永不休眠的應用程式 → 加入「Sidepeek」。",
            "打開三星的電池設定",
            listOf(c("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                c("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")))
        Brand.XIAOMI -> Guide(Brand.XIAOMI, "小米", true,
            "設定 → 應用程式 → 管理應用程式 →「Sidepeek」：打開「自動啟動」，「省電策略」選「無限制」。",
            "打開自動啟動設定",
            listOf(c("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
        Brand.OPPO -> Guide(Brand.OPPO, "OPPO／realme／OnePlus", true,
            "設定 → 應用程式 → 應用程式管理 →「Sidepeek」→ 耗電管理：允許「自動啟動」和「在背景執行」。",
            "打開自動啟動設定",
            listOf(c("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                c("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                c("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                c("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
        Brand.VIVO -> Guide(Brand.VIVO, "vivo", true,
            "設定 → 電池 → 背景耗電管理 →「Sidepeek」選「允許背景高耗電」；再到 i管家 → 應用管理 → 自啟動管理，打開「Sidepeek」。",
            "打開自啟動設定",
            listOf(c("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))
        Brand.HUAWEI -> Guide(Brand.HUAWEI, "華為／榮耀", true,
            "設定 → 電池 → 應用啟動管理 →「Sidepeek」改成手動管理，打開「自動啟動」「關聯啟動」「在背景活動」。",
            "打開應用啟動管理",
            listOf(c("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                c("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
        Brand.ASUS -> Guide(Brand.ASUS, "ASUS", true,
            "手機管家 → 自動啟動管理員 → 允許「Sidepeek」。",
            "打開自動啟動管理員",
            listOf(c("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")))
        Brand.OTHER -> Guide(Brand.OTHER, "", false, "", "打開 App 資訊", emptyList())
    }

    /** Opens the brand's screen, else this app's App info page. */
    fun open(ctx: Context): Boolean {
        for (cn in guide().screens) {
            try {
                ctx.startActivity(Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
            } catch (_: Exception) {}
        }
        return try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (_: Exception) { false }
    }
}
