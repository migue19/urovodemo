package com.drabatx.urovopaymentapp.presentation.view.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import com.drabatx.urovopaymentapp.R
import com.drabatx.urovopaymentapp.data.model.pos2.models.PosInputDatas
import com.drabatx.urovopaymentapp.presentation.view.viewmodels.CardReaderViewModel
import com.drabatx.urovopaymentapp.presentation.view.widgets.MyTopBar
import com.urovo.i9000s.api.emv.ContantPara
import kotlinx.coroutines.Dispatchers

class CardReaderScreen(val posInputDatas: PosInputDatas) : Screen {
    private val TAG = "CardReaderScreen"

    @Composable
    override fun Content() {
        val monto by remember { mutableStateOf("0.00") }
        val noTarjeta by remember { mutableStateOf("") }

        Log.i(TAG, "Content: ${posInputDatas.iTransNo}")
        val viewModel: CardReaderViewModel = getViewModel()

        Scaffold(topBar = { MyTopBar("Leer Tarjeta") }) { peddingValues ->
            Column(
                modifier = Modifier.padding(
                    vertical = peddingValues.calculateTopPadding(), horizontal = 16.dp
                )
            ) {
                Row {
                    Text(text = "Monto")
                    Text(text = posInputDatas.amt)
                }
                Row {
                    Text(text = "No. de Tarjeta")
                    Text(text = noTarjeta)
                }
                Box(modifier = Modifier.weight(1f)) {
                    Image(
                        painter = painterResource(id = R.drawable.icon_card_9100_en),
                        contentDescription = stringResource(R.string.please_read_the_card_in_the_following_way),
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
        LaunchedEffect(Dispatchers.IO) {
            viewModel.startKernelCoroutine(ContantPara.CheckCardMode.INSERT_OR_TAP)
            viewModel.initEmvListener(posInputDatas)
        }
    }
}