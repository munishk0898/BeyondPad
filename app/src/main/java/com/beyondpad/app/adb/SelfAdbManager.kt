package com.beyondpad.app.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/**
 * BeyondPad's identity towards the tablet's own ADB daemon (Wireless debugging).
 * The RSA key and self-signed certificate are created once and kept in the app's private storage;
 * the user authorises them once by pairing with a code, exactly like pairing a computer.
 */
class SelfAdbManager private constructor(ctx: Context) : AbsAdbConnectionManager() {

    private val key: PrivateKey
    private val cert: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val keyFile = File(ctx.filesDir, "adb_key.pk8")
        val certFile = File(ctx.filesDir, "adb_cert.der")
        if (keyFile.exists() && certFile.exists()) {
            key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        } else {
            val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }
            val pair = kpg.generateKeyPair()
            val name = X500Name("CN=BeyondPad")
            val now = System.currentTimeMillis()
            val holder = JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(SecureRandom().nextLong() and Long.MAX_VALUE),
                Date(now - 86_400_000L), Date(now + 30L * 365 * 86_400_000L), name, pair.public,
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
            key = pair.private
            cert = JcaX509CertificateConverter().getCertificate(holder)
            keyFile.writeBytes(key.encoded)
            certFile.writeBytes(cert.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = key
    override fun getCertificate(): Certificate = cert
    override fun getDeviceName(): String = "BeyondPad"

    companion object {
        @Volatile private var instance: SelfAdbManager? = null
        fun get(ctx: Context): SelfAdbManager =
            instance ?: synchronized(this) { instance ?: SelfAdbManager(ctx.applicationContext).also { instance = it } }
    }
}
