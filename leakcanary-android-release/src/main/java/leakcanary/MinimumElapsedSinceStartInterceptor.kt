package leakcanary

import android.annotation.SuppressLint
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import leakcanary.HeapAnalysisInterceptor.Chain
import leakcanary.HeapAnalysisJob.Result
import java.io.BufferedReader
import java.io.FileReader
import java.util.concurrent.TimeUnit

@SuppressLint("NewApi")
class MinimumElapsedSinceStartInterceptor(
  private val minimumElapsedSinceStartMillis: Long = TimeUnit.SECONDS.toMillis(30)
) : HeapAnalysisInterceptor {

  private val processStartUptimeMillis by lazy {
    Process.getStartUptimeMillis()
  }

  private val processForkRealtimeMillis by lazy {
    readProcessForkRealtimeMillis()
  }

  override fun intercept(chain: Chain): Result {
    if (elapsedMillisSinceStart() < minimumElapsedSinceStartMillis) {
      chain.job.cancel("app started less than $minimumElapsedSinceStartMillis ms ago.")
    }
    return chain.proceed()
  }

  private fun elapsedMillisSinceStart() = if (Build.VERSION.SDK_INT >= 24) {
    SystemClock.uptimeMillis() - processStartUptimeMillis
  } else {
    SystemClock.elapsedRealtime() - processForkRealtimeMillis
  }

  /**
   * See https://dev.to/pyricau/android-vitals-when-did-my-app-start-24p4#process-fork-time
   */
  private fun readProcessForkRealtimeMillis(): Long {
    val myPid = Process.myPid()
    val ticksAtProcessStart = readProcessStartTicks(myPid)

    val ticksPerSecond = if (Build.VERSION.SDK_INT >= 21) {
      Os.sysconf(OsConstants._SC_CLK_TCK)
    } else {
      val tckConstant = try {
        Class.forName("android.system.OsConstants").getField("_SC_CLK_TCK").getInt(null)
      } catch (e: ClassNotFoundException) {
        Class.forName("libcore.io.OsConstants").getField("_SC_CLK_TCK").getInt(null)
      }
      val os = Class.forName("libcore.io.Libcore").getField("os").get(null)!!
      os::class.java.getMethod("sysconf", Integer.TYPE).invoke(os, tckConstant) as Long
    }
    return ticksAtProcessStart * 1000 / ticksPerSecond
  }

  // Benchmarked (with Jetpack Benchmark) on Pixel 3 running
  // Android 10. Median time: 0.13ms
  private fun readProcessStartTicks(pid: Int): Long {
    val path = "/proc/$pid/stat"
    val stat = BufferedReader(FileReader(path)).use { reader ->
      reader.readLine()
    }
    val fields = stat.substringAfter(") ")
      .split(' ')
    return fields[19].toLong()
  }
}