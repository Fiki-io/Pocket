package com.pocketsamp.server

import com.pocketsamp.core.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

object RconClient {

    suspend fun sendCommand(
        port: Int,
        password: String,
        command: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (password.isBlank()) {
            return@withContext Result.failure(IllegalStateException("rcon_password kosong"))
        }
        if (command.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Perintah kosong"))
        }
        try {
            val packetBytes = buildRconPacket(port, password, command)
            val address = InetAddress.getByName("127.0.0.1")
            val responses = mutableListOf<String>()

            DatagramSocket().use { socket ->
                socket.soTimeout = AppConstants.RCON_TIMEOUT_MS
                socket.send(DatagramPacket(packetBytes, packetBytes.size, address, port))

                val recvBuffer = ByteArray(4096)
                var receiveTimeout = AppConstants.RCON_TIMEOUT_MS
                for (i in 0 until 24) {
                    socket.soTimeout = receiveTimeout
                    val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
                    try {
                        socket.receive(recvPacket)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    val line = parseRconResponse(recvPacket.data, recvPacket.length)
                    if (line != null && line.isNotEmpty()) {
                        responses.add(line)
                    }
                    receiveTimeout = 200
                }
            }
            Result.success(responses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildRconPacket(port: Int, password: String, command: String): ByteArray {
        val passBytes = password.toByteArray(StandardCharsets.UTF_8)
        val cmdBytes = command.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArrayOutputStream(32 + passBytes.size + cmdBytes.size)
        out.write('S'.code)
        out.write('A'.code)
        out.write('M'.code)
        out.write('P'.code)
        out.write(127)
        out.write(0)
        out.write(0)
        out.write(1)
        out.write(port and 0xFF)
        out.write((port ushr 8) and 0xFF)
        out.write('x'.code)
        out.write(passBytes.size and 0xFF)
        out.write((passBytes.size ushr 8) and 0xFF)
        out.write(passBytes)
        out.write(cmdBytes.size and 0xFF)
        out.write((cmdBytes.size ushr 8) and 0xFF)
        out.write(cmdBytes)
        return out.toByteArray()
    }

    private fun parseRconResponse(data: ByteArray, length: Int): String? {
        if (length < 13) return null
        if (data[0] != 'S'.code.toByte() ||
            data[1] != 'A'.code.toByte() ||
            data[2] != 'M'.code.toByte() ||
            data[3] != 'P'.code.toByte()
        ) {
            return null
        }
        val msgLen = (data[11].toInt() and 0xFF) or ((data[12].toInt() and 0xFF) shl 8)
        if (msgLen <= 0 || 13 + msgLen > length) return null
        return String(data, 13, msgLen, StandardCharsets.UTF_8)
    }
}
