package kth.csc.inda.pockettheremin;

import kth.csc.inda.pockettheremin.Oscillator.Waveform;
import kth.csc.inda.pockettheremin.soundeffects.Autotune;
import kth.csc.inda.pockettheremin.soundeffects.Autotune.AutotuneKey;
import kth.csc.inda.pockettheremin.soundeffects.Autotune.AutotuneScale;
import kth.csc.inda.pockettheremin.soundeffects.Portamento;
import kth.csc.inda.pockettheremin.soundeffects.SoundEffect;
import kth.csc.inda.pockettheremin.soundeffects.Tremolo;
import kth.csc.inda.pockettheremin.soundeffects.Vibrato;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This activity provides a playable theremin by using either the device's
 * accelerometer or touch input.
 * 
 * To be frank, this is not like a theremin at all since there are no radiowaves
 * involved, but it's still a fun toy.
 * 
 */
public class PocketThereminActivity extends Activity implements
		SensorEventListener, OnTouchListener {
	/*
	 * Input
	 */
	SensorManager sensors;
	Sensor sensor;
	View touchAmplitude, touchFrequency;
	boolean useSensor;

	/*
	 * Output.
	 */
	AudioManager audioManager;
	AudioTrack audioStream;
	AsyncTask<?, ?, ?> soundGenerator;
	float pitch, volume;
	float maxFrequency, minFrequency, frequencyRange;
	float maxAmplitude, minAmplitude, amplitudeRange;
	int octaveRange;

	Waveform waveform;
	boolean useChiptuneMode;

	boolean usePortamento;
	int portamentoSpeed;

	boolean useTremolo;
	int tremoloSpeed, tremoloDepth;

	boolean useVibrato;
	int vibratoSpeed, vibratoDepth;

	boolean useAutotune;
	AutotuneKey key;
	AutotuneScale scale;

	/**
	 * When the app is started: load graphics and find resources.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// The activity is being created.

		/*
		 * Set layout resource.
		 */
		setContentView(R.layout.main);

		/*
		 * Find input.
		 */
		sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		touchFrequency = findViewById(R.id.touchFrequency);
		touchAmplitude = findViewById(R.id.touchAmplitude);

		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
	}

	/**
	 * Register sensor listeners, load user preferences and start the audio
	 * thread.
	 */
	@Override
	protected void onResume() {
		super.onResume();
		// The activity has become visible (it is now "resumed").

		/*
		 * Remind user to turn on volume.
		 */
		if (0.1 > audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
				/ (float) audioManager
						.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
			alert(getString(R.string.low_volume_notice));

		/*
		 * Get user preferences.
		 */
		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(this);
		useSensor = preferences.getBoolean("accelerometer", false);
		useTremolo = preferences.getBoolean("tremolo", false);
		tremoloSpeed = Integer.parseInt(preferences.getString("tremolo_speed",
				"10"));
		tremoloDepth = Integer.parseInt(preferences.getString("tremolo_depth",
				"100"));

		useVibrato = preferences.getBoolean("vibrato", false);
		vibratoSpeed = Integer.parseInt(preferences.getString("vibrato_speed",
				"5"));
		vibratoDepth = Integer.parseInt(preferences.getString("vibrato_depth",
				"10"));

		usePortamento = preferences.getBoolean("portamento", false);
		useChiptuneMode = preferences.getBoolean("chiptuneMode", false);
		octaveRange = Integer.parseInt(preferences
				.getString("octaveRange", "2"));
		waveform = Waveform.valueOf(preferences.getString("waveform", "SINE"));

		useAutotune = preferences.getBoolean("autotune", false);
		key = AutotuneKey.valueOf(preferences.getString("key", "A"));
		scale = AutotuneScale.valueOf(preferences.getString("scale", "MAJOR"));

		/*
		 * Calculate operational values.
		 */
		minFrequency = 440.00f / (float) Math.pow(2, octaveRange / 2);
		maxFrequency = 440.00f * (float) Math.pow(2, octaveRange / 2);
		frequencyRange = maxFrequency - minFrequency;

		minAmplitude = 0.0f; // TODO Get from system.
		maxAmplitude = 1.0f; // TODO Get from system.
		amplitudeRange = maxAmplitude - minAmplitude;

		/*
		 * Update graphics.
		 */
		((TextView) findViewById(R.id.amplitudeMax)).setText(maxAmplitude + "");
		((TextView) findViewById(R.id.amplitudeMin)).setText(minAmplitude + "");
		((TextView) findViewById(R.id.frequencyMin)).setText(minFrequency
				+ "Hz");
		((TextView) findViewById(R.id.frequencyMax)).setText(maxFrequency
				+ "Hz");

		/*
		 * Register input listeners and start audio thread.
		 */
		if (useSensor) {
			sensors.registerListener(this, sensor,
					SensorManager.SENSOR_DELAY_GAME);
			soundGenerator = new AudioThread().execute();
		} else {
			touchFrequency.setOnTouchListener(this);
			touchAmplitude.setOnTouchListener(this);
			soundGenerator = new AudioThread().execute();
		}
	}

	/**
	 * Unregister sensor listeners and kill the audio thread (i.e free up system
	 * resources when the app isn't used anymore).
	 */
	@Override
	protected void onPause() {
		super.onPause();

		if (sensors != null)
			sensors.unregisterListener(this);

		if (touchFrequency != null)
			touchFrequency.setOnTouchListener(null);

		if (touchAmplitude != null)
			touchAmplitude.setOnTouchListener(null);

		if (soundGenerator != null)
			soundGenerator.cancel(true);

		if (audioStream != null)
			audioStream.release();
	}

	/**
	 * Setup a menu for this activity that allows the user to reach app settings
	 * and an "About this app" page.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, 0, 0, "About");
		menu.add(Menu.NONE, 1, 1, "Settings");
		return super.onCreateOptionsMenu(menu);
	}

	/**
	 * Make sure the activity menu actually does what's intended when a menu
	 * item is selected.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case 0:
			startActivity(new Intent(this, AboutActivity.class));
			return true;
		case 1:
			startActivity(new Intent(this, PreferencesActivity.class));
			return true;
		}
		return false;
	}

	/**
	 * Provide feedback to the user.
	 */
	private void alert(String s) {
		Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
	}

	/**
	 * Set pitch and volume by touch.
	 */
	@Override
	public boolean onTouch(View view, MotionEvent event) {
		for (int i = 0; i < event.getPointerCount(); i++)
			Log.d("Pointer", "Pointer " + (i) + ": x=" + event.getX(i) + ", y="
					+ event.getY(i));

		final int action = event.getAction();
		switch (action & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			// soundGenerator = new AudioThread().execute();

		case MotionEvent.ACTION_MOVE:
			switch (view.getId()) {
			case R.id.touchFrequency:
				pitch = event.getY(event.getPointerId(0))
						* (frequencyRange / view.getHeight());
			case R.id.touchAmplitude:
				volume = event.getX(event.getPointerId(1))
						* (amplitudeRange / view.getWidth());
			}

		case MotionEvent.ACTION_UP:
			/*
			 * while (volume > 0) volume -= 0.0000001f;
			 * 
			 * if (soundGenerator != null) soundGenerator.cancel(true);
			 * 
			 * if (audioStream != null) audioStream.release();
			 */
		}

		return true; // Yes, I want to know about movement.
	}

	@Override
	public final void onAccuracyChanged(Sensor sensor, int accuracy) {
		Log.d(sensor.getName(), "onAccuracyChanged: " + accuracy);
	}

	/**
	 * Set pitch and volume by interpreting sensor values.
	 */
	@Override
	public final void onSensorChanged(SensorEvent event) {
		Sensor sensor = event.sensor;
		Log.d("onSensorChanged (" + sensor.getName() + ")", "Value: "
				+ event.values[0] + ", Resolution: " + sensor.getResolution()
				+ ", Range: " + sensor.getMaximumRange());

		/*
		 * Calibrate sensor stepping.
		 */
		float pitchStep = frequencyRange / sensor.getResolution();
		float volumeStep = amplitudeRange / sensor.getResolution();

		/*
		 * Modulate pitch and amplitude just with the accelerometer, or modulate
		 * pitch with the light sensor and amplitude with the proximity sensor.
		 */
		if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

			// TODO Filter noise with a high-pass filter.
			volume = event.values[0] * volumeStep;
			pitch = (event.values[1] + SensorManager.STANDARD_GRAVITY)
					* pitchStep;
		}
	}

	/**
	 * Generate sounds in a separate thread (as an AsyncTask) by sampling a
	 * frequency. The frequency is modulated by the user input.
	 */
	private class AudioThread extends AsyncTask<Float, Float, Void> {
		boolean play;
		int sampleSize = 256;
		int sampleRate = 44100;
		Oscillator oscillator;
		SoundEffect autotune, tremolo, portamento, vibrato;
		float frequency, amplitude;

		protected void onPreExecute() {
			play = true;
			oscillator = new Oscillator(waveform, sampleSize, sampleRate);

			// Effects
			if (useAutotune)
				autotune = new Autotune(key, scale, octaveRange);
			if (useTremolo)
				tremolo = new Tremolo(tremoloSpeed, tremoloDepth, sampleRate, sampleSize);
			if (usePortamento)
				portamento = new Portamento();
			if (useVibrato)
				vibrato = new Vibrato(vibratoSpeed, vibratoDepth, sampleRate, sampleSize);

			// Audio stream
			int audioFormat = (useChiptuneMode) ? AudioFormat.ENCODING_PCM_8BIT
					: AudioFormat.ENCODING_PCM_16BIT;
			audioStream = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
					AudioFormat.CHANNEL_CONFIGURATION_MONO, audioFormat,
					AudioTrack
							.getMinBufferSize(sampleRate,
									AudioFormat.CHANNEL_CONFIGURATION_MONO,
									audioFormat), AudioTrack.MODE_STREAM);
			audioStream.play();
		}

		protected Void doInBackground(Float... params) {
			while (play) {

				/*
				 * Set initial frequency according to input.
				 */
				frequency = PocketThereminActivity.this.pitch + minFrequency;

				if (frequency > maxFrequency)
					frequency = maxFrequency;

				if (frequency < minFrequency)
					frequency = minFrequency;

				/*
				 * Set initial volume according to input.
				 */
				amplitude = PocketThereminActivity.this.volume;
				audioStream.setStereoVolume(amplitude, amplitude);

				/*
				 * Effects chain.
				 */
				if (autotune != null) // TODO Combine autotune and portamento.
					frequency = autotune.modify(frequency);

				if (vibrato != null)
					frequency = vibrato.modify(frequency);

				if (portamento != null)
					frequency = portamento.modify(frequency);

				if (tremolo != null) {
					amplitude = tremolo.modify(amplitude);
					audioStream.setStereoVolume(amplitude, amplitude);
				}

				/*
				 * Generate sound samples.
				 */
				short[] samples = oscillator.getSamples(frequency);

				// Write samples.
				audioStream.write(samples, 0, sampleSize);

				// Return amplitude and frequency to the GUI thread.
				publishProgress(frequency, amplitude);
			}
			return null;
		}

		protected void onProgressUpdate(Float... progress) {
			((TextView) findViewById(R.id.frequency)).setText(progress[0]
					.shortValue() + "Hz");
			((TextView) findViewById(R.id.amplitude)).setText(progress[1]
					.shortValue() + "");
		}

		@Override
		protected void onCancelled() {
			play = false;
		}
	}
}