package io.github.radumariusi.akvodhub.baselineprofile

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CatalogBaselineProfile {
    @get:Rule
    val rule = BaselineProfileRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun seedCatalog() {
        instrumentation.context.startActivity(
            Intent()
                .setComponent(ComponentName(PACKAGE_NAME, "$PACKAGE_NAME.ProfileCatalogActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        check(device.wait(Until.hasObject(By.text("Catalog de profilare pregătit")), TIMEOUT_MS))
    }

    @Test
    fun startup() {
        rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.text("Filme populare")), TIMEOUT_MS))
        }
    }

    @Test
    fun movieGrid() {
        rule.collect(packageName = PACKAGE_NAME) {
            pressHome()
            startActivityAndWait()
            checkNotNull(device.wait(Until.findObject(By.text("Filme")), TIMEOUT_MS)).click()
            check(device.wait(Until.hasObject(By.text("48+ titluri")), TIMEOUT_MS))

            val x = device.displayWidth / 2
            val height = device.displayHeight
            var reachedNextPage = false
            repeat(20) {
                if (!reachedNextPage) {
                    check(device.swipe(x, height * 4 / 5, x, height / 3, 12))
                    reachedNextPage = device.wait(Until.hasObject(By.text("96+ titluri")), 750)
                }
            }
            check(reachedNextPage) { "Grila nu a încărcat a doua pagină." }
        }
    }

    private companion object {
        const val PACKAGE_NAME = "io.github.radumariusi.akvodhub"
        const val TIMEOUT_MS = 30_000L
    }
}
