package com.irfan.nsd_example_app

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.*
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull
import io.flutter.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.net.InetAddress


class MainActivity : FlutterActivity() {
    private val mChannel = "io.irfan.NSD"
    private var mNsdHelper: NsdHelper? = null
    private var isDiscovery: Boolean = false
    private lateinit var discoveryChannel: MethodChannel

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, mChannel).setMethodCallHandler { call, result ->
            if (call.method == "isConnected") {
                result.success(isConnected())
            } else if (call.method == "changeDeviceName") {
                mNsdHelper?.setDeviceName(call.argument<String>("deviceName")!!)
            } else if (call.method == "initNSD") {
                mNsdHelper = NsdHelper(this)
                mNsdHelper?.initializeNsd()
            } else if (call.method == "broadcastService") {
                call.argument<String>("serviceName")?.let { mNsdHelper?.setServiceName(serviceName = it) }
                call.argument<String>("serviceType")?.let { mNsdHelper?.setServiceType(type = it) }
                mNsdHelper?.registerService(port = call.argument<Int>("port")!!)
            } else if (call.method == "stopBroadcast") {
                mNsdHelper?.tearDown()
            } else if (call.method == "discoverService") {
                discoveryChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "io.irfan.NSD.discovery")
                isDiscovery = true
                mNsdHelper?.setDiscoveryChannel(discoveryChannel)
                call.argument<String>("serviceType")?.let { mNsdHelper?.setServiceType(it) }
                call.argument<String>("serviceName")?.let { mNsdHelper?.setServiceName(it) }
                mNsdHelper?.discoverServices()
            } else if (call.method == "stopDiscovery") {
                isDiscovery = false
                mNsdHelper?.stopDiscovery()
            } else if (call.method == "stopNSD") {
                closeNsd()
            } else {
                result.notImplemented()
            }
        }

    }

    override fun onPause() {
        if (!isDiscovery){
            mNsdHelper?.stopDiscovery()
            Log.d(NsdHelper.TAG, "Discovery stopped on pause")
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        object : Thread() {
            override fun run() {
                try {
                    if (isDiscovery) mNsdHelper?.discoverServices()
                } catch (ex: Exception) {
                    Log.i("---", "Exception in thread:onResume")
                }
            }
        }.start()
    }

    private fun isConnected(): Boolean {
        return (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo?.isConnected
                ?: false
    }

    override fun onStop() {
        if (isDiscovery) mNsdHelper?.stopDiscovery()
        super.onStop()
    }

    private fun closeNsd(){
        mNsdHelper?.tearDown()
        isDiscovery = false
        mNsdHelper = null
    }
    override fun onDestroy() {
        closeNsd()
        super.onDestroy()
    }

}

class NsdHelper(private var mContext: Context) {
    var SERVICE_TYPE = "_http._tcp."
    var SERVICE_NAME = "io.irfan.NSD.service"
    var mNsdManager: NsdManager
    var mDeviceName: String = "User"
    var mResolveListener: ResolveListener? = null
    var mLostResolveListener: ResolveListener? = null
    var mDiscoveryListener: DiscoveryListener? = null
    var mRegistrationListener: RegistrationListener? = null
    var mServiceName = "io.irfan.NSD"
    lateinit var discoverChannel: MethodChannel

    fun initializeNsd() {
        object : Thread() {
            override fun run() {
                try {
                    initializeResolveListener()
                } catch (ex: Exception) {
                    Log.i("---", "Exception in thread:on NSD init")
                }
            }
        }.start()
    }

    fun setDiscoveryChannel(dchannel: MethodChannel) {
        discoverChannel = dchannel
    }

    fun setServiceName(serviceName: String) {
        SERVICE_NAME = serviceName
    }

    fun setServiceType(type: String) {
        SERVICE_TYPE = type
    }

    fun initializeDiscoveryListener() {
        mDiscoveryListener = object : DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service discovery success $service")
                if (service.serviceType != SERVICE_TYPE) {
                    Log.d(TAG, "Unknown Service Type: " + service.serviceType)
                } else if (service.serviceName.contains(SERVICE_NAME)) {
                    mNsdManager.resolveService(service, mResolveListener)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e(TAG, "Service lost $service")
                if (service.serviceName.contains(SERVICE_NAME)) {
                    mNsdManager.resolveService(service, mLostResolveListener)
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
            }
        }
    }

    fun initializeResolveListener() {
        object : Thread() {
            override fun run() {
                try {
                    mResolveListener = object : ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed$errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            Log.e(TAG, "Resolve Succeeded. $serviceInfo")
                            val port: Int = serviceInfo.port
                            val host: InetAddress = serviceInfo.host
                            val foundData: HashMap<String, Any> = HashMap()
                            foundData["name"] = serviceInfo.serviceName.removePrefix("$SERVICE_NAME:")
                            foundData["host"] = host.hostAddress
                            foundData["port"] = port
                            Handler(Looper.getMainLooper()).post {
                                discoverChannel.invokeMethod("foundHost", foundData)
                            }
                        }
                    }

                    mLostResolveListener = object : ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "LostResolve failed $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            Log.e(TAG, "LostResolve Succeeded. $serviceInfo")
                            val port: Int = serviceInfo.port
                            val host: InetAddress = serviceInfo.host
                            val lostData: HashMap<String, Any> = HashMap()
                            lostData["name"] = serviceInfo.serviceName.removePrefix("$SERVICE_NAME:")
                            lostData["host"] = host.hostAddress
                            lostData["port"] = port
                            Handler(Looper.getMainLooper()).post {
                                discoverChannel.invokeMethod("lostHost", lostData)
                            }
                        }
                    }
                } catch (ex: Exception) {
                    Log.i("---", "Exception in thread:on NSD init ResolveListener")
                }
            }
        }.start()
    }

    fun initializeRegistrationListener() {
        mRegistrationListener = object : RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                mServiceName = NsdServiceInfo.serviceName
                Log.d(TAG, "Service registered: $mServiceName")
            }

            override fun onRegistrationFailed(arg0: NsdServiceInfo, arg1: Int) {
                Log.d(TAG, "Service registration failed: $arg1")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered: " + arg0.serviceName)
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.d(TAG, "Service unregistration failed: $errorCode")
            }
        }
    }

    fun setDeviceName(name: String) {
        mDeviceName = name
    }

    fun registerService(port: Int) {
        object : Thread() {
            override fun run() {
                try {
                    tearDown() // Cancel any previous registration request
                    initializeRegistrationListener()
                    val serviceInfo = NsdServiceInfo()
                    serviceInfo.port = port
                    serviceInfo.serviceName = "$SERVICE_NAME:$mDeviceName"
                    serviceInfo.serviceType = SERVICE_TYPE
                    mNsdManager.registerService(serviceInfo, PROTOCOL_DNS_SD, mRegistrationListener)
                } catch (ex: Exception) {
                    Log.i("---", "Exception in thread:Register NSD host")
                }
            }
        }.start()
    }

    fun discoverServices() {
        object : Thread() {
            override fun run() {
                try {
                    stopDiscovery() // Cancel any existing discovery request
                    initializeDiscoveryListener()
                    mNsdManager.discoverServices(SERVICE_TYPE, PROTOCOL_DNS_SD, mDiscoveryListener)
                } catch (ex: Exception) {
                    Log.i("---", "Exception in thread:on NSD Start discovery")
                }
            }
        }.start()
    }

    fun stopDiscovery() {
        if (mDiscoveryListener != null) {
            try {
                mNsdManager.stopServiceDiscovery(mDiscoveryListener)
            } finally {
            }
            mDiscoveryListener = null
        }
    }

    fun tearDown() {
        if (mRegistrationListener != null) {
            try {
                mNsdManager.unregisterService(mRegistrationListener)
            } finally {
            }
            mRegistrationListener = null
        }
    }

    companion object {
        const val TAG = "NsdHelper"
    }

    init {
        mNsdManager = mContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
}
