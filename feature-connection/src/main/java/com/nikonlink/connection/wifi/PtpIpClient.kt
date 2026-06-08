package com.nikonlink.connection.wifi

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import javax.inject.Singleton

/**
 * PTP-IP (Picture Transfer Protocol over TCP/IP) client for Nikon cameras.
 *
 * Nikon cameras expose a PTP-IP server on port 15740 when in WiFi mode
 * (either as a WiFi hotspot or connected to the same network).
 */
@Singleton
class PtpIpClient() {

    companion object {
        const val PTP_IP_PORT = 15740
        private const val TAG = "NikonLink-PTP"
    }

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var sessionId: Int = 0

    val isConnected: Boolean get() = socket?.isConnected == true && socket?.isClosed == false

    /**
     * Connect to the camera PTP-IP server with proper init handshake.
     */
    suspend fun connect(host: String = "192.168.1.1", port: Int = PTP_IP_PORT): kotlin.Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Opening socket to $host:$port")
                socket = Socket().also {
                    it.connect(InetSocketAddress(host, port), 5000)
                    it.soTimeout = 15000
                }
                input = DataInputStream(socket!!.getInputStream())
                output = DataOutputStream(socket!!.getOutputStream())

                // Step 1: Send Init Command Request with GUID and hostname
                val guid = generateGuid()
                val hostName = "NikonLink"
                val initCmdReq = buildInitCommandRequest(guid, hostName)
                Log.d(TAG, "Sending InitCommandRequest (${initCmdReq.size} bytes)")
                output!!.write(initCmdReq)
                output!!.flush()

                // Step 2: Read Init Command Response
                val cmdRespHeader = ByteArray(8)
                input!!.readFully(cmdRespHeader)
                val cmdRespLen = readInt32(cmdRespHeader, 0)
                val cmdRespType = readInt32(cmdRespHeader, 4)
                Log.d(TAG, "InitCmdResponse: len=$cmdRespLen type=$cmdRespType")

                if (cmdRespType != 3) { // InitCommandResponse should be type 3
                    Log.w(TAG, "Unexpected InitCmdResponse type: $cmdRespType")
                }

                // Read remaining response data: connection_number(4) + camera_guid(16) + camera_name
                val cmdRemaining = cmdRespLen - 8
                if (cmdRemaining > 0) {
                    val rest = ByteArray(cmdRemaining)
                    input!!.readFully(rest)
                    sessionId = readInt32(rest, 0)
                    Log.d(TAG, "Got session/connection ID: $sessionId")
                }

                // Step 3: Send Init Event Request
                val initEventReq = buildInitEventRequest(sessionId)
                Log.d(TAG, "Sending InitEventRequest (${initEventReq.size} bytes) connId=$sessionId")
                output!!.write(initEventReq)
                output!!.flush()

                // Step 4: Read Init Event Response
                val eventRespHeader = ByteArray(8)
                input!!.readFully(eventRespHeader)
                val eventRespLen = readInt32(eventRespHeader, 0)
                val eventRespType = readInt32(eventRespHeader, 4)
                Log.d(TAG, "InitEventResponse: len=$eventRespLen type=$eventRespType")

                // Read remaining event response data
                val eventRemaining = eventRespLen - 8
                if (eventRemaining > 0) {
                    val rest = ByteArray(eventRemaining)
                    input!!.readFully(rest)
                }

                Log.d(TAG, "PTP-IP handshake complete, session=$sessionId")
                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "PTP-IP connection failed", e)
                disconnect()
                kotlin.Result.failure(e)
            }
        }

    suspend fun sendCommand(commandCode: Int, params: List<Int> = emptyList()): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val packet = buildCommandPacket(commandCode, sessionId, params)
                output?.write(packet)
                output?.flush()

                val header = ByteArray(8)
                input?.readFully(header)
                val length = readInt32(header, 0)
                val type = readInt32(header, 4)

                if (length > 8) {
                    val data = ByteArray(length - 8)
                    input?.readFully(data)
                    data
                } else {
                    ByteArray(0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendCommand(0x${commandCode.toString(16)}) failed", e)
                null
            }
        }

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

    // --- Packet builders ---

    /** Build Init Command Request: len(4) + type=1(4) + guid(16) + hostname_len(2) + hostname_utf16 */
    private fun buildInitCommandRequest(guid: ByteArray, hostName: String): ByteArray {
        val hostBytes = hostName.toByteArray(Charsets.UTF_16LE)
        val packetLen = 8 + 16 + 2 + hostBytes.size // header + guid + name_len + name
        val packet = ByteArray(packetLen)
        writeInt32(packet, 0, packetLen)
        writeInt32(packet, 4, 1) // type = InitCommandRequest
        System.arraycopy(guid, 0, packet, 8, 16)
        writeInt16(packet, 24, hostBytes.size)
        System.arraycopy(hostBytes, 0, packet, 26, hostBytes.size)
        return packet
    }

    /** Build Init Event Request: len(4) + type=2(4) + connection_number(4) */
    private fun buildInitEventRequest(connectionNumber: Int): ByteArray {
        val packet = ByteArray(12)
        writeInt32(packet, 0, 12)
        writeInt32(packet, 4, 2) // type = InitEventRequest
        writeInt32(packet, 8, connectionNumber)
        return packet
    }

    /** Build PTP command packet: len(4) + type=6(Command)(4) + code(2) + transaction(4) + params... */
    private fun buildCommandPacket(code: Int, session: Int, params: List<Int>): ByteArray {
        val dataLen = 2 + 4 + params.size * 4 // code + transaction + params
        val packetLen = 8 + dataLen
        val packet = ByteArray(packetLen)

        writeInt32(packet, 0, packetLen)
        writeInt32(packet, 4, 6) // type = Command (PTP operation)
        writeInt16(packet, 8, code)
        writeInt32(packet, 10, session) // transaction ID
        params.forEachIndexed { i, param ->
            writeInt32(packet, 14 + i * 4, param)
        }
        return packet
    }

    // --- Helpers ---

    private fun generateGuid(): ByteArray {
        val uuid = UUID.randomUUID()
        val bytes = ByteArray(16)
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        for (i in 0..7) bytes[i] = (msb shr ((7 - i) * 8)).toByte()
        for (i in 0..7) bytes[8 + i] = (lsb shr ((7 - i) * 8)).toByte()
        return bytes
    }

    private fun readInt32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)

    private fun writeInt32(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value shr 24) and 0xFF).toByte()
        data[offset + 1] = ((value shr 16) and 0xFF).toByte()
        data[offset + 2] = ((value shr 8) and 0xFF).toByte()
        data[offset + 3] = (value and 0xFF).toByte()
    }

    private fun writeInt16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value shr 8) and 0xFF).toByte()
        data[offset + 1] = (value and 0xFF).toByte()
    }
}
