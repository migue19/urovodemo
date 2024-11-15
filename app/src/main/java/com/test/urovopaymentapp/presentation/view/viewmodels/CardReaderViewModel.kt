package com.test.urovopaymentapp.presentation.view.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.test.urovopaymentapp.data.model.pos2.models.PosInputDatas
import com.test.urovopaymentapp.domain.repository.MyEmvListener
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class CardReaderViewModel @Inject constructor(
    private val myEmvListener: MyEmvListener,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val TAG = "CardReaderViewModel"
    private val payCardType = 1 //0:mag，1：ic/rfid Solo para mostrar la imagen del tipo de tarjeta

    val livePosInputDatas = myEmvListener.posInputDatas

    val resultEMV = myEmvListener.result

    fun initEmvListener(posInputDatas: PosInputDatas) {
        myEmvListener.initKernel(posInputDatas)
    }

    private val _hasNavigated = MutableLiveData(false)
    val hasNavigated: LiveData<Boolean> get() = _hasNavigated

    fun setHasNavigated() {
        _hasNavigated.value = true
    }
}