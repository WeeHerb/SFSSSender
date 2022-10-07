package com.mslxl.sfss.controller

import com.mslxl.sfss.SFSSStatus
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class SenderAutomaton(val ip:String, val port:Int, val updateInfo: (String) -> Unit) {
    val socket = DatagramSocket()
    private var status: SFSSStatus = SFSSStatus.Disconnect(this)
    val statusName get() = status.javaClass.simpleName
    fun start(){
        thread {
            status.onEnter()
        }
    }

    fun switchState(state: SFSSStatus){
        status.onExit()
        status = state
        state.onEnter()
    }

    fun sendUDP(buf:ByteArray,ip:String, port:Int) {
        val packet = DatagramPacket(buf, 0, buf.size, InetAddress.getByName(ip), port)
        socket.send(packet)
    }
    fun sendUDP(buf:SendMsg,ip:String, port:Int) {
        val data = buf.packData()
        sendUDP(data, ip, port)
    }
    fun sendQR(msg:String) {
        status.sendQRCode(msg)
    }
}