package com.mslxl.sfss

import com.mslxl.sfss.controller.RecvMsg
import com.mslxl.sfss.controller.SendMsg
import com.mslxl.sfss.controller.SenderAutomaton
import java.net.DatagramPacket
import java.net.InetAddress
import java.nio.charset.Charset


sealed class SFSSStatus {


    class Working(val sender: SenderAutomaton) : SFSSStatus() {
        val receiver = arrayListOf<Pair<InetAddress, Int>>()
        override fun onExit() {

        }

        override fun onEnter() {
            while(true){
                if (sender.socket.receiveBufferSize != 0) {
                    val packet = DatagramPacket(ByteArray(1024), 1024)
                    sender.socket.receive(packet)
                    when(val msg = RecvMsg.from(packet)){
                        is RecvMsg.Done -> Unit
                        is RecvMsg.RespondIP -> {
                            sender.updateInfo("Trust IP")
                            sender.sendUDP(byteArrayOf(1,1,4,5,1,4), msg.ip, msg.port)
                            sender.sendUDP(SendMsg.Done(), sender.ip, sender.port)
                        }
                        is RecvMsg.RequestHeartbeat -> {
                            sender.updateInfo("Heartbeat")
                            sender.sendUDP(SendMsg.Heartbeat(), sender.ip, sender.port)
                        }
                        is RecvMsg.RequestResend -> sender.updateInfo("Illegal recv msg?")
                        else -> sender.updateInfo("Illegal recv msg?")
                    }
                }
                Thread.sleep(1000)
            }
        }

        override fun sendQRCode(msg: String) {
            sender.updateInfo("Sync $msg")
            val qrcode = msg.toByteArray(Charsets.UTF_8)
            val len = qrcode.size
            val msg = byteArrayOf(5, (len ushr 8).toByte(), (len and Byte.MAX_VALUE.toInt()).toByte(), *qrcode)
            receiver.forEach {
                val packet = DatagramPacket(msg, 0,msg.size, it.first, it.second)
                sender.socket.send(packet)
            }
        }
    }

    class Disconnect(val sender: SenderAutomaton) : SFSSStatus() {
        override fun onExit() {

        }

        override fun onEnter() {
            sender.updateInfo("Connecting server")
            while (true) {
                sender.sendUDP(SendMsg.Handshake(), sender.ip, sender.port)
                Thread.sleep(5000)
                if (sender.socket.receiveBufferSize != 0) {
                    val packet = DatagramPacket(ByteArray(1024), 1024)
                    sender.socket.receive(packet)
                    val msg = RecvMsg.from(packet)
                    if (msg is RecvMsg.Done) {
                        sender.switchState(Working(sender))
                    } else {
                        sender.updateInfo("Bad response from server ${packet.data}")
                    }
                    break
                }
            }

        }

        override fun sendQRCode(msg: String) {

        }
    }

    abstract fun onExit()
    abstract fun onEnter()
    abstract fun sendQRCode(msg: String)
}


