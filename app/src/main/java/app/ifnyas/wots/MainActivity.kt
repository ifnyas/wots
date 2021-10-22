package app.ifnyas.wots

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.transition.TransitionManager
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.SupportStreetViewPanoramaFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.StreetViewPanoramaCamera
import com.google.android.gms.maps.model.StreetViewPanoramaLink
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import kotlin.math.*

@Suppress("PrivatePropertyName", "unused")
open class MainActivity : AppCompatActivity() {

    private val TAG by lazy { javaClass.simpleName }
    private val pref by lazy { getSharedPreferences("SP", 0) }

    private lateinit var streetView: StreetViewPanorama
    private lateinit var sensorManager: SensorManager

    private var counter = 0
    private var prevY = 0f
    private var gravity = FloatArray(3)
    private var camPos = floatArrayOf(0.5f, 0f, 0f)

    companion object {
        const val PREF_LOC = "PREF_LOC"
        const val PERMISSION_CODE = 23
        const val THRESHOLD = 1.0
        const val STEPS_TO_MOVE = 16
    }

    private val sensorEventListener by lazy {
        object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent?) {
                when (event?.sensor?.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> vectorChanged(event.values)
                    Sensor.TYPE_ACCELEROMETER -> accelChanged(event.values)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermission()
    }

    override fun onResume() {
        super.onResume()
        initSensor()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorEventListener)
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) initFun()
        else {
            val permission = Manifest.permission.ACTIVITY_RECOGNITION
            val checkSelfPermission = ContextCompat.checkSelfPermission(this, permission)

            if (checkSelfPermission != PackageManager.PERMISSION_DENIED) initFun()
            else ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults.isNotEmpty()
        if (granted) initFun() else finishAffinity()
    }

    private fun initFun() {
        initStreetView()
        initSensor()
        initBtn()
    }

    private fun initBtn() {
        findViewById<MaterialButton>(R.id.btn_help).setOnClickListener {
            helpToggle()
        }
        findViewById<MaterialButton>(R.id.btn_map).setOnClickListener {
            editLocToggle()
        }
    }

    private fun initSensor() {
        if (!::sensorManager.isInitialized) sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorManager.apply {
            getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
                registerListener(
                    sensorEventListener, it,
                    SensorManager.SENSOR_DELAY_NORMAL,
                    SensorManager.SENSOR_DELAY_UI
                )
            }

            getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                registerListener(
                    sensorEventListener, it,
                    SensorManager.SENSOR_DELAY_NORMAL,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
        }
    }

    private fun initStreetView() {
        // init
        val prefStartLoc = pref.getString(PREF_LOC, getString(R.string.pref_loc_def))
        val streetView = supportFragmentManager.findFragmentById(R.id.view_street)
                as SupportStreetViewPanoramaFragment

        // async
        streetView.getStreetViewPanoramaAsync {
            this.streetView = it.apply {
                setPosition(createLatLng("$prefStartLoc"))
            }
        }
    }

    private fun helpToggle() {
        val layHelp = findViewById<ConstraintLayout>(R.id.lay_help)
        layHelp.visibility = if (layHelp.visibility == VISIBLE) INVISIBLE else VISIBLE
        layHelp.beginTransition()
    }

