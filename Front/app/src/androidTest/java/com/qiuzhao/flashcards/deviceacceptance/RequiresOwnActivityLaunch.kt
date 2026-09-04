package com.qiuzhao.flashcards.deviceacceptance

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Vendor builds such as MIUI abort every activity launch that originates from the app's own
 * uid while the process hosts an instrumentation (ActivityTaskManager "Abort background
 * activity starts", result code 102), even when a host `am start` already put a resumed
 * activity in the foreground. Compose test rules launch their activity through exactly that
 * path, so on those devices the rule would hang forever instead of finishing the run.
 *
 * This rule probes the capability with a uniquely tagged canary launch. Wrap the real
 * activity-launching rule with `RuleChain.outerRule(RequiresOwnActivityLaunch()).around(...)`
 * so the canary runs first: on devices that allow the launch the suite behaves as before;
 * on devices that block it the test is assume-skipped with an explicit reason. The probe
 * result is cached per process, so only the first skipped test pays the timeout.
 */
class RequiresOwnActivityLaunch(private val timeoutSeconds: Long = 5) : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            assumeTrue(SKIP_MESSAGE, ownActivityLaunchAllowed(timeoutSeconds))
            base.evaluate()
        }
    }

    companion object {
        private const val CANARY_EXTRA = "shankaCanaryLaunch"
        private const val SKIP_MESSAGE =
            "device blocks the app's own activity launches under instrumentation " +
                "(vendor BAL policy, e.g. MIUI); skipping tests that launch activities"

        @Volatile
        private var cached: Boolean? = null

        private fun ownActivityLaunchAllowed(timeoutSeconds: Long): Boolean {
            cached?.let { return it }
            val app = InstrumentationRegistry.getInstrumentation()
                .targetContext.applicationContext as Application
            val resumed = CountDownLatch(1)
            val callbacks = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    if (activity.intent?.getBooleanExtra(CANARY_EXTRA, false) == true) resumed.countDown()
                }
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            }
            app.registerActivityLifecycleCallbacks(callbacks)
            try {
                val launcher = app.packageManager.getLaunchIntentForPackage(app.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.putExtra(CANARY_EXTRA, true)
                    ?: return false.also { cached = it }
                app.startActivity(launcher)
                val allowed = resumed.await(timeoutSeconds, TimeUnit.SECONDS)
                cached = allowed
                return allowed
            } finally {
                app.unregisterActivityLifecycleCallbacks(callbacks)
            }
        }
    }
}
