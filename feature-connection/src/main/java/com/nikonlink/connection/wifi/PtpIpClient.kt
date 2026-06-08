package com.nikonlink.connection.wifi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal PTP-IP (Picture Transfer Protocol over TCP/IP) client.
 *
 * Nikon cameras expose a PTP-IP server on port 15740 when in WiFi mode.
 */
@Singleton
class PtpIpClient() {

    companion object {
        const val PTP_IP_PORT = 15740
        const val INIT_COMMAND_REQUEST = 1
        const val INIT_EVENT_REQUEST = 2
        const val INIT_RESPONSE_OK = 3
    }

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var sessionId: Int = 0

    val isConnected: Boolean get() = socket?.isConnected == true && socket?.isClosed == false

    /**
     * Connect to the camera PTP-IP server and perform the init handshake.
     */
    suspend fun connect(host: String = "192.168.1.1", port: Int = PTP_IP_PORT): kotlin.Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                socket = Socket().also {
                    it.connect(InetSocketAddress(host, port), 5000)
                    it.soTimeout = 10000
                }
                input = DataInputStream(socket!!.getInputStream())
                output = DataOutputStream(socket!!.getOutputStream())

                // PTP-IP Init Command Request
                val initPacket = buildInitPacket(INIT_COMMAND_REQUEST)
                output!!.write(initPacket)
                output!!.flush()

                // Read Init Command Ack
                val ackPacket = ByteArray(12)
                input!!.readFully(ackPacket)
                sessionId = ((ackPacket[8].toInt() and 0xFF) shl 24) or
                        ((ackPacket[9].toInt() and 0xFF) shl 16) or
                        ((ackPacket[10].toInt() and 0xFF) shl 8) or
                        (ackPacket[11].toInt() and 0xFF)

                // PTP-IP Init Event Request
                val eventPacket = buildInitPacket(INIT_EVENT_REQUEST)
                output!!.write(eventPacket)
                output!!.flush()

                // Read Init Event Ack
                input!!.readFully(ackPacket)

                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                disconnect()
                kotlin.Result.failure(e)
            }
        }

    /**
     * Send a raw PTP command packet and receive the response.
     */
    suspend fun sendCommand(commandCode: Int, params: List<Int> = emptyList()): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val packet = buildCommandPacket(commandCode, params)
                output?.write(packet)
                output?.flush()

                // Read response header: length(4) + type(2) + code(2) + transaction(4) + paramCount(4)
                val header = ByteArray(16)
                input?.readFully(header)

                val length = ((header[0].toInt() and 0xFF) shl 24) or
                        ((header[1].toInt() and 0xFF) shl 16) or
                        ((header[2].toInt() and 0xFF) shl 8) or
                        (header[3].toInt() and 0xFF)

                if (length > 16) {
                    val data = ByteArray(length - 16)
                    input?.readFully(data)
                    data
                } else {
                    ByteArray(0)
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Read raw data from the socket (for file downloads).
     */
    suspend fun readData(length: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(length)
            var totalRead = 0
            while (totalRead < length) {
                val read = input?.read(buffer, totalRead, length - totalRead) ?: -1
                if (read == -1) break
                totalRead += read
            }
            buffer.copyOf(totalRead)
        } catch (e: Exception) {
            null
        }
    }

    fun disconnect() {
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        input = null
        output = null
    }

    private fun buildInitPacket(requestType: Int): ByteArray {
        return ByteArray(12).apply {
            this[0] = 0; this[1] = 0; this[2] = 0; this[3] = 12
            this[4] = 0; this[5] = 0; this[6] = 0; this[7] = requestType.toByte()
            this[8] = 0; this[9] = 0; this[10] = 0; this[11] = 0
        }
    }

    private fun buildCommandPacket(code: Int, params: List<Int>): ByteArray {
        val payloadSize = 12 + 4 + params.size * 4
        val packet = ByteArray(payloadSize)

        packet[0] = ((payloadSize shr 24) and 0xFF).toByte()
        packet[1] = ((payloadSize shr 16) and 0xFF).toByte()
        packet[2] = ((payloadSize shr 8) and 0xFF).toByte()
        packet[3] = (payloadSize and 0xFF).toByte()

        packet[6] = 0; packet[7] = 1 // type = Command

        packet[8] = ((code shr 8) and 0xFF).toByte()
        packet[9] = (code and 0xFF).toByte()

        packet[12] = 0; packet[13] = 0; packet[14] = 0; packet[15] = 1

        params.forEachIndexed { i, param ->
            val offset = 16 + i * 4
            packet[offset] = ((param shr 24) and 0xFF).toByte()
            packet[offset + 1] = ((param shr 16) and 0xFF).toByte()
            packet[offset + 2] = ((param shr 8) and 0xFF).toByte()
            packet[offset + 3] = (param and 0xFF).toByte()
        }

        return packet
    }
}
