package com.elyudde.sms_advanced

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.elyudde.sms_advanced.permisions.Permissions
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.text.MessageFormat
import java.util.*


/**
 * Created by crazygenius on 1/08/21.
 */

internal class MmsReaderHandler(
    private val context: Context,
    private val result: MethodChannel.Result,
    mmsId: Int,
) :
    RequestPermissionsResultListener {
    private val permissionsList = arrayOf(Manifest.permission.READ_SMS)
    private var mmsId = -1
    fun handleReadMms(permissions: Permissions) {
        if (permissions.checkAndRequestPermission(permissionsList, Permissions.SEND_SMS_ID_REQ)) {
            readMms()
        }
    }

    fun handleReadMmsImage(permissions: Permissions) {
        if (permissions.checkAndRequestPermission(permissionsList, Permissions.SEND_SMS_ID_REQ)) {
            readMmsImage()
        }
    }

    @SuppressLint("Range")
    private fun readMms() {
        val selectionPart = "mid=$mmsId"
        val uri = Uri.parse("content://mms/part")
        val cursor: Cursor? = context.contentResolver.query(
            uri, null,
            selectionPart, null, null
        )
        if (cursor == null) {
            result.error("#01", "permission denied", null)
            return
        }
        val res = JSONObject()

        cursor.use { cur ->
            val address = getMmsAddress(mmsId)
            if (address != null) {
                res.put("address", address)
            }

            if (cur.moveToFirst()) {
                do {
                    val partId = cur.getString(cur.getColumnIndex("_id"))
                    val type = cur.getString(cur.getColumnIndex("ct"))

                    res.put("content-type", type)

                    if ("text/plain" == type) {
                        val data = cur.getString(cur.getColumnIndex("_data"))
                        val body: String? = if (data != null) {
                            // implementation of this method below
                            getMmsText(partId)
                        } else {
                            cur.getString(cur.getColumnIndex("text"))
                        }
                        res.put("body", body)
                    }
                } while (cur.moveToNext())
            }
        }
        result.success(res)
    }

    @SuppressLint("Range")
    private fun readMmsImage() {
        val selectionPart = "mid=$mmsId"
        val uri = Uri.parse("content://mms/part")
        val cursor: Cursor? = context.contentResolver.query(
            uri, null,
            selectionPart, null, null
        )
        if (cursor == null) {
            result.error("#01", "permission denied", null)
            return
        }
        cursor.use { cur ->
            if (cur.moveToFirst()) {
                do {
                    val partId = cur.getString(cur.getColumnIndex("_id"))
                    val type = cur.getString(cur.getColumnIndex("ct"))
                    if (type.contains("image")) {
                        val bitmap: Bitmap = getMmsImagePart(partId) ?: continue

                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        val byteData = stream.toByteArray()

                        result.success(byteData)
                        bitmap.recycle()
                        return
                    }
                } while (cur.moveToNext())

                result.error("#01", "Image not found", null)
            }
        }
    }

    private fun getMmsImagePart(partId: String): Bitmap? {
        val partURI = Uri.parse("content://mms/part/$partId")
        var inStream: InputStream? = null
        var bitmap: Bitmap? = null
        try {
            inStream = context.contentResolver.openInputStream(partURI)
            bitmap = BitmapFactory.decodeStream(inStream)
//            bitmap.recycle()
        } catch (e: IOException) {
            Log.e("SMS", "Error reading image part $mmsId $e ${e.stackTraceToString()}}")
        } finally {
            if (inStream != null) {
                try {
                    inStream.close()
                } catch (_: IOException) {
                }
            }
        }
        return bitmap
    }

    private fun getMmsText(id: String): String {
        val partURI = Uri.parse("content://mms/part/$id")
        var inStream: InputStream? = null
        val sb = StringBuilder()
        try {
            inStream = context.contentResolver.openInputStream(partURI)
            if (inStream != null) {
                val isr = InputStreamReader(inStream, "UTF-8")
                val reader = BufferedReader(isr)
                var temp = reader.readLine()
                while (temp != null) {
                    sb.append(temp)
                    temp = reader.readLine()
                }
            }
        } catch (_: IOException) {
        } finally {
            if (inStream != null) {
                try {
                    inStream.close()
                } catch (_: IOException) {
                }
            }
        }
        return sb.toString()
    }

    private fun getMmsAddress(id: Int): String? {
        val selectionAdd = "msg_id=$id"
        val uriStr: String = MessageFormat.format("content://mms/{0}/addr", id)
        val uriAddress = Uri.parse(uriStr)
        val cAdd: Cursor = context.contentResolver.query(
            uriAddress, null,
            selectionAdd, null, null
        ) ?: return null
        var name: String? = null
        if (cAdd.moveToFirst()) {
            do {
                val addIndx = cAdd.getColumnIndex("address")
                if (addIndx < 0) return null
                val number = cAdd.getString(addIndx)
                if (number != null) {
                    try {
                        number.replace("-", "").toLong()
                        name = number
                    } catch (nfe: NumberFormatException) {
                        if (name == null) {
                            name = number
                        }
                    }
                }
            } while (cAdd.moveToNext())
        }
        cAdd.close()
        return name
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode != Permissions.READ_SMS_ID_REQ) {
            return false
        }
        var isOk = true
        for (res in grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                isOk = false
                break
            }
        }
        if (isOk) {
            readMms()
            return true
        }
        result.error("#01", "permission denied", null)
        return false
    }

    init {
        this.mmsId = mmsId
    }
}

internal class MmsReader(val context: Context, private val binding: ActivityPluginBinding) :
    MethodCallHandler {
    private val permissions: Permissions = Permissions(context, binding.activity)
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (!call.hasArgument("mms_id")) {
            result.error("#01", "mms_id is required", null)
            return
        }

        when (call.method) {
            "readMms" -> {
                val mmsId = call.argument<Int>("mms_id")!!
                val handler = MmsReaderHandler(context, result, mmsId)
                binding.addRequestPermissionsResultListener(handler)
                handler.handleReadMms(permissions)
            }

            "readMmsImage" -> {
                val mmsId = call.argument<Int>("mms_id")!!
                val handler = MmsReaderHandler(context, result, mmsId)
                binding.addRequestPermissionsResultListener(handler)
                handler.handleReadMmsImage(permissions)
            }

            else -> {
                result.notImplemented()
            }
        }


    }

}
