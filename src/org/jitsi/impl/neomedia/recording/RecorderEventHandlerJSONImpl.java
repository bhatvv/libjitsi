/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.recording;

import net.sf.fmj.media.Log;

import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.recording.*;
import org.jitsi.util.*;
import org.json.simple.*;

import java.io.*;
import java.util.*;

/**
 * Implements a <tt>RecorderEventHandler</tt> which handles
 * <tt>RecorderEvents</tt> by writing them to a file in JSON format.
 *
 * @author Boris Grozev
 */
public class RecorderEventHandlerJSONImpl implements RecorderEventHandler {
	/**
	 * The <tt>Logger</tt> used by the <tt>RecorderEventHandlerJSONImpl</tt>
	 * class and its instances for logging output.
	 */
	private static final Logger logger = Logger
			.getLogger(RecorderEventHandlerJSONImpl.class);

	/**
	 * Compares <tt>RecorderEvent</tt>s by their instant (e.g. timestamp).
	 */
	private static final Comparator<RecorderEvent> eventComparator = new Comparator<RecorderEvent>() {
		@Override
		public int compare(RecorderEvent a, RecorderEvent b) {
			return ((Long) a.getInstant()).compareTo(b.getInstant());
		}
	};

	File file;
	private boolean closed = false;

	private final List<RecorderEvent> audioEvents = new LinkedList<RecorderEvent>();

	private final List<RecorderEvent> videoEvents = new LinkedList<RecorderEvent>();

	private static HashMap<String, String> endpointMapping = new HashMap<String, String>();

	/**
	 * {@inheritDoc}
	 */
	public RecorderEventHandlerJSONImpl(String filename) throws IOException {
		file = new File(filename);
		if (!file.createNewFile())
			throw new IOException("File exists or cannot be created: " + file);

		if (!file.canWrite())
			throw new IOException("Cannot write to file: " + file);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean handleEvent(RecorderEvent ev) {
		logger.setLevelInfo();
		logger.info("\n \n \n \n Test Log \n \n \n \n ");
		if (closed)
			return false;

		MediaType mediaType = ev.getMediaType();
		RecorderEvent.Type type = ev.getType();
		long duration = ev.getDuration();
		long ssrc = ev.getSsrc();

		/*
		 * For a RECORDING_ENDED event without a valid instant, find it's
		 * associated (i.e. with the same SSRC) RECORDING_STARTED event and
		 * compute the RECORDING_ENDED instance based on its duration.
		 */
		if (RecorderEvent.Type.RECORDING_ENDED.equals(type)
				&& ev.getInstant() == -1 && duration != -1) {
			List<RecorderEvent> events = MediaType.AUDIO.equals(mediaType) ? audioEvents
					: videoEvents;

			RecorderEvent start = null;
			for (RecorderEvent e : events) {

				if (RecorderEvent.Type.RECORDING_STARTED.equals(e.getType())
						&& e.getSsrc() == ssrc) {
					start = e;
					break;
				}
			}

			if (start != null)
				ev.setInstant(start.getInstant() + duration);
		}

		if (MediaType.AUDIO.equals(mediaType)) {
			audioEvents.add(ev);
			logger.info("Adding audio event with src:" + ev.getSsrc());
		} else if (MediaType.VIDEO.equals(mediaType)) {
			videoEvents.add(ev);
			logger.info("Adding video event with src:" + ev.getSsrc());
		}

		try {
			writeAllEvents();
		} catch (IOException ioe) {
			logger.warn("Failed to write recorder events to file: ");
			return false;
		}

		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void close() {
		// XXX do we want to write everything again?
		try {
			writeAllEvents();
		} catch (IOException ioe) {
			logger.info("Failed to write recorder events to file: ");
		} finally {
			closed = true;
		}
	}

	private void writeAllEvents() throws IOException {
		Collections.sort(audioEvents, eventComparator);
		Collections.sort(videoEvents, eventComparator);
		int nbAudio = audioEvents.size();
		int nbVideo = videoEvents.size();

		Integer videoSize = nbVideo;
		Integer audioSize = nbAudio;

		log("Video Size :" + videoSize.toString());
		log("Audio Size :" + audioSize.toString());

		if (nbAudio + nbVideo > 0) {
			FileWriter writer = new FileWriter(file, false);

			writer.write("{\n");

			if (nbAudio > 0) {
				writer.write("  \"audio\" : [\n");
				writeEvents(audioEvents, writer);

				if (nbVideo > 0)
					writer.write("  ],\n\n");
				else
					writer.write("  ]\n\n");
			}

			if (nbVideo > 0) {
				writer.write("  \"video\" : [\n");
				logger.info("Writing video events");
				writeEvents(videoEvents, writer);
				writer.write("  ]\n");
			}

			writer.write("}\n");

			writer.close();
		}
	}

	private void writeEvents(List<RecorderEvent> events, FileWriter writer)
			throws IOException {
		int idx = 0;
		int size = events.size();
		for (RecorderEvent ev : events) {
			if (++idx == size)
				writer.write("    " + getJSON(ev) + "\n");
			else
				writer.write("    " + getJSON(ev) + ",\n");
		}
	}

	@SuppressWarnings("unchecked")
	private String getJSON(RecorderEvent ev) {
		JSONObject json = new JSONObject();
		json.put("instant", ev.getInstant());

		json.put("type", ev.getType().toString());

		MediaType mediaType = ev.getMediaType();
		if (mediaType != null)
			json.put("mediaType", mediaType.toString());

		if (mediaType.VIDEO.equals(mediaType)) {
			logger.info("Just now wrote a video event to json with srcc:"
					+ ev.getSsrc());
		}

		json.put("ssrc", ev.getSsrc());

		long audioSsrc = ev.getAudioSsrc();
		if (audioSsrc != -1)
			json.put("audioSsrc", audioSsrc);

		RecorderEvent.AspectRatio aspectRatio = ev.getAspectRatio();
		if (aspectRatio != RecorderEvent.AspectRatio.ASPECT_RATIO_UNKNOWN)
			json.put("aspectRatio", aspectRatio.toString());

		long rtpTimestamp = ev.getRtpTimestamp();
		if (rtpTimestamp != -1)
			json.put("rtpTimestamp", rtpTimestamp);

		String endpointId = ev.getEndpointId();
		if (endpointId != null)
			json.put("endpointId", endpointId);

		String participantName = ev.getParticipantName();
		json.put("participant Name", participantName);

		String filename = ev.getFilename();
		if (filename != null) {
			String bareFilename = filename;
			int idx = filename.lastIndexOf('/');
			int len = filename.length();
			if (idx != -1 && idx != len - 1)
				bareFilename = filename.substring(1 + idx, len);

			json.put("filename", bareFilename);
		}

		createMapping(ev);

		return json.toJSONString();

	}

	private void createMapping(RecorderEvent ev) {

		if (endpointMapping.get(ev.getEndpointId()) == null) {
			logger.info("Adding endpoint ID to the mapping:"
					+ ev.getEndpointId());
			endpointMapping.put(ev.getEndpointId(), ev.getParticipantName());
			for (String s : endpointMapping.keySet()) {
				Log.info("\n " + s + ":");
				logger.info(endpointMapping.get(s));
			}
		}

	}

	public static HashMap<String, String> getEndpointMapping() {
		return endpointMapping;
	}

	private static void log(String s) {
		System.err.println(s);
	}

}
