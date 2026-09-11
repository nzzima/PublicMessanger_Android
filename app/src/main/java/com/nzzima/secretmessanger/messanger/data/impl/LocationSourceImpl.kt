package com.nzzima.secretmessanger.messanger.data.impl

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import com.nzzima.secretmessanger.messanger.domain.api.LocationSource
import com.nzzima.secretmessanger.messanger.domain.models.Place
import com.nzzima.secretmessanger.utils.constants.Constants
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [LocationSource] поверх системного `LocationManager`.
 *
 * Без `play-services-location`: свежая точка нужна ровно один раз за отправку, а зависимость
 * потянула бы в сборку половину сервисов Google ради одного вызова.
 *
 * **Срок обязателен, и это не перестраховка.** Приёмник GPS в помещении не отвечает вовсе, а
 * не отвечает отказом: без срока отправка висела бы до собственного таймаута и показывала
 * «сервер не ответил» — про сервер, который в этот момент ничего не делал.
 *
 * Разрешение проверяет экран — сюда попадают уже с ним; `SuppressLint` именно об этом.
 */
class LocationSourceImpl(private val context: Context) : LocationSource {

    override suspend fun current(): Place? {
        val manager = context.getSystemService<LocationManager>() ?: return null
        val known = lastKnown(manager)

        // Свежая известная точка — лучший исход: она уже есть, ждать нечего.
        if (known != null && known.age() <= Constants.LOCATION_FRESH_MS) return known.place()

        val fresh = withTimeoutOrNull(Constants.LOCATION_TIMEOUT_MS) { request(manager) }

        // Устаревшая точка лучше, чем ничего: человек просил поделиться местом, а не
        // измерить его заново. Совсем ничего — честный отказ, его показывает экран.
        return (fresh ?: known)?.place()
    }

    /**
     * Самая свежая из известных точек по всем источникам.
     *
     * Спрашиваются оба: сеть знает место в помещении, GPS — на улице, и какой из них
     * ответит, заранее неизвестно.
     */
    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? = providers(manager)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }

    /**
     * Новая точка от первого ответившего источника.
     *
     * Сеть спрашивается раньше GPS намеренно: она отвечает грубее, но за секунды, а для
     * точки на карте этого достаточно. Ниже тридцатого API одноразового запроса нет вовсе —
     * там остаётся только известная точка.
     */
    private suspend fun request(manager: LocationManager): Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        return providers(manager)
            .sortedByDescending { it == LocationManager.NETWORK_PROVIDER }
            .firstNotNullOfOrNull { provider -> fresh(manager, provider) }
    }

    /** Включённые источники: у выключенного спрашивать нечего. */
    private fun providers(manager: LocationManager): List<String> =
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun fresh(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = android.os.CancellationSignal()
            val executor = Executors.newSingleThreadExecutor()

            manager.getCurrentLocation(provider, signal, executor) { location ->
                executor.shutdown()
                if (continuation.isActive) continuation.resume(location)
            }

            continuation.invokeOnCancellation {
                signal.cancel()
                executor.shutdown()
            }
        }

    private fun Location.age(): Long = System.currentTimeMillis() - time

    private fun Location.place(): Place = Place(latitude, longitude)
}
