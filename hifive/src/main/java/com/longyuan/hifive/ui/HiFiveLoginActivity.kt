package com.longyuan.hifive.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hfopen.sdk.manager.HFOpenApi
import com.longyuan.hifive.HiFiveConfig
import com.longyuan.hifive.HiFiveRequestManager
import com.longyuan.hifive.R
import com.longyuan.hifive.manager.HiFiveLoading
import com.longyuan.hifive.manager.SPUtils
import kotlinx.android.synthetic.main.activity_hi_five_login.*

class HiFiveLoginActivity : AppCompatActivity() {
    private val appIdKey = "appId"
    private val serverCodeKey = "serverCode"
    private val clientIdKey = "clientId"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = ContextCompat.getColor(this,R.color.hifive_background)
        if (HiFiveConfig.isLoginHiFive){
            if (HiFiveConfig.isNetSystemUsable(this)){
                startMusicActivity()
            } else {
                HiFiveConfig.showToast(this,HiFiveConfig.networkErrorMsg)
                finish()
            }
            return
        }

        setContentView(R.layout.activity_hi_five_login)

        SPUtils.getData(this,appIdKey).let {
            etHiFiveInputAppId.setText(it.ifBlank { "3faeec81030444e98acf6af9ba32752a" })
        }
        SPUtils.getData(this,serverCodeKey).let {
            etHiFiveInputServerCode.setText(it.ifBlank { "59b1aff189b3474398" })
        }
        SPUtils.getData(this,clientIdKey).let {
            if (it.isNotBlank())etHiFiveInputClientId.setText(it)
        }

        tvHiFiveInputConfirm.setOnClickListener {view ->
            val appId = etHiFiveInputAppId.text.toString().trim()
            val serverCode = etHiFiveInputServerCode.text.toString().trim()
            val clientId = etHiFiveInputClientId.text.toString().trim()
            when{
                appId.isBlank() -> HiFiveConfig.showToast(view.context,"appId????????????")
                serverCode.isBlank() -> HiFiveConfig.showToast(view.context,"serverCode????????????")
                clientId.isBlank() -> HiFiveConfig.showToast(view.context,"clientId????????????")
                else -> {
                    SPUtils.saveData(view.context,clientIdKey,clientId)
                    if (HiFiveConfig.isNetSystemUsable(view.context)) {
                        HFOpenApi.setVersion("V4.2.0").registerApp(application, appId, serverCode, clientId)
                        HiFiveLoading.show(this)
                        //???clientId??????????????????hifive??????sdk
                        HiFiveRequestManager.loginHiFive(clientId, failed = { msg ->
                            HiFiveLoading.cancel()
                            HiFiveConfig.showToast(view.context,msg)
                        }){
                            SPUtils.saveData(view.context,appIdKey,appId)
                            SPUtils.saveData(view.context,serverCodeKey,serverCode)
                            //???????????????????????????????????????????????????id??????????????????????????????
                            HiFiveRequestManager.updateCollectionSheetId(failed = { startMusicActivity() }){
                                HiFiveConfig.collectionSheetId = it
                                HiFiveRequestManager.updateCollection(1,HiFiveConfig.localCollectionSize,
                                    failed = { startMusicActivity() }){ _,list ->
                                    HiFiveConfig.musicCollection.clear()
                                    HiFiveConfig.musicCollection.addAll(list)
                                    startMusicActivity()
                                }
                            }
                        }
                    }else{
                        HiFiveConfig.showToast(view.context,HiFiveConfig.networkErrorMsg)
                    }
                }
            }
        }

        if (HiFiveConfig.localFile.size < 1)HiFiveConfig.initLocalData(this)
    }

    private fun startMusicActivity(){
        HiFiveLoading.cancel()
        HiFiveConfig.isLoginHiFive = true
        startActivity(Intent(this,HiFiveActivity::class.java))
        finish()
    }
}