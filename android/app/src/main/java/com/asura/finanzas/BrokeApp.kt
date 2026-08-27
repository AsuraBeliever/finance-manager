package com.asura.finanzas

import android.app.Application
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.JsonCache
import com.asura.finanzas.data.RpcClient
import com.asura.finanzas.data.SessionCookieJar

/**
 * Hand-rolled container instead of a DI framework: this app has exactly three
 * long-lived objects, and a framework would cost more to read than it saves.
 */
class BrokeApp : Application() {

    lateinit var cookieJar: SessionCookieJar
        private set
    lateinit var repository: BrokeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        cookieJar = SessionCookieJar(this)
        val rpc = RpcClient(cookieJar)
        repository = BrokeRepository(rpc, JsonCache(this), cookieJar)
    }
}
