package com.drabatx.urovopaymentapp.utils

sealed class UrovoResult<out R> {
    data class Success<out T>(val data: T) : UrovoResult<T>()
    data class Error(val exception: Throwable) : UrovoResult<Nothing>()
    object Loading: UrovoResult<Nothing>()
    object Initial: UrovoResult<Nothing>()
}