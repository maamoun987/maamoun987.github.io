package com.example.nesmat

import android.Manifest
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var radioListView: ListView
    private lateinit var nowPlayingLayout: LinearLayout
    private lateinit var nowPlayingText: TextView
    private lateinit var stopButton: ImageButton
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var muteButton: ImageButton
    private lateinit var updateButton: Button
    private lateinit var exitButton: Button
    private lateinit var aboutButton: ImageButton
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var emptyView: TextView

    private var isMuted = false
    private var currentChannelIndex = -1
    private val channels = mutableListOf<RadioChannel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupControls()
        setupNotificationPermission()
        fetchChannels()
        handleIntent(intent)
    }

    private fun initializeViews() {
        radioListView = findViewById(R.id.radioListView)
        nowPlayingLayout = findViewById(R.id.nowPlayingLayout)
        nowPlayingText = findViewById(R.id.nowPlayingText)
        stopButton = findViewById(R.id.stopButton)
        val stopButton = findViewById<ImageButton>(R.id.stopButton)
        val layoutParams = stopButton.layoutParams
        layoutParams.width = 64 // العرض بالبكسل
        layoutParams.height = 64 // الارتفاع بالبكسل
        stopButton.layoutParams = layoutParams

        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        muteButton = findViewById(R.id.muteButton)
        updateButton = findViewById(R.id.updateButton)
        exitButton = findViewById(R.id.exitButton)
        aboutButton = findViewById(R.id.aboutButton)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        emptyView = findViewById(R.id.emptyView)
    }

    private fun setupControls() {
        stopButton.setOnClickListener { stopRadio() }

        volumeSeekBar.max = 100
        volumeSeekBar.progress = 100
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) setVolume(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        muteButton.setOnClickListener { toggleMute() }
        updateButton.setOnClickListener { if (isNetworkAvailable()) fetchChannels() else showToast("لا يوجد اتصال بالإنترنت!") }
        exitButton.setOnClickListener { stopRadio(); finishAffinity() }

        aboutButton.setOnClickListener {
            val styledTextView = TextView(this).apply {
                text = "تطبيق راديو نسمات\n" +
                        "إصدار 2.0\n\n" +
                        "تم تصميم هذا التطبيق بواسطة مأمون القنطار\n" +
                        "Facebook: Maamoun Alkntar"
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER // محاذاة النص في المنتصف
                gravity = android.view.Gravity.CENTER // محاذاة النص عموديًا وأفقيًا
                setPadding(32, 32, 32, 32) // إضافة مسافات حول النص
                textSize = 16f // حجم النص
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white)) // لون النص الأبيض
            }

            val alertDialog = AlertDialog.Builder(this)
                .setTitle("حول التطبيق")
                .setView(styledTextView) // استخدام TextView مخصص
                .setPositiveButton("تم") { dialog, _ -> dialog.dismiss() }
                .create()

            alertDialog.window?.setBackgroundDrawableResource(android.R.color.background_dark) // خلفية داكنة للمربع
            alertDialog.show()

            // تحسين لون زر "تم"
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_light)) // لون زر أزرق فاتح
            }
        }
        radioListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            playChannel(position)
        }
    }

    private fun setupNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (!isGranted) showToast("الإذن مطلوب للإشعارات")
            }

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    private fun fetchChannels() {
        loadingIndicator.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://drive.google.com/") // قاعدة URL وهمية لأننا نستخدم @Url
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val service = retrofit.create(RadioService::class.java)

                // رابط Google Drive المباشر
                val googleDriveUrl = "https://drive.usercontent.google.com/download?id=1BW4KGukFA7F24j2eefqKWn1HyTC4Hc4G&export=download&authuser=0"

                // طلب البيانات باستخدام الرابط الديناميكي
                val response = service.getChannels(googleDriveUrl)

                if (response.isNotEmpty()) {
                    channels.clear()
                    channels.addAll(response)
                    setupChannelListAdapter()
                } else {
                    showToast("لا توجد قنوات متاحة!")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "خطأ: ${e.message}")
                showToast("فشل في التحديث!")
            } finally {
                loadingIndicator.visibility = View.GONE
                toggleEmptyState()
            }
        }
    }
    private fun setupChannelListAdapter() {
        val adapter = object : ArrayAdapter<RadioChannel>(
            this,
            android.R.layout.simple_list_item_1,
            channels
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.text = channels[position].name

                // تحديد لون النص ليكون دائمًا أبيض
                view.setTextColor(ContextCompat.getColor(context, android.R.color.white))

                // تحديد الخلفية لتكون دائمًا بلون ثابت
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.fixed_background_color)) // قم بتعريف هذا اللون في ملف colors.xml

                return view
            }
        }
        radioListView.adapter = adapter
    }
    private fun playChannel(position: Int) {
        if (position in channels.indices) {
            currentChannelIndex = position
            val channel = channels[position]

            Intent(this, RadioPlayerService::class.java).apply {
                action = "PLAY"
                putExtra("STREAM_URL", channel.url)
                putExtra("CHANNEL_NAME", channel.name)
                putExtra("CHANNEL_INDEX", position)
                startService(this)
            }

            updateNowPlaying(channel.name)
            updateChannelList()
        }
    }

    private fun stopRadio() {
        startService(Intent(this, RadioPlayerService::class.java).apply {
            action = "STOP"
        })
        nowPlayingLayout.visibility = View.GONE
        currentChannelIndex = -1
        updateChannelList()
    }

    private fun setVolume(level: Int) {
        startService(Intent(this, RadioPlayerService::class.java).apply {
            action = "SET_VOLUME"
            putExtra("VOLUME_LEVEL", level)
        })
        isMuted = (level == 0)
        updateMuteButton()
    }

    private fun toggleMute() {
        isMuted = !isMuted
        startService(Intent(this, RadioPlayerService::class.java).apply {
            action = "MUTE"
        })
        updateMuteButton()
    }

    private fun updateMuteButton() {
        muteButton.setImageResource(
            if (isMuted) android.R.drawable.ic_lock_silent_mode
            else android.R.drawable.ic_lock_silent_mode_off
        )
    }

    private fun updateNowPlaying(channelName: String) {
        nowPlayingLayout.visibility = View.VISIBLE
        nowPlayingText.text = "يتم الاستماع الآن إلى: $channelName"
    }

    private fun updateChannelList() {
        (radioListView.adapter as? ArrayAdapter<*>)?.notifyDataSetChanged()
    }

    private fun toggleEmptyState() {
        emptyView.visibility = if (channels.isEmpty()) View.VISIBLE else View.GONE
        radioListView.visibility = if (channels.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("CURRENT_CHANNEL_NAME")?.let { updateNowPlaying(it) }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    private fun setupUpdateButton() {
        updateButton.setOnClickListener {
            if (isNetworkAvailable()) {
                fetchChannels()
                Toast.makeText(this, "تم تحديث القنوات من قاعدة بيانات مأمون", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "لا يوجد اتصال بالإنترنت!", Toast.LENGTH_SHORT).show()
            }
        }
    }

}