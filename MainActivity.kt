package com.example.vpnclient

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vpnclient.adapter.ConfigAdapter
import com.example.vpnclient.data.AppDatabase
import com.example.vpnclient.databinding.ActivityMainBinding
import com.example.vpnclient.model.VlessConfig
import com.example.vpnclient.util.VlessParser
import com.example.vpnclient.vpn.VpnManager
import com.google.android.material.fab.FloatingActionButton
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var configAdapter: ConfigAdapter
    private lateinit var database: AppDatabase
    private lateinit var vpnManager: VpnManager
    private var configs = mutableListOf<VlessConfig>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализируем Timber логирование
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getInstance(this)
        vpnManager = VpnManager(this)

        setupUI()
        loadConfigs()
    }

    private fun setupUI() {
        // Настройка RecyclerView
        configAdapter = ConfigAdapter(
            configs = configs,
            onItemClick = { config -> showConfigDetails(config) },
            onDeleteClick = { config -> deleteConfig(config) },
            onConnectClick = { config -> connectVpn(config) }
        )

        binding.configsRecyclerView.apply {
            adapter = configAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        // FAB для добавления конфига
        binding.addConfigFab.setOnClickListener {
            showAddConfigDialog()
        }

        // Кнопка импорта
        binding.importConfigBtn.setOnClickListener {
            showImportDialog()
        }

        // Кнопка управления VPN
        binding.vpnStatusBtn.setOnClickListener {
            if (vpnManager.isConnected()) {
                vpnManager.disconnect()
            }
        }
    }

    private fun loadConfigs() {
        lifecycleScope.launch {
            val allConfigs = database.vlessConfigDao().getAllConfigs()
            configs.clear()
            configs.addAll(allConfigs)
            configAdapter.notifyDataSetChanged()
            updateVpnStatus()
        }
    }

    private fun showAddConfigDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Добавить конфиг")
            .setMessage("Введите VLESS ссылку или конфигурацию")
            .setView(createConfigInputLayout())
            .setPositiveButton("Добавить") { _, _ ->
                val input = (it as? AlertDialog)?.findViewById<EditText>(R.id.configInput)?.text?.toString()
                if (!input.isNullOrEmpty()) {
                    addConfigFromLink(input)
                }
            }
            .setNegativeButton("Отмена", null)
            .create()
        dialog.show()
    }

    private fun createConfigInputLayout(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 16, 16, 16)
        }

        val input = EditText(this).apply {
            id = R.id.configInput
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "vless://uuid@host:port?..."
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        layout.addView(input)
        return layout
    }

    private fun showImportDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Импортировать конфиги")
            .setItems(arrayOf("Из буфера обмена", "Из файла")) { _, which ->
                when (which) {
                    0 -> importFromClipboard()
                    1 -> importFromFile()
                }
            }
            .create()
        dialog.show()
    }

    private fun importFromClipboard() {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            val link = clip?.getItemAt(0)?.text?.toString() ?: return

            addConfigFromLink(link)
        } catch (e: Exception) {
            Timber.e(e, "Error importing from clipboard")
            showError("Ошибка импорта: ${e.message}")
        }
    }

    private fun importFromFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/*"
        }
        startActivityForResult(intent, IMPORT_FILE_CODE)
    }

    private fun addConfigFromLink(link: String) {
        lifecycleScope.launch {
            try {
                val config = VlessParser.parseVlessLink(link.trim())
                if (config != null) {
                    database.vlessConfigDao().insertConfig(config)
                    loadConfigs()
                    showSuccess("Конфиг добавлен")
                } else {
                    showError("Не удалось распарсить ссылку")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing VLESS link")
                showError("Ошибка: ${e.message}")
            }
        }
    }

    private fun showConfigDetails(config: VlessConfig) {
        val intent = Intent(this, ConfigDetailActivity::class.java)
        intent.putExtra("config", config)
        startActivity(intent)
    }

    private fun deleteConfig(config: VlessConfig) {
        lifecycleScope.launch {
            database.vlessConfigDao().deleteConfig(config)
            loadConfigs()
            showSuccess("Конфиг удален")
        }
    }

    private fun connectVpn(config: VlessConfig) {
        lifecycleScope.launch {
            try {
                vpnManager.startVpn(config)
                updateVpnStatus()
            } catch (e: Exception) {
                Timber.e(e, "VPN connection error")
                showError("Ошибка подключения: ${e.message}")
            }
        }
    }

    private fun updateVpnStatus() {
        if (vpnManager.isConnected()) {
            binding.vpnStatusText.text = "VPN подключен"
            binding.vpnStatusBtn.text = "Отключить"
        } else {
            binding.vpnStatusText.text = "VPN отключен"
            binding.vpnStatusBtn.text = "Подключить"
        }
    }

    private fun showSuccess(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Ошибка")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    companion object {
        const val IMPORT_FILE_CODE = 1001
    }
}
