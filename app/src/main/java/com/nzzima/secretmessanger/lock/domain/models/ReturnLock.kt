package com.nzzima.secretmessanger.lock.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Запирать ли приложение на возврате — решение из двух величин: где мы были и сколько
 * отсутствовали.
 *
 * Вынесено сюда отдельным правилом, потому что внутри экрана его проверить нечем: там
 * жизненный цикл и системные события, а здесь два числа.
 */
object ReturnLock {

    /**
     * Запирать ли приложение.
     *
     * @param wasReady было ли открыто рабочее окно. На самом замке и на входе запирать
     *   нечего, а лишний переход сбросил бы наполовину введённый пароль.
     * @param leftAt когда ушли в фон в миллисекундах; `null` — не уходили вовсе.
     */
    fun shouldLock(wasReady: Boolean, leftAt: Long?, now: Long): Boolean {
        if (!wasReady || leftAt == null) return false

        val away = now - leftAt

        // Отрицательная разница — часы перевели назад, пока нас не было. Ушли мы при этом на
        // неизвестный срок, и считать это «отвлёкся на секунду» нельзя: безопаснее спросить.
        return away < 0 || away > Constants.LOCK_GRACE_MS
    }
}
