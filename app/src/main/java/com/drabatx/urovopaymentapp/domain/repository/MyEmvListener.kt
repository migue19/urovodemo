package com.drabatx.urovopaymentapp.domain.repository

import android.device.SEManager
import android.os.Bundle
import android.os.IInputActionListener
import android.os.RemoteException
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.drabatx.urovopaymentapp.data.model.pos2.models.PosInputDatas
import com.drabatx.urovopaymentapp.domain.model.EmvReason
import com.drabatx.urovopaymentapp.domain.model.EmvReasonsModel
import com.drabatx.urovopaymentapp.utils.UrovoResult
import com.lib.card.constant.CardTypeConstant
import com.urovo.i9000s.api.emv.ContantPara
import com.urovo.i9000s.api.emv.ContantPara.CheckCardResult
import com.urovo.i9000s.api.emv.ContantPara.IssuerScriptResult
import com.urovo.i9000s.api.emv.ContantPara.NfcErrMessageID
import com.urovo.i9000s.api.emv.ContantPara.NfcTipMessageID
import com.urovo.i9000s.api.emv.ContantPara.NfcTransResult
import com.urovo.i9000s.api.emv.ContantPara.PinEntrySource
import com.urovo.i9000s.api.emv.ContantPara.TransactionResult
import com.urovo.i9000s.api.emv.EmvListener
import com.urovo.i9000s.api.emv.EmvNfcKernelApi
import com.urovo.i9000s.api.emv.Funs
import com.urovo.sdk.beeper.BeeperImpl
import com.urovo.sdk.led.LEDDriverImpl
import java.util.Hashtable
import java.util.Locale
import javax.inject.Inject


