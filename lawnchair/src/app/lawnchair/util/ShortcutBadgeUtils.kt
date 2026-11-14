/*
 * Copyright 2026, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import app.lawnchair.gestures.ui.LawnchairShortcutActivity

object ShortcutBadgeUtils {
    private val webSchemes = setOf("http", "https")

    @Volatile
    private var browserPackages: Set<String>? = null

    /** Legacy shortcuts keep their intent; the system strips it from every other shortcut. */
    @JvmStatic
    fun isWebShortcut(intent: Intent?): Boolean {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return false
        val scheme = intent.data?.scheme ?: return false
        return scheme.lowercase() in webSchemes
    }

    @JvmStatic
    fun shouldSkipSourceBadge(context: Context, shortcutInfo: ShortcutInfo?): Boolean {
        if (shortcutInfo == null) return false
        return isWebShortcut(shortcutInfo.intent) ||
            isBrowserShortcut(context, shortcutInfo) ||
            LawnchairShortcutActivity.shouldSkipShortcutBadge(context, shortcutInfo)
    }

    // A pinned shortcut's intent never reaches the launcher, so the owner is the only signal left:
    // browsers publish "add to home screen" pages as pinned shortcuts, while shortcut-menu entries
    // stay dynamic or manifest and keep their badge.
    private fun isBrowserShortcut(context: Context, shortcutInfo: ShortcutInfo): Boolean {
        val mightBePinned = shortcutInfo.isPinned ||
            (!shortcutInfo.isDynamic && !shortcutInfo.isManifestShortcut)
        if (!mightBePinned) return false
        val packageName = shortcutInfo.`package` ?: return false
        return getBrowserPackages(context).contains(packageName)
    }

    private fun getBrowserPackages(context: Context): Set<String> {
        browserPackages?.let { return it }
        return synchronized(this) {
            browserPackages ?: buildBrowserPackages(context).also { browserPackages = it }
        }
    }

    private fun buildBrowserPackages(context: Context): Set<String> {
        val packageManager = context.packageManager
        // A hostless URL only matches apps that can open any web page, i.e. browsers.
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val packages = HashSet<String>()
        packageManager.queryIntentActivities(webIntent, 0)
            .mapTo(packages) { it.activityInfo.packageName }
        packageManager.resolveActivity(webIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
            ?.let(packages::add)
        return packages
    }
}
