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
        /** e.g. ctx.getString(R.string.oem_xiaomi) — shown in titles. */
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

    fun guide(ctx: android.content.Context): Guide = when (brand()) {
        Brand.SAMSUNG -> Guide(Brand.SAMSUNG, ctx.getString(R.string.oem_samsung), false,
            ctx.getString(R.string.oem_samsung_steps),
            ctx.getString(R.string.oem_samsung_btn),
            listOf(c("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                c("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")))
        Brand.XIAOMI -> Guide(Brand.XIAOMI, ctx.getString(R.string.oem_xiaomi), true,
            ctx.getString(R.string.oem_xiaomi_steps),
            ctx.getString(R.string.oem_autostart_btn),
            listOf(c("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
        Brand.OPPO -> Guide(Brand.OPPO, ctx.getString(R.string.oem_oppo), true,
            ctx.getString(R.string.oem_oppo_steps),
            ctx.getString(R.string.oem_autostart_btn),
            listOf(c("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                c("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                c("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                c("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
        Brand.VIVO -> Guide(Brand.VIVO, "vivo", true,
            ctx.getString(R.string.oem_vivo_steps),
            ctx.getString(R.string.oem_vivo_btn),
            listOf(c("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))
        Brand.HUAWEI -> Guide(Brand.HUAWEI, ctx.getString(R.string.oem_huawei), true,
            ctx.getString(R.string.oem_huawei_steps),
            ctx.getString(R.string.oem_huawei_btn),
            listOf(c("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                c("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
        Brand.ASUS -> Guide(Brand.ASUS, "ASUS", true,
            ctx.getString(R.string.oem_asus_steps),
            ctx.getString(R.string.oem_asus_btn),
            listOf(c("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")))
        Brand.OTHER -> Guide(Brand.OTHER, "", false, "", ctx.getString(R.string.wiz_open_app_info), emptyList())
    }

    /** Opens the brand's screen, else this app's App info page. */
    fun open(ctx: Context): Boolean {
        for (cn in guide(ctx).screens) {
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
