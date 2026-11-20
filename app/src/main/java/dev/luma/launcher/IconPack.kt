package dev.luma.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser

/** An installed icon pack (ADW / Nova / Apex standard). */
data class IconPackInfo(val packageName: String, val label: String)

private val PACK_ACTIONS = listOf(
    "org.adw.launcher.THEMES",
    "com.novalauncher.THEME",
    "com.gau.go.launcherex.theme",
    "org.adw.launcher.icons.ACTION_PICK_ICON",
)

fun installedIconPacks(ctx: Context): List<IconPackInfo> {
    val pm = ctx.packageManager
    return PACK_ACTIONS
        .flatMap { pm.queryIntentActivities(Intent(it), 0) }
        .map { it.activityInfo.packageName }
        .distinct()
        .map { IconPackInfo(it, runCatching { pm.getApplicationInfo(it, 0).loadLabel(pm).toString() }.getOrDefault(it)) }
        .sortedBy { it.label.lowercase() }
}

/** A loaded icon pack: maps app components to drawables inside the pack. */
class IconPack private constructor(private val res: Resources, private val pkg: String, private val map: Map<String, String>) {

    fun iconFor(component: ComponentName): Drawable? {
        val name = map[component.flattenToString()] ?: map[component.flattenToShortString()] ?: return null
        val id = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 }
            ?: res.getIdentifier(name, "mipmap", pkg).takeIf { it != 0 } ?: return null
        return ResourcesCompat.getDrawable(res, id, null)
    }

    companion object {
        fun load(ctx: Context, pkg: String): IconPack? = runCatching {
            val res = ctx.packageManager.getResourcesForApplication(pkg)
            val parser: XmlPullParser = res.assets.let { assets ->
                val id = res.getIdentifier("appfilter", "xml", pkg)
                if (id != 0) res.getXml(id)
                else android.util.Xml.newPullParser().apply { setInput(assets.open("appfilter.xml"), null) }
            }
            val map = HashMap<String, String>()
            var type = parser.eventType
            while (type != XmlPullParser.END_DOCUMENT) {
                if (type == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (comp != null && drawable != null && comp.startsWith("ComponentInfo{") && comp.endsWith("}")) {
                        val cn = ComponentName.unflattenFromString(comp.substring(14, comp.length - 1))
                        if (cn != null) {
                            map.putIfAbsent(cn.flattenToString(), drawable)
                            map.putIfAbsent(cn.flattenToShortString(), drawable)
                        }
                    }
                }
                type = parser.next()
            }
            IconPack(res, pkg, map)
        }.getOrNull()
    }
}
