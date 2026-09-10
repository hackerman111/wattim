package io.ronesec.domain.codes

import java.security.MessageDigest
import java.security.SecureRandom

interface SessionCodePort {
    fun generate(length: Int): String
    fun matches(expected: String, entered: String): Boolean
}

object SecureSessionCodePort : SessionCodePort {
    private val random = SecureRandom()
    override fun generate(length: Int): String {
        require(length in 1..10)
        return buildString(length) { repeat(length) { append(random.nextInt(10)) } }
    }

    override fun matches(expected: String, entered: String): Boolean =
        entered.length == expected.length && entered.all { it in '0'..'9' } &&
            MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), entered.toByteArray(Charsets.US_ASCII))
}
