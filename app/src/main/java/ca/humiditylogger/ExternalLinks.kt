package ca.humiditylogger

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens an https page in the user's browser. Without a browser (or with it disabled) Android
 * throws [ActivityNotFoundException], which previously crashed the app; a toast is shown instead.
 *
 * @return true when a browser accepted the request.
 */
internal fun Context.openInBrowser(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (_: ActivityNotFoundException) {
    Toast.makeText(this, "No browser is available to open this link.", Toast.LENGTH_LONG).show()
    false
}
