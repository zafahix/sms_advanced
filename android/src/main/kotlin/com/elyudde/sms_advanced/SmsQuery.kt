package com.elyudde.sms_advanced

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import com.elyudde.sms_advanced.permisions.Permissions
import io.flutter.Log
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.text.MessageFormat
import java.util.*


/**
 * Created by crazygenius on 1/08/21.
 */

internal class SmsQueryHandler(
    private val context: Context,
    private val result: MethodChannel.Result,
    start: Int,
    count: Int,
    threadId: Int,
    address: String?
) :
    RequestPermissionsResultListener {
    private val permissionsList = arrayOf(Manifest.permission.READ_SMS)
    private var mStart = 0
    private var mCount = -1
    private var threadId = -1
    private var address: String? = null
    fun handle(permissions: Permissions) {
        if (permissions.checkAndRequestPermission(permissionsList, Permissions.SEND_SMS_ID_REQ)) {
            querySms()
        }
    }

    private fun readThreadSms(): ArrayList<JSONObject> {
        var start = this.mStart + 0
        var count = this.mCount + 0
        // List threads only
        val list = ArrayList<JSONObject>()
        val cursor =
            context.contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("*"),
                "thread_id = ?",
                arrayOf(threadId.toString()),
                "date DESC"
            ) ?: return arrayListOf()
        if (!cursor.moveToFirst()) {
            cursor.close()
            return arrayListOf()
        }
        do {
            if (start > 0) {
                start--
                continue
            }
            val res = JSONObject()

            res.put("sms_mms", "sms")

            for (idx in 0 until cursor.columnCount) {
                try {
                    if (cursor.getColumnName(idx) == "address" || cursor.getColumnName(idx) == "body") {
                        res.put(cursor.getColumnName(idx), cursor.getString(idx))
                    } else if (cursor.getColumnName(idx) == "date" || cursor.getColumnName(idx) == "date_sent") {
                        res.put(cursor.getColumnName(idx), cursor.getLong(idx))
                    } else {
                        res.put(cursor.getColumnName(idx), cursor.getInt(idx))
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }


            list.add(res)
            if (count > 0) {
                count--
            }
        } while (cursor.moveToNext() && count != 0)
        cursor.close()
        return list
    }

    @SuppressLint("Range")
    private fun readThreadMms(): ArrayList<JSONObject> {
        var start = 0 + this.mStart
        var count = 0 + this.mCount
        // List threads only
        val list = ArrayList<JSONObject>()
        val cursor =
            context.contentResolver.query(
                Uri.parse("content://mms"),
                arrayOf("*"),
                "thread_id = ?",
                arrayOf(threadId.toString()),
                "date DESC"
            ) ?: return arrayListOf()
        if (!cursor.moveToFirst()) {
            cursor.close()
            return arrayListOf()
        }
        do {
            val res = JSONObject()

            res.put("sms_mms", "mms")

            val id = cursor.getInt(cursor.getColumnIndex("_id"))

            val mmsData = readMms(id)
            res.put("_id", id)

            if (mmsData != null) {
                // Merge
                for (key in mmsData.keys()) {
                    res.put(key, mmsData.get(key))
                }
            }
            val date = cursor.getLong(cursor.getColumnIndex("date")) * 1000

            res.put("date", date)
            res.put("date_sent", date)

            cursor.columnNames.forEach {
                if (cursor.isNull(cursor.getColumnIndex(it))) {
//                    Log.d("SMS", "column name: $it is null")
                } else if (!res.has(it) && !arrayOf("_id", "address", "body", "content-type").contains(it)) {
                    if (cursor.getType(cursor.getColumnIndex(it)) == Cursor.FIELD_TYPE_BLOB) {
//                    Log.d("SMS", "column name: $it is blob, value: ${cursor.getBlob(cursor.getColumnIndex(it))}")
//                        res.put(it, cursor.getBlob(cursor.getColumnIndex(it)))
                    } else if (cursor.getType(cursor.getColumnIndex(it)) == Cursor.FIELD_TYPE_FLOAT) {
//                    Log.d("SMS", "column name: $it is float, value:  ${cursor.getFloat(cursor.getColumnIndex(it))}")
                        res.put(it, cursor.getFloat(cursor.getColumnIndex(it)))
                    } else if (cursor.getType(cursor.getColumnIndex(it)) == Cursor.FIELD_TYPE_INTEGER) {
//                    Log.d("SMS", "column name: $it is integer, value:  ${cursor.getInt(cursor.getColumnIndex(it))}")
                        res.put(it, cursor.getInt(cursor.getColumnIndex(it)))
                    } else if (cursor.getType(cursor.getColumnIndex(it)) == Cursor.FIELD_TYPE_STRING) {
//                    Log.d("SMS", "column name: $it is string, value:  ${cursor.getString(cursor.getColumnIndex(it))}")
                        res.put(it, cursor.getString(cursor.getColumnIndex(it)))
                    }
                }
            }

            if (start > 0) {
                start--
                continue
            }
            list.add(res)
            if (count > 0) {
                count--
            }
        } while (cursor.moveToNext() && count != 0)
        cursor.close()
        return list
    }

    private fun readSingleThread() {
        var list: ArrayList<JSONObject> = ArrayList()
        val smsList = readThreadSms()
        val mmsList = readThreadMms()

        list.addAll(smsList)
        list.addAll(mmsList)

//        // Sort and limit
        list.sortByDescending { it.getLong("date") }

        if (mStart > 0 && list.size > mStart) {
            list = ArrayList(list.subList(mStart, list.size))
        }
        if (mCount > 0 && mCount < list.size) {
            list = ArrayList(list.subList(0, mCount))
        }
        result.success(list)
    }

    private fun readThreadsOnly() {
        // List threads only
        val list = ArrayList<JSONObject>()
        val cursor =
            context.contentResolver.query(
                Uri.parse("content://mms-sms/conversations"),
                arrayOf("*"),
                null,
                null,
                null
            )
        if (cursor == null) {
            result.error("#01", "permission denied", null)
            return
        }
        if (!cursor.moveToFirst()) {
            cursor.close()
            result.success(list)
            return
        }
        do {
            val res = JSONObject()
            for (idx in 0 until cursor.columnCount) {
                try {
                    if (cursor.getColumnName(idx) == "address" || cursor.getColumnName(idx) == "body") {
                        res.put(cursor.getColumnName(idx), cursor.getString(idx))
                    } else if (cursor.getColumnName(idx) == "date" || cursor.getColumnName(idx) == "date_sent") {
                        res.put(cursor.getColumnName(idx), cursor.getLong(idx))
                    } else {
                        res.put(cursor.getColumnName(idx), cursor.getInt(idx))
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            if (mStart > 0) {
                mStart--
                continue
            }
            list.add(res)
            if (mCount > 0) {
                mCount--
            }
        } while (cursor.moveToNext() && mCount != 0)
        cursor.close()
        result.success(list)
    }

//    private fun readSms(id: Int): JSONObject? {
//        val selectionPart = "_id=$id"
//        val uri = Uri.parse("content://sms")
//        val cursor: Cursor = context.contentResolver.query(
//            uri, null,
//            selectionPart, null, null
//        ) ?: return null
//        val res = JSONObject()
//        for (idx in 0 until cursor.columnCount) {
//            try {
//                if (cursor.getColumnName(idx) == "address" || cursor.getColumnName(idx) == "body") {
//                    res.put(cursor.getColumnName(idx), cursor.getString(idx))
//                } else if (cursor.getColumnName(idx) == "date" || cursor.getColumnName(idx) == "date_sent") {
//                    res.put(cursor.getColumnName(idx), cursor.getLong(idx))
//                } else {
//                    res.put(cursor.getColumnName(idx), cursor.getInt(idx))
//                }
//            } catch (e: JSONException) {
//                e.printStackTrace()
//            }
//        }
//        return res
//    }

    @SuppressLint("Range")
    private fun readMms(id: Int): JSONObject? {
        val selectionPart = "mid=$id"
        val uri = Uri.parse("content://mms/part")
        val cursor: Cursor = context.contentResolver.query(
            uri, null,
            selectionPart, null, null
        ) ?: return null
        val res = JSONObject()

        cursor.use { cur ->
            val address = getMmsAddress(id)
            if (address != null) {
                res.put("address", address)
            }

            if (cur.moveToFirst()) {
                do {
                    val partId = cur.getString(cur.getColumnIndex("_id"))
                    val contentType = cur.getString(cur.getColumnIndex("ct"))

                    res.put("content-type", contentType)

                    if ("text/plain" == contentType) {
                        val data = cur.getString(cur.getColumnIndex("_data"))
                        val body: String? = if (data != null) {
                            // implementation of this method below
                            getMmsText(partId)
                        } else {
                            cur.getString(cur.getColumnIndex("text"))
                        }
                        res.put("body", body)
                    }

                    cursor.columnNames.forEach {
                        if (cursor.isNull(cursor.getColumnIndex(it))) {
//                    Log.d("SMS", "column name: $it is null")
                        } else if (!res.has(it) && !arrayOf("_id", "address", "body", "content-type").contains(it)) {
                            if (cursor.getType(cursor.getColumnIndex(it)) == Cursor.FIELD_TYPE_BLOB) {
//                    Log.d("SMS", "column name: $it is blob, value: ${cursor.getBlob(cursor.getColumnIndex(it))}")
                                res.put(it, cursor.getBlob(cursor.getColumnIndex(it)))
                            } else if (cursor.getType(cursor.getColumnIndex(it)) == Cursor.FIELD_TYPE_FLOAT) {
//                    Log.d("SMS", "column name: $it is float, value:  ${cursor.getFloat(cursor.getColumnIndex(it))}")
                                res.put(it, cursor.getFloat(cursor.getColumnIndex(it)))
                            } else if (cursor.getType(cursor.getColumnIndex(it)) == Cursor.FIELD_TYPE_INTEGER) {
//                    Log.d("SMS", "column name: $it is integer, value:  ${cursor.getInt(cursor.getColumnIndex(it))}")
                                res.put(it, cursor.getInt(cursor.getColumnIndex(it)))
                            } else if (cursor.getType(cursor.getColumnIndex(it)) == Cursor.FIELD_TYPE_STRING) {
//                    Log.d("SMS", "column name: $it is string, value:  ${cursor.getString(cursor.getColumnIndex(it))}")
                                res.put(it, cursor.getString(cursor.getColumnIndex(it)))
                            }
                        }
                    }
                } while (cur.moveToNext())
            }
        }
        return res
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

//    @SuppressLint("Range")
//    fun getMmsAddress(id: Int): String? {
//        val addrSelection = "type=137 AND msg_id=$id"
//        val uriStr = MessageFormat.format("content://mms/{0}/addr", id)
//        val uriAddress = Uri.parse(uriStr)
//        val columns = arrayOf("address")
//        val cursor = context.contentResolver.query(
//            uriAddress, columns,
//            addrSelection, null, null
//        )
//        var address: String? = ""
//        var value: String?
//        if (cursor!!.moveToFirst()) {
//            do {
//                value = cursor.getString(cursor.getColumnIndex("address"))
//                if (value != null) {
//                    address = value
//                    // Use the first one found if more than one
//                    break
//                }
//            } while (cursor.moveToNext())
//        }
//        cursor.close()
//        return address
//    }

    private fun querySms() {
        if (threadId <= 0) {
            readThreadsOnly()
        } else {
            readSingleThread()
        }
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
            querySms()
            return true
        }
        result.error("#01", "permission denied", null)
        return false
    }

    init {
        this.mStart = start
        this.mCount = count
        this.threadId = threadId
        this.address = address
    }
}

internal class SmsQuery(val context: Context, private val binding: ActivityPluginBinding) :
    MethodCallHandler {
    private val permissions: Permissions = Permissions(context, binding.activity)
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        var start = 0
        var count = -1
        var threadId = -1
        var address: String? = null
        if (call.hasArgument("start")) {
            start = call.argument<Int>("start")!!
        }
        if (call.hasArgument("count")) {
            count = call.argument<Int>("count")!!
        }
        if (call.hasArgument("thread_id")) {
            threadId = call.argument<Int>("thread_id")!!
        }
        if (call.hasArgument("address")) {
            address = call.argument<String>("address")
        }
        val handler = SmsQueryHandler(context, result, start, count, threadId, address)
        binding.addRequestPermissionsResultListener(handler)
        handler.handle(permissions)
    }

}
