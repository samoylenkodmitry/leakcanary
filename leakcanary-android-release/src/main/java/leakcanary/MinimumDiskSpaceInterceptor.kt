package leakcanary

import android.app.Application
import android.os.Build.VERSION.SDK_INT
import android.os.StatFs
import leakcanary.HeapAnalysisInterceptor.Chain
import leakcanary.HeapAnalysisJob.Result

class MinimumDiskSpaceInterceptor(
  private val application: Application,
  private val minimumDiskSpaceBytes: Long = 200_000_000,
) : HeapAnalysisInterceptor {

  override fun intercept(chain: Chain): Result {
    val availableDiskSpace = availableDiskSpace()
    if (availableDiskSpace < minimumDiskSpaceBytes) {
      chain.job.cancel("availableDiskSpace $availableDiskSpace < minimumDiskSpaceBytes $minimumDiskSpaceBytes")
    }
    return chain.proceed()
  }

  private fun availableDiskSpace(): Long {
    val filesDir = application.filesDir!!
    return StatFs(filesDir.absolutePath).run {
      if (SDK_INT >= 18) {
        availableBlocksLong * blockSizeLong
      } else {
        availableBlocks * blockSize.toLong()
      }
    }
  }
}