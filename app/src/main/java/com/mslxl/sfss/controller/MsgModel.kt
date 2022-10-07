package com.mslxl.sfss.controller

import java.net.DatagramPacket

sealed class RecvMsg {
    companion object {
        fun from(msg: ByteArray): RecvMsg? = when (msg[0].toUByte().toInt()) {
            2 -> RequestHeartbeat()
            3 -> RespondIP().apply { unpack(msg) }
            11 -> Done()
            else -> null
        }

        fun from(data: DatagramPacket) : RecvMsg? {
            return from(data.data)
        }

    }
    class RespondIP: RecvMsg(){
        lateinit var ip:String
        var port: Int = -1
        override fun unpack(data: ByteArray) {
            ip = "${data[1].toUByte()}.${data[2].toUByte()}.${data[3].toUByte()}.${data[4].toUByte()}"
            port = data[5].toUByte().toInt().shl(8) or data[6].toUByte().toInt()
        }
    }
    class RequestHeartbeat : RecvMsg() {
        override fun unpack(data: ByteArray) = Unit
    }

    class RequestResend : RecvMsg() {
        override fun unpack(data: ByteArray)  = Unit
    }

    class Done: RecvMsg(){
        override fun unpack(data: ByteArray) = Unit
    }


    abstract fun unpack(data: ByteArray)

}

sealed class SendMsg(val id: Byte) {
    class Handshake : SendMsg(1) {
        override fun packData(): ByteArray = byteArrayOf(id)
    }

    class Heartbeat : SendMsg(22) {
        override fun packData(): ByteArray = byteArrayOf(id)
    }

    class RequestIPList : SendMsg(33) {
        override fun packData(): ByteArray = byteArrayOf(id)
    }
    class Done : SendMsg(11) {
        override fun packData(): ByteArray  = byteArrayOf(id)
    }

    abstract fun packData(): ByteArray
}