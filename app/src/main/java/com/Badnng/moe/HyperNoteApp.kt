package com.Badnng.moe

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.Badnng.moe.helper.AppLogger
import com.Badnng.moe.rules.RecognitionRuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HyperNoteApp : Application(), Application.ActivityLifecycleCallbacks {

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        registerActivityLifecycleCallbacks(this)
        AppLogger.app("Application onCreate, process=${android.os.Process.myPid()}")
        // 预热规则引擎，确保短信/通知广播到达时引擎已就绪
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            if (!RecognitionRuleEngine.isInitialized) {
                RecognitionRuleEngine.initialize(applicationContext)
            }
        }
    }

    override fun onTerminate() {
        AppLogger.app("Application onTerminate")
        AppLogger.flush()
        super.onTerminate()
    }

    override fun onLowMemory() {
        AppLogger.app("Application onLowMemory")
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        AppLogger.app("Application onTrimMemory level=$level")
        super.onTrimMemory(level)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        AppLogger.app("Activity created: ${activity.javaClass.simpleName}")
    }

    override fun onActivityStarted(activity: Activity) {
        AppLogger.app("Activity started: ${activity.javaClass.simpleName}")
    }

    override fun onActivityResumed(activity: Activity) {
        AppLogger.app("Activity resumed: ${activity.javaClass.simpleName}")
    }

    override fun onActivityPaused(activity: Activity) {
        AppLogger.app("Activity paused: ${activity.javaClass.simpleName}")
    }

    override fun onActivityStopped(activity: Activity) {
        AppLogger.app("Activity stopped: ${activity.javaClass.simpleName}")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        AppLogger.app("Activity saveInstanceState: ${activity.javaClass.simpleName}")
    }

    override fun onActivityDestroyed(activity: Activity) {
        AppLogger.app("Activity destroyed: ${activity.javaClass.simpleName}")
    }
}