    private fun editLocToggle() {
        val tilLoc = findViewById<TextInputLayout>(R.id.til_loc)
        val editLoc = findViewById<TextInputEditText>(R.id.edit_loc)
        val currentLoc = pref.getString(PREF_LOC, getString(R.string.pref_loc_def))

        fun goClicked() {
            // move
            val input = "${editLoc.text}".replace(" ", "")
            if (input.contains(",")) {
                streetView.setPosition(createLatLng(input))
                checkReadyThen { savePosition(input) }
            } else tilLoc.error = getString(R.string.edit_loc_err_format)

            // save to pref
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    streetView.location.panoId
                    tilLoc.isErrorEnabled = false
                } catch (e: Exception) {
                    tilLoc.error = getString(R.string.edit_loc_err_invalid)
                }
            }, 1000)
        }

        // loc toggle
        if (tilLoc.visibility == VISIBLE) {
            tilLoc.apply {
                visibility = GONE
                hideKeyboard()
            }
        } else {
            tilLoc.apply {
                visibility = VISIBLE
            }
            editLoc.apply {
                setText("$currentLoc")
                requestFocus()
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_GO) goClicked()
                    true
                }
            }
        }

        // start transition
        tilLoc.beginTransition()
    }

    private fun checkReadyThen(next : () -> Unit) {
        if (::streetView.isInitialized) next()
    }

    private fun updateStreetView(zoom: Float, tilt: Float, bearing: Float) {
        streetView.animateTo(StreetViewPanoramaCamera.Builder().apply {
            zoom(zoom)
            tilt(tilt)
            bearing(bearing)
        }.build(), 500)
    }

    private fun onMovePosition() {
        val location = streetView.location
        val camera = streetView.panoramaCamera
        location.links.let {
            val link = location.links.findClosest(camera.bearing)
            streetView.setPosition(link.panoId)
            savePosition("${streetView.location.position}")
        }
    }

    private fun savePosition(input: String) {
        val loc = input
            .replace("lat/lng: (","")
            .replace(")", "")
        pref.edit().putString(PREF_LOC, loc).apply()
    }

    private fun stepChanged() {
        findViewById<MaterialTextView>(R.id.text_counter).text = "$counter"
        if(++counter % STEPS_TO_MOVE == 0) onMovePosition()
    }

    private fun vectorChanged(values: FloatArray?) {
        // orientation
        val rotationMatrix = FloatArray(16)
        SensorManager.getRotationMatrixFromVector(
            rotationMatrix,
            values)

        // inclination
        val inclination = acos(rotationMatrix[10].toDouble()).toFloat() * 100
        if (inclination > 25.0) {
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                rotationMatrix)
        }

        // orientation
        val orientationInRadians = FloatArray(3)
        SensorManager.getOrientation(
            rotationMatrix,
            orientationInRadians)

        // azimuth
        val azInRadians = orientationInRadians[0]
        val azInDegrees = Math.toDegrees(azInRadians.toDouble()).toFloat()
        val azNormalize = azInDegrees % 360f

        /*
        // pitch
        val piInRadians = orientationInRadians[1]
        val piInDegrees = Math.toDegrees(piInRadians.toDouble()).toFloat()
        val piNormalize = piInDegrees % 180f
         */

        // roll
        val rollMatrix = FloatArray(16)
        if (inclination > 25.0) {
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                rollMatrix)
        }
        else {
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_Z,
                rollMatrix)
        }

        val rollOrientationInRadians = FloatArray(3)
        SensorManager.getOrientation(
            rollMatrix,
            rollOrientationInRadians)

        val roInRadians = rollOrientationInRadians[2]
        val roInDegrees = Math.toDegrees(roInRadians.toDouble()).toFloat()
        val roNormalize = when {
            roInDegrees < -90f -> -90f
            roInDegrees > 90f -> 90f
            else -> roInDegrees
        }

        // update cam
        checkReadyThen {
            updateStreetView(
                0.5f,
                roNormalize,
                azNormalize
            )
        }
    }

    private fun accelChanged(values: FloatArray) {
        // smoothen data
        for (i in values.indices) gravity[i] = gravity[i] + 1.0f * (values[i] - gravity[i])

        // count
        val accel = abs(prevY - gravity[1])
        if((accel in 0.5..1.0)) stepChanged()

        // save prev Y
        prevY = gravity[1]
    }

    private fun Array<StreetViewPanoramaLink>.findClosest(bearing: Float): StreetViewPanoramaLink {
        fun findNormalizedDiff(a: Float, b: Float): Float {
            val diff = a - b
            val normalizedDiff = diff - (360 * floor((diff / 360.0f).toDouble())).toFloat()
            return if ((normalizedDiff < 180.0f)) normalizedDiff else 360.0f - normalizedDiff
        }

        var minBearingDiff = 360f
        var closestLink = this[0]

        for (link in this) {
            if (minBearingDiff > findNormalizedDiff(bearing, link.bearing)) {
                minBearingDiff = findNormalizedDiff(bearing, link.bearing)
                closestLink = link
            }
        }

        return closestLink
    }

    private fun createLatLng(loc: String): LatLng {
        val split = loc.split(",")
        return LatLng(
            split[0].toDoubleOrNull() ?: 0.0,
            split[1].toDoubleOrNull() ?: 0.0)
    }

    private fun ViewGroup.beginTransition() {
        TransitionManager.beginDelayedTransition(this)
    }

    private fun View.hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }
}