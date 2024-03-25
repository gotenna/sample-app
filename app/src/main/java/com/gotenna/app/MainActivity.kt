package com.gotenna.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.gotenna.app.home.HomeViewModel
import com.gotenna.radio.sdk.GotennaClient
import com.gotenna.radio.sdk.common.models.radio.ConnectionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "RadioSDK"

class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    val viewModel: HomeViewModel by viewModels()
    private val ENABLE_BLUETOOTH_REQUEST_CODE = 1
    private val REQUEST_CODE = 2
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*if (!hasRequiredRuntimePermissions()) {
            promptForBlePermissions()
        }*/

        setContent {
            MainScreen()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != RESULT_OK) {
                    promptForBlePermissions()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun promptForBlePermissions() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        permissions.forEach {
            Log.d("Client", "Permission: $it")
        }
        Log.d("Client", "Permission request result: $requestCode, denied: ${grantResults[0] != PackageManager.PERMISSION_GRANTED}")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun Context.hasRequiredRuntimePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onSupportNavigateUp() = navController.navigateUp()


    public fun testUsbConnection() {
        // Quick test for usb scan.
        lifecycleScope.launch(Dispatchers.IO) {
            GotennaClient.observeRadios().collect { list ->
                Log.e(TAG, "observing radios: $list")
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                val usbRadios = GotennaClient.scan(ConnectionType.USB)
                Log.e(TAG, "usbRadios: $usbRadios")
                if (usbRadios.isNotEmpty()) {
                    usbRadios.forEach {
                        it.connect()
                    }
                    return@launch
                }
            }
        }
    }

    fun preProcess(bytes: ByteArray): ByteArray {
        return bytes
    }

    fun postProcess(bytes: ByteArray): ByteArray {
        return bytes
    }

    public fun testBleConnection() {
        if (this.hasRequiredRuntimePermissions()) {
            lifecycleScope.launch(Dispatchers.IO) {
                // perform a scan of devices
                val bleRadios = GotennaClient.scan(ConnectionType.BLE)
                Log.i("BLEScanResults", "result size: ${bleRadios.size} contents: $bleRadios")
                bleRadios.firstOrNull()?.connect()
                GotennaClient.observeRadios().collect {
                    Log.i("Client", "Successfully got an emission of radios")
                    Log.i("Client", "Successfully got a connected radio, sending it a command")
                }
            }
        } else {
            promptForBlePermissions()
        }
    }
}
