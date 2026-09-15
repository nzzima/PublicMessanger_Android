package com.nzzima.secretmessanger.profile.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants

/** Документа профиля нет: регистрация оборвалась на его записи либо аккаунт заведён из консоли. */
class ProfileMissing : Exception(Constants.PROFILE_MISSING)
