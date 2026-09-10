package com.nzzima.secretmessanger.auth.domain

import com.nzzima.secretmessanger.auth.domain.api.AuthenticationRepository
import com.nzzima.secretmessanger.auth.domain.api.LoginRepository
import com.nzzima.secretmessanger.auth.domain.api.ProfileRepository
import com.nzzima.secretmessanger.auth.domain.api.RegistrationRepository
import com.nzzima.secretmessanger.auth.domain.models.AccountFailure
import com.nzzima.secretmessanger.auth.domain.models.LoginAvailability
import kotlinx.coroutines.awaitCancellation

/**
 * [RegistrationRepository] и [AuthenticationRepository] в памяти.
 *
 * [registerFails] задаёт отказ создания аккаунта, [knownCredentials] — пару «почта к
 * паролю», которую примет [signIn]; любая другая пара даёт
 * [AccountFailure.WrongCredentials]. При [hangs] обе операции не завершаются никогда.
 */
class FakeAccountRepository(
    private val uid: String = "uid-1",
    private val registerFails: Throwable? = null,
    private val knownCredentials: Pair<String, String>? = null,
    private val hangs: Boolean = false,
) : RegistrationRepository, AuthenticationRepository {

    var deleted = false
        private set

    override suspend fun register(email: String, password: String): Result<String> {
        if (hangs) awaitCancellation()
        return registerFails?.let { Result.failure(it) } ?: Result.success(uid)
    }

    override suspend fun signIn(email: String, password: String): Result<String> {
        if (hangs) awaitCancellation()
        return if (knownCredentials == Pair(email, password)) {
            Result.success(uid)
        } else {
            Result.failure(AccountFailure.WrongCredentials)
        }
    }

    override suspend fun deleteCurrent(): Result<Unit> {
        deleted = true
        return Result.success(Unit)
    }
}

/**
 * [LoginRepository] в памяти. [taken] задаёт занятые логины как «ключ реестра → uid»,
 * [claimFails] — отказ при попытке занять. При [hangs] [check] не завершается никогда.
 */
class FakeLoginRepository(
    private val taken: MutableMap<String, String> = mutableMapOf(),
    private val claimFails: Throwable? = null,
    private val hangs: Boolean = false,
    private val releaseFails: Throwable? = null,
    private val journal: MutableList<String> = mutableListOf(),
) : LoginRepository {

    var claimed = mutableListOf<String>()
        private set

    /** Отпущенные имена, в порядке вызова. */
    var released = mutableListOf<String>()
        private set

    /** Кто держит [login] в реестре; `null` — имя свободно. */
    fun owner(login: String): String? = taken[LoginRepository.key(login)]

    override suspend fun check(login: String, uid: String?): Result<LoginAvailability> {
        if (hangs) awaitCancellation()
        val owner = taken[LoginRepository.key(login)]
        return Result.success(
            when {
                owner == null -> LoginAvailability.FREE
                owner == uid -> LoginAvailability.MINE
                else -> LoginAvailability.TAKEN
            },
        )
    }

    override suspend fun claim(login: String, uid: String): Result<Unit> {
        claimFails?.let { return Result.failure(it) }
        taken[LoginRepository.key(login)] = uid
        claimed += login
        journal += "claim:$login"
        return Result.success(Unit)
    }

    override suspend fun release(login: String): Result<Unit> {
        releaseFails?.let { return Result.failure(it) }
        taken.remove(LoginRepository.key(login))
        released += login
        journal += "release:$login"
        return Result.success(Unit)
    }
}

/** [ProfileRepository] в памяти. Хранит последний записанный профиль. */
/**
 * [ProfileRepository] в памяти.
 *
 * [withProfile] перечисляет аккаунты, у которых профиль уже есть; [existsFails] задаёт отказ
 * чтения — им проверяется, что оболочка не путает «профиля нет» с «не удалось спросить».
 */
class FakeProfileRepository(
    private val withProfile: MutableSet<String> = mutableSetOf(),
    private val existsFails: Throwable? = null,
    private val updateFails: Throwable? = null,
    private val journal: MutableList<String> = mutableListOf(),
) : ProfileRepository {

    var created: Triple<String, String, String>? = null
        private set

    /** Поля, записанные последней правкой. */
    var updated: ProfileFields? = null
        private set

    override suspend fun createProfile(uid: String, login: String, name: String): Result<Unit> {
        created = Triple(uid, login, name)
        withProfile += uid
        return Result.success(Unit)
    }

    override suspend fun updateProfile(
        uid: String,
        login: String,
        name: String,
        someInfo: String,
    ): Result<Unit> {
        updateFails?.let { return Result.failure(it) }

        updated = ProfileFields(uid, login, name, someInfo)
        journal += "profile:$login"
        return Result.success(Unit)
    }

    override suspend fun exists(uid: String): Result<Boolean> =
        existsFails?.let { Result.failure(it) } ?: Result.success(uid in withProfile)
}

/** Поля профиля, записанные правкой. */
data class ProfileFields(val uid: String, val login: String, val name: String, val someInfo: String)
