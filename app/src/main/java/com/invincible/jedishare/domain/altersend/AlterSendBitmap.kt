package com.invincible.jedishare.domain.altersend

class AlterSendBitmap private constructor(
    val size: Int,
    private val bits: ByteArray
) {
    private var setBits: Int = 0

    constructor(size: Int) : this(size, ByteArray(byteLength(size)))

    init {
        require(size >= 0) { "Bitmap size must be non-negative" }
        require(bits.size == byteLength(size)) {
            "Bitmap is ${bits.size} bytes, expected ${byteLength(size)}"
        }
        for (index in 0 until size) {
            if (get(index)) setBits++
        }
    }

    fun get(index: Int): Boolean {
        if (index < 0 || index >= size) return false
        val byteIndex = index / 8
        val mask = 1 shl (index and 7)
        return (bits[byteIndex].toInt() and mask) != 0
    }

    fun set(index: Int) {
        require(index in 0 until size) { "Bitmap index out of range" }
        val byteIndex = index / 8
        val mask = 1 shl (index and 7)
        if ((bits[byteIndex].toInt() and mask) == 0) {
            bits[byteIndex] = (bits[byteIndex].toInt() or mask).toByte()
            setBits++
        }
    }

    fun count(): Int = setBits

    fun allSet(): Boolean = setBits == size

    fun missing(): List<Int> = buildList {
        for (index in 0 until this@AlterSendBitmap.size) {
            if (this@AlterSendBitmap.get(index).not()) add(index)
        }
    }

    fun serialize(): ByteArray = bits.copyOf()

    companion object {
        private fun byteLength(size: Int): Int = (size + 7) / 8

        fun deserialize(size: Int, bits: ByteArray): AlterSendBitmap =
            AlterSendBitmap(size, bits.copyOf())
    }
}