class MyEmvListener @Inject constructor(
    private val mKernelApi: EmvNfcKernelApi,
    private val iBeeper: BeeperImpl,
    private val iLed: LEDDriverImpl
) : EmvListener {
    private val TAG = "MyEmvListener"

    private val _isRequestOnlineProcess = MutableLiveData(false)
    val isRequestOnlineProcess: LiveData<Boolean> get() = _isRequestOnlineProcess

    private val _result = MutableLiveData<UrovoResult<PosInputDatas>>(UrovoResult.Initial)
    val result: LiveData<UrovoResult<PosInputDatas>> get() = _result

    private val _reasonsEMV = MutableLiveData<EmvReasonsModel>()
    val reasonsEMV: LiveData<EmvReasonsModel> get() = _reasonsEMV

    private val _posInputDatas: MutableLiveData<PosInputDatas> = MutableLiveData()
    val posInputDatas: LiveData<PosInputDatas> get() = _posInputDatas


    fun setPosData(posData: PosInputDatas) {
        _posInputDatas.postValue(posData)
    }

    override fun onRequestSetAmount() {
        Log.i(TAG, "onRequestSetAmount: ${posInputDatas.value?.amt}")
        Log.i(TAG, "MainActivity  onRequestSetAmount")
    }

    override fun onReturnCheckCardResult(
        checkCardResult: CheckCardResult,
        hashtable: Hashtable<String, String>
    ) {

        Log.i(TAG, "onReturnCheckCardResult checkCardResult =$checkCardResult")
        Log.d(TAG, hashtable.toString())
        if (checkCardResult == CheckCardResult.MSR) {
            val stripStr = hashtable["StripInfo"]!!.uppercase(Locale.getDefault())
            val cardNo = hashtable["CardNo"]


            val hstr1 = Funs.TLV_Find("D1", stripStr)
            val hstr2 = Funs.TLV_Find("D2", stripStr)
            val hstr3 = Funs.TLV_Find("D3", stripStr)
            val track3: String
            val track1 = if (hstr1 == "") {
                ""
            } else {
                String(Funs.StrToHexByte(hstr1))
            }
            val track2 = if (hstr2 == "") {
                ""
            } else {
                String(Funs.StrToHexByte(hstr2))
            }
            if (hstr3 != "") {
                track3 = String(Funs.StrToHexByte(hstr3))
                _posInputDatas.postValue(_posInputDatas.value?.update(track3 = track3))
            }
            var index = track2.indexOf("=")
            if (index != -1) {
                val PAN = track2.substring(0, index) //Obtener el número de tarjeta
                index++
                val EXPIRED_DATE = track2.substring(index, index + 4)
                val SERVICE_CODE = track2.substring(index + 4, index + 4 + 3)
                _posInputDatas.postValue(
                    _posInputDatas.value?.update(
                        pan = PAN,
                        track2 = track2,
                        szExpDate = EXPIRED_DATE
                    )
                )

            }
            _posInputDatas.postValue(_posInputDatas.value?.update(swipedMode = CardTypeConstant.MSR))

            _reasonsEMV.postValue(
                EmvReasonsModel.Builder()
                    .setReason(EmvReason.MESSAGE_MSR)
                    .setMessage(cardNo ?: "").build()
            )
        } else if (checkCardResult == CheckCardResult.NEED_FALLBACK) {
            _reasonsEMV.postValue(
                EmvReasonsModel.Builder()
                    .setReason(EmvReason.MESSAGE_ERROR)
                    .setMessage("NEED_FALLBACK ! Please Tap or Swipe Card!").build()
            )
        } else if (checkCardResult == CheckCardResult.BAD_SWIPE) {
            _reasonsEMV.postValue(
                EmvReasonsModel.Builder()
                    .setReason(EmvReason.MESSAGE_ERROR)
                    .setMessage("BAD_SWIPE ! Please Tap or Swipe Card!").build()
            )
        } else if (checkCardResult == CheckCardResult.NOT_ICC) {
            _reasonsEMV.postValue(
                EmvReasonsModel.Builder()
                    .setReason(EmvReason.MESSAGE_ERROR)
                    .setMessage("NOT_ICC, Remove and Insert Card Again!").build()
            )
        } else if (checkCardResult == CheckCardResult.TIMEOUT) {
            _reasonsEMV.postValue(
                EmvReasonsModel.Builder()
                    .setReason(EmvReason.MESSAGE_TIMEOUT)
                    .setMessage("Check Card Time Out!").build()
            )
        } else if (checkCardResult == CheckCardResult.CANCEL) {
            _reasonsEMV.postValue(
                EmvReasonsModel.Builder()
                    .setReason(EmvReason.CANCEL_OPERATION)
                    .setMessage("Cancel Operation!").build()
            )
        } else if (checkCardResult == CheckCardResult.DEVICE_BUSY) {
            _reasonsEMV.postValue(
                EmvReasonsModel.Builder()
                    .setReason(EmvReason.MESSAGE_ERROR)
                    .setMessage("Check Card Device Busy !").build()
            )
        } else if (checkCardResult == CheckCardResult.USE_ICC_CARD) {
            _reasonsEMV.postValue(
                EmvReasonsModel.Builder()
                    .setReason(EmvReason.MESSAGE_ERROR)
                    .setMessage("Chip Card! Please Use Contact Interface,Please Insert Card!")
                    .build()
            )
        } else if (checkCardResult == CheckCardResult.MULT_CARD) {
            _reasonsEMV.postValue(
                EmvReasonsModel.Builder()
                    .setReason(EmvReason.MESSAGE_ERROR)
                    .setMessage("Multi Card! Please Insert One Card!")
                    .build()
            )
        }
    }

    override fun onRequestSelectApplication(arrayList: ArrayList<String>) {
        Log.i(TAG, "MainActivity  onRequestSelectApplication")
        var i = 0
        while (i < arrayList.size) {
            Log.d(TAG, "app name " + i + " : " + arrayList[i])
            i++
        }
        if (i == 1) {
            mKernelApi.selectApplication(0)
        } else {
            _reasonsEMV.postValue(
                EmvReasonsModel.Builder()
                    .setReason(EmvReason.MESSAGE_APP_SELECT)
                    .setMessage(arrayList.joinToString(", "))
                    .build()
            )
        }
    }


    //if contact online pin verify, will callback
    override fun onRequestPinEntry(pinEntrySource: PinEntrySource) {
        Log.i(TAG, "MainActivity  onRequestPinEntry request online pin")
        if (pinEntrySource == PinEntrySource.KEYPAD) {
            _posInputDatas.postValue(_posInputDatas.value?.update(pan = GetCardNo()))
            if (pinEntrySource == PinEntrySource.KEYPAD) {
                _posInputDatas.postValue(_posInputDatas.value?.update(pan = GetCardNo()))
                _reasonsEMV.postValue(EmvReasonsModel("Request Pin", EmvReason.MESSAGE_REQUEST_PIN))
            }
        }
    }

    override fun onRequestOfflinePinEntry(pinEntrySource: PinEntrySource, PinTryCount: Int) {
        Log.i(TAG, "MainActivity  onRequestOfflinePinEntry")
    }

    override fun onRequestConfirmCardno() {
        Log.d(TAG, "CardNo:" + GetCardNo())
        _posInputDatas.postValue(_posInputDatas.value?.update(swipedMode = CardTypeConstant.IC))
        iBeeper.startBeep(1, 200)
        try {
            iLed.turnOn(1)
            iLed.turnOn(2)
            iLed.turnOn(3)
            iLed.turnOn(4)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }

        val pan = GetCardNo()
        _posInputDatas.postValue(
            _posInputDatas.value?.update(
                pan = pan,
                track2 = mKernelApi.getValByTag(0x57),
                szCardSeqNo = mKernelApi.getValByTag(0x5F34),
                szExpDate = mKernelApi.getValByTag(0x5F24)
            )
        )
        //Se auto confirma la tarjeta
        mKernelApi.sendConfirmCardnoResult(true)
    }

    override fun onRequestFinalConfirm() {
        Log.i(TAG, "onRequestFinalConfirm: ")
        mKernelApi.sendFinalConfirmResult(true)
    }

    override fun onRequestOnlineProcess(cardTlvData: String, dataKsn: String) {
        _posInputDatas.postValue(
            _posInputDatas.value?.update(
                pan = GetCardNo(),
                track2 = mKernelApi.getValByTag(0x57),
                szCardSeqNo = mKernelApi.getValByTag(0x5F34),
                szExpDate = mKernelApi.getValByTag(0x5F24),
                file55 = cardTlvData
            )
        )
        val TVR = mKernelApi.getValByTag(0x95)
        Log.i(TAG, "  onRequestOnlineProcess TVR:$TVR")
        val PSN = mKernelApi.getValByTag(0x5F34)
        Log.i(TAG, "MainActivity  onRequestOnlineProcess PSN:$PSN")

        _isRequestOnlineProcess.postValue(true)
        _reasonsEMV.postValue(
            EmvReasonsModel.Builder().setReason(EmvReason.MESSAGE_REQUEST_ONLINE)
                .setMessage("onConfirm PIN")
                .build()
        )
    }


    override fun onReturnBatchData(cardTlvData: String) {
        _posInputDatas.postValue(
            _posInputDatas.value?.update(
                pan = GetCardNo(),
                track2 = mKernelApi.getValByTag(0x57),
                szCardSeqNo = mKernelApi.getValByTag(0x5F34),
                szExpDate = mKernelApi.getValByTag(0x5F24),
                file55 = cardTlvData
            )
        )
        _isRequestOnlineProcess.postValue(true)
    }


    override fun onReturnTransactionResult(transactionResult: TransactionResult) {
        Log.i(TAG, "onReturnTransactionResult: ")
        val TVR = mKernelApi.getValByTag(0x95)
        Log.i(TAG, "  onReturnTransactionResult TVR:$TVR")
        val TSI = mKernelApi.getValByTag(0x9B)
        Log.i(TAG, "  onReturnTransactionResult TSI:$TSI")
        val IAD = mKernelApi.getValByTag(0x9F10)
        Log.i(TAG, "  onReturnTransactionResult IAD:$IAD")
        val AC = mKernelApi.getValByTag(0x9F26)
        Log.i(TAG, "  onReturnTransactionResult AC:$AC")

        if (transactionResult == TransactionResult.ONLINE_APPROVAL || transactionResult == TransactionResult.OFFLINE_APPROVAL) {
            mKernelApi.abortKernel()
            //TODO pago aceptado
            _result.postValue(UrovoResult.Success(posInputDatas.value!!))
        } else if (transactionResult == TransactionResult.ONLINE_DECLINED) {
            mKernelApi.abortKernel()
            _result.postValue(UrovoResult.Error(Throwable("online decline!")))
        } else if (transactionResult == TransactionResult.OFFLINE_DECLINED) {
            _result.postValue(UrovoResult.Error(Throwable("offline decline!")))
        } else if (transactionResult == TransactionResult.ICC_CARD_REMOVED) {
            _result.postValue(UrovoResult.Error(Throwable("icc card removed!")))
        } else if (transactionResult == TransactionResult.TERMINATED) {
            _result.postValue(UrovoResult.Error(Throwable("terminated!")))
        } else if (transactionResult == TransactionResult.CANCELED_OR_TIMEOUT) {
            _result.postValue(UrovoResult.Error(Throwable("canceled or timeout!")))
        } else if (transactionResult == TransactionResult.CANCELED) {
            _result.postValue(UrovoResult.Error(Throwable("canceled!")))
        } else if (transactionResult == TransactionResult.CARD_BLOCKED_APP_FAIL) {
            _result.postValue(UrovoResult.Error(Throwable("card blocked!")))
        } else if (transactionResult == TransactionResult.APPLICATION_BLOCKED_APP_FAIL) {
            _result.postValue(UrovoResult.Error(Throwable("application blocked!")))
        } else if (transactionResult == TransactionResult.INVALID_ICC_DATA) {
            _result.postValue(UrovoResult.Error(Throwable("invalid icc data!")))
        } else if (transactionResult == TransactionResult.NO_EMV_APPS) {
            _result.postValue(UrovoResult.Error(Throwable("no emv apps!")))
        }
    }

    override fun onRequestDisplayText(displayText: ContantPara.DisplayText) {
        Log.i(TAG, "MainActivity  onRequestDisplayText")
        _reasonsEMV.postValue(
            EmvReasonsModel.Builder()
                .setReason(EmvReason.MESSAGE_CARD_MESSAGE)
                .setMessage(displayText.name)
                .build()
        )
    }


    override fun onRequestOfflinePINVerify(
        pinEntrySource: PinEntrySource,
        pinEntryType: Int,
        bundle: Bundle
    ) {
        if (pinEntrySource == PinEntrySource.KEYPAD) { //use in os 8.0
            val pinTryTimes: Int = mKernelApi.getOfflinePinTryTimes()
            bundle.putInt("PinTryTimes", pinTryTimes)
            bundle.putBoolean("isFirstTime", true)
            proc_offlinePin(pinEntryType, pinTryTimes == 1, bundle)
        } else {
        }
    }

    //if you call mEmvApi.getIssuerScriptResult() ,this will callback
    override fun onReturnIssuerScriptResult(issuerScriptResult: IssuerScriptResult, s: String) {
        if (issuerScriptResult == IssuerScriptResult.SUCCESS) {
            Log.d(TAG, "onReturnIssuerScriptResult:$s")
        } else if (issuerScriptResult == IssuerScriptResult.NO_OR_FAIL) {
            Log.d(TAG, "not issuer script result")
        }
    }


    //contactless Tip message callback
    override fun onNFCrequestTipsConfirm(msgID: NfcTipMessageID, msg: String) {
        Log.i(TAG, "onNFCrequestTipsConfirm: $msg")
        _reasonsEMV.postValue(
            EmvReasonsModel.Builder()
                .setReason(EmvReason.MESSAGE_CARD_MESSAGE)
                .setMessage(msg)
                .build()
        )
    }


    //go online send data to host ,then import online result
    override fun onNFCrequestOnline() {
        //should send to host
        Log.d(TAG, "onNFCrequestOnline ")
        _isRequestOnlineProcess.postValue(true)

        _reasonsEMV.postValue(
            EmvReasonsModel.Builder().setMessage("onConfirm PIN")
                .setReason(EmvReason.MESSAGE_REQUEST_ONLINE).build()
        )
    }


    override fun onNFCrequestImportPin(type: Int, lasttimeFlag: Int, amt: String) {
        Log.i(TAG, "onNFCrequestImportPin: ")
        _reasonsEMV.postValue(
            EmvReasonsModel.Builder()
                .setReason(EmvReason.REQUEST_ONLINEPIN).setMessage("onConfirm PIN").build()
        )
        //TODO emv_proc_onlinePin()
    }


    override fun onNFCTransResult(result: NfcTransResult) {
        Log.i(TAG, "onNFCTransResult: ${result.name}")
        val value = mKernelApi.getValByTag(0x5F20)
        Log.i(TAG, "onNFCTransResult: 5F20=$value")
    }

    override fun onNFCErrorInfor(erroCode: NfcErrMessageID, strErrInfo: String) {
        Log.d(TAG, "onNFCErrorInfor: erroCode:$erroCode - onErrorInfor:$strErrInfo")
        _reasonsEMV.postValue(
            EmvReasonsModel.Builder()
                .setReason(EmvReason.MESSAGE_ERROR).setMessage(strErrInfo).build()
        )
    }


    //save data about go online or offline
    override fun onReturnNfcCardData(hashtable: Hashtable<String, String>) {
        val ksn = hashtable["KSN"]
        val TrackData = hashtable["TRACKDATA"]
        val EmvData = hashtable["EMVDATA"]
        val QPBOCType = hashtable["QPBOCTYPE"]
        _posInputDatas.postValue(_posInputDatas.value?.update(swipedMode = CardTypeConstant.RFID))
        var tagValue = mKernelApi.getValByTag(0x9F26)
        if (tagValue != null) {
            Log.d(TAG, "tagValue 0x9F26:$tagValue")
        }
        val Track = mKernelApi.getValByTag(0x57)
        Log.i(TAG, " Track:$Track")
        Log.i(TAG, " CardNo:" + GetCardNo())

        tagValue = mKernelApi.getValByTag(0x9F24) //use for contact or contactless
        if (tagValue != null) {
            Log.d(TAG, "tagValue 0x9F24:$tagValue")
        }
        val TVR = mKernelApi.getValByTag(0x95)
        Log.d(TAG, "tagValue 0x95:$TVR")

        tagValue = mKernelApi.getValByTag(0x9F41)
        if (tagValue != null) Log.d(TAG, "tagValue 0x9F41:$tagValue")
        tagValue = mKernelApi.getValByTag(0x9F1E)
        if (tagValue != null) Log.d(TAG, "tagValue 0x9F1E:$tagValue")
        tagValue = mKernelApi.getValByTag(0x5F34)
        if (tagValue != null) Log.d(TAG, "tagValue 0x5F34:$tagValue")


        val MstripMode = mKernelApi.mstripFlag
        if (MstripMode == 1) {
            val track2 = mKernelApi.getValByTag(0x9F6B)
            Log.d(TAG, "mStrip track2:$track2")
        }

        Log.d(TAG, "onReturnNfcCardData")
        Log.d(TAG, "KSN:$ksn")
        Log.d(TAG, "TrackData:$TrackData")
        Log.d(TAG, "EmvData:$EmvData")

        _posInputDatas.postValue(
            _posInputDatas.value?.update(
                pan = GetCardNo(),
                track2 = mKernelApi.getValByTag(0x57),
                szCardSeqNo = mKernelApi.getValByTag(0x5F34),
                szExpDate = mKernelApi.getValByTag(0x5F24),
                file55 = EmvData
            )
        )
    }


    private fun GetCardNo(): String {
        var cardno = EmvNfcKernelApi.getInstance().getValByTag(0x5A)
        if (cardno == null || cardno == "") {
            cardno = EmvNfcKernelApi.getInstance().getValByTag(0x57)
            if (cardno == null || cardno == "") return ""
            cardno = cardno.substring(0, cardno.uppercase(Locale.getDefault()).indexOf("D"))
        }

        if ((cardno[cardno.length - 1] == 'f') || (cardno[cardno.length - 1] == 'F')
            || (cardno[cardno.length - 1] == 'd') || (cardno[cardno.length - 1] == 'D')
        ) cardno = cardno.substring(0, cardno.length - 1)
        Log.i(TAG, "GetCardNo: $cardno")
        return cardno
    }

    fun proc_offlinePin(pinEntryType: Int, isLastPinTry: Boolean, bundle: Bundle): Int {
        var iret = 0

        // TODO Auto-generated method stub
        val emvBundle = bundle


        Log.d(
            "applog",
            "proc_offlinePin pinEntryType = $pinEntryType isLastPinTry=$isLastPinTry"
        )

        val paramVar = Bundle()
        paramVar.putInt("inputType", 3) //Offline PlainPin
        paramVar.putInt("CardSlot", 0)

        paramVar.putBoolean("sound", true)
        paramVar.putBoolean("onlinePin", false)
        paramVar.putBoolean("FullScreen", true)
        paramVar.putLong("timeOutMS", 60000)
        paramVar.putString("supportPinLen", "0,4,5,6,7,8,9,10,11,12")
        paramVar.putString("title", "Security Keyboard")
        paramVar.putBoolean("randomKeyboard", false)
        val pinTryTimes = bundle.getInt("PinTryTimes")
        val isFirst = bundle.getBoolean("isFirstTime", false)
        Log.d("applog", "PinTryTimes:$pinTryTimes")
        if (isLastPinTry) {
            if (isFirst) paramVar.putString("message", "Please input PIN \nLast PIN Try")
            else paramVar.putString("message", "Please input PIN \nWrong PIN \nLast Pin Try")
        } else {
            if (isFirst) paramVar.putString("message", "Please input PIN \n")
            else {
                paramVar.putString(
                    "message",
                    "Please input PIN \nWrong PIN \nPin Try Times:$pinTryTimes"
                )
            }
        }


        if (pinEntryType == 1) {
            paramVar.putInt("inputType", 4) //Offline CipherPin

            val pub = emvBundle.getByteArray("pub")
            val publen = emvBundle.getIntArray("publen")
            val exp = emvBundle.getByteArray("exp")
            val explen = emvBundle.getIntArray("explen")

            Log.d("applog", "ModuleLen = " + publen!![0] + ": " + Funs.bytesToHexString(pub))
            Log.d("applog", "ExponentLen = " + explen!![0] + ": " + Funs.bytesToHexString(exp))


            val ModuleLen = publen!![0]
            val ExponentLen = explen!![0]
            val Module = ByteArray(ModuleLen)
            val Exponent = ByteArray(ExponentLen)

            if (ModuleLen == 0 || ExponentLen == 0) {
                mKernelApi.sendOfflinePINVerifyResult(-198)
                return 0
            }

            System.arraycopy(pub, 0, Module, 0, ModuleLen)
            System.arraycopy(exp, 0, Exponent, 0, ExponentLen)

            paramVar.putInt("ModuleLen", ModuleLen) //Modulus length
            paramVar.putString("Module", Funs.bytesToHexString(Module)) //Module
            paramVar.putInt("ExponentLen", ExponentLen) //Exponent length
            paramVar.putString("Exponent", Funs.bytesToHexString(Exponent)) //Exponent
        }


        Log.d("applog", "proc_offlinePin getPinBlockEx start")

        paramVar.putInt("PinTryMode", 1)
        paramVar.putString("ErrorMessage", "Incorrect PIN, # More Retries")
        paramVar.putString("ErrorMessageLast", "Incorrect PIN, Last Chance")


        val se = SEManager()
        iret = se.getPinBlockEx(paramVar, object : IInputActionListener.Stub() {
            override fun onInputChanged(type: Int, result: Int, bundle: Bundle) {
                val resultBundle = bundle
                try {
                    //    7101~7115 The number of remaining PIN tries(7101 PIN BLOCKED   7102 the last one chance  7103 two chances ....)
                    //		7006 PIN length error
                    //		7010 防穷举出错
                    //		7016 Wrong PIN
                    //		7071 The return code is wrong
                    //		7072 IC command failed
                    //		7073 Card data error
                    //		7074 PIN BLOCKED
                    //		7075 Encryption error
                    //
                    //The offline PIN verification result is sent to the kernel
                    //   use api EmvApi.sendOfflinePINVerifyResult();
                    //		    (-198)     //Return code error
                    //		    (-202)     //IC command failed
                    //		    (-192)     //PIN BLOCKED
                    //          (-199)     //user cancel or Pinpad timeout
                    //		    (1)        //bypass
                    //		    (0)        //success

                    Log.i(
                        "applog",
                        "proc_offlinePin：getPinBlockEx===onInputChanged：type=$type，result=$result"
                    )

                    if (type == 2) { // entering PIN
                    } else if (type == 0) //bypass
                    {
                        if (result == 0) {
                            Log.d("applog", "proc_offlinePin bypass")
                            mKernelApi.sendOfflinePINVerifyResult(1) //bypass
                        } else {
                            mKernelApi.sendOfflinePINVerifyResult(-198) //return code error
                        }
                    } else if (type == 3) //Offline plaintext
                    {
                        Log.d("applog", "proc_offlinePin Plaintext offline")
                        if (result == 0) {
                            mKernelApi.sendOfflinePINVerifyResult(0) //Offline plaintext verify successfully
                        } else { //Incorrect PIN, try again
                            val arg1Str = result.toString() + ""
                            if (arg1Str.length >= 4 && "71" == arg1Str.subSequence(0, 2)) {
                                if ("7101" == arg1Str) {
                                    mKernelApi.sendOfflinePINVerifyResult(-192) //PIN BLOCKED
                                } else {
                                    if ("7102" == arg1Str) {
                                        emvBundle.putBoolean("isFirstTime", false)
                                        emvBundle.putInt("PinTryTimes", 1)
                                        proc_offlinePin(
                                            pinEntryType,
                                            true,
                                            emvBundle
                                        ) //try again the last pin try
                                    } else {
                                        emvBundle.putBoolean("isFirstTime", false)
                                        emvBundle.putInt(
                                            "PinTryTimes",
                                            (arg1Str.substring(2, 4).toInt() - 1)
                                        )
                                        proc_offlinePin(pinEntryType, false, emvBundle) //try again
                                    }
                                }
                            } else if ("7074" == arg1Str) {
                                mKernelApi.sendOfflinePINVerifyResult(-192) //PIN BLOCKED
                            } else if ("7072" == arg1Str || "7073" == arg1Str) {
                                mKernelApi.sendOfflinePINVerifyResult(-202) //IC command failed
                            } else {
                                mKernelApi.sendOfflinePINVerifyResult(-198) //Return code error
                            }
                        }
                    } else if (type == 4) //Offline encryption PIN
                    {
                        Log.d("applog", "proc_offlinePin Offline encryption")
                        if (result == 0) {
                            mKernelApi.sendOfflinePINVerifyResult(0) //Offline encryption PIN verify successfully
                        } else {
                            val arg1Str = result.toString() + ""
                            if (arg1Str.length >= 4 && "71" == arg1Str.subSequence(0, 2)) {
                                if ("7101" == arg1Str) {
                                    mKernelApi.sendOfflinePINVerifyResult(-192) //PIN BLOCKED
                                } else {
                                    Log.d(
                                        "applog",
                                        "proc_offlinePin Offline encryption entry pin again"
                                    )
                                    if ("7102" == arg1Str) {
                                        emvBundle.putBoolean("isFirstTime", false)
                                        emvBundle.putInt("PinTryTimes", 1)
                                        proc_offlinePin(
                                            pinEntryType,
                                            true,
                                            emvBundle
                                        ) //try again the last pin try
                                    } else {
                                        emvBundle.putBoolean("isFirstTime", false)
                                        emvBundle.putInt(
                                            "PinTryTimes",
                                            (arg1Str.substring(2, 4).toInt() - 1)
                                        )
                                        proc_offlinePin(pinEntryType, false, emvBundle) //try again
                                    }
                                }
                            } else if ("7074" == arg1Str) {
                                mKernelApi.sendOfflinePINVerifyResult(-192) //PIN BLOCKED
                            } else if ("7072" == arg1Str || "7073" == arg1Str) {
                                mKernelApi.sendOfflinePINVerifyResult(-202) //IC command failed(card removed)
                            } else {
                                mKernelApi.sendOfflinePINVerifyResult(-198) //Return code error
                            }
                        }
                    } else if (type == 0x10) // click Cancel button
                    {
                        mKernelApi.sendOfflinePINVerifyResult(-199) //cancel
                    } else if (type == 0x11) // pinpad timed out
                    {
                        mKernelApi.sendOfflinePINVerifyResult(-199) //timeout
                    } else {
                        mKernelApi.sendOfflinePINVerifyResult(-198) //Return code error
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.d("applog", "proc_offlinePin exception")
                }
            }
        })
        if (iret == -3 || iret == -4) mKernelApi.sendOfflinePINVerifyResult(-198)
        return iret
    }

}
