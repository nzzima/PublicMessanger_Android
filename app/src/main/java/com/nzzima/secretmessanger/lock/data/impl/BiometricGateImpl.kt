package com.nzzima.secretmessanger.lock.data.impl

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import com.nzzima.secretmessanger.lock.domain.api.BiometricGate

/**
 * [BiometricGate] поверх `BiometricManager`.
 *
 * Мерка одна на проверку и на сам запрос — см. [BiometricGate.isAvailable]. Код-пароль
 * устройства входит в неё намеренно: рубеж от этого не опускается, потому что за тем же
 * код-паролем лежит и хранилище, где закрыт ключ от всей переписки.
 */
class BiometricGateImpl(private val context: Context) : BiometricGate {

    override fun isAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(allowed()) == BiometricManager.BIOMETRIC_SUCCESS

    private companion object {

        /**
         * Что принимается за подтверждение.
         *
         * Ниже тридцатого API пара «биометрия + код-пароль» одним доводом не выражается —
         * там остаётся сама биометрия, а код-пароль подключается отдельной настройкой
         * запроса. Поэтому и здесь мерка разная по версиям: спрашивать надо ровно о том, что
         * потом будет предложено человеку.
         */
        fun allowed(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
            } else {
                Authenticators.BIOMETRIC_WEAK
            }
    }
}
