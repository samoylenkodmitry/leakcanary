package leakcanary

import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.app.Application
import android.content.Context
import android.os.Build
import leakcanary.HeapAnalysisInterceptor.Chain

class MinimumMemoryInterceptor(
  private val application: Application,
  private val minimumRequiredAvailableMemoryBytes: Long = 100_000_000,
) : HeapAnalysisInterceptor {

  private val memoryInfo = MemoryInfo()

  override fun intercept(chain: Chain): HeapAnalysisJob.Result {
    val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    if (Build.VERSION.SDK_INT >= 19 && activityManager.isLowRamDevice) {
      chain.job.cancel("low ram device")
    } else {
      activityManager.getMemoryInfo(memoryInfo)

      if (memoryInfo.lowMemory || memoryInfo.availMem <= memoryInfo.threshold) {
        chain.job.cancel("low memory")
      } else {
        val systemAvailableMemory = memoryInfo.availMem - memoryInfo.threshold

        val runtime = Runtime.getRuntime()
        val appUsedMemory = runtime.totalMemory() - runtime.freeMemory()
        val appAvailableMemory = runtime.maxMemory() - appUsedMemory

        val availableMemory = systemAvailableMemory.coerceAtMost(appAvailableMemory)
        if (availableMemory < minimumRequiredAvailableMemoryBytes) {
          chain.job.cancel(
            "not enough free memory: available $availableMemory < min $minimumRequiredAvailableMemoryBytes"
          )
        }
      }
    }

    return chain.proceed()
  }
}