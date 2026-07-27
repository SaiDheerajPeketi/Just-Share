package com.invincible.jedishare.domain.chat

import timber.log.Timber

import java.io.IOException

class TransferFailedException: IOException("Reading incoming data failed")