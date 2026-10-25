package dev.intent.app.connection

import android.widget.Button
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import dev.intent.app.R

/** Keep the scanner in the user's orientation and provide a visible cancel action. */
class PairingScannerActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.pairing_scanner)
        findViewById<Button>(R.id.close_scanner).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        return findViewById(R.id.zxing_barcode_scanner)
    }
}
