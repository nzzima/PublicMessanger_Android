package com.nzzima.secretmessanger.lock

import com.nzzima.secretmessanger.lock.domain.api.BiometricGate

/** [BiometricGate], которым распоряжается тест: есть ли на телефоне чем подтвердить. */
class FakeBiometricGate(var available: Boolean = false) : BiometricGate {

    override fun isAvailable(): Boolean = available
}
