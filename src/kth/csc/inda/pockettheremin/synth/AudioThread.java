package kth.csc.inda.pockettheremin.synth;

import kth.csc.inda.pockettheremin.utils.Global;
import kth.csc.inda.pockettheremin.utils.Global.G;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * This is a linked list of samplers that writes audio buffers to the audio
 * hardware by using Android's AudioTrack API.
 * 
 * Before generating each buffer of samples the thread reads several global
 * values. These values are set by the user in the main activity.
 * 
 * @see <a href=
 *      "http://developer.android.com/reference/android/media/AudioTrack.html"
 *      >Check out the Android Dev Guide for more information.</a>
 */
public class AudioThread implements Runnable {
	private Thread thread;
	private boolean play;
	private Synth synth;
	private Delay delay;
	private Send aux;
	private Sampler sampler;

	public static final int SAMPLE_RATE = AudioTrack
			.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
	public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
	public static final int BUFFER_SIZE = AudioTrack.getMinBufferSize(
			SAMPLE_RATE, AudioFormat.CHANNEL_CONFIGURATION_MONO, AUDIO_FORMAT);
	public static final int SAMPLES_PER_BUFFER = BUFFER_SIZE / 2;

	private final AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC,
			AudioThread.SAMPLE_RATE, AudioFormat.CHANNEL_CONFIGURATION_MONO,
			AudioThread.AUDIO_FORMAT, AudioThread.BUFFER_SIZE,
			AudioTrack.MODE_STREAM);
	private final byte[] buffer = new byte[AudioThread.BUFFER_SIZE];

	public AudioThread() {
		/*
		 * Audio chain.
		 */
		synth = new Synth(null); // First in chain.
		delay = new Delay(synth);
		aux = new Send(delay);
		this.sampler = aux; // Last in chain.

		if (Global.DEBUG)
			Log.d(this.getClass().getSimpleName(), "Sample rate: "
					+ AudioThread.SAMPLE_RATE + ", Buffer size:"
					+ buffer.length + ", Minimum buffer size: "
					+ AudioThread.BUFFER_SIZE);
	}

	public void start() {
		play = true;
		if (thread == null) {
			thread = new Thread(this);
			thread.setPriority(Thread.MAX_PRIORITY);
			thread.start();
		} else
			throw new IllegalThreadStateException();
	}

	@Override
	public void run() {
		track.play();

		while (play) {

			/*
			 * Update settings.
			 */
			synth.setFrequency(G.frequency.get());
			synth.setVolume(G.volume.get());
			synth.setShape(G.synthShape);
			synth.setImd(G.synthIMD);
			synth.setPortamentoSpeed(G.portamentoSpeed);
			synth.setTremoloShape(G.tremoloShape);
			synth.setTremoloSpeed(G.tremoloSpeed);
			synth.setTremoloDepth(G.tremoloDepth);
			synth.setVibratoShape(G.vibratoShape);
			synth.setVibratoSpeed(G.vibratoSpeed);
			synth.setVibratoDepth(G.vibratoDepth);
			delay.setBPM(G.delayBPM);
			delay.setMix(G.delayMix);
			delay.setFeedback(G.delayFeedback);

			/*
			 * Write and play samples.
			 */
			sampler.fillBuffer(buffer);
			track.write(buffer, 0, BUFFER_SIZE);
		}

		track.flush();
		track.stop();
		track.release();
	}

	public void cancel() {
		play = false;

	}
}