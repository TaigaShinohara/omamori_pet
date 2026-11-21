package com.omamoripet.wear.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.FEELING_MINUTE_COUNT
import com.omamoripet.shared.model.FeelingContext
import com.omamoripet.shared.model.FeelingContextQr
import com.omamoripet.shared.model.FeelingMinute
import com.omamoripet.shared.model.MotionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeelingContextTransferTest {
    @Test
    fun `generated QR transfers missing and observed minutes and binds to the selected time`() {
        val context = FeelingContext(
            1_700_000_000_000L, 64, BaselineSource.WATCH_LEARNED,
            List(FEELING_MINUTE_COUNT) { index ->
                when (index) {
                    0 -> FeelingMinute(72, 1, MotionState.STILL_CONFIRMED)
                    30 -> FeelingMinute(109, 9, MotionState.RECENT_MOVEMENT)
                    44 -> FeelingMinute(motion = MotionState.OFF_BODY)
                    else -> FeelingMinute()
                }
            }
        )
        val image = QRCodeWriter().encode(FeelingContextQr.encode(context), BarcodeFormat.QR_CODE, 220, 220)
        val pixels = IntArray(image.width * image.height) { index ->
            if (image[index % image.width, index / image.width]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))
        val decoded = QRCodeReader().decode(bitmap).text
        assertEquals(context, FeelingContextQr.decode(decoded, context.recordedAtEpochMs))
        assertNull(FeelingContextQr.decode(decoded, context.recordedAtEpochMs + 1))
        assertNull(FeelingContextQr.decode(decoded.dropLast(1), context.recordedAtEpochMs))
        assertNull(FeelingContextQr.decode("unrelated", context.recordedAtEpochMs))
    }

    @Test
    fun `minute summarizes observations without creating a value for missing heart rate`() {
        assertEquals(FeelingMinute(), summarizeFeelingMinute(emptyList(), emptyList()))
        assertEquals(
            FeelingMinute(81, 2, MotionState.RECENT_MOVEMENT),
            summarizeFeelingMinute(listOf(60, 101), listOf(MotionState.STILL_CONFIRMED, MotionState.RECENT_MOVEMENT))
        )
        assertEquals(
            FeelingMinute(motion = MotionState.OFF_BODY),
            summarizeFeelingMinute(emptyList(), listOf(MotionState.OFF_BODY))
        )
    }
}
