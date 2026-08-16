package com.lukeneedham.stickerboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.elvishew.xlog.XLog
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.lukeneedham.stickerboard.utilities.StickerImporter
import com.lukeneedham.stickerboard.utilities.Toaster
import com.lukeneedham.stickerboard.utilities.startLogger
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Maximum number of stickers allowed in a single pack, mirrors StickerImporter's limit */
private const val MAX_PACK_SIZE = 128

/** MainActivity class inherits from the AppCompatActivity class - provides the settings view */
class MainActivity : AppCompatActivity() {
	// onCreate
	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var backupSharedPreferences: SharedPreferences
	private lateinit var contextView: View
	private lateinit var toaster: Toaster
	private lateinit var packSpinner: Spinner
	private var pendingPackName: String? = null

	/**
	 * Sets up content view, shared prefs, etc.
	 *
	 * @param savedInstanceState saved state
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		// Inflate view
		super.onCreate(savedInstanceState)

		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		if (!isOnboardingComplete()) {
			startActivity(Intent(this, OnboardingActivity::class.java))
			finish()
			return
		}

		setContentView(R.layout.activity_main)

		startLogger(filesDir)

		XLog.i("=".repeat(80))
		XLog.i("Loaded $packageName:$localClassName")

		val markwon: Markwon = Markwon.create(this)
		val featuresText = findViewById<TextView>(R.id.features_text)
		markwon.setMarkdown(featuresText, getString(R.string.features_text))

		val linksText = findViewById<TextView>(R.id.links_text)
		markwon.setMarkdown(linksText, getString(R.string.links_text))

		// Set late-init attrs
		this.backupSharedPreferences =
			this.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

		XLog.i("Loading private shared preferences: ${this.sharedPreferences.all}")
		XLog.i("Loading backup shared preferences: ${this.backupSharedPreferences.all}")
		this.contextView = findViewById(R.id.activityMainRoot)
		this.toaster = Toaster(baseContext)
		this.packSpinner = findViewById(R.id.addPhotoPackSpinner)
		refreshStickerDirPath()
		// Update UI with config
		seekBar(findViewById(R.id.iconsPerXSb), findViewById(R.id.iconsPerXLbl), "iconsPerX", 4)
		seekBar(findViewById(R.id.iconSizeSb), findViewById(R.id.iconSizeLbl), "iconSize", 80, 20)
		toggle(findViewById(R.id.showBackButton), "showBackButton", true) {}
		toggle(findViewById(R.id.showSearchButton), "showSearchButton", true) {}
		toggle(findViewById(R.id.vibrate), "vibrate", true) {}
		toggle(findViewById(R.id.vertical), "vertical") { isChecked: Boolean ->
			findViewById<SeekBar>(R.id.iconSizeSb).isEnabled = !isChecked
		}
		toggle(findViewById(R.id.restoreOnClose), "restoreOnClose", false) {}
		toggle(findViewById(R.id.scroll), "scroll", false) {}
		toggle(findViewById(R.id.insensitive_sort), "insensitiveSort", false) {}
		toggle(findViewById(R.id.pngFallback), "isPngFallback", true) {}

		val versionText: TextView = findViewById(R.id.versionText)
		var version = getString(R.string.version_text)
		try {
			val packageInfo = packageManager.getPackageInfo(packageName, 0)
			version = packageInfo.versionName ?: version
		} catch (_: PackageManager.NameNotFoundException) {
		}

		versionText.text = version
		XLog.i("Version: $version")
	}

	/**
	 * Handles ACTION_OPEN_DOCUMENT_TREE result and adds stickerDirPath, lastUpdateDate to
	 * this.sharedPreferences and resets recentCache, compatCache
	 */
	private val chooseDirResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == Activity.RESULT_OK) {
				val editor = sharedPreferences.edit()
				val uri = result.data?.data
				val stickerDirPath = result.data?.data.toString()
				val contentResolver = applicationContext.contentResolver

				val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
					Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				if (uri != null) {
					contentResolver.takePersistableUriPermission(uri, takeFlags)
				}

				editor.putString("stickerDirPath", stickerDirPath)
				editor.putString("lastUpdateDate", Calendar.getInstance().time.toString())
				editor.putString("recentCache", "")
				editor.putString("compatCache", "")
				editor.apply()
				refreshStickerDirPath()
				importStickers(stickerDirPath)
			}
		}

	/** Handles the result of the gallery photo picker and copies the chosen photos into pendingPackName */
	private val pickPhotosLauncher =
		registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
			val packName = pendingPackName
			pendingPackName = null
			if (packName != null && uris.isNotEmpty()) {
				addPhotosToPack(packName, uris)
			}
		}

	private val saveFileLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == RESULT_OK) {
				result.data?.data?.also { uri ->
					val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
					val currentDate = Date()
					val logFileName = dateFormatter.format(currentDate)
					val file = File(filesDir, "logs/$logFileName")
					if (file.exists()) {
						contentResolver.openOutputStream(uri)?.use { outputStream ->
							file.inputStream().use { inputStream ->
								inputStream.copyTo(outputStream)
							}
						}
					}
				}
			}
		}

	/**
	 * Called on button press to launch settings
	 *
	 * @param ignoredView: View
	 */
	fun enableKeyboard(ignoredView: View) {
		val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
		startActivity(intent)
	}

	/**
	 * Called on button press to choose a new directory
	 *
	 * @param ignoredView: View
	 */
	fun chooseDir(ignoredView: View) {
		val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
		chooseDirResultLauncher.launch(intent)
	}

	/**
	 * Called on button press to save logs
	 *
	 * @param ignoredView: View
	 */
	fun saveLogs(ignoredView: View) {
		val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
			addCategory(Intent.CATEGORY_OPENABLE)
			type = "text/plain"
			putExtra(Intent.EXTRA_TITLE, "stickerboard.log")
		}
		saveFileLauncher.launch(saveIntent)
	}

	/**
	 * reloadStickers
	 *
	 * Call this function when a user taps the reload stickers button. If we have a set stickerDirPath, call importStickers()
	 *
	 * @param ignoredView: View
	 */
	fun reloadStickers(ignoredView: View) {
		val stickerDirPath = this.sharedPreferences.getString(
			"stickerDirPath",
			null,
		)
		if (stickerDirPath != null) {
			importStickers(stickerDirPath)
		} else {
			this.toaster.toast(
				getString(R.string.imported_034),
			)
		}
	}

	/**
	 * Called on button press to add photo(s) from the device's photo gallery to a sticker pack.
	 * Uses the pack currently selected in addPhotoPackSpinner, prompting for a new pack name first
	 * if the "+ Create new pack" option is selected.
	 *
	 * @param ignoredView: View
	 */
	fun addPhotoFromGallery(ignoredView: View) {
		val selected = packSpinner.selectedItem as? String
		val newPackOption = getString(R.string.add_photo_new_pack_option)
		when (selected) {
			null -> toaster.toast(getString(R.string.add_photo_052))
			newPackOption -> showCreatePackDialog { packName ->
				pendingPackName = packName
				pickPhotosLauncher.launch(
					PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
				)
			}
			else -> {
				pendingPackName = selected
				pickPhotosLauncher.launch(
					PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
				)
			}
		}
	}

	/** Prompts the user for a new pack name, invoking onCreated once a valid, unused name is entered */
	private fun showCreatePackDialog(onCreated: (String) -> Unit) {
		val input = EditText(this)
		input.hint = getString(R.string.add_photo_new_pack_dialog_hint)
		val padding = resources.getDimensionPixelSize(R.dimen.card_margin)
		input.setPadding(padding, padding, padding, padding)

		AlertDialog.Builder(this)
			.setTitle(R.string.add_photo_new_pack_dialog_title)
			.setView(input)
			.setPositiveButton(R.string.add_photo_new_pack_dialog_positive) { _, _ ->
				val packName = input.text.toString().trim()
				when {
					packName.isEmpty() ||
						packName.contains('/') ||
						packName.contains('\\') ||
						packName == "." ||
						packName == ".." ->
						toaster.toast(getString(R.string.add_photo_053))

					File(filesDir, "stickers/$packName").exists() ->
						toaster.toast(getString(R.string.add_photo_054, packName))

					else -> onCreated(packName)
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	/** Copies the given gallery photo URIs into the given pack, up to MAX_PACK_SIZE stickers total */
	private fun addPhotosToPack(packName: String, uris: List<Uri>) {
		val packDir = File(filesDir, "stickers/$packName")
		lifecycleScope.launch(Dispatchers.IO) {
			packDir.mkdirs()
			var packSize = packDir.listFiles { file -> file.isFile }?.size ?: 0
			var addedCount = 0
			var skippedLimit = false
			for (uri in uris) {
				if (packSize >= MAX_PACK_SIZE) {
					skippedLimit = true
					break
				}
				if (copyPhotoToPack(uri, packDir)) {
					addedCount++
					packSize++
				}
			}

			withContext(Dispatchers.Main) {
				if (addedCount > 0) {
					toaster.toast(getString(R.string.add_photo_050, addedCount, packName))
					val editor = sharedPreferences.edit()
					editor.putInt(
						"numStickersImported",
						sharedPreferences.getInt("numStickersImported", 0) + addedCount,
					)
					editor.apply()
					refreshStickerDirPath()
					refreshPackSpinner(packName)
				} else if (!skippedLimit) {
					toaster.toast(getString(R.string.add_photo_051, packName))
				}
				if (skippedLimit) {
					toaster.toast(getString(R.string.imported_032, MAX_PACK_SIZE, packName))
				}
			}
		}
	}

	/** Copies a single gallery photo into packDir, returning true on success */
	private fun copyPhotoToPack(uri: Uri, packDir: File): Boolean {
		return try {
			val mimeType = contentResolver.getType(uri)
			val extension =
				mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
			val destPhoto =
				File(packDir, "gallery_${System.currentTimeMillis()}_${System.nanoTime()}.$extension")
			val copied = contentResolver.openInputStream(uri)?.use { input ->
				destPhoto.outputStream().use { output ->
					input.copyTo(output)
				}
				true
			} ?: false
			copied
		} catch (e: IOException) {
			XLog.e("There was an IOException when copying a gallery photo into a pack!")
			XLog.e(e)
			false
		}
	}

	/**
	 * Refreshes addPhotoPackSpinner with the current list of sticker packs (sub-directories of the
	 * internal stickers directory), plus an option to create a new pack.
	 *
	 * @param selectPack pack name to select afterwards, if present in the refreshed list
	 */
	private fun refreshPackSpinner(selectPack: String? = null) {
		val stickersDir = File(filesDir, "stickers")
		val packNames =
			stickersDir.listFiles { file -> file.isDirectory && !file.name.contains("__compatSticker__") }
				?.map { it.name }
				?.sorted()
				?: emptyList()
		val options = packNames + getString(R.string.add_photo_new_pack_option)

		val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
		packSpinner.adapter = adapter

		val targetIndex = selectPack?.let { packNames.indexOf(it) }?.takeIf { it >= 0 } ?: 0
		packSpinner.setSelection(targetIndex)
	}

	/** Import files from storage to internal directory */
	private fun importStickers(stickerDirPath: String) {
		toaster.toast(getString(R.string.imported_010))
		val button = findViewById<Button>(R.id.updateStickerPackInfoBtn)
		val button2 = findViewById<Button>(R.id.reloadStickerPackInfoBtn)
		val progressBar = findViewById<LinearProgressIndicator>(R.id.linearProgressIndicator)
		button.isEnabled = false
		button2.isEnabled = false

		lifecycleScope.launch(Dispatchers.IO) {
			val totalStickers =
				StickerImporter(baseContext, toaster, progressBar).importStickers(stickerDirPath)

			withContext(Dispatchers.Main) {
				if (toaster.messages.size > 0) {
					toaster.toastOnMessages()
				} else {
					toaster.toast(getString(R.string.imported_020, totalStickers))
				}

				val editor = sharedPreferences.edit()
				editor.putInt("numStickersImported", totalStickers)
				editor.apply()
				refreshStickerDirPath()
				button.isEnabled = true
				button2.isEnabled = true
			}
		}
	}

	/**
	 * Add toggle logic for each toggle/ checkbox in the layout
	 *
	 * @param compoundButton CompoundButton
	 * @param sharedPrefKey String - Id/Key of the SharedPreferences to update
	 * @param sharedPrefDefault Boolean - default value (default=false)
	 * @param callback (Boolean) -> Unit - Add custom behaviour with a callback - for instance to
	 * disable some options
	 */
	private fun toggle(
		compoundButton: CompoundButton,
		sharedPrefKey: String,
		sharedPrefDefault: Boolean = false,
		callback: (Boolean) -> Unit,
	) {
		compoundButton.isChecked =
			this.backupSharedPreferences.getBoolean(sharedPrefKey, sharedPrefDefault)
		callback(compoundButton.isChecked)
		compoundButton.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
			showChangedPrefText()
			callback(compoundButton.isChecked)
			val editor = this.backupSharedPreferences.edit()
			editor.putBoolean(sharedPrefKey, isChecked)
			editor.apply()
		}
	}

	/**
	 * Add seekbar logic for each seekbar in the layout
	 *
	 * @param seekBar SeekBar
	 * @param seekBarLabel TextView - the label with a value updated when the progress is changed
	 * @param sharedPrefKey String - Id/Key of the SharedPreferences to update
	 * @param sharedPrefDefault Int - default value
	 * @param multiplier Int - multiplier (used to update SharedPreferences and set the
	 * seekBarLabel)
	 */
	private fun seekBar(
		seekBar: SeekBar,
		seekBarLabel: TextView,
		sharedPrefKey: String,
		sharedPrefDefault: Int,
		multiplier: Int = 1,
	) {
		seekBarLabel.text =
			this.backupSharedPreferences.getInt(sharedPrefKey, sharedPrefDefault).toString()
		seekBar.progress =
			this.backupSharedPreferences.getInt(sharedPrefKey, sharedPrefDefault) / multiplier
		seekBar.setOnSeekBarChangeListener(
			object : OnSeekBarChangeListener {
				var progressMultiplier = sharedPrefDefault
				override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
					progressMultiplier = progress * multiplier
					seekBarLabel.text = progressMultiplier.toString()
				}

				override fun onStartTrackingTouch(seekBar: SeekBar) {}
				override fun onStopTrackingTouch(seekBar: SeekBar) {
					val editor = backupSharedPreferences.edit()
					editor.putInt(sharedPrefKey, progressMultiplier)
					editor.apply()
					showChangedPrefText()
				}
			},
		)
	}

	/** Reads saved sticker dir path from preferences */
	private fun refreshStickerDirPath() {
		findViewById<TextView>(R.id.stickerPackInfoPath).text =
			this.sharedPreferences.getString(
				"stickerDirPath", resources.getString(R.string.update_sticker_pack_info_path),
			)
		findViewById<TextView>(R.id.stickerPackInfoDate).text =
			this.sharedPreferences.getString(
				"lastUpdateDate", resources.getString(R.string.update_sticker_pack_info_date),
			)
		findViewById<TextView>(R.id.stickerPackInfoTotal).text =
			this.sharedPreferences.getInt("numStickersImported", 0).toString()
		refreshPackSpinner()
	}

	/**
	 * Checks whether the user has completed the onboarding flow. Installs that already had a
	 * sticker directory configured before onboarding existed are treated as already onboarded, so
	 * existing users aren't sent through it retroactively.
	 *
	 * @return Boolean true if onboarding should be skipped
	 */
	private fun isOnboardingComplete(): Boolean {
		if (this.sharedPreferences.getBoolean("onboardingComplete", false)) {
			return true
		}
		if (this.sharedPreferences.contains("stickerDirPath")) {
			this.sharedPreferences.edit().putBoolean("onboardingComplete", true).apply()
			return true
		}
		return false
	}

	/** Reusable function to warn about changing preferences */
	private fun showChangedPrefText() {
		this.toaster.toast(
			getString(R.string.pref_000),
		)
	}
}
