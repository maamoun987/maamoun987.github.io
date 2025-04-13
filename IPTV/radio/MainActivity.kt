package com.example.nesmat

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var categorySpinner: Spinner
    private lateinit var radioRecyclerView: RecyclerView
    private lateinit var nowPlayingLayout: LinearLayout
    private lateinit var nowPlayingText: TextView
    private lateinit var stopButton: ImageButton
    private lateinit var muteButton: ImageButton
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var updateButton: Button
    private lateinit var exitButton: Button

    private var isMuted = false
    private var currentChannelIndex = -1
    private val channels = mutableListOf<RadioChannel>()
    private val displayedChannels = mutableListOf<RadioChannel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force dark theme
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        setContentView(R.layout.activity_main)

        initializeViews()
        setupControls()
        setupCategorySpinner()
        setupNotificationPermission()
        fetchChannels()
    }

    private fun initializeViews() {
        categorySpinner = findViewById(R.id.categorySpinner)
        radioRecyclerView = findViewById(R.id.radioRecyclerView)
        nowPlayingLayout = findViewById(R.id.nowPlayingLayout)
        nowPlayingText = findViewById(R.id.nowPlayingText)
        stopButton = findViewById(R.id.stopButton)
        muteButton = findViewById(R.id.muteButton)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        emptyView = findViewById(R.id.emptyView)
        updateButton = findViewById(R.id.updateButton)
        exitButton = findViewById(R.id.exitButton)

        radioRecyclerView.layoutManager = LinearLayoutManager(this)
        radioRecyclerView.adapter = RadioAdapter(displayedChannels) { position -> playChannel(position) }

        // Add divider between items in RecyclerView
        val dividerItemDecoration = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        ContextCompat.getDrawable(this, R.drawable.divider)?.let { dividerItemDecoration.setDrawable(it) }
        radioRecyclerView.addItemDecoration(dividerItemDecoration)
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
        updateButton.setOnClickListener { fetchChannels() }

        // Add exit button functionality
        exitButton.setOnClickListener { finish() }
    }

    private fun setupCategorySpinner() {
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCategory = parent?.getItemAtPosition(position).toString()
                filterChannelsByCategory(selectedCategory)

                // Show the current category
                nowPlayingText.text = "تشاهد الآن: $selectedCategory"
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
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

    private fun fetchChannels() {
        loadingIndicator.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://raw.githubusercontent.com/") // قاعدة URL عامة
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val service = retrofit.create(RadioService::class.java)
                val radioJsonUrl = "https://raw.githubusercontent.com/maamoun987/maamoun987.github.io/main/IPTV/radio.json"

                val response = service.getChannels(radioJsonUrl)

                if (response.isNotEmpty()) {
                    channels.clear()
                    channels.addAll(response)
                    setupCategorySpinnerData()
                    filterChannelsByCategory(channels.firstOrNull()?.category ?: "")
                } else {
                    showEmptyState()
                }
            } catch (e: Exception) {
                showEmptyState()
                Log.e("MainActivity", "Error fetching channels: ${e.message}")
            } finally {
                loadingIndicator.visibility = View.GONE
            }
        }
    }

    private fun setupCategorySpinnerData() {
        val categories = channels.map { it.category }.distinct()
        val adapter = ArrayAdapter(this, R.layout.spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
    }

    private fun filterChannelsByCategory(category: String) {
        displayedChannels.clear()
        displayedChannels.addAll(channels.filter { it.category == category })
        radioRecyclerView.adapter?.notifyDataSetChanged()
        toggleEmptyState()
    }

    private fun playChannel(position: Int) {
        if (position in displayedChannels.indices) {
            currentChannelIndex = position
            val channel = displayedChannels[position]

            Intent(this, RadioPlayerService::class.java).apply {
                action = "PLAY"
                putExtra("STREAM_URL", channel.url)
                putExtra("CHANNEL_NAME", channel.name)
                putExtra("CHANNEL_INDEX", position)
                startService(this)
            }

            updateNowPlaying(channel.name)
        }
    }

    private fun stopRadio() {
        Intent(this, RadioPlayerService::class.java).apply {
            action = "STOP"
            startService(this)
        }
        nowPlayingLayout.visibility = View.GONE
        currentChannelIndex = -1
    }

    private fun toggleMute() {
        isMuted = !isMuted
        Intent(this, RadioPlayerService::class.java).apply {
            action = "MUTE"
            startService(this)
        }
        muteButton.setImageResource(
            if (isMuted) android.R.drawable.ic_lock_silent_mode
            else android.R.drawable.ic_lock_silent_mode_off
        )
    }

    private fun setVolume(level: Int) {
        Intent(this, RadioPlayerService::class.java).apply {
            action = "SET_VOLUME"
            putExtra("VOLUME_LEVEL", level)
            startService(this)
        }
        isMuted = (level == 0)
        muteButton.setImageResource(
            if (isMuted) android.R.drawable.ic_lock_silent_mode
            else android.R.drawable.ic_lock_silent_mode_off
        )
    }

    private fun updateNowPlaying(channelName: String) {
        nowPlayingLayout.visibility = View.VISIBLE
        nowPlayingText.text = "يتم الاستماع الآن إلى: $channelName"
    }

    private fun toggleEmptyState() {
        if (displayedChannels.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            radioRecyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            radioRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showEmptyState() {
        emptyView.visibility = View.VISIBLE
        radioRecyclerView.visibility = View.GONE
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop radio service when app is closed
        stopRadio()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.info -> {
                showInfoDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("حول التطبيق")
            .setMessage("هذا التطبيق من تصميم المطور Maamoun987.")
            .setPositiveButton("حسناً") { _, _ -> }
            .show()
    }
    infoButton.setOnClickListener {
        Toast.makeText(this, "Information about the app", Toast.LENGTH_SHORT).show()
    }
}