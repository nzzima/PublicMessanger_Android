package com.nzzima.secretmessanger.messanger.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants

/** Телефон не отдал координаты: ни свежих, ни последних известных. */
class PlaceUnknown : Exception(Constants.PLACE_UNKNOWN)
